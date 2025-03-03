import { KafkaProducer, options } from './kafka_producer.js';
import { resource_device_schema, message as resource_device_message } from './schemas/resource_device_schema.js';
import { user_schema, message as user_message } from './schemas/user_schema.js';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.2/index.js';

const resourceTopicName = __ENV.RESOURCE_DEVICE_TOPIC_NAME || 'aicore-resource-device';
const userTopicName = __ENV.USER_TOPIC_NAME || 'aicore-users-topic';
const producer = new KafkaProducer();

export function setup(){
    const resourceSchemaManager = new KafkaProducer(resource_device_schema);
    resourceSchemaManager.init(resourceTopicName, resource_device_schema)
    const resourceSchema = resourceSchemaManager.getSchemaObject()

    const userSchemaManager = new KafkaProducer(user_schema);
    userSchemaManager.init(userTopicName, user_schema)
    const userSchema = userSchemaManager.getSchemaObject()

    resourceSchemaManager.cleanup()
    userSchemaManager.cleanup()

    return {resourceSchema, userSchema}
}

export { options };

export default function (data) {
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
      '/reports/summary.json': JSON.stringify(data), //the default data object
      stdout: textSummary(data, { indent: " ", enableColors: true })
    };
  }
