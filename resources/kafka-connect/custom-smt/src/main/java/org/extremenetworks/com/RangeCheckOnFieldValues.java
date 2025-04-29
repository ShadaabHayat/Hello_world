package org.extremenetworks.com;

import static org.apache.kafka.connect.transforms.util.Requirements.requireMap;
import static org.apache.kafka.connect.transforms.util.Requirements.requireStruct;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

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

/**
 * A Kafka Connect Custom Transform that checks whether field values fall within
 * specified numeric or timestamp ranges.
 * <p>
 * Usage:
 * 1. Configure your ranges in the connector config under the "Between" field.
 * 2. The transform validates that each field value (or each value in a list) is
 * within at least one provided range.
 * </p>
 */
public class RangeCheckOnFieldValues<R extends ConnectRecord<R>> implements Transformation<R> {
    private static final Logger LOG = LoggerFactory.getLogger(RangeCheckOnFieldValues.class);
    private static final String PURPOSE = "field validation";
    private static final DateTimeFormatter[] TIMESTAMP_FORMATTERS = new DateTimeFormatter[] {
            DateTimeFormatter.ISO_ZONED_DATE_TIME,
            DateTimeFormatter.ISO_INSTANT,
    };

    public static final ConfigDef CONFIG_DEF = new ConfigDef()
            .define("fields.GreaterThan", ConfigDef.Type.STRING, "", ConfigDef.Importance.MEDIUM,
                    "JSON mapping of fields to single threshold for GreaterThan check (number or ISO timestamp)")
            .define("fields.LesserThan", ConfigDef.Type.STRING, "", ConfigDef.Importance.MEDIUM,
                    "JSON mapping of fields to single threshold for LesserThan check (number or ISO timestamp)")
            .define("fields.Between", ConfigDef.Type.STRING, "", ConfigDef.Importance.MEDIUM,
                    "JSON mapping of fields to list of [min, max] ranges for Between check")
            .define("fields.GreaterThanEqualTo", ConfigDef.Type.STRING, "", ConfigDef.Importance.MEDIUM,
                    "JSON mapping of fields to single threshold for GreaterThanEqualTo check (number or ISO timestamp)")
            .define("fields.LesserThanEqualTo", ConfigDef.Type.STRING, "", ConfigDef.Importance.MEDIUM,
                    "JSON mapping of fields to single threshold for LesserThanEqualTo check (number or ISO timestamp)")
            .define("topic.name", ConfigDef.Type.STRING, "", ConfigDef.Importance.HIGH,
                    "The target topic on which the transform to be applied");

    // For single-threshold checks, we expect a single numeric value per field.
    private Map<String, Number> greaterThanConfig = new HashMap<>();
    private Map<String, Number> lesserThanConfig = new HashMap<>();
    // For Between, we expect a list of [min, max] pairs.
    private Map<String, List<List<?>>> betweenConfig = new HashMap<>();
    private Map<String, Number> greaterThanEqualToConfig = new HashMap<>();
    private Map<String, Number> lesserThanEqualToConfig = new HashMap<>();
    private String targetTopic;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void configure(Map<String, ?> props) {
        SimpleConfig config = new SimpleConfig(CONFIG_DEF, props);
        try {
            String gtConfigStr = config.getString("fields.GreaterThan");
            if (!gtConfigStr.isEmpty()) {
                greaterThanConfig = objectMapper.readValue(gtConfigStr, new TypeReference<Map<String, Number>>() {
                });
            }
            String ltConfigStr = config.getString("fields.LesserThan");
            if (!ltConfigStr.isEmpty()) {
                lesserThanConfig = objectMapper.readValue(ltConfigStr, new TypeReference<Map<String, Number>>() {
                });
            }
            String betweenConfigStr = config.getString("fields.Between");
            if (!betweenConfigStr.isEmpty()) {
                betweenConfig = objectMapper.readValue(betweenConfigStr,
                        new TypeReference<Map<String, List<List<?>>>>() {
                        });
            }
            String gteConfigStr = config.getString("fields.GreaterThanEqualTo");
            if (!gteConfigStr.isEmpty()) {
                greaterThanEqualToConfig = objectMapper.readValue(gteConfigStr,
                        new TypeReference<Map<String, Number>>() {
                        });
            }
            String lteConfigStr = config.getString("fields.LesserThanEqualTo");
            if (!lteConfigStr.isEmpty()) {
                lesserThanEqualToConfig = objectMapper.readValue(lteConfigStr,
                        new TypeReference<Map<String, Number>>() {
                        });
            }
            targetTopic = config.getString("topic.name");
            LOG.debug("RangeCheckOnFieldValues configured for topic: {}", targetTopic);
            LOG.debug("Parsed configurations - GT: {}, LT: {}, Between: {}, GTE: {}, LTE: {}",
                    greaterThanConfig.keySet(), lesserThanConfig.keySet(), betweenConfig.keySet(),
                    greaterThanEqualToConfig.keySet(), lesserThanEqualToConfig.keySet());
            LOG.debug("Total fields to validate: {}",
                    greaterThanConfig.size() + lesserThanConfig.size() + betweenConfig.size() +
                            greaterThanEqualToConfig.size() + lesserThanEqualToConfig.size());
            LOG.debug("Configuration complete");
        } catch (IOException e) {
            throw new ConfigException("Failed to parse RangeCheck configuration", e);
        }
    }

