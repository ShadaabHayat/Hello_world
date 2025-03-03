FROM maven:3.8-openjdk-11 AS builder

COPY ./custom-smt /app/transformations
WORKDIR /app/transformations

RUN mvn clean package

FROM confluentinc/cp-kafka-connect:7.7.1

RUN confluent-hub install --no-prompt confluentinc/kafka-connect-s3:10.3.0

COPY ./plugins/connect-fieldandtime-partitioner-1.1.0-SNAPSHOT.jar /usr/share/confluent-hub-components/confluentinc-kafka-connect-s3/lib/

COPY --from=builder /app/transformations/target/custom-smt-1.0-SNAPSHOT.jar /usr/share/java/
