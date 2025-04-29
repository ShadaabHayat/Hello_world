/**
 * This class contains the source code for whitelisting fields based on a target schema.
 */

package org.extremenetworks.com;

import org.apache.kafka.common.cache.Cache;
import org.apache.kafka.common.cache.LRUCache;
import org.apache.kafka.common.cache.SynchronizedCache;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.connector.ConnectRecord;
import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.errors.DataException;
import org.apache.kafka.connect.transforms.Transformation;
import org.apache.kafka.connect.transforms.util.SimpleConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;

import org.extremenetworks.com.schemaconverter.SchemaConverter;

import static org.apache.kafka.connect.transforms.util.Requirements.requireStruct;

/**
 * Interface for configuration property names used by Whitelister SMT.
 */
interface ConfigName {
    String SCHEMA_DIRECTORY = "schema.directory";
    String SCHEMA_TYPE = "schema.type";
    String SCHEMA_SUFFIX = "schema.suffix";
    String SCHEMA_CACHE_TTL_MINUTES = "file.schema.cache.ttl.minutes";
    String SCHEMA_CACHE_MAX_SIZE = "file.schema.cache.max.size";
    String UPDATED_SCHEMA_CACHE_SIZE = "updated.schema.cache.size";
    String IGNORE_MISSING_SCHEMA = "ignore.missing.schema";
}

public abstract class Whitelister<R extends ConnectRecord<R>> implements Transformation<R> {
    private static final Logger LOG = LoggerFactory.getLogger(Whitelister.class);

    public static final ConfigDef CONFIG_DEF = new ConfigDef()
            .define(ConfigName.SCHEMA_DIRECTORY, ConfigDef.Type.STRING, "", ConfigDef.Importance.HIGH,
                    "Path to the directory containing schema files")
            .define(ConfigName.SCHEMA_TYPE, ConfigDef.Type.STRING, "json", ConfigDef.Importance.MEDIUM,
                    "Schema type: 'json' or 'avro'")
            .define(ConfigName.SCHEMA_SUFFIX, ConfigDef.Type.STRING, "", ConfigDef.Importance.LOW,
                    "Optional suffix for schema files (e.g., '.schema.json')")
            .define(ConfigName.SCHEMA_CACHE_TTL_MINUTES, ConfigDef.Type.INT, 5, ConfigDef.Importance.LOW,
                    "TTL for schema file cache in minutes (default: 5 minutes)")
            .define(ConfigName.SCHEMA_CACHE_MAX_SIZE, ConfigDef.Type.INT, 1000, ConfigDef.Importance.LOW,
                    "Maximum number of entries in the schema content cache (default: 1000)")
            .define(ConfigName.UPDATED_SCHEMA_CACHE_SIZE, ConfigDef.Type.INT, 100, ConfigDef.Importance.LOW,
                    "Updated schema cache size (default: 100)")
            .define(ConfigName.IGNORE_MISSING_SCHEMA, ConfigDef.Type.BOOLEAN, true, ConfigDef.Importance.LOW,
                    "Ignore missing schema files (default: false)");

    private static final String PURPOSE = "whitelist fields based on target schema";
    private String schemaDirectory;
    private String schemaType;
    private String schemaSuffix;
    private Cache<String, Schema> schemaCache;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private SchemaConverter schemaConverter;
    private boolean ignoreMissingSchema;
    
    // Cache for schema file content with TTL
    private com.github.benmanes.caffeine.cache.Cache<String, String> schemaContentCache;