    @Override
    public R apply(R record) {
        LOG.info("Applying range validation on record: {}", record);
        if (!record.topic().equals(targetTopic)) {
            return record;
        }
        try {
            if (operatingSchema(record) != null) {
                applyWithSchema(record);
            } else {
                applySchemaless(record);
            }
            // If we get here, all checks passed
            return record;
        } catch (DataException e) {
            // Log and rethrow the exception from validateFieldValues
            LOG.warn("Record failed range validation. Details: {}", record);
            throw e;
        }
    }

    private void applyWithSchema(R record) {
        final Struct value = requireStruct(operatingValue(record), PURPOSE);
        Map<String, Object> recordMap = extractRecordMap(value);
        validateFieldValues(recordMap);
    }

    private void applySchemaless(R record) {
        final Map<String, Object> value = requireMap(operatingValue(record), PURPOSE);
        validateFieldValues(value);
    }

    /**
     * Consolidated method to validate field values against all configured range
     * conditions.
     */
    private void validateFieldValues(Map<String, Object> recordMap) {
        List<String> failedGreaterThan = new ArrayList<>();
        List<String> failedLesserThan = new ArrayList<>();
        List<String> failedBetween = new ArrayList<>();
        List<String> failedGreaterThanEqualTo = new ArrayList<>();
        List<String> failedLesserThanEqualTo = new ArrayList<>();

        validateRangeCondition(recordMap, greaterThanConfig, (a, b) -> a > b, "GreaterThan", failedGreaterThan);
        validateRangeCondition(recordMap, lesserThanConfig, (a, b) -> a < b, "LesserThan", failedLesserThan);
        validateBetween(recordMap, failedBetween);
        validateRangeCondition(recordMap, greaterThanEqualToConfig, (a, b) -> a >= b, "GreaterThanEqualTo",
                failedGreaterThanEqualTo);
        validateRangeCondition(recordMap, lesserThanEqualToConfig, (a, b) -> a <= b, "LesserThanEqualTo",
                failedLesserThanEqualTo);

        // Only build and throw the error if we collected any failures
        boolean hasFailures = !failedGreaterThan.isEmpty() || !failedLesserThan.isEmpty() ||
                !failedBetween.isEmpty() || !failedGreaterThanEqualTo.isEmpty() ||
                !failedLesserThanEqualTo.isEmpty();

        if (hasFailures) {
            StringBuilder errorMsg = new StringBuilder("RangeCheck VALIDATION_FAILED: ");
            if (!failedGreaterThan.isEmpty()) {
                errorMsg.append("Failed GreaterThan fields: ").append(failedGreaterThan).append(". ");
            }
            if (!failedLesserThan.isEmpty()) {
                errorMsg.append("Failed LesserThan fields: ").append(failedLesserThan).append(". ");
            }
            if (!failedBetween.isEmpty()) {
                errorMsg.append("Failed Between fields: ").append(failedBetween).append(". ");
            }
            if (!failedGreaterThanEqualTo.isEmpty()) {
                errorMsg.append("Failed GreaterThanEqualTo fields: ").append(failedGreaterThanEqualTo).append(". ");
            }
            if (!failedLesserThanEqualTo.isEmpty()) {
                errorMsg.append("Failed LesserThanEqualTo fields: ").append(failedLesserThanEqualTo).append(". ");
            }
            throw new DataException(errorMsg.toString());
        }
    }

