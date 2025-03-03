const user_schema = `{
    "type": "record",
    "name": "User",
    "namespace": "example.namespace",
    "fields": [
      {
        "name": "id",
        "type": "int"
      },
      {
        "name": "password",
        "type": "string"
      },
      {
        "name": "last_login",
        "type": [
          "null",
          "string"
        ],
        "default": null
      },
      {
        "name": "created_on",
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
        "name": "status",
        "type": "string"
      },
      {
        "name": "email",
        "type": "string"
      },
      {
        "name": "first_name",
        "type": "string"
      },
      {
        "name": "last_name",
        "type": "string"
      },
      {
        "name": "is_staff",
        "type": "boolean"
      },
      {
        "name": "is_password_set",
        "type": "boolean"
      },
      {
        "name": "is_superuser",
        "type": "boolean"
      },
      {
        "name": "verification_code",
        "type": [
          "null",
          "string"
        ],
        "default": null
      },
      {
        "name": "role_id",
        "type": "int"
      },
      {
        "name": "profile_image",
        "type": "string"
      },
      {
        "name": "deleted_at",
        "type": "string"
      },
      {
        "name": "is_2fa",
        "type": "boolean"
      },
      {
        "name": "secret_2fa_key",
        "type": [
          "null",
          "string"
        ],
        "default": null
      },
      {
        "name": "is_synced",
        "type": "boolean"
      },
      {
        "name": "scim_external_id",
        "type": "string"
      },
      {
        "name": "scim_username",
        "type": "string"
      },
      {
        "name": "is_domain_approved",
        "type": "boolean"
      },
      {
        "name": "is_xcd_admin",
        "type": "boolean"
      },
      {
        "name": "jit_external_id",
        "type": "string"
      },
      {
        "name": "__deleted",
        "type": "string"
      },
      {
        "name": "__op",
        "type": "string"
      },
      {
        "name": "__ts_ms",
        "type": "long"
      }
    ]
  }
  `;

  const message = {
    "id": 52868,
    "password": "",
    "last_login": null,
    "created_on": new Date().toISOString(),
    "last_modified_at": new Date().toISOString(),
    "is_deleted": true,
    "status": "DISABLED",
    "email": `ug-user${Math.floor(Math.random() * 1000000)}@emumbapk.onmicrosoft.com`,
    "first_name": "TEST idp users",
    "last_name": "",
    "is_staff": false,
    "is_password_set": false,
    "is_superuser": false,
    "verification_code": null,
    "role_id": 3,
    "profile_image": "",
    "deleted_at": "",
    "is_2fa": false,
    "secret_2fa_key": null,
    "is_synced": true,
    "scim_external_id": "mn-1",
    "scim_username": `${Math.random().toString(36).substring(2,34)}ug-user${Math.floor(Math.random() * 1000000)}@emumbaPK.onmicrosoft.com`,
    "is_domain_approved": true,
    "is_xcd_admin": false,
    "jit_external_id": "",
    "__deleted": "false",
    "__op": "r",
    "__ts_ms": Date.now()
};

  export { user_schema, message };