    /**
     * Initialize the transformation with the given configuration properties.
     *
     * @param props The configuration properties
     */
    @Override
    public void configure(Map<String, ?> props) {
        final SimpleConfig config = new SimpleConfig(CONFIG_DEF, props);

        schemaDirectory = config.getString(ConfigName.SCHEMA_DIRECTORY);
        schemaType = config.getString(ConfigName.SCHEMA_TYPE);
        schemaSuffix = config.getString(ConfigName.SCHEMA_SUFFIX);
        ignoreMissingSchema = config.getBoolean(ConfigName.IGNORE_MISSING_SCHEMA);

        int schemaCacheMaxSize = config.getInt(ConfigName.SCHEMA_CACHE_MAX_SIZE);

        schemaConverter = new SchemaConverter();

        // Initialize schema cache using LRU cache for Kafka Connect compatibility
        schemaCache = new SynchronizedCache<>(new LRUCache<>(schemaCacheMaxSize));

        // Initialize schema content cache with TTL and maximum size
        int schemaCacheTtlMinutes = config.getInt(ConfigName.SCHEMA_CACHE_TTL_MINUTES);
        schemaContentCache = Caffeine.newBuilder()
                .expireAfterWrite(schemaCacheTtlMinutes, TimeUnit.MINUTES)
                .maximumSize(schemaCacheMaxSize)
                .build();

        LOG.info("Configured Whitelister with schema directory: " + schemaDirectory + ", schema type: " + schemaType);
    }

    /**
     * Get a Kafka Connect Schema object for a specific topic, parsing the schema file if necessary
     *
     * @param schemaContent The content of the schema file
     * @param schemaName The name of the schema
     * @return A Schema object representing the schema file
     */
    private Schema getSchemaFromContent(String schemaContent, String schemaName) throws IOException {
        // Use the configured schema type
        if ("json".equalsIgnoreCase(schemaType)) {
            return schemaConverter.convertJsonToConnectSchema(schemaContent, schemaName);
        } else {
            // Default to Avro for any other value
            return schemaConverter.convertAvroToConnectSchema(schemaContent, schemaName);
        }
    }

    /**
     * Apply the transformation to the record
     *
     * @param record The record to transform
     * @return The transformed record
     */
    @Override
    public R apply(R record) {
        // Early return if no schema or no transformation needed
        Schema schema = operatingSchema(record);
        if (schema == null || schema.type() != Schema.Type.STRUCT) {
            throw new DataException("Schema is null or not a struct for record: " + record);
        }

        // Get the topic name from the record
        String topic = record.topic();
        String targetSchemaName = topic.replace("-", "_");

        String schemaPath = getSchemaPath(topic);
        String schemaContent = getSchemaContent(schemaPath);

        if (schemaContent == null || schemaContent.isEmpty()) {
            if (ignoreMissingSchema) {
                LOG.warn("Schema file not found or empty for topic '{}' at path '{}'. Returning original record as configured with ignore.missing.schema=true",
                        topic, schemaPath);
                return record;
            } else {
                throw new DataException(String.format("Schema content is null or empty for topic '%s' at path '%s'. " +
                        "Ensure the schema file exists and is not empty, or set ignore.missing.schema=true to skip missing schemas.",
                        topic, schemaPath));
            }
        }

        // Get the target schema from the schema file
        // Check if we've already parsed this schema
        Schema targetSchema;
        boolean isFromCache = false;
        
        // Try to get schema from cache first
        Schema cachedSchema = schemaCache.get(topic);
        if (cachedSchema != null) {
            LOG.info("Using cached schema for topic: " + topic);
            targetSchema = cachedSchema;
            isFromCache = true;
        } else {
            try {
                // Convert the schema content to a Kafka Connect Schema object
                targetSchema = getSchemaFromContent(schemaContent, targetSchemaName);
                
                // Cache the schema
                schemaCache.put(topic, targetSchema);
            } catch (IOException e) {
                throw new DataException("Error converting schema content to Connect schema: " + e.getMessage(), e);
            }
        }
        
        // Check if schemas are equivalent (same check for both cached and newly parsed schemas)
        if (schemaConverter.areSchemasEquivalent(targetSchema, schema)) {
            String source = isFromCache ? "Cached" : "Target";
            LOG.info(source + " schema is equivalent to original message schema for topic: " + topic + " returning original record");
            return record;
        }
        
        // Apply the transformation with the target schema
        return applyWithTargetSchema(record, targetSchema);
        
    }