    /**
     * Helper method to validate single-threshold range-based conditions.
     */
    private void validateRangeCondition(Object recordValue, Map<String, Number> config,
            BiPredicate<Double, Double> comparator, String checkType, List<String> failedFields) {
        for (Map.Entry<String, Number> entry : config.entrySet()) {
            String fieldPath = entry.getKey();
            Object fieldObj = getNestedFieldValue(recordValue, fieldPath);
            if (fieldObj == null) {
                LOG.warn("Field {} is missing for {} check", fieldPath, checkType);
                continue;
            }
            Number threshold = entry.getValue();
            LOG.debug("Checking threshold condition for field '{}', threshold={}, value={}",
                    fieldPath, threshold, fieldObj);
            if (fieldObj instanceof List<?>) {
                for (Object item : (List<?>) fieldObj) {
                    Number fieldValue = toNumber(item);
                    if (fieldValue == null) {
                        // If the element is null or non-numeric, skip it
                        LOG.warn("Skipping {} check for '{}'; encountered non-numeric or null item in array", checkType,
                                fieldPath);
                        continue;
                    }
                    if (!comparator.test(fieldValue.doubleValue(), threshold.doubleValue())) {
                        LOG.error("{} check failed for field {}: {} does not satisfy condition {}",
                                checkType, fieldPath, fieldValue, threshold);
                        failedFields.add(fieldPath + "=" + fieldValue);
                        // return false;
                    }
                }
            } else {
                Number fieldValue = toNumber(fieldObj);
                if (fieldValue == null) {
                    LOG.warn("Skipping {} check for '{}'; non-numeric or null value", checkType, fieldPath);
                    continue;
                }
                if (!comparator.test(fieldValue.doubleValue(), threshold.doubleValue())) {
                    LOG.error("{} check failed for field {}: {} does not satisfy condition {}",
                            checkType, fieldPath, fieldValue, threshold);
                    failedFields.add(fieldPath + "=" + fieldValue);
                    // return false;
                }
            }
        }
        // return true;
    }

    private Object getNestedFieldValue(Object recordValue, String fieldPath) {
        String[] keys = fieldPath.split("\\.");
        Object value = recordValue;

        for (String key : keys) {
            LOG.debug("inside getNestedFieldValue() Processing path segment: '{}'", key);
            if (value instanceof Struct) {
                LOG.debug("getNestedFieldValue() - Handling Struct for key: '{}'", key);
                value = ((Struct) value).get(key);
            } else if (value instanceof Map) {
                value = ((Map<?, ?>) value).get(key);
            } else if (value instanceof List<?>) { // Handle lists
                LOG.debug("getNestedFieldValue() - Handling List for key: '{}'", key);
                List<?> list = (List<?>) value;
                List<Object> extractedValues = new ArrayList<>();

                for (Object item : list) {
                    Object extracted = getNestedFieldValue(item, key);
                    if (extracted != null) {
                        extractedValues.add(extracted);
                    }
                }
                return extractedValues.isEmpty() ? null : extractedValues;
            } else {
                return null;
            }

            if (value == null)
                return null;
        }
        return value;
    }

    // Removed unused import: java.time.Instant

    /**
     * Validates that fields in the recordMap are within any of the configured
     * ranges.
     *
     * @param recordMap a map of field paths to their values
     * @return true if all fields match at least one valid range, false otherwise
     */
    private void validateBetween(Map<String, Object> recordMap, List<String> failedBetween) {
        for (Map.Entry<String, List<List<?>>> entry : betweenConfig.entrySet()) {
            String fieldPath = entry.getKey();
            LOG.debug("validateBetween() - Evaluating field '{}' against {} ranges", fieldPath,
                    entry.getValue().size());
            Object fieldObj = getNestedFieldValue(recordMap, fieldPath);

            if (fieldObj == null) {
                LOG.warn("Field {} is missing for Between check", fieldPath);
                continue;
            }

            boolean valid = false;
            if (fieldObj instanceof List<?>) {
                for (Object item : (List<?>) fieldObj) {
                    Number fieldValue = toNumber(item);
                    if (fieldValue == null) {
                        // Skip null / non-numeric array items
                        LOG.warn("Skipping Between check for '{}'; encountered non-numeric or null item in array",
                                fieldPath);
                        continue;
                    }
                    if (isWithinRange(fieldValue, entry.getValue())) {
                        valid = true;
                    } else {
                        LOG.error("Between check failed for field {}: {} not within any valid range", fieldPath,
                                fieldValue);
                        failedBetween.add(fieldPath + "=" + fieldValue);
                        // return false;
                    }
                }
            } else {
                Number fieldValue = toNumber(fieldObj);
                LOG.debug("validateBetween() - Checking if {} is within configured ranges", fieldValue);
                if (fieldValue == null) {
                    LOG.warn("Skipping Between check for '{}'; encountered non-numeric or null value", fieldPath);
                    continue;
                }
                valid = isWithinRange(fieldValue, entry.getValue());
                if (!valid) {
                    LOG.error("Between check failed for field {}: {} not within any valid range", fieldPath,
                            fieldValue);
                    failedBetween.add(fieldPath + "=" + fieldValue);
                    // return false;
                }
            }
        }
        // return true;
    }

