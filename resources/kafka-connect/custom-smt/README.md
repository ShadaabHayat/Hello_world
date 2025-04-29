
# Kafka Connect Custom Transforms

This repository contains custom Single Message Transforms (SMTs) for Kafka Connect.

## EqualityCheck Transform

A Kafka Connect transformation that validates records based on field equality and non-equality conditions.

### EqualityCheck Configuration

#### Required Parameters

```properties
"transforms": "EqualityCheck",
"transforms.EqualityCheck.type": "org.extremenetworks.com.EqualityCheckOnFields",
"transforms.EqualityCheck.topic.name": "source_topic",
"transforms.EqualityCheck.fields.notEquality": "{\"field1\": [\"value1\", \"value2\"], \"field2\": [\"value3\"]}",
"transforms.EqualityCheck.fields.Equality": "{\"field3\": [\"value4\"], \"field4\": [\"value5\"]}",
"errors.deadletterqueue.topic.name": "dlq_topic",
"behavior.on.null.values": "ignore"
```

#### Configuration Guidelines

- **`behavior.on.null.values`** is mandatory and defines how null values in the record are handled:
  - `ignore`: Null values are ignored during validation.
  - `fail`: Null values cause validation failure. This has to be avoided as we expect null value records.
- Each property value representing JSON must:
  - Be enclosed in double quotes (`"`).
  - Escape internal double quotes using a backslash (`\"`).
  - Avoid any newlines (`\n`).

#### Example Configuration

```properties
"transforms": "EqualityCheck,EqualityCheckUsers",
"transforms.EqualityCheck.type": "org.extremenetworks.com.EqualityCheckOnFields",
"transforms.EqualityCheck.topic.name": "aicore.public.subs",
"transforms.EqualityCheck.fields.Equality": "{\"last_modified_at\": [\"2024-12-03T12:00:00+00:00\", \"2024-12-09T18:00:00+00:00\", \"2024-12-06T15:00:00+00\"]}",
"transforms.EqualityCheck.fields.notEquality": "{\"status\": [\"EXPIRED\"]}",
"transforms.EqualityCheckUsers.type": "org.extremenetworks.com.EqualityCheckOnFields",
"transforms.EqualityCheckUsers.topic.name": "aicore.public.users_user",
"transforms.EqualityCheckUsers.fields.Equality": "{\"last_modified_at\": [\"2023-12-28T12:48:55.110673Z\", \"2023-09-22T11:45:35.448113Z\", \"2024-03-14T09:17:09.677411Z\", \"2024-07-15T11:12:30.611162Z\", \"2024-05-23T10:08:11.056928Z\"]}",
"transforms.EqualityCheckUsers.fields.notEquality": "{\"status\": [\"EXPIRED\", \"PENDING\"]}",
"behavior.on.null.values": "ignore"
```

### Examples

#### Valid Record (Passes Checks)

```json
{
    "field1": "valueA",        // Not in notEquality list: ["value1", "value2"]
    "field2": "valueB",        // Not in notEquality list: ["value3"]
    "field3": "value4",        // Matches equality list: ["value4"]
    "field4": "value5"         // Matches equality list: ["value5"]
}
```

#### Invalid Records (Fails Checks)

**Example 1: Fails NotEquality Check:**

```json
{
    "field1": "value1",        // Matches notEquality list
    "field2": "value3",        // Matches notEquality list
    "field3": "value4",        // Passes equality check
    "field4": "value5"         // Passes equality check
}
```

**Example 2: Fails Equality Check:**

```json
{
    "field1": "valueX",        // Not in notEquality list
    "field2": "valueY",        // Not in notEquality list
    "field3": "valueA",        // Not in equality list: ["value4"]
    "field4": null             // Ignored as per `behavior.on.null.values`
}
```

### Behavior

- Records failing validation are routed to the configured DLQ topic.
- Passing records maintain their original topic.
- Null values are ignored as per the `behavior.on.null.values: ignore` setting.

## MaskCustomFields Transform

A Kafka Connect transformation that provides field-level data masking and hashing for sensitive information in your data streams.

### MaskCustomFields Configuration

```properties
transforms=mask_transform
transforms.mask_transform.type=org.extremenetworks.com.MaskCustomFields$Value
transforms.mask_transform.mask.fields=field1,field2,nested.field3
transforms.mask_transform.topic.name=topic_to_mask
transforms.mask_transform.mask.type=hash
```

#### Required Parameters

