package org.extremenetworks.com.schemaconverter;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.junit.Before;
import org.junit.Test;

/**
 * Complex test cases for SchemaConverter class.
 * Tests complex Avro and JSON schema parsing with various edge cases.
 */
public class ComplexSchemaConverterTest {
    
    private SchemaConverter schemaConverter;
    private static final String SCHEMAS_DIR = "src/test/resources/schemas/";
    
    @Before
    public void setUp() {
        schemaConverter = new SchemaConverter();
    }
    
    /**
     * Helper method to read a schema file.
     */
    private String readSchemaFile(String filename) throws IOException {
        return new String(Files.readAllBytes(Paths.get(SCHEMAS_DIR + filename)));
    }
    
    /**
     * Helper method to validate a field exists and has the expected type.
     */
    private void validateField(Schema schema, String fieldName, Schema.Type expectedType) {
        Field field = schema.field(fieldName);
        assertNotNull("Field " + fieldName + " should exist", field);
        assertEquals("Field " + fieldName + " should have type " + expectedType, 
                expectedType, field.schema().type());
    }
    
    /**
     * Helper method to validate a nested field exists and has the expected type.
     */
    private void validateNestedField(Schema schema, String parentField, String childField, Schema.Type expectedType) {
        Field parent = schema.field(parentField);
        assertNotNull("Parent field " + parentField + " should exist", parent);
        assertEquals("Parent field should be a STRUCT", Schema.Type.STRUCT, parent.schema().type());
        
        Field child = parent.schema().field(childField);
        assertNotNull("Child field " + childField + " should exist", child);
        assertEquals("Child field " + childField + " should have type " + expectedType, 
                expectedType, child.schema().type());
    }
    
    /**
     * Test conversion of a complex Avro schema with various field types.
     */
    @Test
    public void testConvertComplexAvroToConnectSchema() throws IOException {
        String avroSchema = readSchemaFile("complex_avro_schema.avsc");
        
        Schema schema = schemaConverter.convertAvroToConnectSchema(avroSchema, "ComplexRecord");
        
        assertNotNull("Schema should not be null", schema);
        assertEquals("Schema should be of type STRUCT", Schema.Type.STRUCT, schema.type());
        assertEquals("Schema should have correct name", "org.extremenetworks.com.test.ComplexRecord", schema.name());
        
        // Validate primitive fields
        validateField(schema, "id", Schema.Type.STRING);
        validateField(schema, "timestamp", Schema.Type.INT64);
        
        // Validate nullable field
        Field nullableField = schema.field("nullable_field");
        assertNotNull("Nullable field should exist", nullableField);
        assertEquals("Nullable field should be STRING", Schema.Type.STRING, nullableField.schema().type());
        assertTrue("Nullable field should be optional", nullableField.schema().isOptional());
        
        // Validate array fields
        Field arrayField = schema.field("array_of_primitives");
        assertNotNull("Array field should exist", arrayField);
        assertEquals("Array field should be ARRAY", Schema.Type.ARRAY, arrayField.schema().type());
        assertEquals("Array items should be STRING", Schema.Type.STRING, 
                arrayField.schema().valueSchema().type());
        
        // Validate array of records
        Field arrayOfRecords = schema.field("array_of_records");
        assertNotNull("Array of records field should exist", arrayOfRecords);
        assertEquals("Array of records field should be ARRAY", Schema.Type.ARRAY, 
                arrayOfRecords.schema().type());
        assertEquals("Array items should be STRUCT", Schema.Type.STRUCT, 
                arrayOfRecords.schema().valueSchema().type());
    }
    
    /**
     * Test Avro schema with complex union types.
     */
    @Test
    public void testAvroSchemaWithComplexUnions() throws IOException {
        String avroSchema = "{\n"
                + "  \"type\": \"record\",\n"
                + "  \"name\": \"UnionRecord\",\n"
                + "  \"fields\": [\n"
                + "    {\n"
                + "      \"name\": \"multi_type_field\",\n"
                + "      \"type\": [\"null\", \"string\", \"int\", \"boolean\"],\n"
                + "      \"default\": null\n"
                + "    },\n"
                + "    {\n"
                + "      \"name\": \"union_with_array\",\n"
                + "      \"type\": [\"null\", {\"type\": \"array\", \"items\": \"string\"}],\n"
                + "      \"default\": null\n"
                + "    }\n"
                + "  ]\n"
                + "}";
        
        Schema schema = schemaConverter.convertAvroToConnectSchema(avroSchema, "UnionRecord");
        
        assertNotNull("Schema should not be null", schema);
        assertEquals("Schema should be of type STRUCT", Schema.Type.STRUCT, schema.type());
        
        // Validate multi-type field
        Field multiTypeField = schema.field("multi_type_field");
        assertNotNull("Multi-type field should exist", multiTypeField);
        assertTrue("Multi-type field should be optional", multiTypeField.schema().isOptional());
    }
    
