package org.extremenetworks.com;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.sink.SinkRecord;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.kafka.connect.data.Field;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import nl.altindag.log.LogCaptor;

public class MaskCustomFieldsTest {

    private static final Logger log = LoggerFactory.getLogger(MaskCustomFields.class);
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()-_+=<>?";
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generateRandomPassword(int length) {
        StringBuilder password = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = RANDOM.nextInt(CHARACTERS.length());
            password.append(CHARACTERS.charAt(index));
        }
        return password.toString();
    }

    private MaskCustomFields<SinkRecord> setupTransformation(String maskType) {
        Map<String, String> props = new HashMap<>();
        props.put("mask.fields", "password, username, code, secret, data, is_secret, worth_float32, worth_float64, profile.phone, profile.metadata.ip_address");
        props.put("topic.name", "my-topic");
        if (maskType != null) {
            props.put("mask.type", maskType);
        }
        MaskCustomFields<SinkRecord> transformation = new MaskCustomFields.Value<>();
        transformation.configure(props);
        return transformation;
    }

    @BeforeEach
    public void setUp() {
        // Default setup uses hash masking

    }
     
     @Test
    public void maskFieldWithSha256() {
        MaskCustomFields<SinkRecord> maskCustomFields = setupTransformation("hash");
        // generate random password
        Schema metadataSchema = SchemaBuilder.struct()
            .field("device_id", Schema.INT32_SCHEMA)
            .field("ip_address", Schema.STRING_SCHEMA)
            .build();

        String password = generateRandomPassword(10);
        Schema profileSchema = SchemaBuilder.struct()
            .field("phone", Schema.INT32_SCHEMA)
            .field("country", Schema.STRING_SCHEMA)
            .field("metadata", metadataSchema)  // New nested field
            .build();



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
                .field("profile", profileSchema)
                .build();

       Struct metadataStruct = new Struct(metadataSchema)
            .put("device_id", 1234)
            .put("ip_address", "192.168.1.1");

    // Create the profile struct
       Struct profileStruct = new Struct(profileSchema)
            .put("phone", 156789)
            .put("country", "USA")
            .put("metadata", metadataStruct);

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
                .put("worth_float64", 123.45)
                .put("profile", profileStruct);

        SinkRecord record = new SinkRecord("my-topic", 0, null, null, schema, struct, 0);
        SinkRecord transformedRecord = maskCustomFields.apply(record);

        String usernameHash = DigestUtils.sha256Hex(struct.get("username").toString());
        String passwordHash = DigestUtils.sha256Hex(struct.get("password").toString());
        String codeHash = DigestUtils.sha256Hex(struct.get("code").toString());
        String secretHash = DigestUtils.sha256Hex(struct.get("secret").toString());
        String isSecretHash = DigestUtils.sha256Hex(struct.get("is_secret").toString());
        String worthFloat32Hash = DigestUtils.sha256Hex(struct.get("worth_float32").toString());
        String worthFloat64Hash = DigestUtils.sha256Hex(struct.get("worth_float64").toString());
        String phoneHash = DigestUtils.sha256Hex(profileStruct.get("phone").toString());

        Struct transformedValue = (Struct) transformedRecord.value();
        Struct transformedProfile = transformedValue.getStruct("profile");

        Struct transformedMetadata = transformedProfile.getStruct("metadata");
        String maskedIpAddress = transformedMetadata.getString("ip_address");
        String expectedMaskedIp = DigestUtils.sha256Hex("192.168.1.1");

        assertNotNull(transformedProfile.get("phone"));
        assertNotNull(transformedValue.get("password"));
        assertNotNull(transformedProfile);
        assertNotNull(transformedMetadata);

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
        assertEquals(phoneHash, transformedProfile.getString("phone"));
        assertEquals("USA", transformedProfile.getString("country"));
        assertEquals(expectedMaskedIp, maskedIpAddress);


    }
    
    @Test
    public void testApplySchemaless() {
        MaskCustomFields<SinkRecord> maskCustomFields = setupTransformation("hash");
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
        MaskCustomFields<SinkRecord> maskCustomFields = setupTransformation("hash");
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
        MaskCustomFields<SinkRecord> maskCustomFields = setupTransformation("hash");
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

    @Test
    public void testMaskTypeWithSchemalessRecord() {
        MaskCustomFields<SinkRecord> maskCustomFields = setupTransformation("redact");

        Map<String, Object> value = new HashMap<>();
        value.put("username", "testuser");  // string
        value.put("password", "secret123");  // string
        value.put("secret", 12345);  // number
        value.put("is_secret", true);  // boolean
        value.put("worth_float32", 123.45f);  // float
        value.put("age", 30);  // non-masked field

        SinkRecord record = new SinkRecord("my-topic", 0, null, null, null, value, 0);
        SinkRecord transformedRecord = maskCustomFields.apply(record);

        @SuppressWarnings("unchecked")
        Map<String, Object> transformedValue = (Map<String, Object>) transformedRecord.value();

        // Masked string fields should be empty strings
        assertEquals("", transformedValue.get("username"));
        assertEquals("", transformedValue.get("password"));
        // Masked numeric field should be 0
        assertEquals(0, transformedValue.get("secret"));
        // Masked boolean field should be empty string (in schemaless mode)
        assertEquals("", transformedValue.get("is_secret"));
        // Masked float field should be 0
        assertEquals(0, transformedValue.get("worth_float32"));
        // Non-masked field should remain unchanged
        assertEquals(30, transformedValue.get("age"));
    }

    @Test
    public void testMaskTypeWithSchema() {
        // Setup transformation with redact type
        MaskCustomFields<SinkRecord> maskCustomFields = setupTransformation("redact");

        // Create schema with various types
        Schema metadataSchema = SchemaBuilder.struct()
            .field("device_id", Schema.INT32_SCHEMA)
            .field("ip_address", Schema.STRING_SCHEMA)
            .build();

        Schema profileSchema = SchemaBuilder.struct()
            .field("phone", Schema.INT32_SCHEMA)
            .field("country", Schema.STRING_SCHEMA)
            .field("metadata", metadataSchema)
            .build();

        Schema schema = SchemaBuilder.struct()
                .field("code", Schema.STRING_SCHEMA)
                .field("username", Schema.STRING_SCHEMA)
                .field("password", Schema.STRING_SCHEMA)
                .field("secret", Schema.INT32_SCHEMA)
                .field("age", Schema.INT32_SCHEMA)
                .field("is_secret", Schema.BOOLEAN_SCHEMA)
                .field("worth_float32", Schema.FLOAT32_SCHEMA)
                .field("worth_float64", Schema.FLOAT64_SCHEMA)
                .field("profile", profileSchema)
                .build();

        // Create nested structs
        Struct metadataStruct = new Struct(metadataSchema)
            .put("device_id", 1234)
            .put("ip_address", "192.168.1.1");

        Struct profileStruct = new Struct(profileSchema)
            .put("phone", 156789)
            .put("country", "USA")
            .put("metadata", metadataStruct);

        // Create main struct
        Struct struct = new Struct(schema)
                .put("code", "12345")
                .put("username", "user123")
                .put("password", "password123")
                .put("secret", 101010)
                .put("age", 25)
                .put("is_secret", true)
                .put("worth_float32", 123.45f)
                .put("worth_float64", 123.45)
                .put("profile", profileStruct);

        SinkRecord record = new SinkRecord("my-topic", 0, null, null, schema, struct, 0);
        SinkRecord transformedRecord = maskCustomFields.apply(record);

        Struct transformedValue = (Struct) transformedRecord.value();
        Struct transformedProfile = transformedValue.getStruct("profile");
        Struct transformedMetadata = transformedProfile.getStruct("metadata");

        // Verify masked string fields are empty
        assertEquals("", transformedValue.getString("code"));
        assertEquals("", transformedValue.getString("username"));
        assertEquals("", transformedValue.getString("password"));

        // Verify masked numeric fields are zero - use explicit types to avoid ambiguity
        assertEquals(Integer.valueOf(0), Integer.valueOf(transformedValue.getInt32("secret")));
        assertEquals(Boolean.FALSE, Boolean.valueOf(transformedValue.getBoolean("is_secret")));
        assertEquals(Float.valueOf(0.0f), Float.valueOf(transformedValue.getFloat32("worth_float32")));
        assertEquals(Double.valueOf(0.0), Double.valueOf(transformedValue.getFloat64("worth_float64")));

        // Verify non-masked fields remain unchanged
        assertEquals(Integer.valueOf(25), Integer.valueOf(transformedValue.getInt32("age")));
        assertEquals("USA", transformedProfile.getString("country"));

        // Verify nested masked fields
        assertEquals(Integer.valueOf(0), Integer.valueOf(transformedProfile.getInt32("phone")));
        assertEquals("", transformedMetadata.getString("ip_address"));
        assertEquals(Integer.valueOf(1234), Integer.valueOf(transformedMetadata.getInt32("device_id"))); // Not masked
    }
    // ... (keep your existing imports, setup code, and other tests)

@Test
public void testMakeUpdatedSchemaWithArrayAndNestedStruct() {
    // 1) Define a schema with nested struct and an array of structs
    Schema innerNestedSchema = SchemaBuilder.struct()
            .field("maskedField", Schema.STRING_SCHEMA)
            .field("unmaskedField", Schema.INT32_SCHEMA)
            .build();

    Schema arrayElementSchema = SchemaBuilder.struct()
            .field("arrayMaskedField", Schema.STRING_SCHEMA)
            .field("arrayUnmaskedField", Schema.INT32_SCHEMA)
            .build();

    Schema mainSchema = SchemaBuilder.struct()
            .field("topMaskedField", Schema.STRING_SCHEMA)
            .field("topUnmaskedField", Schema.INT32_SCHEMA)
            .field("nestedStruct", innerNestedSchema)
            .field("nestedArray", SchemaBuilder.array(arrayElementSchema).optional().build())
            .build();

    // 2) Build the struct data
    Struct innerNestedStruct = new Struct(innerNestedSchema)
            .put("maskedField", "secret-data")
            .put("unmaskedField", 123);

    Struct arrayElement1 = new Struct(arrayElementSchema)
            .put("arrayMaskedField", "arr-secret1")
            .put("arrayUnmaskedField", 111);

    Struct arrayElement2 = new Struct(arrayElementSchema)
            .put("arrayMaskedField", "arr-secret2")
            .put("arrayUnmaskedField", 222);

    Struct mainStruct = new Struct(mainSchema)
            .put("topMaskedField", "some-secret")
            .put("topUnmaskedField", 999)
            .put("nestedStruct", innerNestedStruct)
            .put("nestedArray", java.util.Arrays.asList(arrayElement1, arrayElement2));

    // 3) Configure transformation to mask certain fields (note the dot notation for nested, array fields)
    //    Example: "nestedStruct.maskedField" and "nestedArray.arrayMaskedField" are masked
    MaskCustomFields<SinkRecord> transformation = setupTransformation("hash");
    Map<String, String> props = new HashMap<>();
    props.put("mask.fields", "topMaskedField, nestedStruct.maskedField, nestedArray.arrayMaskedField");
    props.put("topic.name", "my-topic");
    props.put("mask.type", "hash");
    transformation.configure(props);

    // 4) Apply transformation
    SinkRecord record = new SinkRecord("my-topic", 0, null, null, mainSchema, mainStruct, 0);
    SinkRecord transformedRecord = transformation.apply(record);

    // 5) Retrieve the *updated schema*
    Schema updatedSchema = transformedRecord.valueSchema();
    assertNotNull(updatedSchema);

    // 6) Verify topMaskedField is replaced with OPTIONAL_STRING_SCHEMA
    Field topMaskedField = updatedSchema.field("topMaskedField");
    assertNotNull(topMaskedField);
    assertEquals(Schema.Type.STRING, topMaskedField.schema().type());
    assertEquals(true, topMaskedField.schema().isOptional());

    // 7) Verify array structure was also updated
    Field nestedArrayField = updatedSchema.field("nestedArray");
    assertNotNull(nestedArrayField);
    Schema arraySchema = nestedArrayField.schema();
    assertEquals(Schema.Type.ARRAY, arraySchema.type());

    // Check the array's element schema
    Schema elementSchema = arraySchema.valueSchema();
    // "arrayMaskedField" should be optional string
    Field arrayMaskedField = elementSchema.field("arrayMaskedField");
    assertNotNull(arrayMaskedField);
    assertEquals(Schema.Type.STRING, arrayMaskedField.schema().type());
    assertEquals(true, arrayMaskedField.schema().isOptional());

    // "arrayUnmaskedField" should remain INT32
    Field arrayUnmaskedField = elementSchema.field("arrayUnmaskedField");
    assertNotNull(arrayUnmaskedField);
    assertEquals(Schema.Type.INT32, arrayUnmaskedField.schema().type());

    // 8) Verify nested struct
    Field nestedStructField = updatedSchema.field("nestedStruct");
    assertNotNull(nestedStructField);
    Schema nestedStructSchema = nestedStructField.schema();
    Field maskedField = nestedStructSchema.field("maskedField");
    assertNotNull(maskedField);
    assertEquals(Schema.Type.STRING, maskedField.schema().type()); // Should be optional string if hashed
    assertEquals(true, maskedField.schema().isOptional());

    Field unmaskedField = nestedStructSchema.field("unmaskedField");
    assertNotNull(unmaskedField);
    assertEquals(Schema.Type.INT32, unmaskedField.schema().type());
}

@Test
public void testMakeUpdatedNestedSchemaOnly() {
    // For direct coverage of the nested schema builder, we can build a nested struct
    Schema nestedSchema = SchemaBuilder.struct()
            .field("firstName", Schema.STRING_SCHEMA)
            .field("lastName", Schema.STRING_SCHEMA)
            .build();

    // Our "top" schema with the nested struct
    Schema parentSchema = SchemaBuilder.struct()
            .field("userInfo", nestedSchema)
            .build();

    // Create data
    Struct nestedStruct = new Struct(nestedSchema)
            .put("firstName", "Alice")
            .put("lastName", "Smith");

    Struct parentStruct = new Struct(parentSchema)
            .put("userInfo", nestedStruct);

    // Setup transformation to mask userInfo.lastName
    // So anything matching "userInfo.lastName" is hashed
    MaskCustomFields<SinkRecord> transformation = setupTransformation("hash");
    Map<String, String> props = new HashMap<>();
    props.put("mask.fields", "userInfo.lastName");
    props.put("topic.name", "my-topic");
    transformation.configure(props);

    // Apply transformation
    SinkRecord record = new SinkRecord("my-topic", 0, null, null, parentSchema, parentStruct, 0);
    SinkRecord transformedRecord = transformation.apply(record);

    // Check updated schema
    Schema updatedParentSchema = transformedRecord.valueSchema();
    assertNotNull(updatedParentSchema);
    Field userInfoField = updatedParentSchema.field("userInfo");
    assertNotNull(userInfoField);

    // The nested struct inside userInfo should have lastName as optional string
    Schema updatedUserInfoSchema = userInfoField.schema();
    Field lastNameField = updatedUserInfoSchema.field("lastName");
    assertNotNull(lastNameField);
    assertEquals(Schema.Type.STRING, lastNameField.schema().type());
    // For hash, it should be optional
    assertEquals(true, lastNameField.schema().isOptional());

    // "firstName" remains unchanged
    Field firstNameField = updatedUserInfoSchema.field("firstName");
    assertNotNull(firstNameField);
    assertEquals(Schema.Type.STRING, firstNameField.schema().type());
    assertEquals(false, firstNameField.schema().isOptional());
}


@Test
public void testProcessArrayField() {
    // 1) Define the schema for our DeviceRecord
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
        .put("channelNumber", 101)
        .put("channelChangeCount", 3);

    Struct channel2 = new Struct(channelSchema)
        .put("channelNumber", 202)
        .put("channelChangeCount", 5);

    Struct deviceValue = new Struct(deviceSchema)
        .put("severity", "CRITICAL")
        .put("interfaceName", "Gig0/1")
        .put("channelList", Arrays.asList(channel1, channel2));

    // 3) Create a special configuration for this test
    Map<String, String> props = new HashMap<>();
    props.put("mask.fields", "channelList.channelNumber");
    props.put("topic.name", "test-topic"); // Match the topic in record creation below
    props.put("mask.type", "hash");
    @SuppressWarnings("resource")
    MaskCustomFields<SinkRecord> maskCustomFields = new MaskCustomFields.Value<>();
    maskCustomFields.configure(props);

    // 4) Create a SinkRecord with the schema+value
    SinkRecord record = new SinkRecord("test-topic", 0, null, null, deviceSchema, deviceValue, 0);

    // 5) Apply the transformation
    SinkRecord transformedRecord = maskCustomFields.apply(record);

    // 6) Verify the transformation was applied 
    Struct updatedValue = (Struct) transformedRecord.value();
    @SuppressWarnings("unchecked")
    List<Struct> updatedChannels = (List<Struct>) updatedValue.get("channelList");

    assertNotNull(updatedChannels);
    assertEquals(2, updatedChannels.size());

    String channel1Hash = DigestUtils.sha256Hex("101");
    String channel2Hash = DigestUtils.sha256Hex("202");
    
    // Verify both channel numbers are properly masked with expected hash values
    assertEquals(channel1Hash, updatedChannels.get(0).getString("channelNumber"));
    assertEquals(channel2Hash, updatedChannels.get(1).getString("channelNumber"));
    
   

    for (Struct channel : updatedChannels) {
        Object maskedField = channel.get("channelNumber");
        assertNotNull(maskedField);  
        assertEquals(Schema.Type.STRING, channel.schema().field("channelNumber").schema().type());
        assertEquals(true, channel.schema().field("channelNumber").schema().isOptional());
    }
}

@Test
public void testNestedArrayFieldMasking() {
    // 1) Define the schema for Detail record inside the array
    Schema detailSchema = SchemaBuilder.struct().name("com.extremecloudiq.common.kafka.copilot.ap.client.basicavro.Detail")
        .field("clientId", Schema.INT64_SCHEMA)
        .field("channel", Schema.INT32_SCHEMA)
        .field("ssid", Schema.STRING_SCHEMA)
        .field("username", Schema.STRING_SCHEMA)
        .build();

    // 2) Define the schema for the main copilot_ap_client record
    Schema clientSchema = SchemaBuilder.struct().name("com.extremecloudiq.common.kafka.copilot.ap.client.basicavro.copilot_ap_client")
        .field("timestamp", Schema.INT64_SCHEMA)
        .field("ownerId", Schema.INT64_SCHEMA)
        .field("orgId", Schema.INT64_SCHEMA)
        .field("deviceId", Schema.INT64_SCHEMA)
        .field("deviceMgtIp", Schema.STRING_SCHEMA)
        .field("deviceSn", Schema.STRING_SCHEMA)
        .field("details", SchemaBuilder.array(detailSchema).optional().build())
        .field("type", Schema.STRING_SCHEMA)
        .build();

    // 3) Build test data with sample values
    Struct detail1 = new Struct(detailSchema)
        .put("clientId", 1001L)
        .put("channel", 36)
        .put("ssid", "Guest-WiFi")
        .put("username", "john.doe@example.com");

    Struct detail2 = new Struct(detailSchema)
        .put("clientId", 1002L)
        .put("channel", 48)
        .put("ssid", "Corp-Network")
        .put("username", "jane.smith@company.com");

    Struct clientValue = new Struct(clientSchema)
        .put("timestamp", System.currentTimeMillis())
        .put("ownerId", 5001L)
        .put("orgId", 100L)
        .put("deviceId", 2001L)
        .put("deviceMgtIp", "192.168.1.10")
        .put("deviceSn", "EXTR12345678")
        .put("details", Arrays.asList(detail1, detail2))
        .put("type", "WiFi");

    // 4) Create a dedicated configuration for this test 
    Map<String, String> props = new HashMap<>();
    props.put("mask.fields", "details.username");
    props.put("topic.name", "client-topic");
    props.put("mask.type", "hash");
    
    MaskCustomFields<SinkRecord> maskCustomFields = new MaskCustomFields.Value<>();
    maskCustomFields.configure(props);

    // 5) Create a SinkRecord with the schema+value
    SinkRecord record = new SinkRecord("client-topic", 0, null, null, clientSchema, clientValue, 0);

    // 6) Apply the transformation
    SinkRecord transformedRecord = maskCustomFields.apply(record);

    // 7) Verify the transformation was applied
    Struct updatedValue = (Struct) transformedRecord.value();
    @SuppressWarnings("unchecked")
    List<Struct> updatedDetails = (List<Struct>) updatedValue.get("details");

    assertNotNull(updatedDetails);
    assertEquals(2, updatedDetails.size());

    // Calculate expected hash values for usernames
    String username1Hash = DigestUtils.sha256Hex("john.doe@example.com");
    String username2Hash = DigestUtils.sha256Hex("jane.smith@company.com");
    
    // Verify both usernames are properly masked with expected hash values
    assertEquals(username1Hash, updatedDetails.get(0).getString("username"));
    assertEquals(username2Hash, updatedDetails.get(1).getString("username"));

    // Verify other fields remain unchanged
    assertEquals(1001L, updatedDetails.get(0).getInt64("clientId").longValue());
    assertEquals(36, updatedDetails.get(0).getInt32("channel").intValue());
    assertEquals("Guest-WiFi", updatedDetails.get(0).getString("ssid"));
    
    assertEquals(1002L, updatedDetails.get(1).getInt64("clientId").longValue());
    assertEquals(48, updatedDetails.get(1).getInt32("channel").intValue());
    assertEquals("Corp-Network", updatedDetails.get(1).getString("ssid"));

    // Verify schema type changes for masked fields
    assertEquals(Schema.Type.STRING, updatedDetails.get(0).schema().field("username").schema().type());
    assertTrue(updatedDetails.get(0).schema().field("username").schema().isOptional());
}

@Test
public void testHiveConnectClientEventMasking() {
    // 1) Define the nested ClientInfo schema
    Schema clientInfoSchema = SchemaBuilder.struct().name("com.extremecloudiq.common.kafka.hive.connect.client.event.basicavro.ClientInfo")
        .field("hostName", Schema.STRING_SCHEMA)
        .field("ipAddress", Schema.STRING_SCHEMA)
        .field("locationId", Schema.INT64_SCHEMA)
        .field("orgId", Schema.INT32_SCHEMA)
        .field("ownerId", Schema.INT32_SCHEMA)
        .field("ssid", Schema.STRING_SCHEMA)
        .field("userName", Schema.STRING_SCHEMA)
        .build();

    // 2) Define the main schema for hive_connect_client_event
    Schema hiveSchema = SchemaBuilder.struct().name("com.extremecloudiq.common.kafka.hive.connect.client.event.basicavro.hive_connect_client_event")
        .field("actionType", Schema.STRING_SCHEMA)
        .field("clientDeviceId", Schema.OPTIONAL_INT64_SCHEMA)
        .field("clientInfo", clientInfoSchema)
        .field("clientMac", Schema.STRING_SCHEMA)
        .field("families", SchemaBuilder.array(Schema.STRING_SCHEMA).build())
        .field("family", Schema.OPTIONAL_STRING_SCHEMA)
        .field("locationId", Schema.OPTIONAL_INT64_SCHEMA)
        .field("ownerId", Schema.INT32_SCHEMA)
        .build();

    // 3) Create test data with sample values
    Struct clientInfoValue = new Struct(clientInfoSchema)
        .put("hostName", "laptop-1234")
        .put("ipAddress", "192.168.1.105")
        .put("locationId", 5000L)
        .put("orgId", 100)
        .put("ownerId", 200)
        .put("ssid", "Corporate-WiFi")
        .put("userName", "user@example.com");

    Struct hiveValue = new Struct(hiveSchema)
        .put("actionType", "CONNECT")
        .put("clientDeviceId", 12345L)
        .put("clientInfo", clientInfoValue)
        .put("clientMac", "AA:BB:CC:DD:EE:FF")
        .put("families", Arrays.asList("Web", "Email", "Streaming"))
        .put("family", "Mobile")
        .put("locationId", 7500L)
        .put("ownerId", 321);

    // 4) Create configuration to mask locationId and clientInfo.ipAddress
    Map<String, String> props = new HashMap<>();
    props.put("mask.fields", "locationId, clientInfo.ipAddress");
    props.put("topic.name", "hive-client-topic");
    props.put("mask.type", "hash");
    
    MaskCustomFields<SinkRecord> maskCustomFields = new MaskCustomFields.Value<>();
    maskCustomFields.configure(props);

    // 5) Create a SinkRecord with the schema+value
    SinkRecord record = new SinkRecord("hive-client-topic", 0, null, null, hiveSchema, hiveValue, 0);

    // 6) Apply the transformation
    SinkRecord transformedRecord = maskCustomFields.apply(record);

    // 7) Verify the transformation was applied
    Struct updatedValue = (Struct) transformedRecord.value();
    Struct updatedClientInfo = updatedValue.getStruct("clientInfo");

    // Calculate expected hash values
    String locationIdHash = DigestUtils.sha256Hex("7500");
    String ipAddressHash = DigestUtils.sha256Hex("192.168.1.105");
    
    // Verify masked fields
    assertEquals(locationIdHash, updatedValue.getString("locationId"));
    assertEquals(ipAddressHash, updatedClientInfo.getString("ipAddress"));
    
    // Verify schema type changes for masked fields
    assertEquals(Schema.Type.STRING, updatedValue.schema().field("locationId").schema().type());
    assertEquals(Schema.Type.STRING, updatedClientInfo.schema().field("ipAddress").schema().type());
    assertTrue(updatedValue.schema().field("locationId").schema().isOptional());
    
    // Verify non-masked fields remain unchanged
    assertEquals("CONNECT", updatedValue.getString("actionType"));
    assertEquals(12345L, updatedValue.getInt64("clientDeviceId").longValue());
    assertEquals("AA:BB:CC:DD:EE:FF", updatedValue.getString("clientMac"));
    assertEquals("Mobile", updatedValue.getString("family"));
    assertEquals(321, updatedValue.getInt32("ownerId").intValue());
    
    // Verify nested non-masked fields
    assertEquals("laptop-1234", updatedClientInfo.getString("hostName"));
    assertEquals(5000L, updatedClientInfo.getInt64("locationId").longValue());
    assertEquals(100, updatedClientInfo.getInt32("orgId").intValue());
    assertEquals(200, updatedClientInfo.getInt32("ownerId").intValue());
    assertEquals("Corporate-WiFi", updatedClientInfo.getString("ssid"));
    assertEquals("user@example.com", updatedClientInfo.getString("userName"));
    
    // Verify array fields
    @SuppressWarnings("unchecked")
    List<String> families = (List<String>) updatedValue.get("families");
    assertEquals(3, families.size());
    assertEquals("Web", families.get(0));
    assertEquals("Email", families.get(1));
    assertEquals("Streaming", families.get(2));
}

@Test
public void testMaskedFieldLogsAndMetadataLogsAreLogged() {
    LogCaptor logCaptor = LogCaptor.forClass(MaskCustomFields.class);

    Schema schema = SchemaBuilder.struct()
            .field("username", Schema.STRING_SCHEMA)
            .field("password", Schema.STRING_SCHEMA)
            .build();

    Struct struct = new Struct(schema)
            .put("username", "john.doe")
            .put("password", "secret123");

    // Configure transformation
    MaskCustomFields<SinkRecord> maskCustomFields = setupTransformation("hash");

    SinkRecord record = new SinkRecord("my-topic", 0, null, null, schema, struct, 0);
    maskCustomFields.apply(record);

    // Fetch logs
    List<String> infoLogs = logCaptor.getInfoLogs();

    // Verify masked field log line exists
    boolean hasMaskedSummary = infoLogs.stream().anyMatch(log ->
            log.contains("Masked fields summary: topic='my-topic'")
                    && log.contains("password=hashed")
                    && log.contains("username=hashed")
    );
    assertTrue(hasMaskedSummary, "Masked fields summary should include hashed fields");

    // Verify metadata field log exists
    boolean hasMetadataSummary = infoLogs.stream().anyMatch(log ->
            log.contains("Field metadata summary: topic='my-topic'")
                    && log.contains("username:STRING")
                    && log.contains("password:STRING")
    );
    assertTrue(hasMetadataSummary, "Field metadata summary should include type info");
}


@Test
public void testApplySchemalessLogsForRedactAndHash() {
    LogCaptor logCaptor = LogCaptor.forClass(MaskCustomFields.class);

    Map<String, Object> inputMapHash = new HashMap<>();
    inputMapHash.put("username", "testuser");

    Map<String, String> hashProps = new HashMap<>();
    hashProps.put("mask.fields", "username");
    hashProps.put("topic.name", "my-topic");
    hashProps.put("mask.type", "hash");

    MaskCustomFields<SinkRecord> hashTransform = new MaskCustomFields.Value<>();
    hashTransform.configure(hashProps);

    SinkRecord hashRecord = new SinkRecord("my-topic", 0, null, null, null, inputMapHash, 0);
    hashTransform.apply(hashRecord);

    logCaptor.getInfoLogs().forEach(System.out::println);

    boolean loopLogHash = logCaptor.getInfoLogs().stream().anyMatch(log ->
        log.contains("applySchemaless inside for: topic='my-topic'")
    );
    assertTrue(loopLogHash, "Expected loop log not found for hash");

    boolean hashLogFound = logCaptor.getInfoLogs().stream().anyMatch(log ->
        log.contains("applySchemaless inside hash for: topic='my-topic'") &&
        log.contains("field='username'")
    );
    assertTrue(hashLogFound, "Expected hash log not found");

    logCaptor.clearLogs();

    Map<String, Object> inputMapRedact = new HashMap<>();
    inputMapRedact.put("username", "testuser");

    Map<String, String> redactProps = new HashMap<>();
    redactProps.put("mask.fields", "username");
    redactProps.put("topic.name", "my-topic");
    redactProps.put("mask.type", "redact");

    MaskCustomFields<SinkRecord> redactTransform = new MaskCustomFields.Value<>();
    redactTransform.configure(redactProps);

    SinkRecord redactRecord = new SinkRecord("my-topic", 0, null, null, null, inputMapRedact, 0);
    redactTransform.apply(redactRecord);

    System.out.println("Captured Logs for REDACT:");
    logCaptor.getInfoLogs().forEach(System.out::println);

    boolean loopLogRedact = logCaptor.getInfoLogs().stream().anyMatch(log ->
        log.contains("applySchemaless inside for: topic='my-topic'")
    );
    assertTrue(loopLogRedact, "Expected loop log not found for redact");

    boolean redactLogFound = logCaptor.getInfoLogs().stream().anyMatch(log ->
        log.contains("applySchemaless inside redact for: topic='my-topic'") &&
        log.contains("field='username'")
    );
    assertTrue(redactLogFound, "Expected redact log not found");
}




    public void printTransformedValue(Struct transformedValue) {
        System.out.println("Transformed Value:");

        for (Field field : transformedValue.schema().fields()) {
            Object fieldValue = transformedValue.get(field);

            if (fieldValue instanceof Struct) {
                System.out.println("Field Name: " + field.name() + " (Nested Struct):");
                Struct nestedStruct = (Struct) fieldValue;

                for (Field nestedField : nestedStruct.schema().fields()) {
                    Object nestedValue = nestedStruct.get(nestedField);
                    System.out.println("  " + nestedField.name() + ": " + nestedValue);
                }
            } else {
                System.out.println("Field Name: " + field.name() + ", Value: " + fieldValue);
            }
        }
    }

}
