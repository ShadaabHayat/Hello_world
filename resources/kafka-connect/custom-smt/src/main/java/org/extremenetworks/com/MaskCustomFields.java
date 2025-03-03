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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.kafka.connect.transforms.util.Requirements.requireMap;
import static org.apache.kafka.connect.transforms.util.Requirements.requireStruct;

public abstract class MaskCustomFields<R extends ConnectRecord<R>> implements Transformation<R> {

    public static final ConfigDef CONFIG_DEF = new ConfigDef()
            .define(ConfigName.MaskFields, ConfigDef.Type.LIST, Collections.emptyList(), ConfigDef.Importance.HIGH,
                    "list of fields to mask")
            .define(ConfigName.TopicName, ConfigDef.Type.STRING, "", ConfigDef.Importance.HIGH,
                    "topic name in which fields are to be masked");

    private static final String PURPOSE = "mask certain fields from certain tables with hash values";
    private static final Logger log = LoggerFactory.getLogger(MaskCustomFields.class);
    private String topicName;
    private List<String> maskFields;

    private Cache<Schema, Schema> schemaUpdateCache;

    @Override
    public void configure(Map<String, ?> props) {
        final SimpleConfig config = new SimpleConfig(CONFIG_DEF, props);

        topicName = config.getString(ConfigName.TopicName);
        maskFields = config.getList(ConfigName.MaskFields);

        schemaUpdateCache = new SynchronizedCache<>(new LRUCache<Schema, Schema>(16));
    }

    @Override
    public R apply(R record) {
        if (operatingSchema(record) == null) {
            return applySchemaless(record);
        } else {
            return applyWithSchema(record);
        }
    }

    private R applySchemaless(R record) {
        final Map<String, Object> value = requireMap(operatingValue(record), PURPOSE);
        final Map<String, Object> updatedValue = new HashMap<>(value);

        if (topicName.equals(record.topic())) {
            for (String fieldName : maskFields) {
                if (updatedValue.containsKey(fieldName)) {
                    Object fieldValue = updatedValue.get(fieldName);
                    try {
                        if (fieldValue == null) {
                            updatedValue.put(fieldName, null);
                        } else if (fieldValue.toString().trim().isEmpty()) {
                            // Preserve empty strings instead of hashing them
                            updatedValue.put(fieldName, "");
                            log.debug("Empty string found for field {}, preserving empty value", fieldName);
                        } else {
                            String maskedValue = DigestUtils.sha256Hex(fieldValue.toString());
                            updatedValue.put(fieldName, maskedValue);
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
        final Struct value = requireStruct(operatingValue(record), PURPOSE);

        Schema updatedSchema = schemaUpdateCache.get(value.schema());
        if (updatedSchema == null) {
            updatedSchema = makeUpdatedSchema(value.schema(), record);
            schemaUpdateCache.put(value.schema(), updatedSchema);
        }

        final Struct updatedValue = new Struct(updatedSchema);

        for (Field field : value.schema().fields()) {
            if (topicName.equals(record.topic()) && maskFields.contains(field.name())) {
                try {
                    Object fieldValue = value.get(field);
                    if (fieldValue == null) {
                        updatedValue.put(field.name(), null);
                    } else if (fieldValue.toString().trim().isEmpty()) {
                        // Preserve empty strings instead of hashing them
                        updatedValue.put(field.name(), "");
                        log.debug("Empty string found for field {}, preserving empty value", field.name());
                    } else {
                        String maskedValue = DigestUtils.sha256Hex(fieldValue.toString());
                        updatedValue.put(field.name(), maskedValue);
                    }
                } catch (Exception e) {
                    log.warn("Failed to mask field {}: {}", field.name(), e.getMessage());
                    updatedValue.put(field.name(), "masked");
                }
            } else {
                updatedValue.put(field.name(), value.get(field));
            }
        }

        return newRecord(record, updatedSchema, updatedValue);
    }

    @Override
    public ConfigDef config() {
        return CONFIG_DEF;
    }

    @Override
    public void close() {
        schemaUpdateCache = null;
    }

    private Schema makeUpdatedSchema(Schema schema, R record) {
        final SchemaBuilder builder = SchemaUtil.copySchemaBasics(schema, SchemaBuilder.struct());

        for (Field field : schema.fields()) {
            if (topicName.equals(record.topic()) && maskFields.contains(field.name())) {
                builder.field(field.name(), Schema.OPTIONAL_STRING_SCHEMA);
            } else {
                builder.field(field.name(), field.schema());
            }
        }
        return builder.build();
    }

    protected abstract Schema operatingSchema(R record);

    protected abstract Object operatingValue(R record);

    protected abstract R newRecord(R record, Schema updatedSchema, Object updatedValue);

    private interface ConfigName {
        String MaskFields = "mask.fields";
        String TopicName = "topic.name";
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
            return record.newRecord(record.topic(), record.kafkaPartition(), record.keySchema(), record.key(), updatedSchema, updatedValue, record.timestamp());
        }

    }
}
