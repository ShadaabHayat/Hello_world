import { KafkaAvroProducer } from './kafka_avro_producer.js';
import { KafkaJsonProducer } from './kafka_json_producer.js';
import { resource_device_schema, message as resource_device_message } from './schemas/resource_device_schema.js';
import { user_schema, message as user_message } from './schemas/user_schema.js';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.2/index.js';
import { config } from './config.js';

// Export options for k6
export const options = {
    vus: config.vus,
    duration: config.duration
};

const resourceTopicName = config.resourceDeviceTopicName + "-" + config.schemaType;
const userTopicName = config.userTopicName + "-" + config.schemaType;

// Factory function to create the appropriate producer based on schema type
function createProducer(schemaType) {
    return schemaType === 'json' ? new KafkaJsonProducer() : new KafkaAvroProducer();
}

// Create producer based on schema type
const producer = createProducer(config.schemaType);

export function setup() {
    // Initialize resource device schema
    const resourceSchemaManager = createProducer(config.schemaType);
    resourceSchemaManager.init(resourceTopicName, resource_device_schema);
    const resourceSchema = resourceSchemaManager.getSchemaObject();

    // Initialize user schema
    const userSchemaManager = createProducer(config.schemaType);
    userSchemaManager.init(userTopicName, user_schema);
    const userSchema = userSchemaManager.getSchemaObject();

    // Clean up schema managers after initialization
    resourceSchemaManager.cleanup();
    userSchemaManager.cleanup();

    return { resourceSchema, userSchema };
}

export default function (data) {
    // Produce messages using the appropriate schema type
    producer.produceMessage(resource_device_message, data.resourceSchema, resourceTopicName);
    producer.produceMessage(user_message, data.userSchema, userTopicName);
}

export function teardown() {
    if (producer) {
        producer.cleanup();
    }
}

export function handleSummary(data) {
    return {
        '/reports/summary.json': JSON.stringify(data),
        stdout: textSummary(data, { indent: " ", enableColors: true })
    };
}