    // overloading just for test cases and to ensure backward compatibility with
    // test cases
    private boolean validateRangeCondition(
            Object recordValue,
            Map<String, Number> config,
            BiPredicate<Double, Double> comparator,
            String checkType) {
        // Delegate to the new signature
        List<String> dummyFailedFields = new ArrayList<>();
        validateRangeCondition(recordValue, config, comparator, checkType, dummyFailedFields);
        return dummyFailedFields.isEmpty(); // Return true if no validation failures
    }

    private boolean validateBetween(Map<String, Object> recordMap) {
        List<String> dummyFailedFields = new ArrayList<>();
        validateBetween(recordMap, dummyFailedFields);
        return dummyFailedFields.isEmpty(); // Return true if no validation failures
    }

    /**
     * Helper method to check if a value falls within any of the valid ranges.
     */
    private boolean isWithinRange(Number fieldValue, List<List<?>> ranges) {
        for (List<?> range : ranges) {
            if (range.size() != 2)
                continue;
            Number min = toNumber(range.get(0));
            Number max = toNumber(range.get(1));
            if (fieldValue.doubleValue() >= min.doubleValue() && fieldValue.doubleValue() <= max.doubleValue()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Converts an object to a Number.
     * Supports Number, String representing a numeric value or timestamp,
     * java.util.Date, and Java 8 date/time objects.
     */
    private Number toNumber(Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj);
        } else if (obj instanceof String) {
            String str = ((String) obj).trim();
            // Try numeric parsing first
            try {
                return Double.parseDouble(str);
            } catch (NumberFormatException nfe) {
                // Try parsing as a timestamp
                try {
                    // Try parsing as Instant first (handles ISO-8601 formats)
                    return java.time.Instant.parse(str).toEpochMilli();
                } catch (DateTimeParseException e) {
                    // Try with our formatters
                    for (DateTimeFormatter formatter : TIMESTAMP_FORMATTERS) {
                        try {
                            return ZonedDateTime.parse(str, formatter).toInstant().toEpochMilli();
                        } catch (Exception e2) {
                            // Continue to next formatter
                        }
                    }
                    LOG.error("Failed to convert string to number or timestamp: {}", str, e);
                    throw new DataException("Unable to convert string to number or timestamp: " + str);
                }
            }
        } else if (obj instanceof java.util.Date) {
            return ((java.util.Date) obj).getTime();
        } else if (obj instanceof java.time.Instant) {
            return ((java.time.Instant) obj).toEpochMilli();
        } else if (obj instanceof java.time.ZonedDateTime) {
            return ((java.time.ZonedDateTime) obj).toInstant().toEpochMilli();
        } else if (obj instanceof java.time.temporal.Temporal) {
            // Other Java 8 temporal objects
            try {
                return java.time.Instant.from((java.time.temporal.Temporal) obj).toEpochMilli();
            } catch (Exception e) {
                LOG.error("Failed to convert temporal object to epoch millis: {}", obj, e);

                throw new DataException("Unsupported temporal type: " + obj, e);
            }
        }
        LOG.error("Unsupported type for numeric/timestamp conversion: {}", obj);

        throw new DataException("Unsupported type for numeric/timestamp conversion: " + obj);
    }

    /**
     * Extracts a Map from a Struct.
     */
    private Map<String, Object> extractRecordMap(Struct struct) {
        Map<String, Object> map = new HashMap<>();
        for (Field field : struct.schema().fields()) {
            map.put(field.name(), struct.get(field));
        }
        return map;
    }

    @Override
    public void close() {
        // No resources to clean up.
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
}
