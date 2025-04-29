package org.extremenetworks.com.schemaconverter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.confluent.connect.avro.AvroData;
import io.confluent.connect.avro.AvroDataConfig;
import org.apache.avro.Schema.Parser;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.List;

/**
 * Utility class for converting between different schema formats and Kafka Connect Schema.
 * Uses Confluent's libraries for Avro and JSON schema conversion.
 */
public class SchemaConverter {
    /**
     * Enum representing different schema types.
     */
    public enum SchemaType {
        JSON, AVRO, UNKNOWN
    }
    private static final Logger LOG = LoggerFactory.getLogger(SchemaConverter.class);
    private final AvroData avroData;
    private final ObjectMapper objectMapper;

    /**
     * Constructor for SchemaConverter.
     */
    public SchemaConverter() {
        objectMapper = new ObjectMapper();
        AvroDataConfig avroDataConfig = new AvroDataConfig.Builder()
                .with(AvroDataConfig.ENHANCED_AVRO_SCHEMA_SUPPORT_CONFIG, true)
                .with(AvroDataConfig.CONNECT_META_DATA_CONFIG, false)
                .build();
        avroData = new AvroData(avroDataConfig);
    }

    /**
     * Converts an Avro schema string to a Kafka Connect Schema.
     *
     * @param avroSchemaContent The Avro schema as a string
     * @param schemaName The name to give the schema
     * @return A Kafka Connect Schema object
     * @throws IOException If there's an error parsing the schema
     */
    public Schema convertAvroToConnectSchema(String avroSchemaContent, String schemaName) throws IOException {
        if (avroSchemaContent == null || avroSchemaContent.trim().isEmpty()) {
            throw new IOException("Cannot convert null or empty Avro schema content");
        }
        
        try {
            
            // Parse the Avro schema
            Parser parser = new Parser();
            org.apache.avro.Schema avroSchema = parser.parse(avroSchemaContent);
            
            // Handle complex union types
            if (avroSchema.getType() == org.apache.avro.Schema.Type.UNION) {
                LOG.info("Processing Avro schema with union type");
                return handleAvroUnionSchema(avroSchema, schemaName);
            }
            
            // Convert to Connect schema with enhanced configuration
            AvroDataConfig avroDataConfig = new AvroDataConfig.Builder()
                    .with(AvroDataConfig.ENHANCED_AVRO_SCHEMA_SUPPORT_CONFIG, true)
                    .with(AvroDataConfig.CONNECT_META_DATA_CONFIG, false)
                    .build();
            AvroData enhancedAvroData = new AvroData(avroDataConfig);
            Schema connectSchema = enhancedAvroData.toConnectSchema(avroSchema);
            
            // Validate the resulting schema
            if (connectSchema == null) {
                throw new IOException("AvroData converter returned null schema");
            }
            
            if (connectSchema.type() == Schema.Type.STRUCT && connectSchema.fields().isEmpty() && 
                    !avroSchema.getFields().isEmpty()) {
                LOG.warn("Avro schema conversion resulted in empty struct, but original had {} fields", 
                         avroSchema.getFields().size());
                // This is a sign that conversion failed to properly map fields
            }
            
            LOG.info("Converted Avro schema to Connect schema with {} fields", 
                     connectSchema.type() == Schema.Type.STRUCT ? connectSchema.fields().size() : 0);
            return connectSchema;
        } catch (org.apache.avro.SchemaParseException e) {
            LOG.error("Failed to parse Avro schema: {}", e.getMessage());
            throw new IOException("Failed to parse Avro schema: " + e.getMessage(), e);
        } catch (Exception e) {
            LOG.error("Failed to convert Avro schema to Connect schema: {}", e.getMessage());
            throw new IOException("Failed to convert Avro schema to Connect schema: " + e.getMessage(), e);
        }
    }