    /**
     * Test Avro schema with logical types.
     */
    @Test
    public void testAvroSchemaWithLogicalTypes() throws IOException {
        String avroSchema = "{\n"
                + "  \"type\": \"record\",\n"
                + "  \"name\": \"LogicalTypesRecord\",\n"
                + "  \"fields\": [\n"
                + "    {\n"
                + "      \"name\": \"timestamp_millis\",\n"
                + "      \"type\": {\"type\": \"long\", \"logicalType\": \"timestamp-millis\"}\n"
                + "    },\n"
                + "    {\n"
                + "      \"name\": \"date\",\n"
                + "      \"type\": {\"type\": \"int\", \"logicalType\": \"date\"}\n"
                + "    }\n"
                + "  ]\n"
                + "}";
        
        Schema schema = schemaConverter.convertAvroToConnectSchema(avroSchema, "LogicalTypesRecord");
        
        assertNotNull("Schema should not be null", schema);
        assertEquals("Schema should be of type STRUCT", Schema.Type.STRUCT, schema.type());
        
        // Validate timestamp fields
        validateField(schema, "timestamp_millis", Schema.Type.INT64);
        validateField(schema, "date", Schema.Type.INT32);
    }
    
    /**
     * Test handling of invalid Avro schema.
     */
    @Test
    public void testInvalidAvroSchema() {
        // Test with malformed schema
        String malformedSchema = "{ \"type\": \"record\", \"name\": \"InvalidRecord\", \"fields\": [ { \"name\": \"field1\" } ] }";
        
        try {
            schemaConverter.convertAvroToConnectSchema(malformedSchema, "InvalidRecord");
            fail("Should throw an exception for malformed schema");
        } catch (IOException e) {
            // Expected exception
            assertTrue("Exception message should mention parsing error", 
                    e.getMessage().contains("Failed to parse Avro schema"));
        }
        
        // Test with empty schema
        try {
            schemaConverter.convertAvroToConnectSchema("", "EmptySchema");
            fail("Should throw an exception for empty schema");
        } catch (IOException e) {
            // Expected exception
            assertTrue("Exception message should mention empty schema", 
                    e.getMessage().contains("Cannot convert null or empty Avro schema content"));
        }
    }
    
    /**
     * Test conversion of a complex JSON schema with various field types.
     */
    @Test
    public void testConvertComplexJsonToConnectSchema() throws IOException {
        String jsonSchema = readSchemaFile("complex_json_schema.json");
        
        Schema schema = schemaConverter.convertJsonToConnectSchema(jsonSchema, "ComplexRecord");
        
        assertNotNull("Schema should not be null", schema);
        assertEquals("Schema should be of type STRUCT", Schema.Type.STRUCT, schema.type());
        assertEquals("Schema should have correct name", "ComplexRecord", schema.name());
        
        // Validate primitive fields
        validateField(schema, "id", Schema.Type.STRING);
        validateField(schema, "timestamp", Schema.Type.INT64);
        
        // Validate nullable field
        Field nullableField = schema.field("nullable_field");
        assertNotNull("Nullable field should exist", nullableField);
        assertEquals("Nullable field should be STRING", Schema.Type.STRING, nullableField.schema().type());
        assertTrue("Nullable field should be optional", nullableField.schema().isOptional());
    }
    
