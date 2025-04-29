/**
 * This class contains the source code for Masking PII columns.
 */

package org.extremenetworks.com;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.kafka.common.cache.Cache;
import org.apache.kafka.common.cache.LRUCache;
import org.apache.kafka.common.cache.SynchronizedCache;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.connector.ConnectRecord;
import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.transforms.Transformation;
import org.apache.kafka.connect.transforms.util.SchemaUtil;
import org.apache.kafka.connect.transforms.util.SimpleConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.kafka.connect.transforms.util.Requirements.requireMap;
import static org.apache.kafka.connect.transforms.util.Requirements.requireStruct;

public abstract class MaskCustomFields<R extends ConnectRecord<R>> implements Transformation<R> {
    private static final Logger LOG = LoggerFactory.getLogger(MaskCustomFields.class);

    public static final ConfigDef CONFIG_DEF = new ConfigDef()
            .define(ConfigName.MaskFields, ConfigDef.Type.LIST, Collections.emptyList(), ConfigDef.Importance.HIGH,
                    "list of fields to mask")
            .define(ConfigName.TopicName, ConfigDef.Type.STRING, "", ConfigDef.Importance.HIGH,
                    "topic name in which fields are to be masked")
            .define(ConfigName.MaskType, ConfigDef.Type.STRING, "hash", ConfigDef.Importance.MEDIUM,
                    "type of masking to apply: 'hash' (SHA-256 hash) or 'redact' (replace with empty/zero values)");

    private static final String PURPOSE = "mask certain fields from certain tables with hash or redacted values";
    private static final Logger log = LoggerFactory.getLogger(MaskCustomFields.class);
    private String topicName;
    private List<String> maskFields;
    private String maskType;
    private final List<String> maskedFieldLogs = new ArrayList<>();
    private final List<String> fieldMetadataLogs = new ArrayList<>();
    private Cache<Schema, Schema> schemaUpdateCache;

    // Map for storing default values for different types when using mask mode
    private static final Map<Schema.Type, Object> DEFAULT_VALUES = new HashMap<>();

    static {
        DEFAULT_VALUES.put(Schema.Type.INT8, (byte) 0);
        DEFAULT_VALUES.put(Schema.Type.INT16, (short) 0);
        DEFAULT_VALUES.put(Schema.Type.INT32, 0);
        DEFAULT_VALUES.put(Schema.Type.INT64, 0L);
        DEFAULT_VALUES.put(Schema.Type.FLOAT32, 0.0f);
        DEFAULT_VALUES.put(Schema.Type.FLOAT64, 0.0d);
        DEFAULT_VALUES.put(Schema.Type.BOOLEAN, false);
        DEFAULT_VALUES.put(Schema.Type.STRING, "");
    }

    @Override
    public void configure(Map<String, ?> props) {
        final SimpleConfig config = new SimpleConfig(CONFIG_DEF, props);

        topicName = config.getString(ConfigName.TopicName);
        maskFields = config.getList(ConfigName.MaskFields);
        maskType = config.getString(ConfigName.MaskType);
        schemaUpdateCache = new SynchronizedCache<>(new LRUCache<Schema, Schema>(16));
        
        LOG.debug("MaskCustomFields configured: topicName={}, maskFields={}, maskType={}", topicName, maskFields,
                maskType);

    }

    @Override
    public R apply(R record) {
        maskedFieldLogs.clear();
        fieldMetadataLogs.clear();
        LOG.debug("apply method: topic={}, valueClass={}",
                record.topic(),
                operatingValue(record) != null ? operatingValue(record).getClass().getName() : "null");
        R result = (operatingSchema(record) == null) ? applySchemaless(record) : applyWithSchema(record);
        if (!maskedFieldLogs.isEmpty()) {
            LOG.info("Masked fields summary: topic='{}', key={}, fields=[{}]",
                    record.topic(), record.key(), String.join(", ", maskedFieldLogs));
        }
        if (!fieldMetadataLogs.isEmpty()) {
            LOG.info("Field metadata summary: topic='{}', key={}, fields=[{}]",
                    record.topic(), record.key(), String.join(", ", fieldMetadataLogs));
        }
        return result;
    }

