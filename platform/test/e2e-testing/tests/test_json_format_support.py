
import pytest
from test_template import assertion_template

def test_json_record_value_support():
    expected_fields = ["id", "num1", "str1", "ssn", "nest1.str2", "nest1.str3", "nest1.arr1", "rdc", "env", "last_modified_at"] #, "__op", "__ts_ms", "__deleted"]
    override_sink_config = { "value.converter": "io.confluent.connect.json.JsonSchemaConverter" }
    assertion_template(
        43, "INSERT", expected_fields,
        override_sink_config=override_sink_config,
        value_format="JSON")
