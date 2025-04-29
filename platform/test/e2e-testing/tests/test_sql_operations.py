
import pytest
from test_template import assertion_template

def test_insert_operation():
    expected_fields = ["id", "num1", "str1", "ssn", "nest1", "rdc", "env", "last_modified_at", "__op", "__ts_ms", "__deleted"]
    assertion_template(23, "INSERT", expected_fields)


def test_update_operation():
    expected_fields = ["id", "num1", "str1", "ssn", "nest1", "rdc", "env", "last_modified_at", "__op", "__ts_ms", "__deleted"]
    assertion_template(61, "UPDATE", expected_fields)


def test_delete_operation():
    expected_fields = ["id", "num1", "str1", "ssn", "nest1", "rdc", "env", "last_modified_at", "__op", "__ts_ms", "__deleted"]
    assertion_template(47, "DELETE", expected_fields)
