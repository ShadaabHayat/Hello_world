import { Writer, Connection, SchemaRegistry, SCHEMA_TYPE_AVRO } from 'k6/x/kafka';
import { check } from 'k6';

export const config = {
    kafkaBrokers: __ENV.KAFKA_BROKERS || 'localhost:9092',
    schemaRegistryUrl: __ENV.SCHEMA_REGISTRY_URL || 'http://localhost:8081/',
    vus: parseInt(__ENV.K6_VUS || '2'),
    duration: __ENV.K6_DURATION || '2s',
    partitionsCountForTopic: parseInt(__ENV.PARTITIONS_COUNT_FOR_TOPIC || '4'),
};

export const options = {
    vus: config.vus,
    duration: config.duration,
    thresholds: {
        'checks': ['rate==1.0'],
    },
};


export class KafkaProducer {
    constructor(schema) {
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

            this.valueSchemaObject = this.schemaRegistry.createSchema({
                subject: `${topicName}-value`,
                schema: this.schema,
                schemaType: SCHEMA_TYPE_AVRO,
            });
            console.log('Successfully registered schema with ID:', this.valueSchemaObject.id);


            this.connection.createTopic({
                topic: topicName,
                numPartitions: config.partitionsCountForTopic,
                replicationFactor: 1,
            });
            console.log('Successfully created/verified topic');
        } catch (error) {
            console.error('Failed during initialization:', error);
            throw error;
        }
    }

    produceMessage(message, schema, topicName) {
        try {
            const serializedValue = this.schemaRegistry.serialize({
                data: message,
                schema: schema,
                schemaType: SCHEMA_TYPE_AVRO,
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
            console.error('Error producing message:', error);
            check(null, {
                'message sent': () => false
            });
        }
    }

    getSchemaObject(){
        return this.valueSchemaObject;
    }

    cleanup() {
        try {
            if (this.writer){
                this.writer.close();
            }
            if (this.connection){
                this.connection.close();
            }
            console.log('Successfully closed Kafka connections');
        } catch (error) {
            console.error('Error closing Kafka connections:', error);
            throw error;
        }
    }
}
