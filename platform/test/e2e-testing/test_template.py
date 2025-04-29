
import time

import params
import cleanup
import producer
import kafka_connect
import reader

import polars as pl

global_wait_time = 10

def run_test_for_records(
    records: list[dict],
    override_sink_config: dict={}, wait_time: float=global_wait_time, create_dlq: bool=False,
    should_create_topic: bool=True, should_publish_schema: bool=True,
    value_format: str="AVRO") -> (list[pl.DataFrame], list[str]):

    cleanup.delete_all()

    producer.push_records(
        records,
        value_format=value_format,
        should_create_topic=should_create_topic,
        should_publish_schema=should_publish_schema)

    if create_dlq:
        producer.create_topic("aicore.dlq")

    kafka_connect.create_sink_connector(params.sink_config["name"], override_sink_config)

    time.sleep(wait_time)

    df_list, key_uris = reader.read_s3()
    return df_list, key_uris


def run_test_for_ids(
    ids: range | list[int], op: str,
    override_sink_config: dict={}, wait_time: float=global_wait_time, create_dlq: bool=False,
    should_create_topic: bool=True, should_publish_schema: bool=True,
    value_format: str="AVRO") -> (list[pl.DataFrame], list[str]):

    records = []
    for record_id in ids:
        rec = producer.generate_record_for_id(record_id, op)
        records.append(rec)

    return run_test_for_records(
        records=records,
        override_sink_config=override_sink_config,
        wait_time=wait_time, create_dlq=create_dlq,
        should_create_topic=should_create_topic,
        should_publish_schema=should_publish_schema,
        value_format=value_format)


def assertion_template_for_records(
        records: list[dict], op: str, expected_fields: list[str],
        custom_assertion=None, expected_record_count: int=None,
        override_sink_config: dict={}, wait_time: float=global_wait_time, create_dlq: bool=False,
        should_create_topic: bool=True, should_publish_schema: bool=True,
        value_format: str="AVRO", kwargs={}):

    short_operation_name = producer.operation_map[op]
    record_count = len(records)

    if expected_record_count is None:
        expected_record_count = record_count

    df_list, _ = run_test_for_records(
        records=records,
        override_sink_config=override_sink_config,
        wait_time=wait_time, create_dlq=create_dlq,
        should_create_topic=should_create_topic,
        should_publish_schema=should_publish_schema,
        value_format=value_format)

    df = pl.concat(df_list) if df_list else pl.DataFrame()

    # Uncomment for debugging purposes to view S3 data
    # with pl.Config() as cfg:
    #     cfg.set_tbl_cols(-1)
    #     print(df)

    assert len(df) == expected_record_count, f"Expected {record_count} records in output, found {len(df)}"

    assert len(expected_fields) == len(df.columns), f"Expected columns are <{expected_fields}>, found <{df.columns}>"

    for field in expected_fields:
        assert field in df.columns, f"Expected to find {field} in output"

    if custom_assertion is not None:
        custom_assertion(df, **kwargs)


def assertion_template(
        record_count: int, op: str, expected_fields: list[str],
        custom_assertion=None, expected_record_count: int=None, create_dlq: bool=False,
        wait_time: float=global_wait_time,  should_create_topic: bool=True, should_publish_schema: bool=True,
        override_sink_config: dict={}, value_format: str="AVRO", kwargs={}):

    records = []
    for record_id in range(record_count):
        rec = producer.generate_record_for_id(record_id, op)
        records.append(rec)

    return assertion_template_for_records(
        records=records, op=op, expected_fields=expected_fields,
        custom_assertion=custom_assertion, expected_record_count=expected_record_count,
        override_sink_config=override_sink_config, wait_time=wait_time, create_dlq=create_dlq,
        should_create_topic=should_create_topic, should_publish_schema=should_publish_schema,
        value_format=value_format, kwargs=kwargs)


def get_nested_values(row, field):
    field_parts = field.split(".")
    candidates = [row]
    part_counter = 0

    while part_counter < len(field_parts):
        if len(candidates) == 0:
            return []

        field_part = field_parts[part_counter]
        new_candidates = []

        for candidate in candidates:
            if type(candidate) == list:
                new_candidates = new_candidates + candidate
            elif field_part in candidate:
                new_candidates = new_candidates + [ candidate[field_part] ]
                part_counter += 1
            else:
                print(f"Unknown field <{field_part}> in record <{candidate}>")
                break

        candidates = new_candidates

    return candidates
