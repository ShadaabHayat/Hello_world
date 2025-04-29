package org.extremenetworks.com;

import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.errors.DataException;
import org.apache.kafka.connect.sink.SinkRecord;
import org.junit.Before;
import org.junit.Test;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

import static org.junit.Assert.*;

public class RangeCheckOnFieldValuesTest {
    private RangeCheckOnFieldValues<SinkRecord> rangeChecker;
    private Map<String, String> props;

    @Before
    public void setUp() {
        props = new HashMap<>();
        props.put("transforms", "RangeCheck");
        props.put("transforms.RangeCheck.type", "org.extremenetworks.com.RangeCheckOnFieldValues");
        props.put("fields.GreaterThan", "{\"field1\": 100}");
        props.put("fields.LesserThan", "{\"field2\": 200}");
        props.put("fields.GreaterThanEqualTo", "{\"field4\": 100}");
        props.put("fields.LesserThanEqualTo", "{\"field5\": 200}");
        props.put("fields.Between", "{\"field3\": [[50, 150], [200, 250]]}");

        props.put("topic.name", "uztna_dq1");
        props.put("errors.deadletterqueue.topic.name", "aicore_dlq");
        props.put("behavior.on.null.values", "ignore");

    }

    @Test
    public void testRecordWithinValidRange() {
        rangeChecker = new RangeCheckOnFieldValues<>();
        rangeChecker.configure(props);
        Schema schema = SchemaBuilder.struct()
                .field("field1", Schema.INT32_SCHEMA)
                .field("field2", Schema.INT32_SCHEMA)
                .field("field3", Schema.INT32_SCHEMA)
                .field("field4", Schema.INT32_SCHEMA)
                .field("field5", Schema.INT32_SCHEMA)
                .build();

        Struct struct1 = new Struct(schema)
                .put("field1", 150) // Greater than 100
                .put("field2", 100) // Less than 200
                .put("field3", 120) // Between [50,150]
                .put("field4", 100) // Greater than or equal to 100
                .put("field5", 180); // Less than or equal to 200

        SinkRecord record = new SinkRecord("uztna_dq1", 0, null, null, schema, struct1, 0);
        SinkRecord transformed = rangeChecker.apply(record);

        assertNotNull(transformed);
        assertEquals("uztna_dq1", transformed.topic());
        assertEquals(struct1, transformed.value());
    }

    @Test(expected = DataException.class)
    public void testRecordViolatingRange() {
        rangeChecker = new RangeCheckOnFieldValues<>();
        rangeChecker.configure(props);
        Schema schema = SchemaBuilder.struct()
                .field("field1", Schema.INT32_SCHEMA)
                .field("field2", Schema.INT32_SCHEMA)
                .field("field3", Schema.INT32_SCHEMA)
                .field("field4", Schema.INT32_SCHEMA)
                .field("field5", Schema.INT32_SCHEMA)
                .build();

        Struct value = new Struct(schema)
                .put("field1", 150) // greater than 100 (should pass)
                .put("field2", 250) // Greater than 200 (should fail)
                .put("field3", 300) // Not in range [50,150] or [200,250] (should fail)
                .put("field4", 90) // Less than 100 (should fail)
                .put("field5", 250); // Greater than 200 (should fail)

        SinkRecord record = new SinkRecord("uztna_dq1", 0, null, null, schema, value, 0);
        rangeChecker.apply(record);

    }

    @Test
    public void testGreaterThanPass() {
        rangeChecker = new RangeCheckOnFieldValues<>();

        Map<String, String> testProps = new HashMap<>();
        testProps.put("fields.GreaterThan", "{\"field1\": 100}");
        testProps.put("topic.name", "uztna_dq1");
        rangeChecker.configure(testProps);

        Schema schema = SchemaBuilder.struct()
                .field("field1", Schema.INT32_SCHEMA)
                .build();

        Struct value = new Struct(schema).put("field1", 101);
        SinkRecord record = new SinkRecord("uztna_dq1", 0, null, null, schema, value, 0);
        SinkRecord transformed = validateRecord(rangeChecker.apply(record));

        // Expect original topic/value because it passes
        assertEquals("uztna_dq1", transformed.topic());
        assertEquals(value, transformed.value());
    }

