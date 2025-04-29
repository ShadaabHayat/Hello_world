from test_template import assertion_template

def test_smt_whitelister_avro():
    whitelister_template(
        record_count=10
    )

def whitelister_template(record_count, schema_type="avro"):
    schema_suffix = ".avsc"

    override_sink_config = {
        "transforms": "Whitelist, InsertRDCInfo,InsertEnvInfo,InsertTimeStamp,tsFormat",

        "transforms.InsertEnvInfo.static.field": "env",
        "transforms.InsertEnvInfo.static.value": "e2e-test",
        "transforms.InsertEnvInfo.type": "org.apache.kafka.connect.transforms.InsertField$Value",

        "transforms.InsertRDCInfo.static.field": "rdc",
        "transforms.InsertRDCInfo.static.value": "minikube",
        "transforms.InsertRDCInfo.type": "org.apache.kafka.connect.transforms.InsertField$Value",

        "transforms.InsertTimeStamp.timestamp.field": "last_modified_at",
        "transforms.InsertTimeStamp.type": "org.apache.kafka.connect.transforms.InsertField$Value",

        "transforms.tsFormat.field": "last_modified_at",
        "transforms.tsFormat.format": "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "transforms.tsFormat.target.type": "string",
        "transforms.tsFormat.type": "org.apache.kafka.connect.transforms.TimestampConverter$Value",

        "transforms.Whitelist.type": "org.extremenetworks.com.Whitelister$Value",
        "transforms.Whitelist.schema.directory": '/opt/kafka/test-schemas/',
        "transforms.Whitelist.schema.type": schema_type,
        "transforms.Whitelist.schema.suffix": schema_suffix
    }

    expected_fields = ["id", "num1", "nest1", "__op", "__ts_ms", "__deleted", "rdc", "env", "last_modified_at"]

    assertion_template(
        record_count=record_count,
        op="INSERT",
        expected_fields=expected_fields,
        override_sink_config=override_sink_config
    )
