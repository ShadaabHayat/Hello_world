
import pytest
from test_template import run_test_for_ids

@pytest.mark.parametrize("flush_size", [1, 12, 31, 50])
def test_flush_size(flush_size):
    override_sink_config = { "flush.size": flush_size }
    df_list, key_uris = run_test_for_ids(range(3 * flush_size), "INSERT", override_sink_config)

    for df, key in zip(df_list, key_uris):
        assert len(df) == flush_size, f"Expected <{key}> to have <{flush_size}> records, found <{len(df)}>"
