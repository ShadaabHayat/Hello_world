import json
import pytest

import reader
import params
from test_template import assertion_template, get_nested_values


def assert_range_check(df, id_field: str, id_min: int, id_max: int):
    """Asserts that the values of the specified field fall within the given range."""
    for row in df.iter_rows(named=True):
        for value in get_nested_values(row, id_field):
            assert id_min <= value <= id_max, (
                f"Expected {id_field} to be between {id_min} and {id_max}, got {value}"
            )


def test_smt_RangeCheckOnFieldValues():
    """Test case for the RangeCheck SMT on field values."""
    
    # Record counts
    record_count = 50
    expected_record_count = 40
    expected_dlq_record_count = record_count - expected_record_count

    # Expected fields in the output
    expected_fields = [
        "id", "num1", "str1", "ssn", "nest1",
        "rdc", "env", "last_modified_at", "__op", "__ts_ms", "__deleted"
    ]

    # Define range check parameters
    id_field = "id"
    id_min = 10
    id_max = 100

    # "Between" config for the range check
    between_config = json.dumps({id_field: [[id_min, id_max]]})

    # Kafka SMT transformation configuration
    override_sink_config = {
        "transforms": "InsertRDCInfo,InsertEnvInfo,InsertTimeStamp,tsFormat,RangeCheck",

        # Insert Environment Info
        "transforms.InsertEnvInfo.static.field": "env",
        "transforms.InsertEnvInfo.static.value": "e2e-test",
        "transforms.InsertEnvInfo.type": "org.apache.kafka.connect.transforms.InsertField$Value",

        # Insert RDC Info
        "transforms.InsertRDCInfo.static.field": "rdc",
        "transforms.InsertRDCInfo.static.value": "minikube",
        "transforms.InsertRDCInfo.type": "org.apache.kafka.connect.transforms.InsertField$Value",

        # Insert Timestamp
        "transforms.InsertTimeStamp.timestamp.field": "last_modified_at",
        "transforms.InsertTimeStamp.type": "org.apache.kafka.connect.transforms.InsertField$Value",

        # Timestamp Format Conversion
        "transforms.tsFormat.field": "last_modified_at",
        "transforms.tsFormat.format": "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "transforms.tsFormat.target.type": "string",
        "transforms.tsFormat.type": "org.apache.kafka.connect.transforms.TimestampConverter$Value",

        # Range Check
        "transforms.RangeCheck.type": "org.extremenetworks.com.RangeCheckOnFieldValues",
        "transforms.RangeCheck.topic.name": "aicore.public." + params.sink_config["table"],
        "transforms.RangeCheck.fields.Between": between_config
    }

    # Run the assertion template
    assertion_template(
        record_count=record_count,
        op="INSERT",
        create_dlq=True,
        expected_fields=expected_fields,
        custom_assertion=assert_range_check,
        expected_record_count=expected_record_count,
        override_sink_config=override_sink_config,
        kwargs={
            "id_field": id_field,
            "id_min": id_min,
            "id_max": id_max,
        }
    )

    # Validate DLQ records
    dlq_records = reader.read_kafka("aicore.dlq")
    assert len(dlq_records) == expected_dlq_record_count, (
        f"Expected to find <{expected_dlq_record_count}> in DLQ, found <{len(dlq_records)}> instead"
    )