    /**
     * Converts a JSON schema string to a Kafka Connect Schema using Jackson.
     * 
     * @param jsonSchemaContent The JSON schema as a string
     * @param schemaName The name to give the schema
     * @return A Kafka Connect Schema object
     * @throws IOException If there's an error parsing the schema
     */
    public Schema convertJsonToConnectSchema(String jsonSchemaContent, String schemaName) throws IOException {
        if (jsonSchemaContent == null || jsonSchemaContent.trim().isEmpty()) {
            throw new IOException("Cannot convert null or empty JSON schema content");
        }
        
        try {
            // Parse the JSON schema using Jackson
            JsonNode rootNode = objectMapper.readTree(jsonSchemaContent);
            
            // Check if this is a schema with references that need to be resolved
            if (containsReferences(rootNode)) {
                LOG.info("JSON schema contains references, attempting to resolve");
                rootNode = resolveReferences(rootNode, jsonSchemaContent);
            }
            
            // Handle oneOf, anyOf, allOf constructs
            if (rootNode.has("oneOf") || rootNode.has("anyOf") || rootNode.has("allOf")) {
                LOG.info("JSON schema contains oneOf/anyOf/allOf construct");
                return handleJsonSchemaPolymorphism(rootNode, schemaName);
            }
            
            // Create a schema builder with the provided name
            SchemaBuilder builder = SchemaBuilder.struct();
            if (schemaName != null && !schemaName.isEmpty()) {
                builder.name(schemaName);
            }
            
            // Add schema version if available
            if (rootNode.has("$schema")) {
                String schemaVersion = rootNode.get("$schema").asText();
                builder.parameter("$schema", schemaVersion);
            }
            
            // Add schema description if available
            if (rootNode.has("description")) {
                String description = rootNode.get("description").asText();
                builder.doc(description);
            }
            
            // Get the properties node
            JsonNode propertiesNode = rootNode.get("properties");
            if (propertiesNode != null && propertiesNode.isObject()) {
                // Add each property as a field in the schema
                Iterator<Map.Entry<String, JsonNode>> fields = propertiesNode.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> field = fields.next();
                    String fieldName = field.getKey();
                    JsonNode fieldSchema = field.getValue();
                    
                    // Check if this field is required
                    boolean isRequired = isFieldRequired(fieldName, rootNode);
                    
                    // Add the field with the appropriate type
                    Schema fieldConnectSchema = convertJsonNodeToConnectSchema(fieldSchema);
                    
                    // If field has a description, add it as a doc
                    if (fieldSchema.has("description")) {
                        String fieldDescription = fieldSchema.get("description").asText();
                        fieldConnectSchema = SchemaBuilder.type(fieldConnectSchema.type())
                                .doc(fieldDescription)
                                .build();
                    }
                    
                    // Make the field required if specified
                    if (isRequired) {
                        builder.field(fieldName, fieldConnectSchema);
                    } else {
                        // Ensure the field is optional
                        Schema optionalSchema = fieldConnectSchema.isOptional() ? 
                                fieldConnectSchema : 
                                SchemaBuilder.type(fieldConnectSchema.type())
                                        .optional()
                                        .build();
                        builder.field(fieldName, optionalSchema);
                    }
                }
            }
            
            Schema connectSchema = builder.build();
            
            // Validate the resulting schema
            if (connectSchema.type() == Schema.Type.STRUCT && connectSchema.fields().isEmpty() && 
                    propertiesNode != null && propertiesNode.size() > 0) {
                LOG.warn("JSON schema conversion resulted in empty struct, but original had {} properties", 
                         propertiesNode.size());
                // This is a sign that conversion failed to properly map fields
            }
            
            LOG.info("Converted JSON schema to Connect schema with {} fields and name: {}", 
                    connectSchema.fields().size(),
                    connectSchema.name() != null ? connectSchema.name() : "unnamed");
            
            return connectSchema;
        } catch (Exception e) {
            LOG.error("Failed to convert JSON schema to Connect schema: {}", e.getMessage());
            throw new IOException("Failed to convert JSON schema to Connect schema: " + e.getMessage(), e);
        }
    }
    
    /**
     * Converts a JSON node representing a schema to a Kafka Connect Schema.
     * 
     * @param schemaNode The JSON node representing a schema
     * @return A Kafka Connect Schema object
     */
    /**
     * Handles Avro union schema conversion to Kafka Connect Schema.
     * Focuses on the common nullable union pattern supported by Confluent Schema Registry.
     *
     * @param unionSchema The Avro union schema
     * @param schemaName The name to give the schema
     * @return A Kafka Connect Schema object
     */
    private Schema handleAvroUnionSchema(org.apache.avro.Schema unionSchema, String schemaName) {
        // Get the types in the union
        List<org.apache.avro.Schema> types = unionSchema.getTypes();
        
        // Check for the common "nullable" pattern: ["null", "type"]
        // This is the primary union pattern supported by Confluent Schema Registry
        if (types.size() == 2 && types.get(0).getType() == org.apache.avro.Schema.Type.NULL) {
            // This is a nullable field, convert the non-null type
            Schema valueSchema = avroData.toConnectSchema(types.get(1));
            return SchemaBuilder.type(valueSchema.type())
                    .optional()
                    .defaultValue(null)
                    .build();
        } else if (types.size() == 2 && types.get(1).getType() == org.apache.avro.Schema.Type.NULL) {
            // Handle case where null is the second type
            Schema valueSchema = avroData.toConnectSchema(types.get(0));
            return SchemaBuilder.type(valueSchema.type())
                    .optional()
                    .defaultValue(null)
                    .build();
        }
        
        // For other union types, Confluent Schema Registry has limited support
        // The safest approach is to convert to STRING as a fallback
        LOG.warn("Complex Avro union types beyond nullable fields are not fully supported by Confluent Schema Registry: {}. "
                + "Converting to STRING schema as fallback.", types);
        return Schema.OPTIONAL_STRING_SCHEMA;
    }
    
    /**
     * Checks if a JSON schema contains references that need to be resolved.
     * 
     * @param schemaNode The JSON schema node
     * @return true if the schema contains references
     */
    private boolean containsReferences(JsonNode schemaNode) {
        if (schemaNode == null) {
            return false;
        }
        
        // Check if this node has a $ref
        if (schemaNode.has("$ref")) {
            return true;
        }
        
        // Recursively check all object fields
        if (schemaNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = schemaNode.fields();
            while (fields.hasNext()) {
                if (containsReferences(fields.next().getValue())) {
                    return true;
                }
            }
        }
        
        // Check array elements
        if (schemaNode.isArray()) {
            for (JsonNode element : schemaNode) {
                if (containsReferences(element)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Resolves references in a JSON schema.
     * This is a simplified implementation that only handles local references.
     * 
     * @param schemaNode The JSON schema node
     * @param fullSchema The full schema content for resolving references
     * @return The schema with resolved references
     */
    private JsonNode resolveReferences(JsonNode schemaNode, String fullSchema) {
        // This is a simplified implementation
        // For a production-ready solution, consider using a dedicated JSON Schema library
        LOG.warn("Reference resolution is limited to basic local references");
        
        try {
            // For now, just return the original schema
            // A full implementation would traverse the schema and resolve all $ref nodes
            return schemaNode;
        } catch (Exception e) {
            LOG.error("Failed to resolve references: {}", e.getMessage());
            return schemaNode;
        }
    }
    
    /**
     * Handles JSON Schema polymorphism constructs (oneOf, anyOf, allOf).
     * 
     * @param schemaNode The JSON schema node containing polymorphic definitions
     * @param schemaName The name to give the schema
     * @return A Kafka Connect Schema
     */
    private Schema handleJsonSchemaPolymorphism(JsonNode schemaNode, String schemaName) {
        // Create a schema builder with the provided name
        SchemaBuilder builder = SchemaBuilder.struct();
        if (schemaName != null && !schemaName.isEmpty()) {
            builder.name(schemaName);
        }
        
        // Handle oneOf - we'll create a struct with fields from all schemas
        if (schemaNode.has("oneOf") && schemaNode.get("oneOf").isArray()) {
            LOG.info("Processing oneOf construct in JSON schema");
            JsonNode oneOfNode = schemaNode.get("oneOf");
            
            // For each schema in oneOf, extract its properties
            for (JsonNode subSchema : oneOfNode) {
                if (subSchema.has("properties") && subSchema.get("properties").isObject()) {
                    JsonNode propertiesNode = subSchema.get("properties");
                    Iterator<Map.Entry<String, JsonNode>> fields = propertiesNode.fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> field = fields.next();
                        String fieldName = field.getKey();
                        JsonNode fieldSchema = field.getValue();
                        
                        // Only add if not already added
                        if (builder.field(fieldName) == null) {
                            builder.field(fieldName, convertJsonNodeToConnectSchema(fieldSchema));
                        }
                    }
                }
            }
        }
        
        // Handle anyOf - similar to oneOf
        if (schemaNode.has("anyOf") && schemaNode.get("anyOf").isArray()) {
            LOG.info("Processing anyOf construct in JSON schema");
            JsonNode anyOfNode = schemaNode.get("anyOf");
            
            // Similar to oneOf handling
            for (JsonNode subSchema : anyOfNode) {
                if (subSchema.has("properties") && subSchema.get("properties").isObject()) {
                    JsonNode propertiesNode = subSchema.get("properties");
                    Iterator<Map.Entry<String, JsonNode>> fields = propertiesNode.fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> field = fields.next();
                        String fieldName = field.getKey();
                        JsonNode fieldSchema = field.getValue();
                        
                        // Only add if not already added
                        if (builder.field(fieldName) == null) {
                            builder.field(fieldName, convertJsonNodeToConnectSchema(fieldSchema));
                        }
                    }
                }
            }
        }
        
        // Handle allOf - combine all schemas
        if (schemaNode.has("allOf") && schemaNode.get("allOf").isArray()) {
            LOG.info("Processing allOf construct in JSON schema");
            JsonNode allOfNode = schemaNode.get("allOf");
            
            // For allOf, all schemas must be satisfied, so we combine all properties
            for (JsonNode subSchema : allOfNode) {
                if (subSchema.has("properties") && subSchema.get("properties").isObject()) {
                    JsonNode propertiesNode = subSchema.get("properties");
                    Iterator<Map.Entry<String, JsonNode>> fields = propertiesNode.fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> field = fields.next();
                        String fieldName = field.getKey();
                        JsonNode fieldSchema = field.getValue();
                        
                        // For allOf, we might override existing fields with more specific definitions
                        builder.field(fieldName, convertJsonNodeToConnectSchema(fieldSchema));
                    }
                }
            }
        }
        
        return builder.build();
    }
    
    /**
     * Checks if a field is required in the JSON schema.
     * 
     * @param fieldName The field name to check
     * @param schemaNode The JSON schema node
     * @return true if the field is required
     */
    private boolean isFieldRequired(String fieldName, JsonNode schemaNode) {
        if (schemaNode.has("required") && schemaNode.get("required").isArray()) {
            JsonNode requiredNode = schemaNode.get("required");
            for (JsonNode requiredField : requiredNode) {
                if (requiredField.isTextual() && requiredField.asText().equals(fieldName)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private Schema convertJsonNodeToConnectSchema(JsonNode schemaNode) {
        if (schemaNode == null) {
            return Schema.OPTIONAL_STRING_SCHEMA;
        }
        
        // Handle $ref if present
        if (schemaNode.has("$ref")) {
            LOG.warn("$ref encountered but reference resolution is limited. Using STRING schema as fallback.");
            return Schema.OPTIONAL_STRING_SCHEMA;
        }
        
        // Handle oneOf, anyOf, allOf
        if (schemaNode.has("oneOf") || schemaNode.has("anyOf") || schemaNode.has("allOf")) {
            LOG.info("Nested polymorphic schema encountered");
            return handleJsonSchemaPolymorphism(schemaNode, null);
        }
        
        // Get the type from the schema node
        String type = schemaNode.has("type") ? schemaNode.get("type").asText() : "string";
        
        // Handle array of types (e.g., ["string", "null"])
        if (schemaNode.has("type") && schemaNode.get("type").isArray()) {
            LOG.info("Multiple types specified in JSON schema");
            // Check if this is a nullable type
            boolean isNullable = false;
            String primaryType = "string";
            
            for (JsonNode typeNode : schemaNode.get("type")) {
                if (typeNode.asText().equals("null")) {
                    isNullable = true;
                } else {
                    primaryType = typeNode.asText();
                }
            }
            
            type = primaryType;
        }
        
        switch (type) {
            case "object":
                // Handle object type (nested struct)
                SchemaBuilder builder = SchemaBuilder.struct().optional();
                
                // Add description if available
                if (schemaNode.has("description")) {
                    builder.doc(schemaNode.get("description").asText());
                }
                
                JsonNode propertiesNode = schemaNode.get("properties");
                if (propertiesNode != null && propertiesNode.isObject()) {
                    Iterator<Map.Entry<String, JsonNode>> fields = propertiesNode.fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> field = fields.next();
                        String fieldName = field.getKey();
                        JsonNode fieldSchema = field.getValue();
                        
                        // Check if this field is required
                        boolean isRequired = isFieldRequired(fieldName, schemaNode);
                        Schema fieldConnectSchema = convertJsonNodeToConnectSchema(fieldSchema);
                        
                        if (isRequired && fieldConnectSchema.isOptional()) {
                            // Make required
                            fieldConnectSchema = SchemaBuilder.type(fieldConnectSchema.type()).build();
                        }
                        
                        builder.field(fieldName, fieldConnectSchema);
                    }
                }
                
                return builder.build();
                
            case "array":
                // Handle array type
                JsonNode itemsNode = schemaNode.get("items");
                if (itemsNode != null) {
                    Schema itemSchema = convertJsonNodeToConnectSchema(itemsNode);
                    return SchemaBuilder.array(itemSchema).optional().build();
                } else {
                    return SchemaBuilder.array(Schema.OPTIONAL_STRING_SCHEMA).optional().build();
                }
                
            case "integer":
                // Check for format
                if (schemaNode.has("format")) {
                    String format = schemaNode.get("format").asText();
                    if ("int64".equals(format)) {
                        return Schema.OPTIONAL_INT64_SCHEMA;
                    }
                }
                return Schema.OPTIONAL_INT32_SCHEMA;
                
            case "number":
                // Check for format
                if (schemaNode.has("format")) {
                    String format = schemaNode.get("format").asText();
                    if ("float".equals(format)) {
                        return Schema.OPTIONAL_FLOAT32_SCHEMA;
                    }
                }
                return Schema.OPTIONAL_FLOAT64_SCHEMA;
                
            case "boolean":
                return Schema.OPTIONAL_BOOLEAN_SCHEMA;
                
            case "string":
                // Handle special string formats
                if (schemaNode.has("format")) {
                    String format = schemaNode.get("format").asText();
                    if ("date".equals(format) || "date-time".equals(format)) {
                        return Schema.OPTIONAL_STRING_SCHEMA; // Could use Timestamp in some cases
                    } else if ("byte".equals(format)) {
                        return Schema.OPTIONAL_BYTES_SCHEMA;
                    }
                }
                return Schema.OPTIONAL_STRING_SCHEMA;
                
            default: // any other type
                return Schema.OPTIONAL_STRING_SCHEMA;
        }
    }

    /**
     * Checks if a schema content is equivalent to a given Schema object.
     * 
     * @param schemaContent The schema content as a string
     * @param schema The Schema object to compare against
     * @return true if they are equivalent, false otherwise
     */

    public boolean isSchemaEquivalent(String schemaContent, Schema schema) {
        if (schemaContent == null || schema == null) {
            return false;
        }
        
        try {
            // Try to convert the schema content to a Connect schema based on the schema name
            // This assumes schema name contains information about the schema type
            Schema convertedSchema;
            
            // Simple check for schema type based on name suffix
            if (schema.name() != null && schema.name().toLowerCase().endsWith("json")) {
                convertedSchema = convertJsonToConnectSchema(schemaContent, schema.name());
            } else {
                convertedSchema = convertAvroToConnectSchema(schemaContent, schema.name());
            }
            
            // Compare the converted schema with the provided schema
            return areSchemasEquivalent(convertedSchema, schema);
        } catch (Exception e) {
            LOG.error("Error comparing schemas: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Compares two Schema objects to check if they are equivalent.
     * 
     * @param schema1 First schema to compare
     * @param schema2 Second schema to compare
     * @return true if the schemas are equivalent, false otherwise
     */
    public boolean areSchemasEquivalent(Schema schema1, Schema schema2) {
        if (schema1 == null || schema2 == null) {
            return schema1 == schema2;
        }
        
        // Check if the schema types match
        if (schema1.type() != schema2.type()) {
            return false;
        }
        
        // For struct types, compare the fields
        if (schema1.type() == Schema.Type.STRUCT) {
            Map<String, String> fields1 = extractFieldsFromSchema(schema1);
            Map<String, String> fields2 = extractFieldsFromSchema(schema2);
            
            return compareFieldMaps(fields1, fields2);
        }
        
        // For other types, just compare the types
        return true;
    }
    
    /**
     * Extracts field information from a Schema object.
     * 
     * @param schema The Schema object to extract fields from
     * @return A map of field names to type strings
     */
    private Map<String, String> extractFieldsFromSchema(Schema schema) {
        Map<String, String> fields = new HashMap<>();
        if (schema != null && schema.type() == Schema.Type.STRUCT) {
            for (org.apache.kafka.connect.data.Field field : schema.fields()) {
                fields.put(field.name(), field.schema().type().toString());
            }
        }
        return fields;
    }
    
    /**
     * Compares two field maps for equivalence.
     * 
     * @param map1 First map of field names to types
     * @param map2 Second map of field names to types
     * @return true if the maps are equivalent, false otherwise
     */
    private boolean compareFieldMaps(Map<String, String> map1, Map<String, String> map2) {
        // Check if they have the same number of fields
        if (map1.size() != map2.size()) {
            return false;
        }
        
        // Check if all field names match and types are compatible
        for (Map.Entry<String, String> entry : map1.entrySet()) {
            String fieldName = entry.getKey();
            String type1 = entry.getValue();
            
            // Check if field exists in map2
            if (!map2.containsKey(fieldName)) {
                return false;
            }
            
            // Get the type from map2
            String type2 = map2.get(fieldName);
            
            // For MaskCustomFields SMT, we need to be more flexible with types
            // since it can convert between types (especially in hash mode)
            if (!areTypesCompatible(type1, type2)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Checks if two schema types are compatible.
     * This is more flexible than strict equality to support the MaskCustomFields SMT.
     * 
     * @param type1 First type
     * @param type2 Second type
     * @return true if types are compatible
     */
    private boolean areTypesCompatible(String type1, String type2) {
        // If types are exactly the same, they're compatible
        if (type1.equals(type2)) {
            return true;
        }
        
        // For numeric types, consider them compatible with each other
        if ((type1.equals("INT32") || type1.equals("INT64") || type1.equals("FLOAT32") || type1.equals("FLOAT64")) &&
            (type2.equals("INT32") || type2.equals("INT64") || type2.equals("FLOAT32") || type2.equals("FLOAT64"))) {
            return true;
        }
        
        // When using hash mode in MaskCustomFields, everything can be converted to STRING
        if (type2.equals("STRING")) {
            return true;
        }
        
        return false;
    }
}
