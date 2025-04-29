package org.extremenetworks.com.schemaconverter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.List;

import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.junit.Before;
import org.junit.Test;

public class SchemaConverterTest {
    
    private SchemaConverter schemaConverter;
    
    @Before
    public void setUp() {
        schemaConverter = new SchemaConverter();
    }
    
    @Test
    public void testConvertAvroToConnectSchema() throws IOException {
        String avroSchema = "{\n" +
                "  \"type\": \"record\",\n" +
                "  \"name\": \"TestRecord\",\n" +
                "  \"fields\": [\n" +
                "    {\"name\": \"stringField\", \"type\": \"string\"},\n" +
                "    {\"name\": \"intField\", \"type\": \"int\"},\n" +
                "    {\"name\": \"booleanField\", \"type\": \"boolean\"},\n" +
                "    {\"name\": \"doubleField\", \"type\": \"double\"}\n" +
                "  ]\n" +
                "}";
        
        Schema schema = schemaConverter.convertAvroToConnectSchema(avroSchema, "TestRecord");
        
        assertNotNull("Schema should not be null", schema);
        assertEquals("Schema should be of type STRUCT", Schema.Type.STRUCT, schema.type());
        assertEquals("Schema should have 4 fields", 4, schema.fields().size());
        
        List<Field> fields = schema.fields();
        assertEquals("Field name should match", "stringField", fields.get(0).name());
        assertEquals("Field type should match", Schema.Type.STRING, fields.get(0).schema().type());
        
        assertEquals("Field name should match", "intField", fields.get(1).name());
        assertEquals("Field type should match", Schema.Type.INT32, fields.get(1).schema().type());
        
        assertEquals("Field name should match", "booleanField", fields.get(2).name());
        assertEquals("Field type should match", Schema.Type.BOOLEAN, fields.get(2).schema().type());
        
        assertEquals("Field name should match", "doubleField", fields.get(3).name());
        assertEquals("Field type should match", Schema.Type.FLOAT64, fields.get(3).schema().type());
    }
    
    @Test
    public void testConvertJsonToConnectSchema() throws IOException {
        String jsonSchema = "{\n" +
                "  \"type\": \"object\",\n" +
                "  \"properties\": {\n" +
                "    \"stringField\": {\"type\": \"string\"},\n" +
                "    \"integerField\": {\"type\": \"integer\"},\n" +
                "    \"booleanField\": {\"type\": \"boolean\"},\n" +
                "    \"numberField\": {\"type\": \"number\"}\n" +
                "  }\n" +
                "}";
        
        Schema schema = schemaConverter.convertJsonToConnectSchema(jsonSchema, "TestRecord");
        
        assertNotNull("Schema should not be null", schema);
        assertEquals("Schema should be of type STRUCT", Schema.Type.STRUCT, schema.type());
        assertEquals("Schema should have 4 fields", 4, schema.fields().size());
        
        List<Field> fields = schema.fields();
        assertEquals("Field name should match", "stringField", fields.get(0).name());
        assertEquals("Field type should match", Schema.Type.STRING, fields.get(0).schema().type());
        
        assertEquals("Field name should match", "integerField", fields.get(1).name());
        assertEquals("Field type should match", Schema.Type.INT32, fields.get(1).schema().type());
        
        assertEquals("Field name should match", "booleanField", fields.get(2).name());
        assertEquals("Field type should match", Schema.Type.BOOLEAN, fields.get(2).schema().type());
        
        assertEquals("Field name should match", "numberField", fields.get(3).name());
        assertEquals("Field type should match", Schema.Type.FLOAT64, fields.get(3).schema().type());
    }
    