    @Test(expected = DataException.class)
    public void testGreaterThanFail() {
        rangeChecker = new RangeCheckOnFieldValues<>();
        rangeChecker.configure(props);

        Schema schema = SchemaBuilder.struct()
                .field("field1", Schema.INT32_SCHEMA)
                .build();

        // Below threshold => fail
        Struct value = new Struct(schema).put("field1", 99);
        SinkRecord record = new SinkRecord("uztna_dq1", 0, null, null, schema, value, 0);
        rangeChecker.apply(record);

    }

    @Test
    public void testBetweenPass() {
        rangeChecker = new RangeCheckOnFieldValues<>();
        rangeChecker.configure(props); // Use props configured in setup()

        Schema schema = SchemaBuilder.struct()
                .field("field1", Schema.INT32_SCHEMA)
                .field("field2", Schema.INT32_SCHEMA)
                .field("field3", Schema.INT32_SCHEMA)
                .field("field4", Schema.INT32_SCHEMA)
                .field("field5", Schema.INT32_SCHEMA)
                .build();

        // Values set according to the setup configuration (assuming these ranges from
        // setup)
        Struct value = new Struct(schema)
                .put("field1", 150) // GreaterThan 100 => pass
                .put("field2", 199) // GreaterThan 200 => pass
                .put("field3", 210) // Between [50,150] or [200,250] => pass
                .put("field4", 190) // LesserThan 100 => pass
                .put("field5", 199); // LesserThan 200 => pass

        SinkRecord record = new SinkRecord("uztna_dq1", 0, null, null, schema, value, 0);
        SinkRecord transformed = validateRecord(rangeChecker.apply(record));

        // Should remain on original topic as all checks pass
        assertEquals("uztna_dq1", transformed.topic());
        assertEquals(value, transformed.value());
    }

    @Test(expected = DataException.class)
    public void testBetweenFail() {
        RangeCheckOnFieldValues<SinkRecord> transform = new RangeCheckOnFieldValues<>();
        Map<String, String> localProps = new HashMap<>(props);

        localProps.put("topic.name", "uztna_dq1");
        localProps.put("fields.Between", "{\"field1\":[[50,60],[90,100]]}");
        transform.configure(localProps);

        Schema schema = SchemaBuilder.struct()
                .field("field1", Schema.INT32_SCHEMA)
                .build();

        // Value = 75 => not in [50,60] or [90,100]
        Struct value = new Struct(schema).put("field1", 75);
        SinkRecord record = new SinkRecord("uztna_dq1", 0, null, null, schema, value, 0);
        transform.apply(record);
      
    }

    @Test
    public void testGreaterThanEqualToPass() {
        rangeChecker = new RangeCheckOnFieldValues<>();
        rangeChecker.configure(props); // Use props configured in setup()

        // For schemaless records, we need to include all required fields
        Map<String, Object> value = new HashMap<>();
        value.put("field1", 150); // > 100 (passes GreaterThan)
        value.put("field2", 5); // < 200 (passes LesserThan)
        value.put("field3", 125); // In [50,150] (passes Between)
        value.put("field4", 200); // >= 100 (passes GreaterThanEqualTo)
        value.put("field5", 200); // <= 200 (passes LesserThanEqualTo)

        SinkRecord record = new SinkRecord("uztna_dq1", 0, null, null, null, value, 0);
        SinkRecord transformed = validateRecord(rangeChecker.apply(record));

        assertEquals("uztna_dq1", transformed.topic());
        assertEquals(value, transformed.value());
    }

    @Test
    public void testLesserThanPass() {
        rangeChecker = new RangeCheckOnFieldValues<>();
        rangeChecker.configure(props); // Use props configured in setup()

        Schema schema = SchemaBuilder.struct()
                .field("field1", Schema.INT32_SCHEMA)
                .field("field2", Schema.INT32_SCHEMA)
                .field("field3", Schema.INT32_SCHEMA)
                .field("field4", Schema.INT32_SCHEMA)
                .field("field5", Schema.INT32_SCHEMA)
                .build();

        // Provide ALL fields from props with passing values
        Struct structValue = new Struct(schema)
                .put("field1", 150) // > 100 (passes GreaterThan)
                .put("field2", 5) // < 200 (passes LesserThan)
                .put("field3", 125) // In [50,150] (passes Between)
                .put("field4", 100) // >= 100 (passes GreaterThanEqualTo)
                .put("field5", 200); // <= 200 (passes LesserThanEqualTo)

        SinkRecord record = new SinkRecord("uztna_dq1", 0, null, null, schema, structValue, 0);
        SinkRecord transformed = validateRecord(rangeChecker.apply(record));

        assertEquals("uztna_dq1", transformed.topic());
        assertEquals(structValue, transformed.value());
    }

