// Common configuration for Kafka load testing
export const config = {
    kafkaBrokers: __ENV.KAFKA_BROKERS || 'localhost:9092',
    schemaRegistryUrl: __ENV.SCHEMA_REGISTRY_URL || 'http://localhost:8081/',
    vus: parseInt(__ENV.K6_VUS || '2'),
    duration: __ENV.K6_DURATION || '2s',
    partitionsCountForTopic: parseInt(__ENV.PARTITIONS_COUNT_FOR_TOPIC || '4'),
    schemaType: (__ENV.SCHEMA_TYPE || 'avro').toLowerCase(),
    resourceDeviceTopicName: __ENV.RESOURCE_DEVICE_TOPIC_NAME || 'aicore-resource-device',
    userTopicName: __ENV.USER_TOPIC_NAME || 'aicore-users-topic',
    networkDeviceTopicName: __ENV.NETWORK_DEVICE_TOPIC_NAME || 'aicore-network-device',
    // Flags to control whether to produce data for each topic
    produceResourceDevice: (__ENV.PRODUCE_RESOURCE_DEVICE || 'true').toLowerCase() === 'true',
    produceUser: (__ENV.PRODUCE_USER || 'true').toLowerCase() === 'true',
    produceNetworkDevice: (__ENV.PRODUCE_NETWORK_DEVICE || 'true').toLowerCase() === 'true'
};