| Parameter | Description | Required | Default |
|-----------|-------------|----------|----------|
| `type` | The SMT implementation class | Yes | - |
| `mask.fields` | Comma-separated list of fields to mask | Yes | - |
| `topic.name` | Topic name to apply the transformation | Yes | - |
| `mask.type` | Type of masking to apply: 'hash' or 'redact' | No | hash |

#### Masking Types

- **hash**: Applies SHA-256 hashing to field values, converting all masked fields to strings
- **mask**: Replaces values with default empty/zero values while preserving the original data type

### Example Configuration

```properties
# Example configuration for hashing sensitive user data
transforms=mask_pii
transforms.mask_pii.type=org.extremenetworks.com.MaskCustomFields$Value
transforms.mask_pii.mask.fields=email,ssn,credit_card,profile.phone,profile.address.street
transforms.mask_pii.topic.name=customer.data
transforms.mask_pii.mask.type=hash

# Example configuration for redacting with default values
transforms=mask_financial
transforms.mask_financial.type=org.extremenetworks.com.MaskCustomFields$Value
transforms.mask_financial.mask.fields=account_balance,transaction_amount,credit_score
transforms.mask_financial.topic.name=financial.data
transforms.mask_financial.mask.type=redact
```

### Features

- **Flexible Masking Options**: Choose between hashing (SHA-256) or replacing with default values
- **Type Preservation**: When using 'mask' type, original data types are preserved
- **Nested Field Support**: Mask fields within nested structures using dot notation
- **Schema Handling**: Automatically updates schema based on masking type
- **Performance Optimized**: Minimal overhead for high-throughput Kafka pipelines

### Data Type Handling

#### Hash Mode (`mask.type=hash`)

In hash mode, all masked fields are converted to strings containing the SHA-256 hash of the original value.

| Original Type | Transformed Type | Example |
|---------------|------------------|----------|
| String | String | "<john@example.com>" → "96d9632f363564cc3032521409cf22a852f2032eec099ed5967c0d000cec607a" |
| Integer | String | 12345 → "5994471abb01112afcc18159f6cc74b4f511b99806da59b3caf5a9c173cacfc5" |
| Boolean | String | true → "b5bea41b6c623f7c09f1bf24dcae58ebab3c0cdd90ad966bc43a45b44867e12b" |

#### Redact Mode (`mask.type=redact`)

In redact mode, fields are replaced with default empty values while preserving their original data type:

| Original Type | Default Value |
|---------------|---------------|
| String | "" (empty string) |
| Integer/Long | 0 |
| Float/Double | 0.0 |
| Boolean | false |
| Struct | (recursively masked) |

### Use Cases

#### PII Data Protection

Mask personally identifiable information to comply with privacy regulations:

```properties
transforms=mask_pii
transforms.mask_pii.type=org.extremenetworks.com.MaskCustomFields$Value
transforms.mask_pii.mask.fields=email,phone,address,ssn,date_of_birth
transforms.mask_pii.topic.name=customer.profiles
transforms.mask_pii.mask.type=hash
```

#### Financial Data Anonymization

Replace sensitive financial values with zeros while preserving data types:

```properties
transforms=mask_financial
transforms.mask_financial.type=org.extremenetworks.com.MaskCustomFields$Value
transforms.mask_financial.mask.fields=account_balance,credit_limit,transaction_amount
transforms.mask_financial.topic.name=financial.transactions
transforms.mask_financial.mask.type=redact
```

## Whitelister Transform

A Kafka Connect transformation that filters record fields based on a target schema definition, ensuring that only fields defined in the schema are included in the output. This SMT is particularly useful for data governance, privacy compliance, and schema standardization.

### How It Works

The Whitelister SMT reads schema files (JSON or Avro) that define the expected structure of your data. It then filters incoming records to only include fields that are present in the schema. This is useful for:

1. Enforcing schema compliance
2. Removing unexpected or sensitive fields
3. Lock down the schema, so newer fields go through strict legal review in order to be allowed or pass through the pipeline

### Whitelister Configuration

```properties
transforms=whitelister
transforms.whitelister.type=org.extremenetworks.com.Whitelister$Value
transforms.whitelister.schema.directory=/path/to/schemas
transforms.whitelister.schema.type=json
transforms.whitelister.schema.suffix=.schema.json
transforms.whitelister.file.schema.cache.ttl.minutes=5
transforms.whitelister.file.schema.cache.max.size=1000
transforms.whitelister.updated.schema.cache.size=100
transforms.whitelister.ignore.missing.schema=true
```