    @Test
    public void testTimestampFieldPass() {
        RangeCheckOnFieldValues<SinkRecord> transform = new RangeCheckOnFieldValues<>();

        // Create test-specific configuration
        Map<String, String> testConfig = new HashMap<>();
        testConfig.put("fields.GreaterThan", "{\"field1\": 100}");
        testConfig.put("fields.LesserThan", "{\"field2\": 200}");
        testConfig.put("fields.GreaterThanEqualTo", "{\"field4\": 100}");
        testConfig.put("fields.LesserThanEqualTo", "{\"field5\": 200}");
        // Use a range that would work for timestamps in milliseconds
        testConfig.put("fields.Between", "{\"field3\": [[1600000000000, 1700000000000]]}");
        testConfig.put("topic.name", "uztna_dq1");

        transform.configure(testConfig);

        Map<String, Object> value = new HashMap<>();
        value.put("field1", 150);
        value.put("field2", 5);
        value.put("field3", "2023-06-01T00:00:00Z"); // ~1685577600000 ms
        value.put("field4", 100);
        value.put("field5", 200);

        SinkRecord record = new SinkRecord("uztna_dq1", 0, null, null, null, value, 0);
        SinkRecord transformed = validateRecord(transform.apply(record));

        assertEquals("uztna_dq1", transformed.topic());
        assertEquals(value, transformed.value());
    }

    @Test(expected = DataException.class)
    public void testTimestampFieldFail() {
        // Testing out-of-range timestamp
        RangeCheckOnFieldValues<SinkRecord> transform = new RangeCheckOnFieldValues<>();
        Map<String, String> localProps = new HashMap<>(props);
        localProps.put("topic.name", "uztna_dq1");
        localProps.put("fields.LesserThan", "{\"field3\":1672531200000}");
        transform.configure(localProps);

        Map<String, Object> value = new HashMap<>();
        // "2023-06-01T00:00:00Z" => epoch is 1685577600000 => not < 1672531200000 =>
        // fail
        value.put("field3", "2023-06-01T00:00:00Z");

        SinkRecord record = new SinkRecord("uztna_dq1", 0, null, null, null, value, 0);
        //SinkRecord transformed = validateRecord(transform.apply(record));
        transform.apply(record);
        // Should go to DLQ
        //assertEquals("aicore_dlq", transformed.topic());
        //assertNull(transformed.value());
    }

    @Test
    public void testToNumber() throws Exception {
        RangeCheckOnFieldValues<SinkRecord> transform = new RangeCheckOnFieldValues<>();

        // Access private method via reflection
        Method toNumberMethod = RangeCheckOnFieldValues.class.getDeclaredMethod("toNumber", Object.class);
        toNumberMethod.setAccessible(true);

        // Test with Number types
        assertEquals(42, ((Number) toNumberMethod.invoke(transform, 42)).intValue());
        assertEquals(3.14, ((Number) toNumberMethod.invoke(transform, 3.14)).doubleValue(), 0.001);

        // Test with String numeric values
        assertEquals(42.0, ((Number) toNumberMethod.invoke(transform, "42")).doubleValue(), 0.001);
        assertEquals(-10.5, ((Number) toNumberMethod.invoke(transform, " -10.5 ")).doubleValue(), 0.001);

        // Test with timestamp strings
        String isoInstant = "2023-09-22T10:15:30Z";
        long expectedEpochMillis = java.time.Instant.parse(isoInstant).toEpochMilli();
        assertEquals(expectedEpochMillis, ((Number) toNumberMethod.invoke(transform, isoInstant)).longValue());

        // Test with Date object
        java.util.Date date = new java.util.Date();
        assertEquals(date.getTime(), ((Number) toNumberMethod.invoke(transform, date)).longValue());

        // Test with Instant
        java.time.Instant instant = java.time.Instant.now();
        assertEquals(instant.toEpochMilli(), ((Number) toNumberMethod.invoke(transform, instant)).longValue());

        // Test with ZonedDateTime
        java.time.ZonedDateTime zdt = java.time.ZonedDateTime.now();
        assertEquals(zdt.toInstant().toEpochMilli(), ((Number) toNumberMethod.invoke(transform, zdt)).longValue());

        // Test error case - unsupported type
        try {
            toNumberMethod.invoke(transform, new Object());
            fail("Should have thrown an exception");
        } catch (InvocationTargetException e) {
            assertTrue(e.getCause() instanceof DataException);
        }
    }