    /**
     * Test JSON schema with oneOf/anyOf/allOf constructs.
     */
    @Test
    public void testJsonSchemaWithPolymorphism() throws IOException {
        // Test oneOf construct
        String oneOfSchema = "{\n"
                + "  \"type\": \"object\",\n"
                + "  \"oneOf\": [\n"
                + "    {\n"
                + "      \"properties\": {\n"
                + "        \"type\": { \"enum\": [\"type_a\"] },\n"
                + "        \"a_value\": { \"type\": \"string\" }\n"
                + "      },\n"
                + "      \"required\": [\"type\", \"a_value\"]\n"
                + "    },\n"
                + "    {\n"
                + "      \"properties\": {\n"
                + "        \"type\": { \"enum\": [\"type_b\"] },\n"
                + "        \"b_value\": { \"type\": \"integer\" }\n"
                + "      },\n"
                + "      \"required\": [\"type\", \"b_value\"]\n"
                + "    }\n"
                + "  ]\n"
                + "}";
        
        Schema oneOfSchemaResult = schemaConverter.convertJsonToConnectSchema(oneOfSchema, "OneOfRecord");
        
        assertNotNull("Schema should not be null", oneOfSchemaResult);
        assertEquals("Schema should be of type STRUCT", Schema.Type.STRUCT, oneOfSchemaResult.type());
        
        // Validate that fields from both schemas are present
        validateField(oneOfSchemaResult, "type", Schema.Type.STRING);
        validateField(oneOfSchemaResult, "a_value", Schema.Type.STRING);
        validateField(oneOfSchemaResult, "b_value", Schema.Type.INT32);
    }
    
    /**
     * Test JSON schema with references.
     */
    @Test
    public void testJsonSchemaWithReferences() throws IOException {
        String schemaWithRefs = "{\n"
                + "  \"$schema\": \"http://json-schema.org/draft-07/schema#\",\n"
                + "  \"type\": \"object\",\n"
                + "  \"properties\": {\n"
                + "    \"ref_id\": { \"type\": \"string\" },\n"
                + "    \"ref_value\": { \"$ref\": \"#/definitions/common_value\" }\n"
                + "  },\n"
                + "  \"definitions\": {\n"
                + "    \"common_value\": {\n"
                + "      \"type\": \"object\",\n"
                + "      \"properties\": {\n"
                + "        \"name\": { \"type\": \"string\" },\n"
                + "        \"value\": { \"type\": \"number\" }\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "}";
        
        Schema schema = schemaConverter.convertJsonToConnectSchema(schemaWithRefs, "ReferencesRecord");
        
        assertNotNull("Schema should not be null", schema);
        assertEquals("Schema should be of type STRUCT", Schema.Type.STRUCT, schema.type());
        
