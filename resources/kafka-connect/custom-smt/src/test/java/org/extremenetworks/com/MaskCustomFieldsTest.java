package org.extremenetworks.com;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.sink.SinkRecord;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class MaskCustomFieldsTest {

    private static final Logger log = LoggerFactory.getLogger(MaskCustomFields.class);
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()-_+=<>?";
    private static final SecureRandom RANDOM = new SecureRandom();
    private MaskCustomFields<SinkRecord> maskCustomFields;

    public static String generateRandomPassword(int length) {
        StringBuilder password = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = RANDOM.nextInt(CHARACTERS.length());
            password.append(CHARACTERS.charAt(index));
        }
        return password.toString();
    }

    @Before
    public void setUp() {
        Map<String, String> props = new HashMap<>();
        props.put("mask.fields", "password, username, code, secret, data, is_secret, worth_float32, worth_float64");
        props.put("topic.name", "my-topic");
        maskCustomFields = new MaskCustomFields.Value<>();
        maskCustomFields.configure(props);
    }

    @Test
    public void maskFieldWithSha256() {
        // generate random password
        String password = generateRandomPassword(10);

        Schema schema = SchemaBuilder.struct()
                .field("code", Schema.STRING_SCHEMA)
                .field("username", Schema.STRING_SCHEMA)
                .field("password", Schema.STRING_SCHEMA)
                .field("secret", Schema.INT32_SCHEMA)
                .field("age", Schema.INT32_SCHEMA)
                .field("address", Schema.STRING_SCHEMA)
                .field("email", Schema.STRING_SCHEMA)
                .field("is_secret", Schema.BOOLEAN_SCHEMA)
                .field("worth_float32", Schema.FLOAT32_SCHEMA)
                .field("worth_float64", Schema.FLOAT64_SCHEMA)
                .build();

        Struct struct = new Struct(schema)
                .put("code", "12345")
                .put("username", "user123")
                .put("password", password)
                .put("secret", 101010)
                .put("age", 25)
                .put("address", "1234 Main St")
                .put("email", "jawad@gmail.com")
                .put("is_secret", true)
                .put("worth_float32", 123.45f)
                .put("worth_float64", 123.45);

        SinkRecord record = new SinkRecord("my-topic", 0, null, null, schema, struct, 0);
        SinkRecord transformedRecord = maskCustomFields.apply(record);

        String usernameHash = DigestUtils.sha256Hex(struct.get("username").toString());
        String passwordHash = DigestUtils.sha256Hex(struct.get("password").toString());
        String codeHash = DigestUtils.sha256Hex(struct.get("code").toString());
        String secretHash = DigestUtils.sha256Hex(struct.get("secret").toString());
        String isSecretHash = DigestUtils.sha256Hex(struct.get("is_secret").toString());
        String worthFloat32Hash = DigestUtils.sha256Hex(struct.get("worth_float32").toString());
        String worthFloat64Hash = DigestUtils.sha256Hex(struct.get("worth_float64").toString());


        Struct transformedValue = (Struct) transformedRecord.value();

        assertNotNull(transformedValue.get("password"));

        assertNotNull(transformedValue.get("username"));
        assertNotNull(transformedValue.get("code"));
        assertNotNull(transformedValue.get("secret"));
        assertNotNull(transformedValue.get("age"));
        assertNotNull(transformedValue.get("address"));
        assertNotNull(transformedValue.get("email"));
        assertNotNull(transformedValue.get("is_secret"));
        assertNotNull(transformedValue.get("worth_float32"));
        assertNotNull(transformedValue.get("worth_float64"));
        assertEquals(passwordHash, transformedValue.getString("password"));
        assertEquals(usernameHash, transformedValue.getString("username"));
        assertEquals(codeHash, transformedValue.getString("code"));
        assertEquals(secretHash, transformedValue.getString("secret"));
        assertEquals(25, (long) transformedValue.getInt32("age"));
        assertEquals("1234 Main St", transformedValue.getString("address"));
        assertEquals("jawad@gmail.com", transformedValue.getString("email"));
        assertEquals(isSecretHash, transformedValue.getString("is_secret"));
        assertEquals(worthFloat32Hash, transformedValue.getString("worth_float32"));
        assertEquals(worthFloat64Hash, transformedValue.getString("worth_float64"));
    }

    @Test
    public void testApplySchemaless() {
        // generate random password and data
        String password = generateRandomPassword(10);
        String username = "testuser123";
        String code = "ABC123";
        int secret = 987654;
        boolean isSecret = true;
        float worthFloat32 = 456.78f;
        double worthFloat64 = 901.23;

        // Create a schemaless record (using Map)
        Map<String, Object> value = new HashMap<>();
        value.put("code", code);
        value.put("username", username);
        value.put("password", password);
        value.put("secret", secret);
        value.put("age", 30);
        value.put("address", "5678 Side St");
        value.put("email", "test@example.com");
        value.put("is_secret", isSecret);
        value.put("worth_float32", worthFloat32);
        value.put("worth_float64", worthFloat64);

        // Create a record without schema
        SinkRecord record = new SinkRecord("my-topic", 0, null, null, null, value, 0);
        SinkRecord transformedRecord = maskCustomFields.apply(record);

        // Get the transformed value
        @SuppressWarnings("unchecked")
        Map<String, Object> transformedValue = (Map<String, Object>) transformedRecord.value();

        // Verify the transformations
        assertEquals(DigestUtils.sha256Hex(code), transformedValue.get("code"));
        assertEquals(DigestUtils.sha256Hex(username), transformedValue.get("username"));
        assertEquals(DigestUtils.sha256Hex(password), transformedValue.get("password"));
        assertEquals(DigestUtils.sha256Hex(String.valueOf(secret)), transformedValue.get("secret"));
        assertEquals(30, transformedValue.get("age"));
        assertEquals("5678 Side St", transformedValue.get("address"));
        assertEquals("test@example.com", transformedValue.get("email"));
        assertEquals(DigestUtils.sha256Hex(String.valueOf(isSecret)), transformedValue.get("is_secret"));
        assertEquals(DigestUtils.sha256Hex(String.valueOf(worthFloat32)), transformedValue.get("worth_float32"));
        assertEquals(DigestUtils.sha256Hex(String.valueOf(worthFloat64)), transformedValue.get("worth_float64"));
    }

    @Test
    public void testNullAndEmptyFieldsWithSchema() {
        Schema schema = SchemaBuilder.struct()
                .field("username", Schema.OPTIONAL_STRING_SCHEMA)
                .field("password", Schema.OPTIONAL_STRING_SCHEMA)
                .field("secret", Schema.OPTIONAL_STRING_SCHEMA)
                .build();

        Struct struct = new Struct(schema)
                .put("username", null)
                .put("password", "")
                .put("secret", "   ");

        SinkRecord record = new SinkRecord("my-topic", 0, null, null, schema, struct, 0);
        SinkRecord transformedRecord = maskCustomFields.apply(record);

        Struct transformedValue = (Struct) transformedRecord.value();

        assertNull(transformedValue.getString("username"));
        assertEquals("", transformedValue.getString("password"));
        assertEquals("", transformedValue.getString("secret"));
    }

    @Test
    public void testNullAndEmptyFieldsSchemaless() {
        Map<String, Object> value = new HashMap<>();
        value.put("username", "");  // empty string
        value.put("password", null);  // null value
        value.put("secret", "   ");  // whitespace string
        value.put("code", "actual-value");  // normal value for comparison

        SinkRecord record = new SinkRecord("my-topic", 0, null, null, null, value, 0);
        SinkRecord transformedRecord = maskCustomFields.apply(record);

        @SuppressWarnings("unchecked")
        Map<String, Object> transformedValue = (Map<String, Object>) transformedRecord.value();

        // Empty string should be preserved
        assertEquals("", transformedValue.get("username"));
        // Null should remain null
        assertNull(transformedValue.get("password"));
        // Whitespace string should be preserved as empty
        assertEquals("", transformedValue.get("secret"));
        // Normal value should be hashed
        assertEquals(DigestUtils.sha256Hex("actual-value"), transformedValue.get("code"));
    }

}
