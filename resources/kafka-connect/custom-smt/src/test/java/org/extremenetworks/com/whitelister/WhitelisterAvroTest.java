package org.extremenetworks.com.whitelister;

import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.sink.SinkRecord;
import org.extremenetworks.com.Whitelister;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Test cases for the Whitelister SMT with Avro schema files
 */
public class WhitelisterAvroTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File avroSchemaFile;
    private Whitelister<SinkRecord> xform;

    @Before
    public void setUp() throws IOException {
        xform = new Whitelister.Value<>();

        // Create a schema directory
        File schemaDir = tempFolder.newFolder("schemas");
        
        // Create a temporary Avro schema file for avro-topic
        avroSchemaFile = new File(schemaDir, "avro-topic.avsc");
        try (FileWriter writer = new FileWriter(avroSchemaFile)) {
            writer.write("{"
                    + "\n  \"type\": \"record\",\n"
                    + "  \"name\": \"User\",\n" +
                    "  \"fields\": [\n" +
                    "    {\"name\": \"id\", \"type\": \"int\"},\n" +
                    "    {\"name\": \"name\", \"type\": \"string\"},\n" +
                    "    {\"name\": \"address\", \"type\": {\n" +
                    "      \"type\": \"record\",\n" +
                    "      \"name\": \"Address\",\n" +
                    "      \"fields\": [\n" +
                    "        {\"name\": \"street\", \"type\": \"string\"},\n" +
                    "        {\"name\": \"city\", \"type\": \"string\"},\n" +
                    "        {\"name\": \"details\", \"type\": {\n" +
                    "          \"type\": \"record\",\n" +
                    "          \"name\": \"Details\",\n" +
                    "          \"fields\": [\n" +
                    "            {\"name\": \"floor\", \"type\": \"int\"},\n" +
                    "            {\"name\": \"apartment\", \"type\": \"string\"}\n" +
                    "          ]\n" +
                    "        }}\n" +
                    "      ]\n" +
                    "    }}\n" +
                    "  ]\n" +
                    "}");
        }
    }

    @After
    public void tearDown() {
        xform.close();
    }

    @Test
    public void testAvroSchemaWithSchema() throws IOException {
        Map<String, Object> props = new HashMap<>();
        props.put("schema.directory", avroSchemaFile.getParentFile().getAbsolutePath());
        props.put("schema.type", "avro");
        xform.configure(props);
        
        // Create a schema with nested fields
        Schema addressDetailsSchema = SchemaBuilder.struct()
                .field("floor", Schema.INT32_SCHEMA)
                .field("apartment", Schema.STRING_SCHEMA)
                .field("extra_detail", Schema.STRING_SCHEMA) // This field is not in the schema file
                .build();
                
        Schema addressSchema = SchemaBuilder.struct()
                .field("street", Schema.STRING_SCHEMA)
                .field("city", Schema.STRING_SCHEMA)
                .field("zipcode", Schema.STRING_SCHEMA) // This field is not in the schema file
                .field("details", addressDetailsSchema)
                .build();
                
        Schema schema = SchemaBuilder.struct()
                .field("id", Schema.INT32_SCHEMA)
                .field("name", Schema.STRING_SCHEMA)
                .field("email", Schema.STRING_SCHEMA) // This should be removed
                .field("address", addressSchema)
                .build();

        // Create a struct with all fields
        Struct addressDetailsStruct = new Struct(addressDetailsSchema)
                .put("floor", 3)
                .put("apartment", "3B")
                .put("extra_detail", "Corner unit"); // This should be removed

        Struct addressStruct = new Struct(addressSchema)
                .put("street", "123 Main St")
                .put("city", "Springfield")
                .put("zipcode", "12345") // This should be removed
                .put("details", addressDetailsStruct);

        Struct struct = new Struct(schema)
                .put("id", 42)
                .put("name", "John Doe")
                .put("email", "john@example.com") // This should be removed
                .put("address", addressStruct);
                
        SinkRecord record = new SinkRecord("avro-topic", 0, null, null, schema, struct, 0);
        SinkRecord transformedRecord = xform.apply(record);
        
        // Verify the transformation
        Struct transformedValue = (Struct) transformedRecord.value();
        
        // Check that the primitive fields are preserved
        assertEquals(42, transformedValue.get("id"));
        assertEquals("John Doe", transformedValue.get("name"));
        
        // Check that the email field is removed
        assertNull(transformedValue.schema().field("email"));
        
        // Check that the address field is preserved but filtered
        Struct transformedAddress = (Struct) transformedValue.get("address");
        assertEquals("123 Main St", transformedAddress.get("street"));
        assertEquals("Springfield", transformedAddress.get("city"));
        
        // Check that the zipcode field is removed
        assertNull(transformedAddress.schema().field("zipcode"));
        
        // Check that the details field is preserved but filtered
        Struct transformedDetails = (Struct) transformedAddress.get("details");
        assertEquals(3, transformedDetails.get("floor"));
        assertEquals("3B", transformedDetails.get("apartment"));
        
        // Check that the extra_detail field is removed
        assertNull(transformedDetails.schema().field("extra_detail"));
    }

    @Test
    public void testArrayOfStructsWithAvro() throws IOException {
        // Create an Avro schema file with array of structs
        File avroArraySchemaFile = new File(avroSchemaFile.getParentFile(), "avro-array-topic.avsc");
        try (FileWriter writer = new FileWriter(avroArraySchemaFile)) {
            writer.write("{"
                    + "\n  \"type\": \"record\",\n"
                    + "  \"name\": \"User\",\n" +
                    "  \"fields\": [\n" +
                    "    {\"name\": \"id\", \"type\": \"int\"},\n" +
                    "    {\"name\": \"name\", \"type\": \"string\"},\n" +
                    "    {\"name\": \"tags\", \"type\": {\"type\": \"array\", \"items\": \"string\"}},\n" +
                    "    {\"name\": \"contacts\", \"type\": {\n" +
                    "      \"type\": \"array\",\n" +
                    "      \"items\": {\n" +
                    "        \"type\": \"record\",\n" +
                    "        \"name\": \"Contact\",\n" +
                    "        \"fields\": [\n" +
                    "          {\"name\": \"type\", \"type\": \"string\"},\n" +
                    "          {\"name\": \"value\", \"type\": \"string\"},\n" +
                    "          {\"name\": \"verified\", \"type\": \"boolean\"}\n" +
                    "        ]\n" +
                    "      }\n" +
                    "    }}\n" +
                    "  ]\n" +
                    "}");
        }
        
        Map<String, Object> props = new HashMap<>();
        props.put("schema.directory", avroSchemaFile.getParentFile().getAbsolutePath());
        props.put("schema.type", "avro");
        xform.configure(props);
        
        // Create a schema with arrays of primitives and structs
        Schema contactSchema = SchemaBuilder.struct()
                .field("type", Schema.STRING_SCHEMA)
                .field("value", Schema.STRING_SCHEMA)
                .field("verified", Schema.BOOLEAN_SCHEMA)
                .field("priority", Schema.INT32_SCHEMA) // This field is not in the schema file
                .build();
                
        Schema schema = SchemaBuilder.struct()
                .field("id", Schema.INT32_SCHEMA)
                .field("name", Schema.STRING_SCHEMA)
                .field("tags", SchemaBuilder.array(Schema.STRING_SCHEMA).build())
                .field("contacts", SchemaBuilder.array(contactSchema).build())
                .field("extra", Schema.STRING_SCHEMA) // This field is not in the schema file
                .build();
                
        // Create a struct with array values
        List<String> tags = Arrays.asList("tag1", "tag2", "tag3");
        
        List<Struct> contacts = new ArrayList<>();
        contacts.add(new Struct(contactSchema)
                .put("type", "email")
                .put("value", "john@example.com")
                .put("verified", true)
                .put("priority", 1));
        contacts.add(new Struct(contactSchema)
                .put("type", "phone")
                .put("value", "+1234567890")
                .put("verified", false)
                .put("priority", 2));
                
        Struct struct = new Struct(schema)
                .put("id", 42)
                .put("name", "John Doe")
                .put("tags", tags)
                .put("contacts", contacts)
                .put("extra", "This should be removed");
                
        SinkRecord record = new SinkRecord("avro-array-topic", 0, null, null, schema, struct, 0);
        SinkRecord transformedRecord = xform.apply(record);
        
        // Verify the transformation
        Struct transformedValue = (Struct) transformedRecord.value();
        
        // Check that the primitive fields are preserved
        assertEquals(42, transformedValue.get("id"));
        assertEquals("John Doe", transformedValue.get("name"));
        
        // Check that the array of primitives is preserved
        List<String> transformedTags = transformedValue.getArray("tags");
        assertEquals(3, transformedTags.size());
        assertEquals("tag1", transformedTags.get(0));
        assertEquals("tag2", transformedTags.get(1));
        assertEquals("tag3", transformedTags.get(2));
        
        // Check that the array of structs is properly filtered
        List<Struct> transformedContacts = transformedValue.getArray("contacts");
        assertEquals(2, transformedContacts.size());
        
        Struct contact1 = transformedContacts.get(0);
        assertEquals("email", contact1.get("type"));
        assertEquals("john@example.com", contact1.get("value"));
        assertEquals(true, contact1.get("verified"));
        assertNull(contact1.schema().field("priority")); // Should be removed
        
        Struct contact2 = transformedContacts.get(1);
        assertEquals("phone", contact2.get("type"));
        assertEquals("+1234567890", contact2.get("value"));
        assertEquals(false, contact2.get("verified"));
        assertNull(contact2.schema().field("priority")); // Should be removed
        
        // Check that the extra field is removed
        assertNull(transformedValue.schema().field("extra"));
    }

    @Test
    public void testNullValuesWithAvro() throws IOException {
        // Create an Avro schema file with nullable fields
        File avroNullableSchemaFile = new File(avroSchemaFile.getParentFile(), "avro-nullable-topic.avsc");
        try (FileWriter writer = new FileWriter(avroNullableSchemaFile)) {
            writer.write("{"
                    + "\n  \"type\": \"record\",\n"
                    + "  \"name\": \"User\",\n"
                    + "  \"fields\": [\n" +
                    "    { \"name\": \"id\", \"type\": \"int\" },\n" +
                    "    { \"name\": \"name\", \"type\": [\"null\", \"string\"] },\n" +
                    "    { \"name\": \"email\", \"type\": [\"null\", \"string\"] }\n" +
                    "  ]\n" +
                    "}");
        }
        
        Map<String, Object> props = new HashMap<>();
        props.put("schema.directory", avroSchemaFile.getParentFile().getAbsolutePath());
        props.put("schema.type", "avro");
        xform.configure(props);
        
        // Create a schema with nullable fields
        Schema schema = SchemaBuilder.struct()
                .field("id", Schema.INT32_SCHEMA)
                .field("name", Schema.OPTIONAL_STRING_SCHEMA)
                .field("email", Schema.OPTIONAL_STRING_SCHEMA)
                .field("extra", Schema.OPTIONAL_STRING_SCHEMA) // This field is not in the schema file
                .build();
                
        // Create a struct with null values
        Struct struct = new Struct(schema)
                .put("id", 42)
                .put("name", null)
                .put("email", null)
                .put("extra", null);
                
        SinkRecord record = new SinkRecord("avro-nullable-topic", 0, null, null, schema, struct, 0);
        SinkRecord transformedRecord = xform.apply(record);
        
        // Verify the transformation
        Struct transformedValue = (Struct) transformedRecord.value();
        
        // Check that the fields are correctly filtered
        assertEquals(42, transformedValue.get("id"));
        assertNull(transformedValue.get("name"));
        assertNull(transformedValue.get("email"));
        assertNull(transformedValue.schema().field("extra"));
    }

    @Test
    public void testAvroSchemaWithCustomSuffix() throws IOException {
        // Create a schema file with custom suffix
        File customSchemaFile = new File(avroSchemaFile.getParentFile(), "custom-topic-schema.avsc");
        try (FileWriter writer = new FileWriter(customSchemaFile)) {
            writer.write("{"
                    + "\n  \"type\": \"record\",\n"
                    + "  \"name\": \"CustomItem\",\n" +
                    "  \"fields\": [\n" +
                    "    {\"name\": \"id\", \"type\": \"int\"},\n" +
                    "    {\"name\": \"name\", \"type\": \"string\"},\n" +
                    "    {\"name\": \"description\", \"type\": \"string\"}\n" +
                    "  ]\n" +
                    "}");
        }
        
        Map<String, Object> props = new HashMap<>();
        props.put("schema.directory", avroSchemaFile.getParentFile().getAbsolutePath());
        props.put("schema.type", "avro");
        props.put("schema.suffix", "-schema.avsc");
        xform.configure(props);
        
        // Create a schema and struct
        Schema schema = SchemaBuilder.struct()
                .field("id", Schema.INT32_SCHEMA)
                .field("name", Schema.STRING_SCHEMA)
                .field("description", Schema.STRING_SCHEMA)
                .field("extra", Schema.STRING_SCHEMA) // This field is not in the schema file
                .build();
                
        Struct struct = new Struct(schema)
                .put("id", 42)
                .put("name", "Custom Item")
                .put("description", "This is a custom item")
                .put("extra", "This should be removed");
                
        SinkRecord record = new SinkRecord("custom-topic", 0, null, null, schema, struct, 0);
        SinkRecord transformedRecord = xform.apply(record);
        
        // Verify the transformation
        Struct transformedValue = (Struct) transformedRecord.value();
        
        // Check that the fields are correctly filtered
        assertEquals(42, transformedValue.get("id"));
        assertEquals("Custom Item", transformedValue.get("name"));
        assertEquals("This is a custom item", transformedValue.get("description"));
        assertNull(transformedValue.schema().field("extra"));
    }
    
    @Test
    public void testNestedArrayOfObjectsWithAvro() throws IOException {
        // Create a schema file based on the provided network device schema
        File networkDeviceSchemaFile = new File(avroSchemaFile.getParentFile(), "network-device-topic.avsc");
        try (FileWriter writer = new FileWriter(networkDeviceSchemaFile)) {
            writer.write("{"
                    + "\n  \"type\": \"record\",\n"
                    + "  \"name\": \"NetworkDevice\",\n"
                    + "  \"namespace\": \"com.example\",\n"
                    + "  \"fields\": [\n"
                    + "    {\"name\": \"id\", \"type\": \"string\"},\n"
                    + "    {\"name\": \"created_at\", \"type\": \"string\"},\n"
                    + "    {\"name\": \"last_modified_at\", \"type\": \"string\"},\n"
                    + "    {\"name\": \"is_deleted\", \"type\": \"boolean\"},\n"
                    + "    {\"name\": \"name\", \"type\": \"string\"},\n"
                    + "    {\"name\": \"device_type\", \"type\": \"string\"},\n"
                    + "    {\"name\": \"status\", \"type\": \"string\"},\n"
                    + "    {\"name\": \"network_config\", \"type\": {\n"
                    + "      \"type\": \"record\",\n"
                    + "      \"name\": \"NetworkConfig\",\n"
                    + "      \"fields\": [\n"
                    + "        {\"name\": \"ip_address\", \"type\": \"string\"},\n"
                    + "        {\"name\": \"data_in_bytes\", \"type\": \"int\"},\n"
                    + "        {\"name\": \"data_out_bytes\", \"type\": \"int\"},\n"
                    + "        {\"name\": \"subnet_mask\", \"type\": \"string\"},\n"
                    + "        {\"name\": \"gateway\", \"type\": \"string\"},\n"
                    + "        {\"name\": \"interfaces\", \"type\": {\n"
                    + "          \"type\": \"array\",\n"
                    + "          \"items\": {\n"
                    + "            \"type\": \"record\",\n"
                    + "            \"name\": \"Interface\",\n"
                    + "            \"fields\": [\n"
                    + "              {\"name\": \"name\", \"type\": \"string\"},\n"
                    + "              {\"name\": \"throughput_in_bytes\", \"type\": \"int\"},\n"
                    + "              {\"name\": \"type\", \"type\": \"string\"},\n"
                    + "              {\"name\": \"mac_address\", \"type\": \"string\"},\n"
                    + "              {\"name\": \"settings\", \"type\": {\n"
                    + "                \"type\": \"record\",\n"
                    + "                \"name\": \"InterfaceSettings\",\n"
                    + "                \"fields\": [\n"
                    + "                  {\"name\": \"speed\", \"type\": \"string\"},\n"
                    + "                  {\"name\": \"duplex\", \"type\": \"string\"},\n"
                    + "                  {\"name\": \"mtu\", \"type\": \"int\"}\n"
                    + "                ]\n"
                    + "              }}\n"
                    + "            ]\n"
                    + "          }\n"
                    + "        }}\n"
                    + "      ]\n"
                    + "    }},\n"
                    + "    {\"name\": \"security\", \"type\": {\n"
                    + "      \"type\": \"record\",\n"
                    + "      \"name\": \"Security\",\n"
                    + "      \"fields\": [\n"
                    + "        {\"name\": \"encryption\", \"type\": \"boolean\"},\n"
                    + "        {\"name\": \"firewall\", \"type\": {\n"
                    + "          \"type\": \"array\",\n"
                    + "          \"items\": {\n"
                    + "            \"type\": \"record\",\n"
                    + "            \"name\": \"Firewall\",\n"
                    + "            \"fields\": [\n"
                    + "              {\"name\": \"enabled\", \"type\": \"boolean\"},\n"
                    + "              {\"name\": \"rules\", \"type\": {\n"
                    + "                \"type\": \"record\",\n"
                    + "                \"name\": \"Rules\",\n"
                    + "                \"fields\": [\n"
                    + "                  {\"name\": \"inbound\", \"type\": \"string\"},\n"
                    + "                  {\"name\": \"outbound\", \"type\": \"string\"}\n"
                    + "                ]\n"
                    + "              }}\n"
                    + "            ]\n"
                    + "          }\n"
                    + "        }}\n"
                    + "      ]\n"
                    + "    }}\n"
                    + "  ]\n"
                    + "}");
        }
        
        Map<String, Object> props = new HashMap<>();
        props.put("schema.directory", avroSchemaFile.getParentFile().getAbsolutePath());
        props.put("schema.type", "avro");
        xform.configure(props);
        
        // Create interface settings schema
        Schema interfaceSettingsSchema = SchemaBuilder.struct()
                .field("speed", Schema.STRING_SCHEMA)
                .field("duplex", Schema.STRING_SCHEMA)
                .field("mtu", Schema.INT32_SCHEMA)
                .field("extra_setting", Schema.STRING_SCHEMA) // Not in schema file
                .build();
        
        // Create rules schema for firewall
        Schema rulesSchema = SchemaBuilder.struct()
                .field("inbound", Schema.STRING_SCHEMA)
                .field("outbound", Schema.STRING_SCHEMA)
                .field("extra_rule", Schema.STRING_SCHEMA) // Not in schema file
                .build();
        
        // Create firewall schema
        Schema firewallSchema = SchemaBuilder.struct()
                .field("enabled", Schema.BOOLEAN_SCHEMA)
                .field("rules", rulesSchema)
                .field("extra_firewall_field", Schema.STRING_SCHEMA) // Not in schema file
                .build();
        
        // Create interface schema
        Schema interfaceSchema = SchemaBuilder.struct()
                .field("name", Schema.STRING_SCHEMA)
                .field("throughput_in_bytes", Schema.INT32_SCHEMA)
                .field("type", Schema.STRING_SCHEMA)
                .field("mac_address", Schema.STRING_SCHEMA)
                .field("settings", interfaceSettingsSchema)
                .field("extra_interface_field", Schema.STRING_SCHEMA) // Not in schema file
                .build();
        
        // Create network config schema with array of interfaces
        Schema networkConfigSchema = SchemaBuilder.struct()
                .field("ip_address", Schema.STRING_SCHEMA)
                .field("data_in_bytes", Schema.INT32_SCHEMA)
                .field("data_out_bytes", Schema.INT32_SCHEMA)
                .field("subnet_mask", Schema.STRING_SCHEMA)
                .field("gateway", Schema.STRING_SCHEMA)
                .field("interfaces", SchemaBuilder.array(interfaceSchema))
                .field("extra_config", Schema.STRING_SCHEMA) // Not in schema file
                .build();
        
        // Create security schema
        Schema securitySchema = SchemaBuilder.struct()
                .field("encryption", Schema.BOOLEAN_SCHEMA)
                .field("firewall", SchemaBuilder.array(firewallSchema))
                .field("extra_security", Schema.STRING_SCHEMA) // Not in schema file
                .build();
        
        // Create the main schema
        Schema schema = SchemaBuilder.struct()
                .field("id", Schema.STRING_SCHEMA)
                .field("created_at", Schema.STRING_SCHEMA)
                .field("last_modified_at", Schema.STRING_SCHEMA)
                .field("is_deleted", Schema.BOOLEAN_SCHEMA)
                .field("name", Schema.STRING_SCHEMA)
                .field("device_type", Schema.STRING_SCHEMA)
                .field("status", Schema.STRING_SCHEMA)
                .field("network_config", networkConfigSchema)
                .field("security", securitySchema)
                .field("extra_field", Schema.STRING_SCHEMA) // Not in schema file
                .build();
                
        // Create interface settings struct
        Struct interfaceSettings1 = new Struct(interfaceSettingsSchema)
                .put("speed", "1Gbps")
                .put("duplex", "full")
                .put("mtu", 1500)
                .put("extra_setting", "should be removed");
        
        Struct interfaceSettings2 = new Struct(interfaceSettingsSchema)
                .put("speed", "10Gbps")
                .put("duplex", "half")
                .put("mtu", 9000)
                .put("extra_setting", "should be removed");
        
        // Create rules struct
        Struct rules = new Struct(rulesSchema)
                .put("inbound", "allow tcp from any to any")
                .put("outbound", "allow tcp from any to any")
                .put("extra_rule", "should be removed");
        
        // Create firewall struct
        Struct firewall = new Struct(firewallSchema)
                .put("enabled", true)
                .put("rules", rules)
                .put("extra_firewall_field", "should be removed");
        
        // Create interface structs
        Struct interface1 = new Struct(interfaceSchema)
                .put("name", "eth0")
                .put("throughput_in_bytes", 1024)
                .put("type", "ethernet")
                .put("mac_address", "00:11:22:33:44:55")
                .put("settings", interfaceSettings1)
                .put("extra_interface_field", "should be removed");
        
        Struct interface2 = new Struct(interfaceSchema)
                .put("name", "eth1")
                .put("throughput_in_bytes", 2048)
                .put("type", "ethernet")
                .put("mac_address", "AA:BB:CC:DD:EE:FF")
                .put("settings", interfaceSettings2)
                .put("extra_interface_field", "should be removed");
        
        // Create network config struct with array of interfaces
        Struct networkConfig = new Struct(networkConfigSchema)
                .put("ip_address", "192.168.1.100")
                .put("data_in_bytes", 5000)
                .put("data_out_bytes", 3000)
                .put("subnet_mask", "255.255.255.0")
                .put("gateway", "192.168.1.1")
                .put("interfaces", Arrays.asList(interface1, interface2))
                .put("extra_config", "should be removed");
        
        // Create security struct
        Struct security = new Struct(securitySchema)
                .put("encryption", true)
                .put("firewall", Arrays.asList(firewall))
                .put("extra_security", "should be removed");
        
        // Create the main struct
        Struct struct = new Struct(schema)
                .put("id", "device-123")
                .put("created_at", "2025-04-08T09:00:00Z")
                .put("last_modified_at", "2025-04-08T10:00:00Z")
                .put("is_deleted", false)
                .put("name", "Router-1")
                .put("device_type", "router")
                .put("status", "active")
                .put("network_config", networkConfig)
                .put("security", security)
                .put("extra_field", "should be removed");
                
        // Create and transform the record
        SinkRecord record = new SinkRecord("network-device-topic", 0, null, null, schema, struct, 0);
        SinkRecord transformedRecord = xform.apply(record);
        
        // Verify the transformation
        Struct transformedValue = (Struct) transformedRecord.value();
        
        // Check top-level fields
        assertEquals("device-123", transformedValue.get("id"));
        assertEquals("2025-04-08T09:00:00Z", transformedValue.get("created_at"));
        assertEquals("2025-04-08T10:00:00Z", transformedValue.get("last_modified_at"));
        assertEquals(false, transformedValue.get("is_deleted"));
        assertEquals("Router-1", transformedValue.get("name"));
        assertEquals("router", transformedValue.get("device_type"));
        assertEquals("active", transformedValue.get("status"));
        assertNull(transformedValue.schema().field("extra_field"));
        
        // Check network_config fields
        Struct transformedNetworkConfig = (Struct) transformedValue.get("network_config");
        assertEquals("192.168.1.100", transformedNetworkConfig.get("ip_address"));
        assertEquals(5000, transformedNetworkConfig.get("data_in_bytes"));
        assertEquals(3000, transformedNetworkConfig.get("data_out_bytes"));
        assertEquals("255.255.255.0", transformedNetworkConfig.get("subnet_mask"));
        assertEquals("192.168.1.1", transformedNetworkConfig.get("gateway"));
        assertNull(transformedNetworkConfig.schema().field("extra_config"));
        
        // Check interfaces array
        List<Struct> transformedInterfaces = (List<Struct>) transformedNetworkConfig.get("interfaces");
        assertEquals(2, transformedInterfaces.size());
        
        // Check first interface
        Struct transformedInterface1 = transformedInterfaces.get(0);
        assertEquals("eth0", transformedInterface1.get("name"));
        assertEquals(1024, transformedInterface1.get("throughput_in_bytes"));
        assertEquals("ethernet", transformedInterface1.get("type"));
        assertEquals("00:11:22:33:44:55", transformedInterface1.get("mac_address"));
        assertNull(transformedInterface1.schema().field("extra_interface_field"));
        
        // Check first interface settings
        Struct transformedSettings1 = (Struct) transformedInterface1.get("settings");
        assertEquals("1Gbps", transformedSettings1.get("speed"));
        assertEquals("full", transformedSettings1.get("duplex"));
        assertEquals(1500, transformedSettings1.get("mtu"));
        assertNull(transformedSettings1.schema().field("extra_setting"));
        
        // Check second interface
        Struct transformedInterface2 = transformedInterfaces.get(1);
        assertEquals("eth1", transformedInterface2.get("name"));
        assertEquals(2048, transformedInterface2.get("throughput_in_bytes"));
        assertEquals("ethernet", transformedInterface2.get("type"));
        assertEquals("AA:BB:CC:DD:EE:FF", transformedInterface2.get("mac_address"));
        assertNull(transformedInterface2.schema().field("extra_interface_field"));
        
        // Check second interface settings
        Struct transformedSettings2 = (Struct) transformedInterface2.get("settings");
        assertEquals("10Gbps", transformedSettings2.get("speed"));
        assertEquals("half", transformedSettings2.get("duplex"));
        assertEquals(9000, transformedSettings2.get("mtu"));
        assertNull(transformedSettings2.schema().field("extra_setting"));
        
        // Check security fields
        Struct transformedSecurity = (Struct) transformedValue.get("security");
        assertTrue(transformedSecurity.getBoolean("encryption"));
        
        // Check firewall array
        List<Struct> transformedFirewalls = (List<Struct>) transformedSecurity.get("firewall");
        assertEquals(1, transformedFirewalls.size());
        
        // Check firewall fields
        Struct transformedFirewall = transformedFirewalls.get(0);
        assertTrue(transformedFirewall.getBoolean("enabled"));
        assertNull(transformedFirewall.schema().field("extra_firewall_field"));
        
        // Check rules fields
        Struct transformedRules = (Struct) transformedFirewall.get("rules");
        assertEquals("allow tcp from any to any", transformedRules.get("inbound"));
        assertEquals("allow tcp from any to any", transformedRules.get("outbound"));
        assertNull(transformedRules.schema().field("extra_rule"));
        
        assertNull(transformedSecurity.schema().field("extra_security"));
    }
}