    @Test
    public void testClose() {
        // Just verify close() doesn't throw any exceptions
        RangeCheckOnFieldValues<SinkRecord> transform = new RangeCheckOnFieldValues<>();
        transform.close();

        // No assertion needed - this just ensures the method is covered
    }

    @Test
    public void testConfig() {
        RangeCheckOnFieldValues<SinkRecord> transform = new RangeCheckOnFieldValues<>();
        ConfigDef config = transform.config();

        // Verify config is not null
        assertNotNull(config);

        // Verify specific config properties exist
        assertTrue(config.configKeys().containsKey("fields.GreaterThan"));
        assertTrue(config.configKeys().containsKey("fields.LesserThan"));
        assertTrue(config.configKeys().containsKey("fields.Between"));
        assertTrue(config.configKeys().containsKey("fields.GreaterThanEqualTo"));
        assertTrue(config.configKeys().containsKey("fields.LesserThanEqualTo"));
        assertTrue(config.configKeys().containsKey("topic.name"));

        // Verify importance of topic.name is HIGH
        assertEquals(ConfigDef.Importance.HIGH, config.configKeys().get("topic.name").importance);
    }

    @Test
    public void testToNumberComprehensive() throws Exception {
        RangeCheckOnFieldValues<SinkRecord> transform = new RangeCheckOnFieldValues<>();
        Method toNumberMethod = RangeCheckOnFieldValues.class.getDeclaredMethod("toNumber", Object.class);
        toNumberMethod.setAccessible(true);

        // Additional timestamp format tests
        String zonedDateTime = "2023-09-22T10:15:30+01:00[Europe/London]";
        assertEquals(
                ZonedDateTime.parse(zonedDateTime).toInstant().toEpochMilli(),
                ((Number) toNumberMethod.invoke(transform, zonedDateTime)).longValue());

        // Test with java.time.LocalDate through reflection (should throw exception)
        try {
            java.time.LocalDate localDate = java.time.LocalDate.of(2023, 9, 22);
            toNumberMethod.invoke(transform, localDate);
            fail("Should throw exception for LocalDate without proper conversion");
        } catch (InvocationTargetException e) {
            assertTrue(e.getCause() instanceof DataException);
        }

        // Test with invalid timestamp string
        try {
            toNumberMethod.invoke(transform, "not-a-timestamp");
            fail("Should have thrown exception for invalid timestamp");
        } catch (InvocationTargetException e) {
            assertTrue(e.getCause() instanceof DataException);
        }
    }

    @Test
    public void testGetNestedFieldValue() throws Exception {
        RangeCheckOnFieldValues<SinkRecord> transform = new RangeCheckOnFieldValues<>();
        Method method = RangeCheckOnFieldValues.class.getDeclaredMethod("getNestedFieldValue", Object.class,
                String.class);
        method.setAccessible(true);

        // Test with Map - simple key
        Map<String, Object> simpleMap = new HashMap<>();
        simpleMap.put("key1", "value1");
        assertEquals("value1", method.invoke(transform, simpleMap, "key1"));

        // Test with Map - nested path
        Map<String, Object> nestedMap = new HashMap<>();
        Map<String, Object> innerMap = new HashMap<>();
        innerMap.put("innerKey", "innerValue");
        nestedMap.put("outerKey", innerMap);
        assertEquals("innerValue", method.invoke(transform, nestedMap, "outerKey.innerKey"));

        // Test with List in path
        Map<String, Object> mapWithList = new HashMap<>();
        List<Map<String, String>> listOfMaps = new ArrayList<>();
        Map<String, String> item1 = new HashMap<>();
        item1.put("name", "John");
        Map<String, String> item2 = new HashMap<>();
        item2.put("name", "Jane");
        listOfMaps.add(item1);
        listOfMaps.add(item2);
        mapWithList.put("people", listOfMaps);

        Object result = method.invoke(transform, mapWithList, "people.name");
        assertTrue(result instanceof List);
        List<?> resultList = (List<?>) result;
        assertEquals(2, resultList.size());
        assertTrue(resultList.contains("John"));
        assertTrue(resultList.contains("Jane"));

        // Test with null value in path
        Map<String, Object> mapWithNull = new HashMap<>();
        mapWithNull.put("nullKey", null);
        assertNull(method.invoke(transform, mapWithNull, "nullKey.anything"));

        // Test with non-existent key
        assertNull(method.invoke(transform, simpleMap, "nonExistentKey"));

        // Test with Struct
        Schema schema = SchemaBuilder.struct()
                .field("structField", Schema.STRING_SCHEMA)
                .build();
        Struct struct = new Struct(schema).put("structField", "structValue");
        assertEquals("structValue", method.invoke(transform, struct, "structField"));
    }