    /**
     * Apply the transformation to the record using the target schema derived from the schema file.
     *
     * This method performs the actual field filtering based on the target schema:
     * 1. Uses the target schema directly from the schema file
     * 2. Creates a new struct with only the fields that exist in both the source record and target schema
     * 3. Handles nested structs and arrays of structs recursively
     *
     * @param record The record to transform
     * @param targetSchema The schema derived from the schema file
     * @return The transformed record with only the fields defined in the target schema
     */
    private R applyWithTargetSchema(R record, Schema targetSchema) {
        final Struct value = requireStruct(operatingValue(record), PURPOSE);
        Schema sourceSchema = operatingSchema(record);

        // Create a new struct with the target schema
        final Struct updatedValue = new Struct(targetSchema);

        // Copy fields from source to target where they exist in both schemas
        copyMatchingFields(value, sourceSchema, updatedValue, targetSchema);

        return newRecord(record, targetSchema, updatedValue);
    }

    /**
     * Copy fields from source struct to target struct where they exist in both schemas.
     *
     * This method handles:
     * 1. Primitive fields - direct copy
     * 2. Null values - preserved for optional fields
     * 3. Nested structs - recursively processed
     * 4. Arrays of primitives - direct copy
     * 5. Arrays of structs - each struct is recursively processed
     *
     * @param sourceStruct The source struct to copy fields from
     * @param sourceSchema The schema of the source struct
     * @param targetStruct The target struct to copy fields to
     * @param targetSchema The schema of the target struct
     */
    private void copyMatchingFields(Struct sourceStruct, Schema sourceSchema, Struct targetStruct, Schema targetSchema) {
        // Process each field in the target schema
        for (Field targetField : targetSchema.fields()) {
            final String fieldName = targetField.name();
            final Field sourceField = sourceSchema.field(fieldName);
            final Schema targetFieldSchema = targetField.schema();

            // Skip fields that don't exist in the source schema
            if (sourceField == null) {
                LOG.debug("Field '{}' exists in target schema but not in source schema, skipping", fieldName);
                continue;
            }

            // Get the value from the source struct
            final Object fieldValue = sourceStruct.get(fieldName);

            // Handle null values
            if (fieldValue == null) {
                handleNullValue(fieldName, targetField, targetStruct);
                continue;
            }

            // Handle different field types
            final Schema.Type targetType = targetFieldSchema.type();
            
            if (targetType == Schema.Type.STRUCT && fieldValue instanceof Struct) {
                handleNestedStruct(fieldName, fieldValue, sourceField, targetField, targetStruct);
            } else if (targetType == Schema.Type.ARRAY && fieldValue instanceof List) {
                handleArray(fieldName, fieldValue, sourceField, targetField, targetStruct);
            } else {
                // Handle primitive fields with conversion if needed
                Object convertedValue = convertValueIfNeeded(fieldValue, sourceField.schema(), targetField.schema());
                targetStruct.put(fieldName, convertedValue);
            }
        }
    }

