package org.extremenetworks.com;

import static org.apache.kafka.connect.transforms.util.Requirements.requireMap;
import static org.apache.kafka.connect.transforms.util.Requirements.requireStruct;

import java.io.IOException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.connect.connector.ConnectRecord;
import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.errors.DataException;
import org.apache.kafka.connect.transforms.Transformation;
import org.apache.kafka.connect.transforms.util.SimpleConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class EqualityCheckOnFields<R extends ConnectRecord<R>> implements Transformation<R> {
    private static final Logger LOG = LoggerFactory.getLogger(EqualityCheckOnFields.class);
    private static final String PURPOSE = "field validation";
    private static final DateTimeFormatter[] TIMESTAMP_FORMATTERS = new DateTimeFormatter[] {
        DateTimeFormatter.ISO_ZONED_DATE_TIME,
        DateTimeFormatter.ISO_INSTANT,
    };

    public static final ConfigDef CONFIG_DEF = new ConfigDef()
            .define("fields.notEquality", ConfigDef.Type.STRING,"",
            ConfigDef.Importance.MEDIUM, "JSON string of fields for not equality check")
            .define("topic.name", ConfigDef.Type.STRING, "",
            ConfigDef.Importance.HIGH, "The target topic on which the transform to be applied")
            .define("fields.Equality", ConfigDef.Type.STRING, "",
                    ConfigDef.Importance.MEDIUM, "JSON string of fields for equality check");

    private Map<String, List<?>> equalityConfig = new HashMap<>();

    private Map<String, List<?>> notEqualityConfig = new HashMap<>();
    private String targetTopic;

    @Override
    public void configure(Map<String, ?> props) {
        SimpleConfig config = new SimpleConfig(CONFIG_DEF, props);

        LOG.info("Parsing Config ...");

        equalityConfig = parseJsonToMap(config.getString("fields.Equality"));
        notEqualityConfig=parseJsonToMap(config.getString("fields.notEquality"));
        targetTopic= config.getString("topic.name");
        if (this.targetTopic == null || this.targetTopic.trim().isEmpty()) {
            throw new ConfigException("topic.name is required and cannot be empty");
        }

        LOG.info("inside configure , Equality Config: {}", equalityConfig);
        LOG.info("inside configure, Non-Equality Config: {}", notEqualityConfig);

    }

    @Override
    public R apply(R record) {
        LOG.debug("Processing record with Equality Config: {}", equalityConfig);
        LOG.debug("Processing record with Non-Equality Config: {}", notEqualityConfig);

        if (!record.topic().equals(targetTopic)){
            return record;
        }
        boolean allChecksPassed;

        if (operatingSchema(record) != null) {
            allChecksPassed = applyWithSchema(record);
        } else {
            allChecksPassed = applySchemaless(record);
        }

        if (allChecksPassed) {
            return record;
        } else {
            LOG.warn("Record failed validation. Details: {}", record.toString());

            return (R) record.newRecord(record.topic(), record.kafkaPartition(), record.keySchema(), record, null, null,
                    System.currentTimeMillis());
            //  send To DLQ
        }
    }
     private boolean applyWithSchema(R record) {
        final Struct value = requireStruct(operatingValue(record), PURPOSE);


        Map<String, Object> recordMap = extractRecordMap(value);

        return checkConditions(recordMap);
    }

    private boolean applySchemaless(R record) {
        final Map<String, Object> value = requireMap(operatingValue(record), PURPOSE);
        return checkConditions(value);
    }

    private boolean checkConditions(Map<String, Object> record) {
    // Validate and perform "Not Equality" checks
    boolean notEqualityCheckPassed = true;
    if (!notEqualityConfig.isEmpty()) {
        Set<String> missingFields = notEqualityConfig.keySet().stream()
                .filter(field -> !record.containsKey(field))
                .collect(Collectors.toSet());

        if (!missingFields.isEmpty()) {
            throw new ConfigException("Required Non-Equality fields are missing in record: " + missingFields +
                    ". Connector configuration needs to be corrected.");
        }

        // Check that none of the values in the record match those in notEqualityConfig
        notEqualityCheckPassed = notEqualityConfig.entrySet().stream()
                .noneMatch(entry -> isValueInList(record.get(entry.getKey()), entry.getValue()));
    }

    if (!notEqualityCheckPassed) {
        return false; // Record fails not-equality check
    }

    // Validate and perform "Equality" checks
    boolean equalityCheckPassed = false;
    if (!equalityConfig.isEmpty()) {
        Set<String> missingFields = equalityConfig.keySet().stream()
                .filter(field -> !record.containsKey(field))
                .collect(Collectors.toSet());

        if (!missingFields.isEmpty()) {
            throw new ConfigException("Required Equality fields are missing in record: " + missingFields +
                    ". Connector configuration needs to be corrected.");
        }

        // Check that all values in the record match those in equalityConfig
        equalityCheckPassed = equalityConfig.entrySet().stream()
                .allMatch(entry -> isValueInList(record.get(entry.getKey()), entry.getValue()));
    }

    // If there are no equality checks defined, consider it a pass as long as not-equality checks passed
    return equalityCheckPassed || equalityConfig.isEmpty();
}

private boolean isValueInList(Object fieldValue, List<?> checkValues) {
        if (fieldValue == null) {

            return checkValues.contains(null);
        }

        // For ZonedDateTime (with timezone) comparisons
        if ((fieldValue instanceof String && isValidTimestamp((String) fieldValue))
                || (fieldValue instanceof ZonedDateTime)) {

            ZonedDateTime fieldZonedDateTime = (fieldValue instanceof String)
                    ? ZonedDateTime.parse(((String) fieldValue).trim())  : (ZonedDateTime) fieldValue;


                // Compare against string timestamps with timezone in checkValues
                return checkValues.stream()
                        .filter(Objects::nonNull)
                        .anyMatch(val ->{
                            if (val instanceof String) {
                                try {
                                    ZonedDateTime checkTimestamp = ZonedDateTime.parse(((String) val).trim());
                                    return fieldZonedDateTime.isEqual(checkTimestamp);
                                } catch (DateTimeParseException e) {
                                    LOG.error("Invalid timestamp in given config: {}", val);
                                }
                            }
                            return false;  // Return false if the value is not a valid string timestamp
                        });
                    }

        if (fieldValue instanceof String) {


            String trimmedValue = ((String) fieldValue).trim();

            List<?> trimmedCheckValues = checkValues.stream()
                .filter(Objects::nonNull)
                .map(value -> value instanceof String ? ((String) value).trim() : value)
                .collect(Collectors.toList());
            return trimmedCheckValues.contains(trimmedValue);
        }

        if (fieldValue instanceof Number) {

            double doubleValue = ((Number) fieldValue).doubleValue();
            return checkValues.stream()
                    .filter(Objects::nonNull)
                    .anyMatch(val -> val instanceof Number && doubleValue == ((Number) val).doubleValue());
        }

        if (fieldValue instanceof Boolean) {
            return checkValues.stream()
                    .filter(Objects::nonNull)
                    .anyMatch(val -> val instanceof Boolean && fieldValue.equals(val));
        }

        // For Timestamp handling
        if (fieldValue instanceof java.sql.Timestamp) {

            // Convert to ZonedDateTime (assuming system's default timezone for non-UTC)
            java.sql.Timestamp fieldValueTimestamp = (java.sql.Timestamp) fieldValue;

            // Convert Timestamp to Instant, and then to ZonedDateTime with UTC
            ZonedDateTime fieldDateTime = fieldValueTimestamp.toInstant().atZone(ZoneOffset.UTC);

            // Convert list values to ZonedDateTime (in UTC) for comparison
            return checkValues.stream()
                    .filter(Objects::nonNull)
                    .anyMatch(val -> val instanceof String && isTimestampMatch(fieldDateTime, ((String) val).trim()));
        }

        return false;
    }


    private Map<String, Object> extractRecordMap(Struct value) {
        Map<String, Object> recordMap = new HashMap<>();
        for (Field field : value.schema().fields()) {
            recordMap.put(field.name(), value.get(field.name()));
        }
        return recordMap;
    }

    private Object operatingValue(R record) {
        return record.value();
    }

    private Schema operatingSchema(R record) {
        return record.valueSchema();
    }
    @Override
    public ConfigDef config() {
        return CONFIG_DEF;
    }


    @Override
    public void close() {
        // No resource cleanup required in this example
    }

    public Map<String, List<?>> getEqualityConfig() {
        return equalityConfig;
    }
    public Map<String, List<?>> getNotEqualityConfig() {
        return notEqualityConfig;
    }


    private Map<String, List<?>> parseJsonToMap(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            LOG.warn("Received null or empty JSON string for parsing.");
            return Map.of(); // Return an empty map
        }
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readValue(jsonString, new TypeReference<Map<String, List<?>>>() {});
        } catch (IOException e) {
            throw new DataException("Error parsing JSON string", e);
        }
    }

    public boolean isValidTimestamp(String timestamp) {
        for (DateTimeFormatter formatter : TIMESTAMP_FORMATTERS) {
            try {
                formatter.parse(timestamp);
                return true;
            } catch (Exception e) {
                // Continue to the next formatter
            }
        }
        return false;
    }
    private boolean isTimestampMatch(ZonedDateTime fieldDateTime, String timestampString) {
        try {
            // Parse the timestamp string, assume the string is in ISO-8601 format
            ZonedDateTime checkTimestamp = ZonedDateTime.parse(timestampString);

            // Compare timestamps
            return fieldDateTime.isEqual(checkTimestamp);
        } catch (Exception e) {
            LOG.error("Failed to parse timestamp string from config: {}", e.getMessage());
            return false;
        }
    }

}