    private R applySchemaless(R record) {
        final Map<String, Object> value = requireMap(operatingValue(record), PURPOSE);
        final Map<String, Object> updatedValue = new HashMap<>(value);

        if (topicName.equals(record.topic())) {
            for (String fieldName : maskFields) {
                if (updatedValue.containsKey(fieldName)) {
                    Object fieldValue = updatedValue.get(fieldName);
                    try {

                        LOG.info("applySchemaless inside for: topic='{}', partition={}, timestamp={}, key={}, field='{}'",
                        record.topic(),
                        record.kafkaPartition(),
                        record.timestamp(),
                        record.key(),
                        fieldName);
                        
                        if (fieldValue == null) {
                            updatedValue.put(fieldName, null);
                        } else if (fieldValue.toString().trim().isEmpty()) {
                            // Preserve empty strings instead of hashing them
                            updatedValue.put(fieldName, "");
                            LOG.debug("Empty string found for field {}, preserving empty value", fieldName);
                        } else if ("redact".equals(maskType)) {
                            // Apply masking by replacing with empty/zero values
                            if (fieldValue instanceof Number) {
                                updatedValue.put(fieldName, 0);
                            } else {
                                updatedValue.put(fieldName, "");
                            }
                            LOG.info("applySchemaless inside redact for: topic='{}', partition={}, timestamp={}, key={}, field='{}'",
                            record.topic(),
                            record.kafkaPartition(),
                            record.timestamp(),
                            record.key(),
                            fieldName);

                            
                        } else {
                            // Default to hashing
                            String maskedValue = DigestUtils.sha256Hex(fieldValue.toString());
                            updatedValue.put(fieldName, maskedValue);
                            LOG.info("applySchemaless inside hash for: topic='{}', partition={}, timestamp={}, key={}, field='{}'",
                            record.topic(),
                            record.kafkaPartition(),
                            record.timestamp(),
                            record.key(),
                            fieldName);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to mask field {}: {}", fieldName, e.getMessage());
                        updatedValue.put(fieldName, "masked");
                    }
                }
            }
        }

        return newRecord(record, null, updatedValue);
    }

    private R applyWithSchema(R record) {
        LOG.debug("applyWithSchema method: topic={}, valueClass={}",
                record.topic(),
                operatingValue(record) != null ? operatingValue(record).getClass().getName() : "null");

        Struct originalValue = requireStruct(operatingValue(record), PURPOSE);

        // 1) Retrieve or build updated schema
        Schema originalSchema = originalValue.schema();

        Schema updatedSchema = schemaUpdateCache.get(originalValue.schema());
        if (updatedSchema == null) {
            updatedSchema = makeUpdatedSchema(originalSchema, record);
            schemaUpdateCache.put(originalSchema, updatedSchema);
        }

        // 2) Build the top-level updated Struct from updatedSchema
        Struct updatedValue = new Struct(updatedSchema);
        // 3) Copy/mask fields
        for (Field field : originalSchema.fields()) {
            String fieldName = field.name();
            Schema.Type fieldType = field.schema().type();
            Object originalFieldValue = originalValue.get(field);

            fieldMetadataLogs.add(fieldName + ":" + fieldType);

            if (topicName.equals(record.topic()) && maskFields.contains(fieldName)) {
                // Mask entire field
                maskFieldValue(updatedValue, fieldName, originalFieldValue);
            } else if (fieldType == Schema.Type.STRUCT && isNestedField(fieldName)) {
                LOG.debug("applyWithSchema Struct if else: field='{}', fieldType=STRUCT", fieldName);
                Struct nestedStructValue = originalValue.getStruct(fieldName);
                processNestedStruct(record, updatedValue, fieldName, nestedStructValue, "");
            } else if (fieldType == Schema.Type.ARRAY) {
                // Recurse into array
                LOG.debug("applyWithSchema Array if else: field='{}', fieldType=ARRAY", fieldName);
                List<?> arrayValue = originalValue.getArray(fieldName);
                processArrayField(record, updatedValue, fieldName, arrayValue, fieldName);
            } else {
                // Retain original for non-masked fields
                updatedValue.put(fieldName, originalFieldValue);
            }
        }

        return newRecord(record, updatedSchema, updatedValue);
    }

    private boolean isNestedField(String fieldName) {
        return maskFields.stream().anyMatch(f -> f.startsWith(fieldName + "."));
    }

    // -------------------------------------------------------------------------
    // processNestedStruct
    // -------------------------------------------------------------------------
    private void processNestedStruct(R record,
            Struct updatedParent,
            String nestedFieldName,
            Struct originalNested,
            String parentPath) {

        // Retrieve the *updated* field definition from the parent’s updated schema
        Field updatedFieldDef = updatedParent.schema().field(nestedFieldName);
        if (updatedFieldDef == null) {
            // If there's no matching field in the updated schema, just skip
            LOG.warn("Field '{}' not found in the updated schema. Skipping.", nestedFieldName);
            return;
        }

        LOG.debug("processNestedStruct: field='{}', parentPath='{}'", nestedFieldName, parentPath);

        // If the actual original nested Struct is null, just set null
        if (originalNested == null) {
            updatedParent.put(nestedFieldName, null);
            return;
        }

        // Build a new Struct using the updated field's schema
        Struct updatedNested = new Struct(updatedFieldDef.schema());

        // Build next path
        String currentPath = parentPath.isEmpty() ? nestedFieldName : parentPath + "." + nestedFieldName;

        // Iterate subfields in the *original* struct to get original data
        for (Field subField : originalNested.schema().fields()) {
            String subFieldName = subField.name();
            String fullFieldName = currentPath + "." + subFieldName;
            Schema.Type subFieldType = subField.schema().type();
            Object subFieldValue;

            // Retrieve subfield properly by type
            if (subFieldType == Schema.Type.STRUCT) {
                subFieldValue = originalNested.getStruct(subFieldName);
            } else if (subFieldType == Schema.Type.ARRAY) {
                subFieldValue = originalNested.getArray(subFieldName);
            } else {
                subFieldValue = originalNested.get(subFieldName);
            }

            if (topicName.equals(record.topic()) && maskFields.contains(fullFieldName)) {
                // Directly masked sub-field
                maskFieldValue(updatedNested, subFieldName, subFieldValue);
            } else if (subFieldType == Schema.Type.ARRAY && hasNestedFieldToMask(fullFieldName)) {
                processArrayField(record, updatedNested, subFieldName, (List<?>) subFieldValue, currentPath);
            } else if (subFieldType == Schema.Type.STRUCT && hasNestedFieldToMask(fullFieldName)) {
                if (subFieldValue != null) {
                    processNestedStruct(record, updatedNested, subFieldName, (Struct) subFieldValue, currentPath);
                } else {
                    updatedNested.put(subFieldName, null);
                }
            } else {
                // No masking or further recursion needed
                updatedNested.put(subFieldName, subFieldValue);
            }
        }

        // Put the updated nested struct in the parent
        updatedParent.put(nestedFieldName, updatedNested);
    }

    // -------------------------------------------------------------------------
    // processArrayField
    // -------------------------------------------------------------------------
    private void processArrayField(R record,
            Struct updatedParent,
            String arrayFieldName,
            List<?> originalArray,
            String parentPath) {

        Field updatedArrayFieldDef = updatedParent.schema().field(arrayFieldName);
        if (updatedArrayFieldDef == null) {
            LOG.debug("processArrayField - Array field '{}' not found in updated schema", arrayFieldName);
            return;
        }

        // Handle null arrays
        if (originalArray == null) {
            updatedParent.put(arrayFieldName, null);
            return;
        }

        // Get updated array and element schemas
        Schema updatedArraySchema = updatedArrayFieldDef.schema();
        Schema updatedElementSchema = updatedArraySchema.valueSchema();

        List<Object> updatedArray = new ArrayList<>();

        // Fix path duplication
        String newParentPath;
        if (parentPath.equals(arrayFieldName)) {
            // Don't duplicate the array name in the path
            newParentPath = arrayFieldName;
        } else {
            // Normal case
            newParentPath = parentPath.isEmpty() ? arrayFieldName : parentPath + "." + arrayFieldName;
        }

        LOG.debug("processArrayField - Processing array '{}' with {} elements, path='{}'",
                arrayFieldName, originalArray.size(), newParentPath);

        // Process each array element
        for (Object element : originalArray) {
            // For non-struct elements, pass them through directly
            if (!(element instanceof Struct)) {
                updatedArray.add(element);
                continue;
            }

            // For struct elements, process each field
            Struct originalElementStruct = (Struct) element;
            Struct updatedElementStruct = new Struct(updatedElementSchema);

            // Use the schema from the updated element struct, which has the correct field
            // types
            for (Field elementField : updatedElementSchema.fields()) {
                String subFieldName = elementField.name();
                String fullFieldName = newParentPath + "." + subFieldName;

                // Check if this field is masked in our config
                boolean isFieldMasked = maskFields.contains(fullFieldName.trim()) && topicName.equals(record.topic());

                LOG.debug("processArrayField Checking field '{}' in array element, masked={}", fullFieldName,
                        isFieldMasked);

                // Get the corresponding field from the original element's schema
                Field originalField = originalElementStruct.schema().field(subFieldName);
                if (originalField == null) {
                    LOG.warn("Field '{}' not found in original schema", subFieldName);
                    continue;
                }

                // Get field value from original based on its type
                Object subValue;
                Schema.Type originalType = originalField.schema().type();
                switch (originalType) {
                    case STRUCT:
                        subValue = originalElementStruct.getStruct(subFieldName);
                        break;
                    case ARRAY:
                        subValue = originalElementStruct.getArray(subFieldName);
                        break;
                    default:
                        subValue = originalElementStruct.get(subFieldName);
                }

                // Get the updated field type (which might be different if the field is masked)
                Schema.Type updatedType = elementField.schema().type();

                LOG.debug("processArrayField updatedType '{}': originalType={}, updatedType={}",
                        fullFieldName, originalType, updatedType);

                // Determine what to do with the field
                if (isFieldMasked) {

                    // This field needs to be masked
                    LOG.debug("processArrayField Masking array element field: '{}'", fullFieldName);
                    maskFieldValue(updatedElementStruct, subFieldName, subValue);
                } else if (topicName.equals(record.topic())
                        && originalType == Schema.Type.ARRAY
                        && hasNestedFieldToMask(fullFieldName)) {
                    LOG.debug("processArrayField Processing nested array: '{}'", fullFieldName);
                    List<?> nestedArray = originalElementStruct.getArray(subFieldName);
                    processArrayField(record, updatedElementStruct, subFieldName, nestedArray, newParentPath);
                } else if (topicName.equals(record.topic())
                        && originalType == Schema.Type.STRUCT
                        && hasNestedFieldToMask(fullFieldName)) {
                    // This is a struct field that contains masked fields
                    LOG.debug("processArrayField Processing nested struct: '{}'", fullFieldName);
                    Struct nestedStruct = originalElementStruct.getStruct(subFieldName);
                    if (nestedStruct != null) {
                        processNestedStruct(record, updatedElementStruct, subFieldName, nestedStruct, newParentPath);
                    } else {
                        updatedElementStruct.put(subFieldName, null);
                    }
                } else {
                    LOG.debug("processArrayField Regular field '{}': originalType={}, updatedType={}",
                            fullFieldName, originalType, updatedType);

                    // If schema type has changed from non-STRING to STRING (common for masked
                    // fields)
                    if (updatedType == Schema.Type.STRING && originalType != Schema.Type.STRING) {
                        LOG.debug("processArrayField Converting value for '{}' from {} to STRING", fullFieldName,
                                originalType);
                        String stringValue = subValue != null ? subValue.toString() : null;
                        updatedElementStruct.put(subFieldName, stringValue);
                    } else {
                        // Pass through original value for fields with compatible schema types
                        updatedElementStruct.put(subFieldName, subValue);
                    }
                }
            }

            updatedArray.add(updatedElementStruct);
        }

        updatedParent.put(arrayFieldName, updatedArray);
        LOG.debug("processArrayField Completed processing array '{}' with {} elements", arrayFieldName,
                updatedArray.size());
    }

    private boolean hasNestedFieldToMask(String parentPath) {
        String prefix = parentPath + ".";
        for (String field : maskFields) {
            if (field.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    // Mask a field value with SHA-256 hash or redact with empty/zero values based
    // on maskType
    private void maskFieldValue(Struct struct, String fieldName, Object fieldValue) {
        if (fieldValue == null) {
            struct.put(fieldName, null);
            LOG.debug("Field '{}' is null; setting to null", fieldName);

        } else if (fieldValue.toString().trim().isEmpty()) {
            // Preserve empty strings instead of hashing them
            struct.put(fieldName, "");
            LOG.debug("Empty string found for field {}, preserving empty value", fieldName);
        } else if ("redact".equals(maskType)) {
            // Apply masking by replacing with empty/zero values based on schema type
            Field field = struct.schema().field(fieldName);
            Schema.Type fieldType = field.schema().type();

            Object defaultValue = DEFAULT_VALUES.getOrDefault(fieldType, "");
            struct.put(fieldName, defaultValue);
            maskedFieldLogs.add(fieldName + "=redacted");
            
        } else {
            // Default to hashing
            String maskedValue = DigestUtils.sha256Hex(fieldValue.toString());
            struct.put(fieldName, maskedValue);
            maskedFieldLogs.add(fieldName + "=hashed");

        }
    }

    @Override
    public ConfigDef config() {
        return CONFIG_DEF;
    }

    @Override
    public void close() {
        schemaUpdateCache = null;
    }

    // -------------------------------------------------------------------------
    // makeUpdatedSchema
    // -------------------------------------------------------------------------
    private Schema makeUpdatedSchema(Schema originalSchema, R record) {
        LOG.debug("makeUpdatedSchema: topic='{}'", topicName);
        SchemaBuilder builder = SchemaUtil.copySchemaBasics(originalSchema, SchemaBuilder.struct());

        for (Field field : originalSchema.fields()) {
            String fieldName = field.name();
            Schema fieldSchema = field.schema();
            LOG.debug("makeUpdatedSchema - field={}, type={}", fieldName, fieldSchema.type());

            if (topicName.equals(record.topic()) && maskFields.contains(fieldName)) {
                // Entire field masked => hash => optional string, or redact => keep original
                // schema
                if ("hash".equals(maskType)) {
                    builder.field(fieldName, Schema.OPTIONAL_STRING_SCHEMA);
                } else {
                    builder.field(fieldName, fieldSchema);
                }
            } else if (fieldSchema.type() == Schema.Type.STRUCT && hasNestedFieldToMask(fieldName)) {
                Schema nestedUpdated = makeUpdatedNestedSchema(record, fieldSchema, fieldName);
                builder.field(fieldName, nestedUpdated);
            } else if (fieldSchema.type() == Schema.Type.ARRAY && hasNestedFieldToMask(fieldName)) {
                // If array elements are themselves structs, build updated element schema
                Schema elementSchema = fieldSchema.valueSchema();
                if (elementSchema.type() == Schema.Type.STRUCT) {
                    Schema updatedElement = makeUpdatedNestedSchema(record, elementSchema, fieldName);
                    builder.field(fieldName, SchemaBuilder.array(updatedElement).optional().build());
                } else {
                    builder.field(fieldName, fieldSchema);
                }
            } else {
                // No changes, keep original field schema
                builder.field(fieldName, fieldSchema);
            }
        }
        return builder.build();
    }

    // -------------------------------------------------------------------------
    // makeUpdatedNestedSchema
    // -------------------------------------------------------------------------
    private Schema makeUpdatedNestedSchema(R record, Schema originalSchema, String parentPath) {
        LOG.debug("makeUpdatedNestedSchema: parentPath='{}'", parentPath);

        SchemaBuilder builder = SchemaBuilder.struct();

        for (Field field : originalSchema.fields()) {
            String fieldName = field.name();
            Schema fieldSchema = field.schema();
            String fullName = parentPath + "." + fieldName;

            if (topicName.equals(record.topic()) && maskFields.contains(fullName)) {
                // Entire sub-field masked => optional string for hash, or keep original for
                // redact
                if ("hash".equals(maskType)) {
                    builder.field(fieldName, Schema.OPTIONAL_STRING_SCHEMA);
                } else {
                    builder.field(fieldName, fieldSchema);
                }
            } else if (fieldSchema.type() == Schema.Type.STRUCT && hasNestedFieldToMask(fullName)) {
                Schema updatedSub = makeUpdatedNestedSchema(record, fieldSchema, fullName);
                builder.field(fieldName, updatedSub);
            } else if (fieldSchema.type() == Schema.Type.ARRAY && hasNestedFieldToMask(fullName)) {
                // If the array element is a struct, build recursively
                if (fieldSchema.valueSchema().type() == Schema.Type.STRUCT) {
                    Schema updatedElem = makeUpdatedNestedSchema(record, fieldSchema.valueSchema(), fullName);
                    builder.field(fieldName, SchemaBuilder.array(updatedElem).optional().build());
                } else {
                    builder.field(fieldName, fieldSchema);
                }
            } else {
                builder.field(fieldName, fieldSchema);
            }
        }

        // Mark the entire nested schema optional so we can safely set null
        return builder.optional().build();
    }

    protected abstract Schema operatingSchema(R record);

    protected abstract Object operatingValue(R record);

    protected abstract R newRecord(R record, Schema updatedSchema, Object updatedValue);

    private interface ConfigName {
        String MaskFields = "mask.fields";
        String TopicName = "topic.name";
        String MaskType = "mask.type";
    }

    public static class Value<R extends ConnectRecord<R>> extends MaskCustomFields<R> {

        @Override
        protected Schema operatingSchema(R record) {
            return record.valueSchema();
        }

        @Override
        protected Object operatingValue(R record) {
            return record.value();
        }

        @Override
        protected R newRecord(R record, Schema updatedSchema, Object updatedValue) {
            return record.newRecord(record.topic(), record.kafkaPartition(), record.keySchema(), record.key(),
                    updatedSchema, updatedValue, record.timestamp());
        }

    }
}