    @Test
    public void testValidateRangeCondition() throws Exception {
        RangeCheckOnFieldValues<SinkRecord> transform = new RangeCheckOnFieldValues<>();
        Method method = RangeCheckOnFieldValues.class.getDeclaredMethod(
                "validateRangeCondition", Object.class, Map.class, BiPredicate.class, String.class);
        method.setAccessible(true);

        // Test with empty config
        Map<String, Number> emptyConfig = new HashMap<>();
        assertTrue((Boolean) method.invoke(transform, new HashMap<>(), emptyConfig,
                (BiPredicate<Double, Double>) (a, b) -> a > b, "GreaterThan"));

        // Test with List field value (all pass)
        Map<String, Object> recordWithList = new HashMap<>();
        List<Integer> numberList = List.of(10, 20, 30);
        recordWithList.put("numbers", numberList);

        Map<String, Number> configForList = new HashMap<>();
        configForList.put("numbers", 5);

        assertTrue((Boolean) method.invoke(transform, recordWithList, configForList,
                (BiPredicate<Double, Double>) (a, b) -> a > b, "GreaterThan"));

        // Test with List field value (one fails)
        List<Integer> mixedList = List.of(10, 2, 30); // 2 < 5
        recordWithList.put("mixedNumbers", mixedList);

        configForList.put("mixedNumbers", 5);

        assertFalse((Boolean) method.invoke(transform, recordWithList, configForList,
                (BiPredicate<Double, Double>) (a, b) -> a > b, "GreaterThan"));

        // Test with missing field
        assertTrue((Boolean) method.invoke(transform, recordWithList,
                Map.of("missingField", 42), (BiPredicate<Double, Double>) (a, b) -> a > b, "GreaterThan"));
    }

    @Test
    public void testValidateBetweenComprehensive() throws Exception {
        RangeCheckOnFieldValues<SinkRecord> transform = new RangeCheckOnFieldValues<>();

        // Access private method and configuration field via reflection
        Method validateBetweenMethod = RangeCheckOnFieldValues.class.getDeclaredMethod("validateBetween", Map.class);
        validateBetweenMethod.setAccessible(true);

        java.lang.reflect.Field betweenConfigField = RangeCheckOnFieldValues.class.getDeclaredField("betweenConfig");
        betweenConfigField.setAccessible(true);

        // Create a test configuration
        Map<String, List<List<?>>> testConfig = new HashMap<>();

        // Range for scalar value: [[0, 100], [200, 300]]
        List<List<?>> scalarRanges = new ArrayList<>();
        scalarRanges.add(Arrays.asList(0, 100));
        scalarRanges.add(Arrays.asList(200, 300));
        testConfig.put("scalarField", scalarRanges);

        // Range for array value: [[50, 150]]
        List<List<?>> arrayRanges = new ArrayList<>();
        arrayRanges.add(Arrays.asList(50, 150));
        testConfig.put("arrayField", arrayRanges);

        // Set the configuration via reflection
        betweenConfigField.set(transform, testConfig);

        // Test case 1: Both scalar and array values are within range
        Map<String, Object> recordMap = new HashMap<>();
        recordMap.put("scalarField", 75); // Within [0, 100]

        List<Integer> validArray = Arrays.asList(60, 100, 145); // All within [50, 150]
        recordMap.put("arrayField", validArray);

        assertTrue((Boolean) validateBetweenMethod.invoke(transform, recordMap));

        // Test case 2: Scalar value not in any range
        Map<String, Object> recordMap2 = new HashMap<>();
        recordMap2.put("scalarField", 150); // Outside [0, 100] and [200, 300]
        recordMap2.put("arrayField", validArray);

        assertFalse((Boolean) validateBetweenMethod.invoke(transform, recordMap2));

        // Test case 3: One value in array outside range
        Map<String, Object> recordMap3 = new HashMap<>();
        recordMap3.put("scalarField", 75);

        List<Integer> invalidArray = Arrays.asList(60, 200, 100); // 200 is outside [50, 150]
        recordMap3.put("arrayField", invalidArray);

        assertFalse((Boolean) validateBetweenMethod.invoke(transform, recordMap3));

        // Test case 4: Missing field
        Map<String, Object> recordMap4 = new HashMap<>();
        recordMap4.put("scalarField", 75);
        // arrayField is missing

        assertTrue((Boolean) validateBetweenMethod.invoke(transform, recordMap4));

        // Test case 5: Scalar in second range
        Map<String, Object> recordMap5 = new HashMap<>();
        recordMap5.put("scalarField", 250); // Within [200, 300]
        recordMap5.put("arrayField", validArray);

        assertTrue((Boolean) validateBetweenMethod.invoke(transform, recordMap5));

        // Test case 6: Timestamp check
        List<List<?>> dateRanges = new ArrayList<>();
        dateRanges.add(Arrays.asList("2023-01-01T00:00:00Z", "2023-12-31T23:59:59Z"));
        testConfig.put("dateField", dateRanges);

        Map<String, Object> recordMap6 = new HashMap<>();
        recordMap6.put("scalarField", 75);
        recordMap6.put("arrayField", validArray);
        recordMap6.put("dateField", "2023-06-15T12:00:00Z"); // Mid-year, should be in range

        assertTrue((Boolean) validateBetweenMethod.invoke(transform, recordMap6));
    }

