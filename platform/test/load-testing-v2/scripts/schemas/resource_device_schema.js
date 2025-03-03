const resource_device_schema = `{
  "type": "record",
  "name": "Device",
  "fields": [
    {
      "name": "id",
      "type": "string"
    },
    {
      "name": "created_at",
      "type": "string"
    },
    {
      "name": "last_modified_at",
      "type": "string"
    },
    {
      "name": "is_deleted",
      "type": "boolean"
    },
    {
      "name": "deleted_at",
      "type": [
        "null",
        "long"
      ],
      "default": null
    },
    {
      "name": "code",
      "type": "string"
    },
    {
      "name": "name",
      "type": "string"
    },
    {
      "name": "local_ip",
      "type": "string"
    },
    {
      "name": "model_name",
      "type": "string"
    },
    {
      "name": "model_number",
      "type": [
        "null",
        "string"
      ],
      "default": null
    },
    {
      "name": "last_connected_location",
      "type": "string"
    },
    {
      "name": "last_connected",
      "type": "long"
    },
    {
      "name": "public_access_key",
      "type": "string"
    },
    {
      "name": "created_by_id",
      "type": [
        "null",
        "string"
      ],
      "default": null
    },
    {
      "name": "workspace_id",
      "type": [
        "null",
        "string"
      ],
      "default": null
    },
    {
      "name": "platform_os",
      "type": [
        "null",
        "string"
      ],
      "default": null
    },
    {
      "name": "is_notification_enabled",
      "type": "boolean"
    },
    {
      "name": "device_notification_token",
      "type": [
        "null",
        "string"
      ],
      "default": null
    },
    {
      "name": "unique_identifier",
      "type": "string"
    },
    {
      "name": "access_mode",
      "type": "string"
    },
    {
      "name": "device_access_revoked",
      "type": "boolean"
    },
    {
      "name": "device_type",
      "type": [
        "null",
        "string"
      ],
      "default": null
    },
    {
      "name": "os_version",
      "type": [
        "null",
        "string"
      ],
      "default": null
    },
    {
      "name": "last_login_user",
      "type": [
        "null",
        "string"
      ],
      "default": null
    },
    {
      "name": "last_login_at",
      "type": [
        "null",
        "long"
      ],
      "default": null
    },
    {
      "name": "user_last_login_at",
      "type": [
        "null",
        "long"
      ],
      "default": null
    },
    {
      "name": "preshared_key",
      "type": [
        "null",
        "string"
      ],
      "default": null
    },
    {
      "name": "private_key",
      "type": [
        "null",
        "string"
      ],
      "default": null
    },
    {
      "name": "dns_status",
      "type": [
        "null",
        "string"
      ],
      "default": null
    },
    {
      "name": "private_ip",
      "type": "string"
    },
    {
      "name": "public_ip",
      "type": "string"
    },
    {
      "name": "auth_request",
      "type": "string"
    },
    {
      "name": "compliance",
      "type": [
        "null",
        "string"
      ],
      "default": null
    },
    {
      "name": "is_craas",
      "type": "boolean"
    },
    {
      "name": "is_zta",
      "type": "boolean"
    },
    {
      "name": "mac_address",
      "type": [
        "null",
        "string"
      ],
      "default": null
    },
    {
      "name": "mac_oui",
      "type": [
        "null",
        "string"
      ],
      "default": null
    },
    {
      "name": "ipv6_address",
      "type": [
        "null",
        "string"
      ],
      "default": null
    },
    {
      "name": "mdm_id",
      "type": [
        "null",
        "string"
      ],
      "default": null
    },
    {
      "name": "raw_mac_address",
      "type": [
        "null",
        "string"
      ],
      "default": null
    },
    {
      "name": "is_guest",
      "type": "boolean"
    },
    {
      "name": "model_serial_number",
      "type": [
        "null",
        "string"
      ],
      "default": null
    },
    {
      "name": "created_by_email",
      "type": [
        "null",
        "string"
      ],
      "default": null
    },
    {
      "name": "license_type",
      "type": "string"
    },
    {
      "name": "alias",
      "type": [
        "null",
        "string"
      ],
      "default": null
    },
    {
      "name": "description",
      "type": [
        "null",
        "string"
      ],
      "default": null
    },
    {
      "name": "is_posture_valid",
      "type": "boolean"
    },
    {
      "name": "connected",
      "type": "boolean"
    }
  ]
}`;

const message = {
  "id": "uuid",
  "created_at": new Date().toISOString(),
  "last_modified_at": new Date().toISOString(),
  "is_deleted": false,
  "deleted_at": null,
  "code": `DEV-${Math.floor(Math.random() * 1000000).toString().padStart(6, '0')}`,
  "name": `Device-${Math.random().toString(36).substring(2, 8).toUpperCase()}`,
  "local_ip": `192.168.${Math.floor(Math.random() * 256)}.${Math.floor(Math.random() * 256)}`,
  "model_name": "XYZ Corp",
  "model_number": null,
  "last_connected_location": "Office",
  "last_connected": 1678886400000,
  "public_access_key": "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890AB",
  "created_by_id": null,
  "workspace_id": null,
  "platform_os": null,
  "is_notification_enabled": true,
  "device_notification_token": null,
  "unique_identifier": `iden-${Math.random().toString(36).substring(2, 8).toUpperCase()}`,
  "access_mode": "ReadWrite",
  "device_access_revoked": false,
  "device_type": null,
  "os_version": null,
  "last_login_user": null,
  "last_login_at": null,
  "user_last_login_at": null,
  "preshared_key": null,
  "private_key": null,
  "dns_status": null,
  "private_ip": "10.0.0.5",
  "public_ip": "8.8.8.8",
  "auth_request": "AUTH123",
  "compliance": null,
  "is_craas": false,
  "is_zta": false,
  "mac_address": null,
  "mac_oui": null,
  "ipv6_address": null,
  "mdm_id": null,
  "raw_mac_address": null,
  "is_guest": false,
  "model_serial_number": null,
  "created_by_email": null,
  "license_type": "Full",
  "alias": null,
  "description": null,
  "is_posture_valid": true,
  "connected": true
}

export { resource_device_schema, message };
