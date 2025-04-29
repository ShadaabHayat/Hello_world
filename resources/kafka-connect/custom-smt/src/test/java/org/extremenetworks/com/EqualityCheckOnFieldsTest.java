package org.extremenetworks.com;

import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.errors.DataException;
import org.apache.kafka.connect.sink.SinkRecord;
import org.junit.jupiter.api.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.Instant;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class EqualityCheckOnFieldsTest {
    private EqualityCheckOnFields<SinkRecord> eqCheckedRecords;
    private Map<String, Object> props;

    @BeforeEach
    public void setUp() {
        props = new HashMap<>();
        props.put("transforms", "EqualityCheck");
        props.put("transforms.EqualityCheck.type", "org.extremenetworks.com.EqualityCheckOnFields");
        props.put("fields.notEquality", "{\"field1\": [9, 0, 8, 4.8234, 9.9999], \"field2\": [\"pol\", \"POC\", \"\", null]}");
        props.put("topic.name", "uztna_dq");
        props.put("fields.Equality", "{\"field4\": [93, 88, 11, 92], \"field6\": [\"pol\", \"POC\", \"\", null]}");
        props.put("errors.deadletterqueue.topic.name", "aicore_dlq");
        props.put("behavior.on.null.values", "ignore");
    }

    @Test
    public void testConfigurationParsing() {
        eqCheckedRecords = new EqualityCheckOnFields<>();
        eqCheckedRecords.configure(props);

        try {
            Method configureMethod = EqualityCheckOnFields.class.getDeclaredMethod("configure", Map.class);
            configureMethod.setAccessible(true);
            configureMethod.invoke(eqCheckedRecords, props);

            Map<String, List<?>> notEqualityConfig = getPrivateFieldValue(eqCheckedRecords, "notEqualityConfig");
            Map<String, List<?>> equalityConfig = getPrivateFieldValue(eqCheckedRecords, "equalityConfig");
            String targetTopic = getPrivateFieldValue(eqCheckedRecords, "targetTopic");

            // Existing assertions
            assertEquals(2, notEqualityConfig.size(), "Expected 2 fields in notEquality config");
            assertTrue(notEqualityConfig.containsKey("field1"), "Expected field1 in notEquality config");
            assertTrue(notEqualityConfig.containsKey("field2"), "Expected field2 in notEquality config");

            // Additional assertions for numeric type handling
            List<?> field1Values = notEqualityConfig.get("field1");
            assertEquals(5, field1Values.size(), "Expected 5 values for field1");
            assertTrue(field1Values.get(0) instanceof Number, "First value should be Integer");
            assertTrue(field1Values.get(3) instanceof Number, "Fourth value should be Double");
            assertEquals(4.8234, ((Number)field1Values.get(3)).doubleValue(), 0.0001, "Double value should match exactly");

            // Additional string handling assertions
            List<?> field2Values = notEqualityConfig.get("field2");
            assertEquals(4, field2Values.size(), "Expected 4 values for field2");
            assertTrue(field2Values.get(0).equals("pol"), "String value should be trimmed");
            assertEquals("", field2Values.get(2), "Empty string should be preserved");
            assertNull(field2Values.get(3), "Null value should be preserved");

            // Test equality config with additional assertions
            assertEquals(2, equalityConfig.size(), "Expected 2 fields in equality config");
            List<?> field4Values = equalityConfig.get("field4");
            List<?> field6Values = equalityConfig.get("field6");

            assertTrue(field4Values.stream().allMatch(v -> v instanceof Number), "All numeric values should be Numbers");
            assertTrue(field6Values.stream().allMatch(v -> v == null || v instanceof String), "String values should be Strings or null");

            // Topic configuration validation
            assertEquals("uztna_dq", targetTopic, "Target topic should match");
            assertNotNull(targetTopic, "Target topic should not be null");
            assertFalse(targetTopic.trim().isEmpty(), "Target topic should not be empty");

        } catch (Exception e) {
            fail("Inside testConfigurationParsing: " + e.getMessage());
        }
    }

    @Test
    public void testConfigureWithEmptyTopic() {
        assertThrows(ConfigException.class, () -> {
            props.put("topic.name", "");
            eqCheckedRecords = new EqualityCheckOnFields<>();
            eqCheckedRecords.configure(props);
        });
    }

    @Test
    public void testCheckConditions() throws Exception {
        eqCheckedRecords = new EqualityCheckOnFields<>();
        eqCheckedRecords.configure(props);

        Method checkConditionsMethod = EqualityCheckOnFields.class.getDeclaredMethod("checkConditions", Map.class);
        checkConditionsMethod.setAccessible(true);

        Schema schema = SchemaBuilder.struct()
                .field("field1", Schema.INT32_SCHEMA)
                .field("field2", SchemaBuilder.string().optional().build())
                .field("field4", Schema.INT32_SCHEMA)
                .field("field6", SchemaBuilder.string().optional().build())
                .build();

        // Test case 1: Record failing not-equality check
        Struct struct1 = new Struct(schema)
                .put("field1", 9)  // This value is in notEquality list
                .put("field2", "pol")  // This value is in notEquality list
                .put("field4", 93)
                .put("field6", "ower");

        SinkRecord record1 = new SinkRecord("uztna_dq", 0, null, null, schema, struct1, 0);
        
        // Expect a DataException when applying the transformation
        assertThrows(DataException.class, () -> {
            eqCheckedRecords.apply(record1);
        }, "Should throw DataException for failed records");

        boolean result1 = (boolean) checkConditionsMethod.invoke(eqCheckedRecords,
                Map.of(
                        "field1", struct1.get("field1"),
                        "field2", struct1.get("field2"),
                        "field4", struct1.get("field4"),
                        "field6", struct1.get("field6")));
        assertFalse(result1, "Record should fail due to not-equality check");

        // Additional assertion for timestamp fields
        Method isValidTimestampMethod = EqualityCheckOnFields.class.getDeclaredMethod("isValidTimestamp", String.class);
        isValidTimestampMethod.setAccessible(true);
        assertTrue((boolean)isValidTimestampMethod.invoke(eqCheckedRecords, "2024-01-30T10:15:30Z"), "Should validate ISO timestamp");
        assertFalse((boolean)isValidTimestampMethod.invoke(eqCheckedRecords, "invalid-date"), "Should reject invalid timestamp");
    }

    @Test
    public void testCheckConditions_with_FieldValue_Null() throws Exception {
        eqCheckedRecords = new EqualityCheckOnFields<>();
        eqCheckedRecords.configure(props);

        Schema schema = SchemaBuilder.struct()
                .field("field1", Schema.INT32_SCHEMA)
                .field("field2", SchemaBuilder.string().optional().build())
                .field("field4", Schema.INT32_SCHEMA)
                .field("field6", SchemaBuilder.string().optional().build())
                .build();

        // Test case with null value in equality check field
        Struct struct3 = new Struct(schema)
                .put("field1", 5)
                .put("field2", "test2")
                .put("field4", 100)
                .put("field6", null);

        SinkRecord record3 = new SinkRecord("uztna_dq", 0, Schema.STRING_SCHEMA, "test-key", schema, struct3, 0);

        Method checkConditionsMethod3 = EqualityCheckOnFields.class.getDeclaredMethod("checkConditions", Map.class);
        checkConditionsMethod3.setAccessible(true);

        Map<String, Object> map1 = new HashMap<>();
        map1.put("field1", struct3.get("field1"));
        map1.put("field2", struct3.get("field2"));
        map1.put("field4", struct3.get("field4"));
        map1.put("field6", struct3.get("field6"));

        boolean result3 = (boolean) checkConditionsMethod3.invoke(eqCheckedRecords, map1);

        assertFalse(result3, "Record should fail due to equality check");
        
        // Expect a DataException when applying the transformation
        assertThrows(DataException.class, () -> {
            eqCheckedRecords.apply(record3);
        }, "Should throw DataException for failed records");
    }

    @Test
    public void testCheckConditions_withPassedRecord() throws Exception {
        eqCheckedRecords = new EqualityCheckOnFields<>();
        eqCheckedRecords.configure(props);

        Schema schema = SchemaBuilder.struct()
                .field("field1", Schema.INT32_SCHEMA)
                .field("field2", Schema.STRING_SCHEMA)
                .field("field4", Schema.INT32_SCHEMA)
                .field("field6", Schema.STRING_SCHEMA)
                .build();

        // Test case with valid values
        Struct struct2 = new Struct(schema)
                .put("field1", 15)  // Not in notEquality list
                .put("field2", "test3")  // Not in notEquality list
                .put("field4", 93)  // In equality list
                .put("field6", "pol");  // In equality list

        SinkRecord record2 = new SinkRecord("uztna_dq", 0, null, null, schema, struct2, 0);
        record2.headers().add("errors.deadletterqueue.topic.name", "aicore_dlq", Schema.STRING_SCHEMA);

        SinkRecord transformedRecord2 = eqCheckedRecords.apply(record2);

        Method checkConditionsMethod2 = EqualityCheckOnFields.class.getDeclaredMethod("checkConditions", Map.class);
        checkConditionsMethod2.setAccessible(true);

        Map<String, Object> map2 = new HashMap<>();
        map2.put("field1", struct2.get("field1"));
        map2.put("field2", struct2.get("field2"));
        map2.put("field4", struct2.get("field4"));
        map2.put("field6", struct2.get("field6"));

        boolean result2 = (boolean) checkConditionsMethod2.invoke(eqCheckedRecords, map2);
        assertEquals("uztna_dq", transformedRecord2.topic(), "Record should retain the original topic");
        assertTrue(result2, "Record should pass checks");
        assertNotNull(transformedRecord2, "Transformed record should not be null");
        assertEquals(schema, transformedRecord2.valueSchema(), "Schema should be preserved");
    }
    @Test
    public void testParseJsonToMapWithInvalidJson() throws Exception {
        EqualityCheckOnFields<SinkRecord> transformer = new EqualityCheckOnFields<>();

        // Get the private method using reflection
        Method method = EqualityCheckOnFields.class.getDeclaredMethod("parseJsonToMap", String.class);
        method.setAccessible(true); // Make it accessible

        try {
            method.invoke(transformer, "{invalid json}");
        } catch (InvocationTargetException e) {
            // Unwrap the actual exception thrown inside the method
            Throwable cause = e.getCause();
            if (cause instanceof DataException) {
                return; // Test passes since the expected exception was thrown
            } else {
                throw e; // Rethrow unexpected exceptions
            }
        }

        // If no exception is thrown, the test should fail
        throw new AssertionError("Expected DataException but no exception was thrown.");
    }

    @Test
    public void testIsValueInListWithBoolean() throws Exception {
        EqualityCheckOnFields<SinkRecord> transformer = new EqualityCheckOnFields<>();
        Method isValueInListMethod = EqualityCheckOnFields.class.getDeclaredMethod("isValueInList", Object.class, List.class);
        isValueInListMethod.setAccessible(true);

        List<?> checkValues = List.of(true, false);
        assertTrue((Boolean) isValueInListMethod.invoke(transformer, true, checkValues));
        assertTrue((Boolean) isValueInListMethod.invoke(transformer, false, checkValues));
        assertFalse((Boolean) isValueInListMethod.invoke(transformer, "true", checkValues)); // Invalid type
    }
    @Test
    public void testApplyWithSchema() {
        eqCheckedRecords = new EqualityCheckOnFields<>();
        eqCheckedRecords.configure(props);

        Schema schema = SchemaBuilder.struct()
                .field("field1", Schema.INT32_SCHEMA)
                .field("field2", Schema.STRING_SCHEMA)
                .field("field4", Schema.INT32_SCHEMA)
                .field("field6", Schema.STRING_SCHEMA)
                .build();

        Struct struct = new Struct(schema)
                .put("field1", 15)  // Not in notEquality list
                .put("field2", "test3")  // Not in notEquality list
                .put("field4", 93)  // In equality list
                .put("field6", "pol");  // In equality list

        SinkRecord record = new SinkRecord("uztna_dq", 0, null, null, schema, struct, 0);
        SinkRecord transformedRecord = eqCheckedRecords.apply(record);

        assertEquals("uztna_dq", transformedRecord.topic(), "Record should retain the original topic");
        assertNotNull(transformedRecord, "Transformed record should not be null");
        assertEquals(schema, transformedRecord.valueSchema(), "Schema should be preserved");
    }
    @Test
    public void testApplySchemaless() {
        eqCheckedRecords = new EqualityCheckOnFields<>();
        eqCheckedRecords.configure(props);

        Map<String, Object> recordMap = new HashMap<>();
        recordMap.put("field1", 15);  // Not in notEquality list
        recordMap.put("field2", "test3");  // Not in notEquality list
        recordMap.put("field4", 93);  // In equality list
        recordMap.put("field6", "pol");  // In equality list

        SinkRecord record = new SinkRecord("uztna_dq", 0, null, null, null, recordMap, 0);
        SinkRecord transformedRecord = eqCheckedRecords.apply(record);

        assertEquals("uztna_dq", transformedRecord.topic(), "Record should retain the original topic");
        assertNotNull(transformedRecord, "Transformed record should not be null");
        assertNull(transformedRecord.valueSchema(), "Schemaless record should have null schema");
    }

    @Test
    public void testIsValueInListWithZonedDateTime() throws Exception {
        eqCheckedRecords = new EqualityCheckOnFields<>();
        Method isValueInListMethod = EqualityCheckOnFields.class.getDeclaredMethod("isValueInList", Object.class, List.class);
        isValueInListMethod.setAccessible(true);

        ZonedDateTime now = ZonedDateTime.now();
        List<?> checkValues = List.of(now.toString(), now.plusHours(1).toString());

        assertTrue((Boolean) isValueInListMethod.invoke(eqCheckedRecords, now, checkValues), "ZonedDateTime value should match");
        assertFalse((Boolean) isValueInListMethod.invoke(eqCheckedRecords, now.plusDays(1), checkValues), "ZonedDateTime value should not match");
    }

    @Test
    public void testIsValueInListWithTimestamp() throws Exception {
        eqCheckedRecords = new EqualityCheckOnFields<>();
        Method isValueInListMethod = EqualityCheckOnFields.class.getDeclaredMethod("isValueInList", Object.class, List.class);
        isValueInListMethod.setAccessible(true);

        // 1. Create Timestamp from an Instant in UTC
        java.sql.Timestamp timestamp = java.sql.Timestamp.from(
                Instant.parse("2024-01-30T12:34:56Z")  // Explicit UTC
        );
        List<?> checkValues = List.of("2024-01-30T12:34:56Z");

        // 2. Verify the timestamp matches the check value
        assertTrue((Boolean) isValueInListMethod.invoke(eqCheckedRecords, timestamp, checkValues), "Timestamp value should match");

        // 3. Test a non-matching timestamp
        java.sql.Timestamp wrongTimestamp = java.sql.Timestamp.from(
                Instant.parse("2025-01-30T12:34:56Z")  // Different UTC time
        );
        assertFalse((Boolean) isValueInListMethod.invoke(eqCheckedRecords, wrongTimestamp, checkValues), "Timestamp value should not match");
    }

    @Test
    public void testIsValidTimestampWithInvalidFormats() throws Exception {
        eqCheckedRecords = new EqualityCheckOnFields<>();

        // Use reflection to access the private method
        Method isValidTimestampMethod = EqualityCheckOnFields.class.getDeclaredMethod("isValidTimestamp", String.class);
        isValidTimestampMethod.setAccessible(true);

        // Test invalid timestamp formats
        assertFalse((Boolean) isValidTimestampMethod.invoke(eqCheckedRecords, "invalid-date"), "Invalid timestamp should return false");  // Pass instance as first argument
        assertFalse((Boolean) isValidTimestampMethod.invoke(eqCheckedRecords, ""), "Empty string should return false");  // Pass instance as first argument
        assertFalse((Boolean) isValidTimestampMethod.invoke(eqCheckedRecords, (String) null), "Null should return false");  // Pass instance as first argument
    }

    @Test
    public void testCheckConditionsWithMissingFields() {
        assertThrows(ConfigException.class, () -> {
            eqCheckedRecords = new EqualityCheckOnFields<>();
            eqCheckedRecords.configure(props);

            Method checkConditionsMethod = EqualityCheckOnFields.class.getDeclaredMethod("checkConditions", Map.class);
            checkConditionsMethod.setAccessible(true);

            Map<String, Object> recordMap = new HashMap<>();
            recordMap.put("field1", 15);  // Missing field2, field4, and field6

            try {
                checkConditionsMethod.invoke(eqCheckedRecords, recordMap);
            } catch (InvocationTargetException e) {
                throw e.getCause();  // Unwrap the ConfigException
            }
        });
    }

    @Test
    public void testParseJsonToMapWithEmptyJson() throws Exception {
        EqualityCheckOnFields<?> eqCheckedRecords = new EqualityCheckOnFields<>();

        // Use reflection to access private method
        Method method = EqualityCheckOnFields.class.getDeclaredMethod("parseJsonToMap", String.class);
        method.setAccessible(true);

        // Invoke method with empty JSON string
        @SuppressWarnings("unchecked")
        Map<String, List<?>> result = (Map<String, List<?>>) method.invoke(eqCheckedRecords, "");

        assertTrue(result.isEmpty(), "Empty JSON should return an empty map");
    }

    @Test
    public void testClose() {
        eqCheckedRecords = new EqualityCheckOnFields<>();
        eqCheckedRecords.close();  // Should not throw any exceptions
    }

    @Test
    public void testConfig() {
        eqCheckedRecords = new EqualityCheckOnFields<>();
        ConfigDef configDef = eqCheckedRecords.config();
        assertNotNull(configDef, "ConfigDef should not be null");
        assertTrue(configDef.names().containsAll(List.of("fields.notEquality", "topic.name", "fields.Equality")), "ConfigDef should contain expected keys");
    }

    @SuppressWarnings("unchecked")
    private <T> T getPrivateFieldValue(Object obj, String fieldName) throws Exception {
        java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return (T) field.get(obj);
    }
}