#### Configuration Parameters

| Parameter | Description | Required | Default |
|-----------|-------------|----------|---------|
| `type` | The SMT implementation class | Yes | - |
| `schema.directory` | Path to directory containing schema files | Yes | - |
| `schema.type` | Schema type: 'json' or 'avro' | No | json |
| `schema.suffix` | Optional suffix for schema files | No | `.json` or `.avsc` based on schema.type |
| `file.schema.cache.ttl.minutes` | TTL for schema file cache in minutes | No | 5 |
| `file.schema.cache.max.size` | Maximum number of entries in the schema content cache | No | 1000 |
| `updated.schema.cache.size` | Updated schema cache size | No | 100 |
| `ignore.missing.schema` | Whether to ignore missing schema files | No | true |

### Features

- **Dynamic Schema Loading**: Automatically selects schema based on topic name
- **Schema-based Filtering**: Only allows fields present in the target schema
- **Support for Multiple Schema Formats**: Works with both JSON Schema and Avro Schema
- **Nested Field Support**: Handles complex nested structures and arrays
- **Schema Preservation**: Maintains the schema structure while removing non-whitelisted fields
- **Performance Optimized**: Uses multi-level caching to minimize overhead for high-throughput Kafka pipelines
- **Configurable Caching**: Adjustable TTL and cache sizes for optimal performance
- **Flexible Error Handling**: Option to ignore or fail on missing schema files

### Schema Resolution

When a message arrives on a topic (e.g., `customer.data`), the SMT:

1. Looks for a schema file at `<schema.directory>/<topic><schema.suffix>`
2. Parses the schema to identify allowed fields
3. Filters the record to only include fields defined in the schema
4. Caches the schema information for future messages on the same topic

### Data Type Handling

The Whitelister preserves the original data types of all fields. For nested structures:

- **Objects/Structs**: Only fields defined in the schema are retained
- **Arrays**: Array elements are processed recursively if they contain objects
- **Primitive Values**: Passed through unchanged if they're in the schema

### Example Configuration

```properties
transforms=schema_enforcer
transforms.schema_enforcer.type=org.extremenetworks.com.Whitelister$Value
transforms.schema_enforcer.schema.directory=/opt/kafka/schemas
transforms.schema_enforcer.schema.type=json
```

With this configuration, if a message comes in on topic `customer.data`, the SMT will look for a schema file at `/opt/kafka/schemas/customer.data.json`.

### Path Notation

The Whitelister uses a sophisticated path notation system:

- **Dot Notation**: For nested fields (e.g., `parent.child.field`)
- **Array Notation**: Uses `[]` suffix for array items (e.g., `addresses[].street`)

This allows for precise targeting of fields at any level of nesting.

### Use Cases

#### Schema Evolution Management

Ensure backward compatibility when schema evolves by filtering out new fields for older consumers:

```properties
transforms=schema_compat
transforms.schema_compat.type=org.extremenetworks.com.Whitelister$Value
transforms.schema_compat.schema.directory=/opt/kafka/schemas/v1
transforms.schema_compat.schema.type=json
transforms.schema_compat.file.schema.cache.ttl.minutes=10
```

#### Data Integration Standardization

Standardize data from multiple sources by ensuring only expected fields are passed through:

```properties
transforms=standardize
transforms.standardize.type=org.extremenetworks.com.Whitelister$Value
transforms.standardize.schema.directory=/opt/kafka/schemas/standard
transforms.standardize.schema.type=json
transforms.standardize.updated.schema.cache.size=200
```

#### Data Privacy Compliance

Remove sensitive fields that shouldn't be propagated to downstream systems:

```properties
transforms=privacy_filter
transforms.privacy_filter.type=org.extremenetworks.com.Whitelister$Value
transforms.privacy_filter.schema.directory=/opt/kafka/schemas/public
transforms.privacy_filter.schema.type=json
transforms.privacy_filter.ignore.missing.schema=false
```

#### High-Performance Processing

Optimize for high-throughput Kafka pipelines with larger cache sizes:

```properties
transforms=high_perf_filter
transforms.high_perf_filter.type=org.extremenetworks.com.Whitelister$Value
transforms.high_perf_filter.schema.directory=/opt/kafka/schemas
transforms.high_perf_filter.schema.type=json
transforms.high_perf_filter.file.schema.cache.ttl.minutes=30
transforms.high_perf_filter.file.schema.cache.max.size=5000
transforms.high_perf_filter.updated.schema.cache.size=500
```
