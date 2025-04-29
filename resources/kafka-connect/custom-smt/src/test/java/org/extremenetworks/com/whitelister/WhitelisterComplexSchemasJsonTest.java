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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.fasterxml.jackson.databind.ObjectMapper;


/**
 * Complex schema test cases for the Whitelister SMT using JSON schemas.
 * These tests focus on deeply nested structures, complex arrays, and edge cases.
 */
public class WhitelisterComplexSchemasJsonTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File schemaDir;
    private Whitelister<SinkRecord> xform;

    @Before
    public void setUp() throws IOException {
        xform = new Whitelister.Value<>();
        
        // Create a schema directory
        schemaDir = tempFolder.newFolder("complex-json-schemas");
    }

    @After
    public void tearDown() {
        xform.close();
    }

    @Test
    public void testFiveLevelNestedStructureJson() throws IOException {
        // Create the JSON schema file with 5-level deep nesting using "properties"
        File schemaFile = new File(schemaDir, "five-level-nested.json");
        try (FileWriter writer = new FileWriter(schemaFile)) {
            writer.write("{\n" +
                    "  \"type\": \"object\",\n" +
                    "  \"title\": \"DeepNested\",\n" +
                    "  \"properties\": {\n" +
                    "    \"id\": {\n" +
                    "      \"type\": \"string\"\n" +
                    "    },\n" +
                    "    \"level1\": {\n" +
                    "      \"type\": \"object\",\n" +
                    "      \"title\": \"Level1\",\n" +
                    "      \"properties\": {\n" +
                    "        \"name\": {\n" +
                    "          \"type\": \"string\"\n" +
                    "        },\n" +
                    "        \"level2\": {\n" +
                    "          \"type\": \"object\",\n" +
                    "          \"title\": \"Level2\",\n" +
                    "          \"properties\": {\n" +
                    "            \"code\": {\n" +
                    "              \"type\": \"integer\"\n" +
                    "            },\n" +
                    "            \"level3\": {\n" +
                    "              \"type\": \"object\",\n" +
                    "              \"title\": \"Level3\",\n" +
                    "              \"properties\": {\n" +
                    "                \"active\": {\n" +
                    "                  \"type\": \"boolean\"\n" +
                    "                },\n" +
                    "                \"level4\": {\n" +
                    "                  \"type\": \"object\",\n" +
                    "                  \"title\": \"Level4\",\n" +
                    "                  \"properties\": {\n" +
                    "                    \"value\": {\n" +
                    "                      \"type\": \"number\"\n" +
                    "                    },\n" +
                    "                    \"level5\": {\n" +
                    "                      \"type\": \"object\",\n" +
                    "                      \"title\": \"Level5\",\n" +
                    "                      \"properties\": {\n" +
                    "                        \"final_value\": {\n" +
                    "                          \"type\": \"string\"\n" +
                    "                        }\n" +
                    "                      }\n" +
                    "                    }\n" +
                    "                  }\n" +
                    "                }\n" +
                    "              }\n" +
                    "            }\n" +
                    "          }\n" +
                    "        }\n" +
                    "      }\n" +
                    "    }\n" +
                    "  }\n" +
                    "}");
        }

        // Configure the transformation for JSON schema type
        Map<String, Object> props = new HashMap<>();
        props.put("schema.directory", schemaDir.getAbsolutePath());
        props.put("schema.type", "json"); // Ensure it's set to "json"
        xform.configure(props);

        // Create source schema with extra fields at each level (Kafka Connect Schema)
        Schema level5Schema = SchemaBuilder.struct()
                .field("final_value", Schema.STRING_SCHEMA)
                .field("extra_level5", Schema.STRING_SCHEMA) // Extra field
                .build();

        Schema level4Schema = SchemaBuilder.struct()
                .field("value", Schema.FLOAT64_SCHEMA)
                .field("level5", level5Schema)
                .field("extra_level4", Schema.INT32_SCHEMA) // Extra field
                .build();

        Schema level3Schema = SchemaBuilder.struct()
                .field("active", Schema.BOOLEAN_SCHEMA)
                .field("level4", level4Schema)
                .field("extra_level3", Schema.STRING_SCHEMA) // Extra field
                .build();

        Schema level2Schema = SchemaBuilder.struct()
                .field("code", Schema.INT32_SCHEMA)
                .field("level3", level3Schema)
                .field("extra_level2", Schema.BOOLEAN_SCHEMA) // Extra field
                .build();

        Schema level1Schema = SchemaBuilder.struct()
                .field("name", Schema.STRING_SCHEMA)
                .field("level2", level2Schema)
                .field("extra_level1", Schema.INT32_SCHEMA) // Extra field
                .build();

        Schema sourceSchema = SchemaBuilder.struct()
                .field("id", Schema.STRING_SCHEMA)
                .field("level1", level1Schema)
                .field("extra_root", Schema.STRING_SCHEMA) // Extra field
                .build();

        // Create source struct with values
        Struct level5 = new Struct(level5Schema)
                .put("final_value", "deepest value")
                .put("extra_level5", "should be removed");

        Struct level4 = new Struct(level4Schema)
                .put("value", 123.456)
                .put("level5", level5)
                .put("extra_level4", 999);

        Struct level3 = new Struct(level3Schema)
                .put("active", true)
                .put("level4", level4)
                .put("extra_level3", "should be removed");

        Struct level2 = new Struct(level2Schema)
                .put("code", 42)
                .put("level3", level3)
                .put("extra_level2", false);

        Struct level1 = new Struct(level1Schema)
                .put("name", "test name")
                .put("level2", level2)
                .put("extra_level1", 123);

        Struct sourceValue = new Struct(sourceSchema)
                .put("id", "test-id")
                .put("level1", level1)
                .put("extra_root", "should be removed");

        // Create the sink record
        SinkRecord record = new SinkRecord("five-level-nested", 0, null, null,
                sourceSchema, sourceValue, 0);

        // Apply the transformation
        SinkRecord transformedRecord = xform.apply(record);

        // Verify the transformation
        assertNotNull(transformedRecord);
        assertNotNull(transformedRecord.value());
        assertTrue(transformedRecord.value() instanceof Struct);

        Struct transformedValue = (Struct) transformedRecord.value();

        // Check root level
        assertEquals("test-id", transformedValue.get("id"));
        assertNull(transformedValue.schema().field("extra_root"));

        // Check level 1
        Struct transformedLevel1 = (Struct) transformedValue.get("level1");
        assertNotNull(transformedLevel1);
        assertEquals("test name", transformedLevel1.get("name"));
        assertNull(transformedLevel1.schema().field("extra_level1"));

        // Check level 2
        Struct transformedLevel2 = (Struct) transformedLevel1.get("level2");
        assertNotNull(transformedLevel2);
        assertEquals(42, transformedLevel2.get("code"));
        assertNull(transformedLevel2.schema().field("extra_level2"));

        // Check level 3
        Struct transformedLevel3 = (Struct) transformedLevel2.get("level3");
        assertNotNull(transformedLevel3);
        assertEquals(true, transformedLevel3.get("active"));
        assertNull(transformedLevel3.schema().field("extra_level3"));

        // Check level 4
        Struct transformedLevel4 = (Struct) transformedLevel3.get("level4");
        assertNotNull(transformedLevel4);
        assertEquals(123.456, transformedLevel4.get("value"));
        assertNull(transformedLevel4.schema().field("extra_level4"));

        // Check level 5
        Struct transformedLevel5 = (Struct) transformedLevel4.get("level5");
        assertNotNull(transformedLevel5);
        assertEquals("deepest value", transformedLevel5.get("final_value"));
        assertNull(transformedLevel5.schema().field("extra_level5"));
    }
 
    @Test
    public void testDeeplyNestedArraysOfStructsJson() throws IOException {
        // Create the JSON schema file with deeply nested arrays of structs
        File schemaFile = new File(schemaDir, "nested-arrays.json");
        try (FileWriter writer = new FileWriter(schemaFile)) {
            writer.write("{\n" +
                    "  \"type\": \"object\",\n" +
                    "  \"title\": \"NetworkTopology\",\n" +
                    "  \"properties\": {\n" +
                    "    \"topology_id\": {\n" +
                    "      \"type\": \"string\"\n" +
                    "    },\n" +
                    "    \"datacenters\": {\n" +
                    "      \"type\": \"array\",\n" +
                    "      \"items\": {\n" +
                    "        \"type\": \"object\",\n" +
                    "        \"title\": \"Datacenter\",\n" +
                    "        \"properties\": {\n" +
                    "          \"dc_id\": {\n" +
                    "            \"type\": \"string\"\n" +
                    "          },\n" +
                    "          \"dc_name\": {\n" +
                    "            \"type\": \"string\"\n" +
                    "          },\n" +
                    "          \"racks\": {\n" +
                    "            \"type\": \"array\",\n" +
                    "            \"items\": {\n" +
                    "              \"type\": \"object\",\n" +
                    "              \"title\": \"Rack\",\n" +
                    "              \"properties\": {\n" +
                    "                \"rack_id\": {\n" +
                    "                  \"type\": \"string\"\n" +
                    "                },\n" +
                    "                \"servers\": {\n" +
                    "                  \"type\": \"array\",\n" +
                    "                  \"items\": {\n" +
                    "                    \"type\": \"object\",\n" +
                    "                    \"title\": \"Server\",\n" +
                    "                    \"properties\": {\n" +
                    "                      \"server_id\": {\n" +
                    "                        \"type\": \"string\"\n" +
                    "                      },\n" +
                    "                      \"server_type\": {\n" +
                    "                        \"type\": \"string\"\n" +
                    "                      },\n" +
                    "                      \"network_interfaces\": {\n" +
                    "                        \"type\": \"array\",\n" +
                    "                        \"items\": {\n" +
                    "                          \"type\": \"object\",\n" +
                    "                          \"title\": \"NetworkInterface\",\n" +
                    "                          \"properties\": {\n" +
                    "                            \"interface_id\": {\n" +
                    "                              \"type\": \"string\"\n" +
                    "                            },\n" +
                    "                            \"ip_address\": {\n" +
                    "                              \"type\": \"string\"\n" +
                    "                            },\n" +
                    "                            \"vlans\": {\n" +
                    "                              \"type\": \"array\",\n" +
                    "                              \"items\": {\n" +
                    "                                \"type\": \"object\",\n" +
                    "                                \"title\": \"Vlan\",\n" +
                    "                                \"properties\": {\n" +
                    "                                  \"vlan_id\": {\n" +
                    "                                    \"type\": \"integer\"\n" +
                    "                                  },\n" +
                    "                                  \"vlan_name\": {\n" +
                    "                                    \"type\": \"string\"\n" +
                    "                                  }\n" +
                    "                                }\n" +
                    "                              }\n" +
                    "                            }\n" +
                    "                          }\n" +
                    "                        }\n" +
                    "                      }\n" +
                    "                    }\n" +
                    "                  }\n" +
                    "                }\n" +
                    "              }\n" +
                    "            }\n" +
                    "          }\n" +
                    "        }\n" +
                    "      }\n" +
                    "    }\n" +
                    "  }\n" +
                    "}");
        }

        // Configure the transformation
        Map<String, Object> props = new HashMap<>();
        props.put("schema.directory", schemaDir.getAbsolutePath());
        props.put("schema.type", "json"); // Changed to "json"
        xform.configure(props);

        // Create schemas for the nested structure with extra fields
        Schema vlanSchema = SchemaBuilder.struct()
                .field("vlan_id", Schema.INT32_SCHEMA)
                .field("vlan_name", Schema.STRING_SCHEMA)
                .field("extra_vlan_field", Schema.STRING_SCHEMA) // Extra field
                .build();

        Schema vlanArraySchema = SchemaBuilder.array(vlanSchema).build();

        Schema networkInterfaceSchema = SchemaBuilder.struct()
                .field("interface_id", Schema.STRING_SCHEMA)
                .field("ip_address", Schema.STRING_SCHEMA)
                .field("vlans", vlanArraySchema)
                .field("extra_interface_field", Schema.BOOLEAN_SCHEMA) // Extra field
                .build();

        Schema networkInterfaceArraySchema = SchemaBuilder.array(networkInterfaceSchema).build();

        Schema serverSchema = SchemaBuilder.struct()
                .field("server_id", Schema.STRING_SCHEMA)
                .field("server_type", Schema.STRING_SCHEMA)
                .field("network_interfaces", networkInterfaceArraySchema)
                .field("extra_server_field", Schema.INT32_SCHEMA) // Extra field
                .build();

        Schema serverArraySchema = SchemaBuilder.array(serverSchema).build();

        Schema rackSchema = SchemaBuilder.struct()
                .field("rack_id", Schema.STRING_SCHEMA)
                .field("servers", serverArraySchema)
                .field("extra_rack_field", Schema.STRING_SCHEMA) // Extra field
                .build();

        Schema rackArraySchema = SchemaBuilder.array(rackSchema).build();

        Schema datacenterSchema = SchemaBuilder.struct()
                .field("dc_id", Schema.STRING_SCHEMA)
                .field("dc_name", Schema.STRING_SCHEMA)
                .field("racks", rackArraySchema)
                .field("extra_dc_field", Schema.BOOLEAN_SCHEMA) // Extra field
                .build();

        Schema datacenterArraySchema = SchemaBuilder.array(datacenterSchema).build();

        Schema sourceSchema = SchemaBuilder.struct()
                .field("topology_id", Schema.STRING_SCHEMA)
                .field("datacenters", datacenterArraySchema)
                .field("extra_topology_field", Schema.STRING_SCHEMA) // Extra field
                .build();

        // Create source structs with values
        // Create VLANs for the first network interface
        Struct vlan1 = new Struct(vlanSchema)
                .put("vlan_id", 100)
                .put("vlan_name", "Production")
                .put("extra_vlan_field", "should be removed");

        Struct vlan2 = new Struct(vlanSchema)
                .put("vlan_id", 200)
                .put("vlan_name", "Development")
                .put("extra_vlan_field", "should be removed");

        // Create network interfaces for the first server
        Struct networkInterface1 = new Struct(networkInterfaceSchema)
                .put("interface_id", "eth0")
                .put("ip_address", "192.168.1.1")
                .put("vlans", Arrays.asList(vlan1, vlan2))
                .put("extra_interface_field", true);

        // Create VLANs for the second network interface
        Struct vlan3 = new Struct(vlanSchema)
                .put("vlan_id", 300)
                .put("vlan_name", "Management")
                .put("extra_vlan_field", "should be removed");

        Struct networkInterface2 = new Struct(networkInterfaceSchema)
                .put("interface_id", "eth1")
                .put("ip_address", "192.168.2.1")
                .put("vlans", Collections.singletonList(vlan3))
                .put("extra_interface_field", false);

        // Create servers for the first rack
        Struct server1 = new Struct(serverSchema)
                .put("server_id", "srv-001")
                .put("server_type", "application")
                .put("network_interfaces", Arrays.asList(networkInterface1, networkInterface2))
                .put("extra_server_field", 42);

        // Create a second server with different network interfaces
        Struct networkInterface3 = new Struct(networkInterfaceSchema)
                .put("interface_id", "eth0")
                .put("ip_address", "192.168.3.1")
                .put("vlans", Collections.emptyList())
                .put("extra_interface_field", true);

        Struct server2 = new Struct(serverSchema)
                .put("server_id", "srv-002")
                .put("server_type", "database")
                .put("network_interfaces", Collections.singletonList(networkInterface3))
                .put("extra_server_field", 43);

        // Create racks for the datacenter
        Struct rack1 = new Struct(rackSchema)
                .put("rack_id", "rack-001")
                .put("servers", Arrays.asList(server1, server2))
                .put("extra_rack_field", "should be removed");

        // Create a second rack with no servers
        Struct rack2 = new Struct(rackSchema)
                .put("rack_id", "rack-002")
                .put("servers", Collections.emptyList())
                .put("extra_rack_field", "should be removed");

        // Create datacenters
        Struct datacenter1 = new Struct(datacenterSchema)
                .put("dc_id", "dc-001")
                .put("dc_name", "Primary")
                .put("racks", Arrays.asList(rack1, rack2))
                .put("extra_dc_field", true);

        // Create a second datacenter with a different rack
        Struct rack3 = new Struct(rackSchema)
                .put("rack_id", "rack-003")
                .put("servers", Collections.emptyList())
                .put("extra_rack_field", "should be removed");

        Struct datacenter2 = new Struct(datacenterSchema)
                .put("dc_id", "dc-002")
                .put("dc_name", "Secondary")
                .put("racks", Collections.singletonList(rack3))
                .put("extra_dc_field", false);

        // Create the root struct
        Struct sourceValue = new Struct(sourceSchema)
                .put("topology_id", "topo-001")
                .put("datacenters", Arrays.asList(datacenter1, datacenter2))
                .put("extra_topology_field", "should be removed");

        // Create the sink record
        SinkRecord record = new SinkRecord("nested-arrays", 0, null, null,
                sourceSchema, sourceValue, 0);

        // Apply the transformation
        SinkRecord transformedRecord = xform.apply(record);

        // Verify the transformation
        assertNotNull(transformedRecord);
        assertNotNull(transformedRecord.value());
        assertTrue(transformedRecord.value() instanceof Struct);

        Struct transformedValue = (Struct) transformedRecord.value();

        // Check root level
        assertEquals("topo-001", transformedValue.get("topology_id"));
        assertNull(transformedValue.schema().field("extra_topology_field"));

        // Check datacenters array
        List<Struct> transformedDatacenters = (List<Struct>) transformedValue.get("datacenters");
        assertEquals(2, transformedDatacenters.size());

        // Check first datacenter
        Struct transformedDc1 = transformedDatacenters.get(0);
        assertEquals("dc-001", transformedDc1.get("dc_id"));
        assertEquals("Primary", transformedDc1.get("dc_name"));
        assertNull(transformedDc1.schema().field("extra_dc_field"));

        // Check racks in first datacenter
        List<Struct> transformedRacks1 = (List<Struct>) transformedDc1.get("racks");
        assertEquals(2, transformedRacks1.size());

        // Check first rack in first datacenter
        Struct transformedRack1 = transformedRacks1.get(0);
        assertEquals("rack-001", transformedRack1.get("rack_id"));
        assertNull(transformedRack1.schema().field("extra_rack_field"));

        // Check servers in first rack
        List<Struct> transformedServers = (List<Struct>) transformedRack1.get("servers");
        assertEquals(2, transformedServers.size());

        // Check first server
        Struct transformedServer1 = transformedServers.get(0);
        assertEquals("srv-001", transformedServer1.get("server_id"));
        assertEquals("application", transformedServer1.get("server_type"));
        assertNull(transformedServer1.schema().field("extra_server_field"));

        // Check network interfaces of first server
        List<Struct> transformedInterfaces = (List<Struct>) transformedServer1.get("network_interfaces");
        assertEquals(2, transformedInterfaces.size());

        // Check first network interface
        Struct transformedInterface1 = transformedInterfaces.get(0);
        assertEquals("eth0", transformedInterface1.get("interface_id"));
        assertEquals("192.168.1.1", transformedInterface1.get("ip_address"));
        assertNull(transformedInterface1.schema().field("extra_interface_field"));

        // Check VLANs of first network interface
        List<Struct> transformedVlans = (List<Struct>) transformedInterface1.get("vlans");
        assertEquals(2, transformedVlans.size());

        // Check first VLAN
        Struct transformedVlan1 = transformedVlans.get(0);
        assertEquals(100, transformedVlan1.get("vlan_id"));
        assertEquals("Production", transformedVlan1.get("vlan_name"));
        assertNull(transformedVlan1.schema().field("extra_vlan_field"));

        // Check second datacenter
        Struct transformedDc2 = transformedDatacenters.get(1);
        assertEquals("dc-002", transformedDc2.get("dc_id"));
        assertEquals("Secondary", transformedDc2.get("dc_name"));
        assertNull(transformedDc2.schema().field("extra_dc_field"));
    }

    @Test
    public void testMixedComplexTypesWithOptionalFieldsJson() throws IOException {
        // Create a JSON schema file with mixed complex types and optional fields
        File schemaFile = new File(schemaDir, "mixed-complex.json");
        try (FileWriter writer = new FileWriter(schemaFile)) {
            writer.write("{\n" +
                    "  \"type\": \"object\",\n" +
                    "  \"title\": \"EnterpriseSystem\",\n" +
                    "  \"properties\": {\n" +
                    "    \"system_id\": {\n" +
                    "      \"type\": \"string\"\n" +
                    "    },\n" +
                    "    \"metadata\": {\n" +
                    "      \"type\": \"object\",\n" +
                    "      \"title\": \"Metadata\",\n" +
                    "      \"properties\": {\n" +
                    "        \"created_at\": {\n" +
                    "          \"type\": \"string\"\n" +
                    "        },\n" +
                    "        \"version\": {\n" +
                    "          \"type\": \"string\"\n" +
                    "        },\n" +
                    "        \"tags\": {\n" +
                    "          \"type\": \"array\",\n" +
                    "          \"items\": {\n" +
                    "            \"type\": \"string\"\n" +
                    "          }\n" +
                    "        }\n" +
                    "      }\n" +
                    "    },\n" +
                    "    \"components\": {\n" +
                    "      \"type\": \"array\",\n" +
                    "      \"items\": {\n" +
                    "        \"type\": \"object\",\n" +
                    "        \"title\": \"Component\",\n" +
                    "        \"properties\": {\n" +
                    "          \"component_id\": {\n" +
                    "            \"type\": \"string\"\n" +
                    "          },\n" +
                    "          \"component_type\": {\n" +
                    "            \"type\": \"string\"\n" +
                    "          },\n" +
                    "          \"config\": {\n" +
                    "            \"type\": [\"null\", \"object\"],\n" +
                    "            \"title\": \"Config\",\n" +
                    "            \"properties\": {\n" +
                    "              \"enabled\": {\n" +
                    "                \"type\": \"boolean\"\n" +
                    "              },\n" +
                    "              \"parameters\": {\n" +
                    "                \"type\": \"string\"\n" + // CHANGED: From object to string
                    "              }\n" +
                    "            }\n" +
                    "          },\n" +
                    "          \"subcomponents\": {\n" +
                    "            \"type\": [\"null\", \"array\"],\n" +
                    "            \"items\": {\n" +
                    "              \"type\": \"object\",\n" +
                    "              \"title\": \"Subcomponent\",\n" +
                    "              \"properties\": {\n" +
                    "                \"subcomponent_id\": {\n" +
                    "                  \"type\": \"string\"\n" +
                    "                },\n" +
                    "                \"status\": {\n" +
                    "                  \"type\": [\"null\", \"string\"]\n" +
                    "                }\n" +
                    "              }\n" +
                    "            }\n" +
                    "          }\n" +
                    "        }\n" +
                    "      }\n" +
                    "    },\n" +
                    "    \"status\": {\n" +
                    "      \"type\": [\"null\", \"object\"],\n" +
                    "      \"title\": \"Status\",\n" +
                    "      \"properties\": {\n" +
                    "        \"state\": {\n" +
                    "          \"type\": \"string\"\n" +
                    "        },\n" +
                    "        \"last_updated\": {\n" +
                    "          \"type\": \"string\"\n" +
                    "        },\n" +
                    "        \"metrics\": {\n" +
                    "          \"type\": [\"null\", \"object\"],\n" +
                    "          \"title\": \"Metrics\",\n" +
                    "          \"properties\": {\n" +
                    "            \"cpu_usage\": {\n" +
                    "              \"type\": \"number\"\n" +
                    "            },\n" +
                    "            \"memory_usage\": {\n" +
                    "              \"type\": \"number\"\n" +
                    "            },\n" +
                    "            \"disk_usage\": {\n" +
                    "              \"type\": \"number\"\n" +
                    "            }\n" +
                    "          }\n" +
                    "        }\n" +
                    "      }\n" +
                    "    }\n" +
                    "  }\n" +
                    "}");
        }

        // Configure the transformation to use the JSON schema
        Map<String, Object> props = new HashMap<>();
        props.put("schema.directory", schemaDir.getAbsolutePath());
        props.put("schema.type", "json"); // Crucial: Set schema type to "json"
        xform.configure(props);

        Schema metricsSchema = SchemaBuilder.struct()
                .field("cpu_usage", Schema.FLOAT64_SCHEMA)
                .field("memory_usage", Schema.FLOAT64_SCHEMA)
                .field("disk_usage", Schema.FLOAT64_SCHEMA)
                .field("extra_metric", Schema.FLOAT64_SCHEMA) // Extra field to be removed
                .optional()
                .build();

        Schema statusSchema = SchemaBuilder.struct()
                .field("state", Schema.STRING_SCHEMA)
                .field("last_updated", Schema.STRING_SCHEMA)
                .field("metrics", metricsSchema)
                .field("extra_status_field", Schema.STRING_SCHEMA) // Extra field to be removed
                .optional()
                .build();

        // Subcomponent schema
        Schema subcomponentSchema = SchemaBuilder.struct()
                .field("subcomponent_id", Schema.STRING_SCHEMA)
                .field("status", Schema.OPTIONAL_STRING_SCHEMA)
                .field("extra_subcomponent_field", Schema.BOOLEAN_SCHEMA) // Extra field to be removed
                .build();

        Schema subcomponentArraySchema = SchemaBuilder.array(subcomponentSchema)
                .optional()
                .build();

        Schema configSchema = SchemaBuilder.struct()
                .field("enabled", Schema.BOOLEAN_SCHEMA)
                .field("parameters", Schema.STRING_SCHEMA) // Changed to STRING_SCHEMA
                .field("extra_config_field", Schema.INT32_SCHEMA) // Extra field to be removed
                .optional()
                .build();

        Schema componentSchema = SchemaBuilder.struct()
                .field("component_id", Schema.STRING_SCHEMA)
                .field("component_type", Schema.STRING_SCHEMA)
                .field("config", configSchema)
                .field("subcomponents", subcomponentArraySchema)
                .field("extra_component_field", Schema.STRING_SCHEMA) // Extra field to be removed
                .build();

        Schema componentArraySchema = SchemaBuilder.array(componentSchema).build();

        Schema metadataSchema = SchemaBuilder.struct()
                .field("created_at", Schema.STRING_SCHEMA)
                .field("version", Schema.STRING_SCHEMA)
                .field("tags", SchemaBuilder.array(Schema.STRING_SCHEMA))
                .field("extra_metadata_field", Schema.INT32_SCHEMA) // Extra field to be removed
                .build();

        Schema sourceSchema = SchemaBuilder.struct()
                .field("system_id", Schema.STRING_SCHEMA)
                .field("metadata", metadataSchema)
                .field("components", componentArraySchema)
                .field("status", statusSchema)
                .field("extra_root_field", Schema.STRING_SCHEMA) // Extra field to be removed
                .build();

        Struct metrics = new Struct(metricsSchema)
                .put("cpu_usage", 75.5)
                .put("memory_usage", 60.2)
                .put("disk_usage", 85.0)
                .put("extra_metric", 99.9); // Extra field

        // Create status (optional)
        Struct status = new Struct(statusSchema)
                .put("state", "running")
                .put("last_updated", "2025-04-16T12:00:00Z")
                .put("metrics", metrics)
                .put("extra_status_field", "should be removed"); // Extra field

        // Create subcomponents with one having null status
        Struct subcomponent1 = new Struct(subcomponentSchema)
                .put("subcomponent_id", "sub-001")
                .put("status", "active")
                .put("extra_subcomponent_field", true); // Extra field

        Struct subcomponent2 = new Struct(subcomponentSchema)
                .put("subcomponent_id", "sub-002")
                .put("status", null) // Null optional field
                .put("extra_subcomponent_field", false); // Extra field

        // Create a map for config parameters
        Map<String, String> parameters = new HashMap<>();
        parameters.put("param1", "value1");
        parameters.put("param2", "value2");

        ObjectMapper objectMapper = new ObjectMapper();
        String parametersJson = objectMapper.writeValueAsString(parameters); // Convert map to JSON string

        Struct config = new Struct(configSchema)
                .put("enabled", true)
                .put("parameters", parametersJson) // Store JSON string in parameters field
                .put("extra_config_field", 42); // Extra field

        // Create components
        Struct component1 = new Struct(componentSchema)
                .put("component_id", "comp-001")
                .put("component_type", "service")
                .put("config", config)
                .put("subcomponents", Arrays.asList(subcomponent1, subcomponent2))
                .put("extra_component_field", "should be removed"); // Extra field

        // Component with null optional fields
        Struct component2 = new Struct(componentSchema)
                .put("component_id", "comp-002")
                .put("component_type", "database")
                .put("config", null) // Null optional field
                .put("subcomponents", null) // Null optional field
                .put("extra_component_field", "should be removed"); // Extra field

        // Create metadata with tags
        Struct metadata = new Struct(metadataSchema)
                .put("created_at", "2025-04-01T00:00:00Z")
                .put("version", "1.0.0")
                .put("tags", Arrays.asList("production", "critical", "monitored"))
                .put("extra_metadata_field", 100); // Extra field

        // Create the root struct
        Struct sourceValue = new Struct(sourceSchema)
                .put("system_id", "sys-001")
                .put("metadata", metadata)
                .put("components", Arrays.asList(component1, component2))
                .put("status", status)
                .put("extra_root_field", "should be removed"); // Extra field

        // Create the sink record
        SinkRecord record = new SinkRecord("mixed-complex", 0, null, null,
                sourceSchema, sourceValue, 0);

        // Apply the transformation
        SinkRecord transformedRecord = xform.apply(record);

        // Verify the transformation
        assertNotNull(transformedRecord);
        assertNotNull(transformedRecord.value());
        assertTrue(transformedRecord.value() instanceof Struct);

        Struct transformedValue = (Struct) transformedRecord.value();

        // Check root level
        assertEquals("sys-001", transformedValue.get("system_id"));
        assertNull(transformedValue.schema().field("extra_root_field"));

        // Check metadata
        Struct transformedMetadata = (Struct) transformedValue.get("metadata");
        assertNotNull(transformedMetadata);
        assertEquals("2025-04-01T00:00:00Z", transformedMetadata.get("created_at"));
        assertEquals("1.0.0", transformedMetadata.get("version"));
        assertNull(transformedMetadata.schema().field("extra_metadata_field"));

        // Check tags array in metadata
        List<String> transformedTags = (List<String>) transformedMetadata.get("tags");
        assertEquals(3, transformedTags.size());
        assertEquals("production", transformedTags.get(0));
        assertEquals("critical", transformedTags.get(1));
        assertEquals("monitored", transformedTags.get(2));

        // Check components array
        List<Struct> transformedComponents = (List<Struct>) transformedValue.get("components");
        assertEquals(2, transformedComponents.size());

        // Check first component
        Struct transformedComponent1 = transformedComponents.get(0);
        assertEquals("comp-001", transformedComponent1.get("component_id"));
        assertEquals("service", transformedComponent1.get("component_type"));
        assertNull(transformedComponent1.schema().field("extra_component_field"));

        // Check config in first component
        Struct transformedConfig = (Struct) transformedComponent1.get("config");
        assertNotNull(transformedConfig);
        assertEquals(true, transformedConfig.get("enabled"));
        assertNull(transformedConfig.schema().field("extra_config_field"));

        // Check parameters map in config - MODIFIED ASSERTION
        String transformedParamsJson = (String) transformedConfig.get("parameters");
        Map<String, String> transformedParams = objectMapper.readValue(transformedParamsJson, Map.class);
        assertEquals(2, transformedParams.size());
        assertEquals("value1", transformedParams.get("param1"));
        assertEquals("value2", transformedParams.get("param2"));

        List<Struct> transformedSubcomponents = (List<Struct>) transformedComponent1.get("subcomponents");
        assertEquals(2, transformedSubcomponents.size());

        Struct transformedSubcomponent1 = transformedSubcomponents.get(0);
        assertEquals("sub-001", transformedSubcomponent1.get("subcomponent_id"));
        assertEquals("active", transformedSubcomponent1.get("status"));
        assertNull(transformedSubcomponent1.schema().field("extra_subcomponent_field"));

        Struct transformedSubcomponent2 = transformedSubcomponents.get(1);
        assertEquals("sub-002", transformedSubcomponent2.get("subcomponent_id"));
        assertNull(transformedSubcomponent2.get("status")); // Null value should be preserved
        assertNull(transformedSubcomponent2.schema().field("extra_subcomponent_field"));

        Struct transformedComponent2 = transformedComponents.get(1);
        assertEquals("comp-002", transformedComponent2.get("component_id"));
        assertEquals("database", transformedComponent2.get("component_type"));
        assertNull(transformedComponent2.get("config")); // Null value should be preserved
        assertNull(transformedComponent2.get("subcomponents")); // Null value should be preserved
        assertNull(transformedComponent2.schema().field("extra_component_field"));

        Struct transformedStatus = (Struct) transformedValue.get("status");
        assertNotNull(transformedStatus);
        assertEquals("running", transformedStatus.get("state"));
        assertEquals("2025-04-16T12:00:00Z", transformedStatus.get("last_updated"));
        assertNull(transformedStatus.schema().field("extra_status_field"));

        
        Struct transformedMetrics = (Struct) transformedStatus.get("metrics");
        assertNotNull(transformedMetrics);
        assertEquals(75.5, transformedMetrics.get("cpu_usage"));
        assertEquals(60.2, transformedMetrics.get("memory_usage"));
        assertEquals(85.0, transformedMetrics.get("disk_usage"));
        assertNull(transformedMetrics.schema().field("extra_metric"));
    }
}
