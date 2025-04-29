import { Writer, Connection, SchemaRegistry, SCHEMA_TYPE_JSON } from 'k6/x/kafka';
import { check } from 'k6';
import { config } from './config.js';

export class KafkaJsonProducer {
    constructor() {
        this.writer = new Writer({
            brokers: config.kafkaBrokers.split(','),
            autoCreateTopic: true
        });
        this.schemaRegistry = new SchemaRegistry({
            url: config.schemaRegistryUrl
        });
    }

    init(topicName, schema) {
        try {
            this.schema = schema;

            this.connection = new Connection({
                address: config.kafkaBrokers.split(',')[0],
            });

            this.connection.listTopics();
            console.log('Successfully connected to Kafka. Available topics:', this.connection.listTopics());

            // Convert Avro schema to JSON schema format
            const jsonSchema = this.convertAvroToJsonSchema(schema);

            // Use a different subject naming convention for JSON schemas
            // Append '-json' to avoid conflicts with Avro schemas
            this.valueSchemaObject = this.schemaRegistry.createSchema({
                subject: `${topicName}-value`,
                schema: jsonSchema,
                schemaType: SCHEMA_TYPE_JSON,
            });
            console.log('Successfully registered JSON schema with ID:', this.valueSchemaObject.id);

            // Create topic if it doesn't exist
            if (!this.connection.listTopics().includes(topicName)) {
                this.connection.createTopic({
                    topic: topicName,
                    numPartitions: config.partitionsCountForTopic,
                    replicationFactor: 1,
                });
                console.log(`Successfully created topic: ${topicName}`);
            } else {
                console.log(`Topic ${topicName} already exists`);
            }
        } catch (error) {
            console.error('Failed during initialization:', error);
            throw error;
        }
    }

    // Helper function to convert Avro schema to JSON schema
    convertAvroToJsonSchema(avroSchema) {
        // Parse the Avro schema string to an object
        const avroSchemaObj = JSON.parse(avroSchema);

        // Create a JSON schema structure
        const jsonSchema = {
            title: avroSchemaObj.name,
            type: "object",
            properties: {}
        };

        // Map Avro fields to JSON schema properties
        for (const field of avroSchemaObj.fields) {
            jsonSchema.properties[field.name] = this.processAvroField(field);

            // Add description if available
            if (field.doc) {
                jsonSchema.properties[field.name].description = field.doc;
            }
        }

        return JSON.stringify(jsonSchema);
    }

    // Process a single Avro field and convert it to JSON schema property
    processAvroField(field) {
        // Handle union types (e.g., ["null", "string"])
        if (Array.isArray(field.type)) {
            // If the field can be null, we need to handle it specially in JSON Schema
            if (field.type.includes("null")) {
                // Get all non-null types
                const nonNullTypes = field.type.filter(t => t !== "null");

                if (nonNullTypes.length === 1) {
                    // If there's only one non-null type, use it with nullable: true
                    return {
                        type: [this.processAvroType(nonNullTypes[0]).type, "null"]
                    };
                } else {
                    // For multiple types including null
                    return {
                        type: [...nonNullTypes.map(t => this.processAvroType(t).type), "null"]
                    };
                }
            } else {
                // For union types that don't include null
                return {
                    type: field.type.map(t => this.processAvroType(t).type)
                };
            }
        } else {
            // For simple types or complex types
            return this.processAvroType(field.type);
        }
    }

    // Process Avro type (which could be a string or an object)
    processAvroType(avroType) {
        // If it's a string, map it to JSON schema type
        if (typeof avroType === 'string') {
            const typeMapping = {
                'string': 'string',
                'int': 'integer',
                'long': 'integer',
                'float': 'number',
                'double': 'number',
                'boolean': 'boolean',
                'bytes': 'string',
                'null': 'null'
            };
            return { type: typeMapping[avroType] || 'string' };
        }

        // If it's an object, it's a complex type
        if (typeof avroType === 'object') {
            // Handle record type (nested object)
            if (avroType.type === 'record') {
                const properties = {};

                // Process each field in the record
                for (const field of avroType.fields) {
                    properties[field.name] = this.processAvroField(field);
                }

                return {
                    type: 'object',
                    properties: properties
                };
            }

            // Handle array type
            if (avroType.type === 'array') {
                return {
                    type: 'array',
                    items: this.processAvroType(avroType.items)
                };
            }

            // Handle map type
            if (avroType.type === 'map') {
                return {
                    type: 'object',
                    additionalProperties: this.processAvroType(avroType.values)
                };
            }

            // Handle enum type
            if (avroType.type === 'enum') {
                return {
                    type: 'string',
                    enum: avroType.symbols
                };
            }
        }

        // Default to string for unknown types
        return { type: 'string' };
    }

    // Helper function to map Avro types to JSON schema types
    mapAvroTypeToJsonType(avroType) {
        const typeMapping = {
            'string': 'string',
            'int': 'integer',
            'long': 'integer',
            'float': 'number',
            'double': 'number',
            'boolean': 'boolean',
            'bytes': 'string',
            'null': 'null'
        };

        return typeMapping[avroType] || 'string';
    }

    produceMessage(message, schema, topicName) {
        try {
            const serializedValue = this.schemaRegistry.serialize({
                data: message,
                schema: schema,
                schemaType: SCHEMA_TYPE_JSON,
            });

            this.writer.produce({
                messages: [{
                    value: serializedValue,
                    topic: topicName
                }]
            });

            check(this.writer, {
                'message sent': () => true
            });
        } catch (error) {
            console.error(`Error producing message to topic ${topicName}:`, error);

            // Provide more detailed error information
            if (error.toString().includes('schema')) {
                console.error('Schema-related error. Check schema compatibility and registry connection.');
            } else if (error.toString().includes('topic')) {
                console.error('Topic-related error. Check if topic exists and is accessible.');
            }

            check(null, {
                'message sent': () => false
            });
        }
    }

    getSchemaObject() {
        return this.valueSchemaObject;
    }

    cleanup() {
        try {
            if (this.writer) {
                this.writer.close();
            }
            if (this.connection) {
                this.connection.close();
            }
            console.log('Successfully closed Kafka connections');
        } catch (error) {
            console.error('Error closing Kafka connections:', error);
            throw error;
        }
    }
}