        // Validate ref_id field
        validateField(schema, "ref_id", Schema.Type.STRING);
    }
    
    /**
     * Test JSON schema with required fields.
     */
    @Test
    public void testJsonSchemaWithRequiredFields() throws IOException {
        String jsonSchema = "{\n"
                + "  \"type\": \"object\",\n"
                + "  \"properties\": {\n"
                + "    \"required_field\": { \"type\": \"string\" },\n"
                + "    \"optional_field\": { \"type\": \"string\" }\n"
                + "  },\n"
                + "  \"required\": [\"required_field\"]\n"
                + "}";
        
        Schema schema = schemaConverter.convertJsonToConnectSchema(jsonSchema, "RequiredFieldsRecord");
        
        assertNotNull("Schema should not be null", schema);
        assertEquals("Schema should be of type STRUCT", Schema.Type.STRUCT, schema.type());
        
        // Validate required field
        Field requiredField = schema.field("required_field");
        assertNotNull("Required field should exist", requiredField);
        assertEquals("Required field should be STRING", Schema.Type.STRING, requiredField.schema().type());
        
        // Validate optional field
        Field optionalField = schema.field("optional_field");
        assertNotNull("Optional field should exist", optionalField);
        assertEquals("Optional field should be STRING", Schema.Type.STRING, optionalField.schema().type());
        assertTrue("Optional field should be optional", optionalField.schema().isOptional());
    }
    
    /**
     * Test schema equivalence checking.
     */
    @Test
    public void testComplexSchemaEquivalence() throws IOException {
        // Create a complex schema using the builder
        Schema schema1 = SchemaBuilder.struct()
                .name("EquivalenceRecord")
                .field("string_field", Schema.STRING_SCHEMA)
                .field("int_field", Schema.INT32_SCHEMA)
                .field("nested_field", SchemaBuilder.struct()
                        .field("nested_string", Schema.STRING_SCHEMA)
                        .field("nested_int", Schema.INT32_SCHEMA)
                        .build())
                .field("array_field", SchemaBuilder.array(Schema.STRING_SCHEMA).build())
                .build();
        
        // Create an equivalent Avro schema
        String avroSchema = "{\n"
                + "  \"type\": \"record\",\n"
                + "  \"name\": \"EquivalenceRecord\",\n"
                + "  \"fields\": [\n"
                + "    {\"name\": \"string_field\", \"type\": \"string\"},\n"
                + "    {\"name\": \"int_field\", \"type\": \"int\"},\n"
                + "    {\"name\": \"nested_field\", \"type\": {\n"
                + "      \"type\": \"record\",\n"
                + "      \"name\": \"NestedRecord\",\n"
                + "      \"fields\": [\n"
                + "        {\"name\": \"nested_string\", \"type\": \"string\"},\n"
                + "        {\"name\": \"nested_int\", \"type\": \"int\"}\n"
                + "      ]\n"
                + "    }},\n"
                + "    {\"name\": \"array_field\", \"type\": {\"type\": \"array\", \"items\": \"string\"}}\n"
                + "  ]\n"
                + "}";
        
        // Convert the Avro schema to a Connect schema
        Schema schema2 = schemaConverter.convertAvroToConnectSchema(avroSchema, "EquivalenceRecord");
        
        // Check that the schemas are equivalent
        assertTrue("Schemas should be equivalent", schemaConverter.areSchemasEquivalent(schema1, schema2));
        
        // Create a non-equivalent schema
        Schema schema3 = SchemaBuilder.struct()
                .name("EquivalenceRecord")
                .field("string_field", Schema.STRING_SCHEMA)
                .field("int_field", Schema.INT32_SCHEMA)
                .field("different_field", Schema.BOOLEAN_SCHEMA) // Different field
                .build();
        
        // Check that the schemas are not equivalent
        assertFalse("Schemas should not be equivalent", schemaConverter.areSchemasEquivalent(schema1, schema3));
    }

    /**
     * Test JSON schema equivalence checking.
     */
    @Test
    public void testJsonSchemaEquivalence() throws IOException {
        // Create a complex schema using the builder
        Schema schema1 = SchemaBuilder.struct()
                .name("JsonEquivalenceRecord")
                .field("string_field", Schema.STRING_SCHEMA)
                .field("number_field", Schema.FLOAT64_SCHEMA)
                .field("nested_object", SchemaBuilder.struct()
                        .field("nested_string", Schema.STRING_SCHEMA)
                        .field("nested_number", Schema.FLOAT64_SCHEMA)
                        .build())
                .field("array_field", SchemaBuilder.array(Schema.STRING_SCHEMA).build())
                .build();
        
        // Create an equivalent JSON schema
        String jsonSchema = "{"
                + "  \"type\": \"object\",\n"
                + "  \"properties\": {\n"
                + "    \"string_field\": { \"type\": \"string\" },\n"
                + "    \"number_field\": { \"type\": \"number\" },\n"
                + "    \"nested_object\": {\n"
                + "      \"type\": \"object\",\n"
                + "      \"properties\": {\n"
                + "        \"nested_string\": { \"type\": \"string\" },\n"
                + "        \"nested_number\": { \"type\": \"number\" }\n"
                + "      }\n"
                + "    },\n"
                + "    \"array_field\": {\n"
                + "      \"type\": \"array\",\n"
                + "      \"items\": { \"type\": \"string\" }\n"
                + "    }\n"
                + "  }\n"
                + "}";
        
        // Convert the JSON schema to a Connect schema
        Schema schema2 = schemaConverter.convertJsonToConnectSchema(jsonSchema, "JsonEquivalenceRecord");
        
        // Check that the schemas are equivalent
        assertTrue("Schemas should be equivalent", schemaConverter.areSchemasEquivalent(schema1, schema2));
        
        // Create a non-equivalent schema
        Schema schema3 = SchemaBuilder.struct()
                .name("JsonEquivalenceRecord")
                .field("string_field", Schema.STRING_SCHEMA)
                .field("number_field", Schema.FLOAT64_SCHEMA)
                .field("different_field", Schema.BOOLEAN_SCHEMA) // Different field
                .build();
        
        // Check that the schemas are not equivalent
        assertFalse("Schemas should not be equivalent", schemaConverter.areSchemasEquivalent(schema1, schema3));
    }
}