    /**
     * Handle null values in the source struct
     *
     * @param fieldName The name of the field
     * @param targetField The target field
     * @param targetStruct The target struct
     */
    private void handleNullValue(String fieldName, Field targetField, Struct targetStruct) {
        // If the field is optional in the target schema, preserve the null value
        if (targetField.schema().isOptional()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Field '{}' is null in source struct and optional in target schema, preserving null value", fieldName);
            }
            targetStruct.put(fieldName, null);
        } else if (LOG.isDebugEnabled()) {
            LOG.debug("Field '{}' is null in source struct and not optional in target schema, skipping", fieldName);
        }
    }

    /**
     * Handle nested struct fields
     *
     * @param fieldName The name of the field
     * @param fieldValue The field value from the source struct
     * @param sourceField The source field
     * @param targetField The target field
     * @param targetStruct The target struct
     */
    private void handleNestedStruct(String fieldName, Object fieldValue, Field sourceField, Field targetField, Struct targetStruct) {
        Struct nestedSourceStruct = (Struct) fieldValue;
        Schema nestedTargetSchema = targetField.schema();

        // Create a new struct with the nested target schema
        Struct nestedTargetStruct = new Struct(nestedTargetSchema);

        // Recursively copy fields for the nested struct
        copyMatchingFields(nestedSourceStruct, sourceField.schema(), nestedTargetStruct, nestedTargetSchema);

        // Add the nested struct to the target struct
        targetStruct.put(fieldName, nestedTargetStruct);
    }

    /**
     * Handle array fields
     *
     * @param fieldName The name of the field
     * @param fieldValue The field value from the source struct
     * @param sourceField The source field
     * @param targetField The target field
     * @param targetStruct The target struct
     */
    private void handleArray(String fieldName, Object fieldValue, Field sourceField, Field targetField, Struct targetStruct) {
        List<?> sourceList = (List<?>) fieldValue;
        Schema targetValueSchema = targetField.schema().valueSchema();
        Schema sourceValueSchema = sourceField.schema().valueSchema();

        // Handle empty arrays with direct copy
        if (sourceList.isEmpty()) {
            targetStruct.put(fieldName, sourceList);
            return;
        }

        // Handle arrays of structs with recursive processing
        if (targetValueSchema.type() == Schema.Type.STRUCT && sourceValueSchema.type() == Schema.Type.STRUCT) {
            processArrayOfStructs(fieldName, sourceList, sourceValueSchema, targetValueSchema, targetStruct);
        } else {
            // Handle arrays of primitives with type conversion if needed
            if (sourceValueSchema.type() != targetValueSchema.type()) {
                List<Object> convertedList = new ArrayList<>(sourceList.size());
                for (Object item : sourceList) {
                    convertedList.add(convertValueIfNeeded(item, sourceValueSchema, targetValueSchema));
                }
                targetStruct.put(fieldName, convertedList);
            } else {
                // Direct copy for compatible types
                targetStruct.put(fieldName, sourceList);
            }
        }
    }

    /**
     * Convert a value to match the target schema type if needed
     *
     * @param value The value to convert
     * @param sourceSchema The schema of the source field
     * @param targetSchema The schema of the target field
     * @return The converted value, or the original value if no conversion is needed
     */
    private Object convertValueIfNeeded(Object value, Schema sourceSchema, Schema targetSchema) {
        if (value == null || sourceSchema.type() == targetSchema.type()) {
            return value;
        }
        
        // Handle numeric conversions
        if (value instanceof Number) {
            Number numValue = (Number) value;
            
            try {
                switch (targetSchema.type()) {
                    case INT8:
                        return numValue.byteValue();
                    case INT16:
                        return numValue.shortValue();
                    case INT32:
                        long longForInt = numValue.longValue();
                        if (longForInt > Integer.MAX_VALUE || longForInt < Integer.MIN_VALUE) {
                            LOG.warn("Value {} for field exceeds INT32 range, using max/min value", longForInt);
                            return longForInt > 0 ? Integer.MAX_VALUE : Integer.MIN_VALUE;
                        }
                        return numValue.intValue();
                    case INT64:
                        return numValue.longValue();
                    case FLOAT32:
                        return numValue.floatValue();
                    case FLOAT64:
                        return numValue.doubleValue();
                    default:
                        // For other types, try to use the original value
                        LOG.debug("No conversion available from {} to {}, using original value", 
                                sourceSchema.type(), targetSchema.type());
                        return value;
                }
            } catch (Exception e) {
                LOG.warn("Error converting value {} from {} to {}: {}", 
                        value, sourceSchema.type(), targetSchema.type(), e.getMessage());
                throw new DataException("Cannot convert value " + value + 
                        " from " + sourceSchema.type() + " to " + targetSchema.type(), e);
            }
        }
        
        // Handle string conversions if target is string
        if (targetSchema.type() == Schema.Type.STRING && value != null) {
            return value.toString();
        }
        
        // Handle boolean conversions
        if (targetSchema.type() == Schema.Type.BOOLEAN && value instanceof Number) {
            Number numValue = (Number) value;
            return numValue.intValue() != 0;
        }
        
        // For incompatible types with no conversion path
        LOG.warn("Cannot convert value {} from {} to {}", 
                value, sourceSchema.type(), targetSchema.type());
        throw new DataException("Cannot convert value " + value + 
                " from " + sourceSchema.type() + " to " + targetSchema.type());
    }
    
    /**
     * Process an array of structs
     *
     * @param fieldName The name of the field
     * @param sourceList The source list of structs
     * @param sourceValueSchema The schema of each item in the source list
     * @param targetValueSchema The schema of each item in the target list
     * @param targetStruct The target struct
     */
    private void processArrayOfStructs(String fieldName, List<?> sourceList, Schema sourceValueSchema, Schema targetValueSchema, Struct targetStruct) {
        List<Struct> sourceStructList = (List<Struct>) sourceList;
        List<Struct> targetStructList = new ArrayList<>(sourceStructList.size());

        // Process each struct in the array
        for (Struct sourceItemStruct : sourceStructList) {
            Struct targetItemStruct = new Struct(targetValueSchema);
            copyMatchingFields(sourceItemStruct, sourceValueSchema, targetItemStruct, targetValueSchema);
            targetStructList.add(targetItemStruct);
        }

        targetStruct.put(fieldName, targetStructList);
    }

    /**
     * Get the path to the schema file based on the topic name
     *
     * @param topic The topic name
     * @return The path to the schema file
     */
    String getSchemaPath(String topic) {
        String schemaFileName = topic + (schemaSuffix != null && !schemaSuffix.isEmpty() ? schemaSuffix :
                ("json".equalsIgnoreCase(schemaType) ? ".json" : ".avsc"));
        return Paths.get(schemaDirectory, schemaFileName).toString();
    }
    /**
     * Get the content of the schema file, caching it for performance
     *
     * @param schemaPath The path to the schema file
     * @return The content of the schema file, or null if not found
     */
    private String getSchemaContent(String schemaPath) {
        // Try to get from cache first
        String cachedContent = schemaContentCache.getIfPresent(schemaPath);
        if (cachedContent != null) {
            LOG.debug("Using cached schema content for: {}", schemaPath);
            // If we cached an empty string, it means the file doesn't exist or had an error
            return cachedContent.isEmpty() ? null : cachedContent;
        }

        // If not in cache, read from file
        File schemaFile = new File(schemaPath);
        if (!schemaFile.exists() || !schemaFile.isFile()) {
            LOG.warn("Schema file not found: {}", schemaPath);
            // Cache empty string to avoid repeated file system checks
            // We use empty string instead of null because Caffeine doesn't cache null values
            schemaContentCache.put(schemaPath, "");
            return null;
        }

        try {
            String content = new String(Files.readAllBytes(Paths.get(schemaPath)));
            // Cache the content
            schemaContentCache.put(schemaPath, content);
            LOG.debug("Loaded and cached schema content for: {}", schemaPath);
            return content;
        } catch (IOException e) {
            LOG.error("Failed to load schema from {}: {}", schemaPath, e.getMessage());
            // Cache empty string for error cases
            schemaContentCache.put(schemaPath, "");
            return null;
        }
    }
    // The schema parsing functionality has been moved to the SchemaConverter class

    @Override
    public ConfigDef config() {
        return CONFIG_DEF;
    }

    @Override
    public void close() {
        schemaCache = null;
        schemaContentCache = null;
    }

    protected abstract Schema operatingSchema(R record);

    protected abstract Object operatingValue(R record);

    protected abstract R newRecord(R record, Schema updatedSchema, Object updatedValue);

    public static class Key<R extends ConnectRecord<R>> extends Whitelister<R> {
        @Override
        protected Schema operatingSchema(R record) {
            return record.keySchema();
        }

        @Override
        protected Object operatingValue(R record) {
            return record.key();
        }

        @Override
        protected R newRecord(R record, Schema updatedSchema, Object updatedValue) {
            return record.newRecord(record.topic(), record.kafkaPartition(), updatedSchema, updatedValue,
                    record.valueSchema(), record.value(), record.timestamp());
        }
    }

    public static class Value<R extends ConnectRecord<R>> extends Whitelister<R> {
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
