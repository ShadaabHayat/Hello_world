import { KafkaAvroProducer } from './kafka_avro_producer.js';
import { KafkaJsonProducer } from './kafka_json_producer.js';
import { resource_device_schema, message as resource_device_message } from './schemas/resource_device_schema.js';
import { user_schema, message as user_message } from './schemas/user_schema.js';
import { network_device_schema, message as network_device_message } from './schemas/network_device_schema.js';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.2/index.js';
import { config } from './config.js';

// Export options for k6
export const options = {
    vus: config.vus,
    duration: config.duration
};

const resourceTopicName = config.resourceDeviceTopicName + "-" + config.schemaType;
const userTopicName = config.userTopicName + "-" + config.schemaType;
const networkDeviceTopicName = config.networkDeviceTopicName + "-" + config.schemaType;

// Factory function to create the appropriate producer based on schema type
function createProducer(schemaType) {
    return schemaType === 'json' ? new KafkaJsonProducer() : new KafkaAvroProducer();
}

// Create producer based on schema type
const producer = createProducer(config.schemaType);

export function setup() {
    let resourceSchema, userSchema, networkDeviceSchema;

    // Initialize resource device schema if enabled
    if (config.produceResourceDevice) {
        const resourceSchemaManager = createProducer(config.schemaType);
        resourceSchemaManager.init(resourceTopicName, resource_device_schema);
        resourceSchema = resourceSchemaManager.getSchemaObject();
        resourceSchemaManager.cleanup();
        console.log(`Resource device schema initialized for topic: ${resourceTopicName}`);
    }

    // Initialize user schema if enabled
    if (config.produceUser) {
        const userSchemaManager = createProducer(config.schemaType);
        userSchemaManager.init(userTopicName, user_schema);
        userSchema = userSchemaManager.getSchemaObject();
        userSchemaManager.cleanup();
        console.log(`User schema initialized for topic: ${userTopicName}`);
    }

    // Initialize network device schema if enabled
    if (config.produceNetworkDevice) {
        const networkDeviceSchemaManager = createProducer(config.schemaType);
        networkDeviceSchemaManager.init(networkDeviceTopicName, network_device_schema);
        networkDeviceSchema = networkDeviceSchemaManager.getSchemaObject();
        networkDeviceSchemaManager.cleanup();
        console.log(`Network device schema initialized for topic: ${networkDeviceTopicName}`);
    }

    return { resourceSchema, userSchema, networkDeviceSchema };
}

export default function (data) {
    // Produce messages using the appropriate schema type based on flags
    if (config.produceResourceDevice && data.resourceSchema) {
        producer.produceMessage(resource_device_message, data.resourceSchema, resourceTopicName);
    }

    if (config.produceUser && data.userSchema) {
        producer.produceMessage(user_message, data.userSchema, userTopicName);
    }

    if (config.produceNetworkDevice && data.networkDeviceSchema) {
        producer.produceMessage(network_device_message, data.networkDeviceSchema, networkDeviceTopicName);
    }
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
