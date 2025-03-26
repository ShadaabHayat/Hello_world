
import json

import pytest

import reader
import params
from test_template import assertion_template


def assert_data_quality_check(df, included_field: str, included_values_list: list[str], excluded_field: str, excluded_values_list: list[str]):
    for _, row in df.iterrows():
        included_field_value = row[included_field]
        excluded_field_value = row[excluded_field]

        assert included_field_value     in included_values_list, \
            f"Did not expect to find <{included_field}={included_field_value}>, allowed values are <{included_values_list}>"

        assert excluded_field_value not in excluded_values_list, \
            f"Did not expect to find <{excluded_field}={excluded_field_value}>, filtered out values are <{excluded_values_list}>"


def test_smt_EqualityCheckOnFields():
    record_count = 79
    expected_record_count = 13
    expected_dlq_record_count = record_count - expected_record_count

    expected_fields = ["id", "num1", "str1", "ssn", "nest1.str2", "nest1.str3", "nest1.arr1", "rdc", "env", "last_modified_at", "__op", "__ts_ms", "__deleted"]

    included_field = "str1"
    get_included_value = lambda i: "abc-" + str(i)

    excluded_field = "num1"
    get_excluded_value = lambda i: 1000 + i

    included_values_list = [ get_included_value(i) for i in range(0, record_count, 4) ] # Include every 4th value
    excluded_values_list = [ get_excluded_value(i) for i in range(1, record_count, 3) ] # Drop every 3rd value

    transformation_equality_param     = json.dumps({ included_field: included_values_list })
    transformation_not_equality_param = json.dumps({ excluded_field: excluded_values_list })

    override_sink_config = {
        "transforms": "InsertRDCInfo,InsertEnvInfo,InsertTimeStamp,tsFormat,EqualityCheck",

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

        "transforms.EqualityCheck.type": "org.extremenetworks.com.EqualityCheckOnFields",
        "transforms.EqualityCheck.topic.name": "aicore.public." + params.sink_config["table"],
        "transforms.EqualityCheck.fields.notEquality": transformation_not_equality_param,
        "transforms.EqualityCheck.fields.Equality": transformation_equality_param,
    }

    assertion_template(
        record_count=record_count, op="INSERT", create_dlq=True,
        expected_fields=expected_fields, custom_assertion=assert_data_quality_check,
        expected_record_count=expected_record_count, override_sink_config=override_sink_config,
        kwargs={
            "included_field": included_field,
            "included_values_list": included_values_list,
            "excluded_field": excluded_field,
            "excluded_values_list": excluded_values_list
            }
        )

    dlq_records = reader.read_kafka("aicore.dlq")
    assert len(dlq_records) == expected_dlq_record_count, f"Expected to find <{expected_dlq_record_count}> in DLQ, found <{len(dlq_records)}> records instead"
