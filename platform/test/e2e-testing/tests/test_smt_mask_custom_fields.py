
import hashlib

import pytest

import params
from test_template import assertion_template


def assert_masking(df, field_to_mask: str, mask_field_prefix: str):
    for _, row in df.iterrows():
        record_id = row["id"]
        masked_ssn = row[field_to_mask]

        s = hashlib.sha256()
        original_ssn = mask_field_prefix + str(record_id)
        s.update(original_ssn.encode())
        expected_ssn = s.hexdigest()

        assert masked_ssn == expected_ssn, f"Expected to find masked SSN for field <{field_to_mask}>, found <{masked_ssn}>"

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

    expected_fields = ["id", "num1", "str1", "ssn", "nest1.str2", "nest1.str3", "nest1.arr1", "rdc", "env", "last_modified_at", "__op", "__ts_ms", "__deleted"]

    assertion_template(
        record_count, "INSERT", expected_fields,
        custom_assertion=assert_masking,
        override_sink_config=override_sink_config,
        kwargs={"field_to_mask": field_to_mask, "mask_field_prefix": mask_field_prefix})


def test_smt_MaskCustomFields():
    field_to_mask = "ssn"
    MaskCustomFields_template(29, field_to_mask, "very-secure-ssn-")


@pytest.mark.skip(reason="Test MaskCustomFields for nested fields: Nested field masking is not implemented in MaskCustomFields SMT. Enable this test when the feature is ready")
@pytest.mark.parametrize("field_to_mask", ["nest1.str2", "nest1.str3"])
def test_smt_MaskCustomFields_for_nested_fields(field_to_mask: str):
    MaskCustomFields_template(31, field_to_mask, "xyz-")