    @Test(expected = DataException.class)
    public void testArrayFieldPass() {
        // Configure the transformer with a "GreaterThan" range check on
        // channelList.channelNumber > 100
        Map<String, String> props = new HashMap<>();
        props.put("fields.GreaterThan", "{\"channelList.channelNumber\":100}");
        props.put("topic.name", "testTopic");
        props.put("mask.type", "hash"); // or any valid mask type, just to satisfy config

        RangeCheckOnFieldValues<SinkRecord> rangeChecker = new RangeCheckOnFieldValues<>();
        rangeChecker.configure(props);

        // 1) Define nested schema for "channelList"
        Schema channelSchema = SchemaBuilder.struct().name("com.example.Channel")
                .field("channelNumber", Schema.INT32_SCHEMA)
                .field("channelChangeCount", Schema.INT32_SCHEMA)
                .build();

        Schema deviceSchema = SchemaBuilder.struct().name("com.example.DeviceRecord")
                .field("severity", Schema.STRING_SCHEMA)
                .field("interfaceName", Schema.STRING_SCHEMA)
                .field("channelList", SchemaBuilder.array(channelSchema).optional().build())
                .build();

        // 2) Build a Struct with an array of "Channel"
        Struct channel1 = new Struct(channelSchema)
                .put("channelNumber", 101) // Passes > 100
                .put("channelChangeCount", 3);

        Struct channel2 = new Struct(channelSchema)
                .put("channelNumber", 50) // Passes > 100
                .put("channelChangeCount", 5);

        Struct deviceValue = new Struct(deviceSchema)
                .put("severity", "CRITICAL")
                .put("interfaceName", "Gig0/1")
                .put("channelList", Arrays.asList(channel1, channel2));

        // Create a SinkRecord that references the schema and value
        SinkRecord record = new SinkRecord("testTopic", 0, null, null, deviceSchema, deviceValue, 0);

        // Apply the RangeCheck transform; it should fail and route to the DLQ topic
        SinkRecord transformed = rangeChecker.apply(record);
    
        // If your code returns null on failure, call validateRecord(...) like in other tests
        SinkRecord finalTransformed = validateRecord(transformed);
    
        // Because channel2 fails the range check, the record should go to DLQ
        assertEquals("aicore_dlq", finalTransformed.topic());
        assertNull(finalTransformed.value());
    }

    public SinkRecord validateRecord(SinkRecord record) {
        if (record.value() == null) {
            return new SinkRecord(
                    "aicore_dlq",
                    record.kafkaPartition(),
                    record.keySchema(),
                    record.key(),
                    record.valueSchema(),
                    record.value(),
                    record.timestamp());
        }
        return record;
    }

}
