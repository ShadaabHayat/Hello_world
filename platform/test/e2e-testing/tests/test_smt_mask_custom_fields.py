
import hashlib

import pytest

import params
from test_template import assertion_template, get_nested_values

def assert_masking(df, field_to_mask: str, mask_field_prefix: str):
    for row in df.iter_rows(named=True):
        record_id = row["id"]
        masked_ssn_values = get_nested_values(row, field_to_mask)

        for i in range(len(masked_ssn_values)):
            masked_ssn = masked_ssn_values[i]

            s = hashlib.sha256()
            original_ssn = mask_field_prefix + str(record_id+i)
            s.update(original_ssn.encode())
            expected_ssn = s.hexdigest()

            assert masked_ssn == expected_ssn, f"Expected to find masked SSN for field <{field_to_mask}>, found <{masked_ssn}>"

# Assertion for redact masking
def assert_redact(df, field_to_mask: str):
    for row in df.iter_rows(named=True):
        redacted_values = get_nested_values(row, field_to_mask)

        for redacted_value in redacted_values:
            # Determine the expected redacted value based on the field's data type
            if isinstance(redacted_value, (int, float)):
                expected_value = 0
            elif isinstance(redacted_value, str):
                expected_value = ""
            else:
                expected_value = None  # Default for unsupported types

            assert redacted_value == expected_value, (
                f"Expected redacted value <{expected_value}> for field <{field_to_mask}>, "
                f"found <{redacted_value}>"
            )

def MaskCustomFields_template(record_count: int, field_to_mask: str, mask_field_prefix: str):
    override_sink_config = {
        "transforms": "InsertRDCInfo,InsertEnvInfo,InsertTimeStamp,Mask_aicore_public_users_ssn,tsFormat",

        "transforms.InsertEnvInfo.static.field": "env",
        "transforms.InsertEnvInfo.static.value": "e2e-test",
        "transforms.InsertEnvInfo.type": "org.apache.kafka.connect.transforms.InsertField$Value",

        "transforms.InsertRDCInfo.static.field": "rdc",
        "transforms.InsertRDCInfo.static.value": "minikube",
        "transforms.InsertRDCInfo.type": "org.apache.kafka.connect.transforms.InsertField$Value",

        "transforms.InsertTimeStamp.timestamp.field": "last_modified_at",
        "transforms.InsertTimeStamp.type": "org.apache.kafka.connect.transforms.InsertField$Value",

        "transforms.Mask_aicore_public_users_ssn.mask.fields": field_to_mask,
        "transforms.Mask_aicore_public_users_ssn.topic.name": "aicore.public." + params.sink_config["table"],
        "transforms.Mask_aicore_public_users_ssn.type": "org.extremenetworks.com.MaskCustomFields$Value",

        "transforms.tsFormat.field": "last_modified_at",
        "transforms.tsFormat.format": "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "transforms.tsFormat.target.type": "string",
        "transforms.tsFormat.type": "org.apache.kafka.connect.transforms.TimestampConverter$Value"
    }

    expected_fields = ["id", "num1", "str1", "ssn", "nest1", "rdc", "env", "last_modified_at", "__op", "__ts_ms", "__deleted"]

    assertion_template(
        record_count, "INSERT", expected_fields,
        custom_assertion=assert_masking,
        override_sink_config=override_sink_config,
        kwargs={"field_to_mask": field_to_mask, "mask_field_prefix": mask_field_prefix})


def test_smt_MaskCustomFields():
    field_to_mask = "ssn"
    MaskCustomFields_template(29, field_to_mask, "very-secure-ssn-")

def RedactCustomFields_template(record_count: int, field_to_mask: str):
    override_sink_config = {
        "transforms": "InsertRDCInfo,InsertEnvInfo,InsertTimeStamp,Mask_aicore_public_users_ssn,tsFormat",

        "transforms.InsertEnvInfo.static.field": "env",
        "transforms.InsertEnvInfo.static.value": "e2e-test",
        "transforms.InsertEnvInfo.type": "org.apache.kafka.connect.transforms.InsertField$Value",

        "transforms.InsertRDCInfo.static.field": "rdc",
        "transforms.InsertRDCInfo.static.value": "minikube",
        "transforms.InsertRDCInfo.type": "org.apache.kafka.connect.transforms.InsertField$Value",

        "transforms.InsertTimeStamp.timestamp.field": "last_modified_at",
        "transforms.InsertTimeStamp.type": "org.apache.kafka.connect.transforms.InsertField$Value",

        "transforms.Mask_aicore_public_users_ssn.mask.fields": field_to_mask,
        "transforms.Mask_aicore_public_users_ssn.topic.name": "aicore.public." + params.sink_config["table"],
        "transforms.Mask_aicore_public_users_ssn.type": "org.extremenetworks.com.MaskCustomFields$Value",
        "transforms.Mask_aicore_public_users_ssn.mask.type": "redact",  # Enable redact masking

        "transforms.tsFormat.field": "last_modified_at",
        "transforms.tsFormat.format": "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "transforms.tsFormat.target.type": "string",
        "transforms.tsFormat.type": "org.apache.kafka.connect.transforms.TimestampConverter$Value"
    }

    expected_fields = ["id", "num1", "str1", "ssn", "nest1", "rdc", "env", "last_modified_at", "__op", "__ts_ms", "__deleted"]

    assertion_template(
        record_count, "INSERT", expected_fields,
        custom_assertion=assert_redact,
        override_sink_config=override_sink_config,
        kwargs={"field_to_mask": field_to_mask}
    )
def test_smt_RedactCustomFields():
    field_to_mask = "ssn"
    RedactCustomFields_template(29, field_to_mask)


@pytest.mark.parametrize("field_to_mask", ["nest1.str2", "nest1.str3"])
def test_smt_RedactCustomFields_for_nested_fields(field_to_mask: str):
    RedactCustomFields_template(31, field_to_mask)

@pytest.mark.parametrize("field_to_mask,mask_field_prefix", [("nest1.str2", "xyz-"), ("nest1.str3", "pqr-"), ("nest1.arr1.username", "user_")])
def test_smt_MaskCustomFields_for_nested_fields(field_to_mask: str, mask_field_prefix: str):
    MaskCustomFields_template(31, field_to_mask, mask_field_prefix)
