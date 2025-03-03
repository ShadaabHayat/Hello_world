
# Kafka Connect EqualityCheck Transform

A Kafka Connect transformation that validates records based on field equality and non-equality conditions.

## Configuration Parameters

### Required Parameters

```properties
"transforms": "EqualityCheck",
"transforms.EqualityCheck.type": "org.extremenetworks.com.EqualityCheckOnFields",
"transforms.EqualityCheck.topic.name": "source_topic",
"transforms.EqualityCheck.fields.notEquality": "{\"field1\": [\"value1\", \"value2\"], \"field2\": [\"value3\"]}",
"transforms.EqualityCheck.fields.Equality": "{\"field3\": [\"value4\"], \"field4\": [\"value5\"]}",
"errors.deadletterqueue.topic.name": "dlq_topic",
"behavior.on.null.values": "ignore"
```

### Configuration Guidelines

- **`behavior.on.null.values`** is mandatory and defines how null values in the record are handled:
  - `ignore`: Null values are ignored during validation.
  - `fail`: Null values cause validation failure. This has to be avoided as we expect null value records.
- Each property value representing JSON must:
  - Be enclosed in double quotes (`"`).
  - Escape internal double quotes using a backslash (`\"`).
  - Avoid any newlines (`\n`).

### Example Configuration

```properties
"transforms": "EqualityCheck,EqualityCheckUsers",
"transforms.EqualityCheck.type": "org.extremenetworks.com.JsonConfigSMT",
"transforms.EqualityCheck.topic.name": "aicore.public.subs",
"transforms.EqualityCheck.fields.Equality": "{\"last_modified_at\": [\"2024-12-03T12:00:00+00:00\", \"2024-12-09T18:00:00+00:00\", \"2024-12-06T15:00:00+00\"]}",
"transforms.EqualityCheck.fields.notEquality": "{\"status\": [\"EXPIRED\"]}",
"transforms.EqualityCheckUsers.type": "org.extremenetworks.com.JsonConfigSMT",
"transforms.EqualityCheckUsers.topic.name": "aicore.public.users_user",
"transforms.EqualityCheckUsers.fields.Equality": "{\"last_modified_at\": [\"2023-12-28T12:48:55.110673Z\", \"2023-09-22T11:45:35.448113Z\", \"2024-03-14T09:17:09.677411Z\", \"2024-07-15T11:12:30.611162Z\", \"2024-05-23T10:08:11.056928Z\"]}",
"transforms.EqualityCheckUsers.fields.notEquality": "{\"status\": [\"EXPIRED\", \"PENDING\"]}",
"behavior.on.null.values": "ignore"
```

## Examples

### Valid Record (Passes Checks)

```json
{
    "field1": "valueA",        // Not in notEquality list: ["value1", "value2"]
    "field2": "valueB",        // Not in notEquality list: ["value3"]
    "field3": "value4",        // Matches equality list: ["value4"]
    "field4": "value5"         // Matches equality list: ["value5"]
}
```

### Invalid Records (Fails Checks)

1. **Fails NotEquality Check:**

```json
{
    "field1": "value1",        // Matches notEquality list
    "field2": "value3",        // Matches notEquality list
    "field3": "value4",        // Passes equality check
    "field4": "value5"         // Passes equality check
}
```

2. **Fails Equality Check:**

```json
{
    "field1": "valueX",        // Not in notEquality list
    "field2": "valueY",        // Not in notEquality list
    "field3": "valueA",        // Not in equality list: ["value4"]
    "field4": null             // Ignored as per `behavior.on.null.values`
}
```

## Behavior

- Records failing validation are routed to the configured DLQ topic.
- Passing records maintain their original topic.
- Null values are ignored as per the `behavior.on.null.values: ignore` setting.

---

This preserves the structure of the previous answer, with the requested change applied. Let me know if there’s anything else to tweak!

## Field Masking SMT

The Field Masking SMT is a custom Single Message Transform that provides field-level data anonymization in Kafka Connect pipelines. This transform can be applied to any topic where data masking is required.

### Overview

This SMT allows you to:

- Mask sensitive fields in your data stream
- Configure masking on a per-topic basis
- Maintain data structure while protecting sensitive information

### Configuration

```properties
transforms=<transform_name>
transforms.<transform_name>.type=org.extremenetworks.com.MaskCustomFields$Value
transforms.<transform_name>.mask.fields=<comma_separated_field_list>
transforms.<transform_name>.topic.name=<topic_name>
```

### Parameters

| Parameter | Description | Required | Default |
|-----------|-------------|-----------|----------|
| `type` | The SMT implementation class | Yes | - |
| `mask.fields` | Comma-separated list of fields to mask | Yes | - |
| `topic.name` | Topic name to apply the transformation | Yes | - |

### Example Usage

```properties
# Example configuration for masking user data
transforms=mask_user_data
transforms.mask_user_data.type=org.extremenetworks.com.MaskCustomFields$Value
transforms.mask_user_data.mask.fields=email,phone,address
transforms.mask_user_data.topic.name=user.data.topic
```

### Implementation Details

- **Transform Type**: Value-level transformation
- **Data Preservation**: Maintains the original data structure
- **Performance**: Minimal impact on message processing
- **Compatibility**: Works with all Kafka Connect sink connectors

### Best Practices

1. **Field Selection**
   - Only mask fields that contain sensitive information
   - Consider regulatory requirements (GDPR, CCPA, etc.)

2. **Performance**
   - Limit the number of fields being masked to minimize processing overhead
   - Consider the impact on downstream applications

3. **Monitoring**
   - Monitor transformation metrics
   - Validate masked data meets security requirements

### Use Cases

- PII (Personally Identifiable Information) masking
- Financial data anonymization
- Healthcare data protection
- Compliance with data privacy regulations