    @Test
    public void testConvertJsonWithNestedObjectToConnectSchema() throws IOException {
        String jsonSchema = "{\n" +
                "  \"type\": \"object\",\n" +
                "  \"properties\": {\n" +
                "    \"stringField\": {\"type\": \"string\"},\n" +
                "    \"nestedObject\": {\n" +
                "      \"type\": \"object\",\n" +
                "      \"properties\": {\n" +
                "        \"nestedString\": {\"type\": \"string\"},\n" +
                "        \"nestedInteger\": {\"type\": \"integer\"}\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}";
        
        Schema schema = schemaConverter.convertJsonToConnectSchema(jsonSchema, "TestRecord");
        
        assertNotNull("Schema should not be null", schema);
        assertEquals("Schema should be of type STRUCT", Schema.Type.STRUCT, schema.type());
        assertEquals("Schema should have 2 fields", 2, schema.fields().size());
        
        Field nestedField = schema.field("nestedObject");
        assertNotNull("Nested field should exist", nestedField);
        assertEquals("Nested field should be of type STRUCT", Schema.Type.STRUCT, nestedField.schema().type());
        
        List<Field> nestedFields = nestedField.schema().fields();
        assertEquals("Nested schema should have 2 fields", 2, nestedFields.size());
        assertEquals("Nested field name should match", "nestedString", nestedFields.get(0).name());
        assertEquals("Nested field type should match", Schema.Type.STRING, nestedFields.get(0).schema().type());
        assertEquals("Nested field name should match", "nestedInteger", nestedFields.get(1).name());
        assertEquals("Nested field type should match", Schema.Type.INT32, nestedFields.get(1).schema().type());
    }
    
    @Test
    public void testConvertJsonWithNullableFieldsToConnectSchema() throws IOException {
        String jsonSchema = "{\n" +
                "  \"type\": \"object\",\n" +
                "  \"properties\": {\n" +
                "    \"requiredString\": {\"type\": \"string\"},\n" +
                "    \"nullableString\": {\"type\": [\"null\", \"string\"]}\n" +
                "  }\n" +
                "}";
        
        Schema schema = schemaConverter.convertJsonToConnectSchema(jsonSchema, "TestRecord");
        
        assertNotNull("Schema should not be null", schema);
        assertEquals("Schema should be of type STRUCT", Schema.Type.STRUCT, schema.type());
        assertEquals("Schema should have 2 fields", 2, schema.fields().size());
        
        Field requiredField = schema.field("requiredString");
        Field nullableField = schema.field("nullableString");
        
        assertNotNull("Required field should exist", requiredField);
        assertNotNull("Nullable field should exist", nullableField);
        
        assertEquals("Required field should be of type STRING", Schema.Type.STRING, requiredField.schema().type());
        assertEquals("Nullable field should be of type STRING", Schema.Type.STRING, nullableField.schema().type());
        
        assertTrue("Nullable field should be optional", nullableField.schema().isOptional());
    }
    
    @Test
    public void testIsSchemaEquivalent() throws IOException {
        // Create a schema using the builder
        Schema schema = SchemaBuilder.struct()
                .name("TestRecord")
                .field("stringField", Schema.STRING_SCHEMA)
                .field("intField", Schema.INT32_SCHEMA)
                .build();
        
        // Convert the schema to Avro format
        String avroSchema = "{\n" +
                "  \"type\": \"record\",\n" +
                "  \"name\": \"TestRecord\",\n" +
                "  \"fields\": [\n" +
                "    {\"name\": \"stringField\", \"type\": \"string\"},\n" +
                "    {\"name\": \"intField\", \"type\": \"int\"}\n" +
                "  ]\n" +
                "}";
        
        // Convert the Avro schema back to a Connect schema
        Schema convertedSchema = schemaConverter.convertAvroToConnectSchema(avroSchema, "TestRecord");
        
        // Check that the fields match between the two schemas
        assertEquals("Schemas should have the same number of fields", 
                schema.fields().size(), convertedSchema.fields().size());
        
        for (int i = 0; i < schema.fields().size(); i++) {
            Field field1 = schema.fields().get(i);
            Field field2 = convertedSchema.fields().get(i);
            
            assertEquals("Field names should match", field1.name(), field2.name());
            assertEquals("Field types should match", field1.schema().type(), field2.schema().type());
        }
    }
}
