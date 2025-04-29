const network_device_schema = `{
  "type": "record",
  "name": "NetworkDevice",
  "namespace": "com.example",
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
      "name": "name",
      "type": "string"
    },
    {
      "name": "device_type",
      "type": "string"
    },
    {
      "name": "status",
      "type": "string"
    },
    {
      "name": "network_config",
      "type": {
        "type": "record",
        "name": "NetworkConfig",
        "fields": [
          {
            "name": "ip_address",
            "type": "string"
          },
          {
            "name": "data_in_bytes",
            "type": "int"
          },
          {
            "name": "data_out_bytes",
            "type": "int"
          },
          {
            "name": "subnet_mask",
            "type": "string"
          },
          {
            "name": "gateway",
            "type": "string"
          },
          {
            "name": "interfaces",
            "type": {
              "type": "array",
              "items": {
                "type": "record",
                "name": "Interface",
                "fields": [
                  {
                    "name": "name",
                    "type": "string"
                  },
                  {
                    "name": "throughput_in_bytes",
                    "type": "int"
                  },
                  {
                    "name": "type",
                    "type": "string"
                  },
                  {
                    "name": "mac_address",
                    "type": "string"
                  },
                  {
                    "name": "settings",
                    "type": {
                      "type": "record",
                      "name": "InterfaceSettings",
                      "fields": [
                        {
                          "name": "speed",
                          "type": "string"
                        },
                        {
                          "name": "duplex",
                          "type": "string"
                        },
                        {
                          "name": "mtu",
                          "type": "int"
                        }
                      ]
                    }
                  }
                ]
              }
            }
          }
        ]
      }
    },
    {
      "name": "security",
      "type": {
        "type": "record",
        "name": "Security",
        "fields": [
          {
            "name": "encryption",
            "type": "boolean"
          },
          {
            "name": "firewall",
            "type": {
              "type": "array",
              "items": {
                "type": "record",
                "name": "Firewall",
                "fields": [
                  {
                    "name": "enabled",
                    "type": "boolean"
                  },
                  {
                    "name": "rules",
                    "type": {
                      "type": "record",
                      "name": "Rules",
                      "fields": [
                        {
                          "name": "inbound",
                          "type": "string"
                        },
                        {
                          "name": "outbound",
                          "type": "string"
                        }
                      ]
                    }
                  }
                ]
              }
            }
          }
        ]
      }
    }
  ]
}`;

// Create a sample message with the schema structure
const message = {
  "id": "net-123456",
  "created_at": new Date().toISOString(),
  "last_modified_at": new Date().toISOString(),
  "is_deleted": false,
  "name": "Network-Device-"+  Math.floor(Math.random() * 100000000) + 1,
  "device_type": "Router",
  "status": "Active",
  "network_config": {
    "data_in_bytes": Math.floor(Math.random() * 2000) + 1,
    "data_out_bytes": Math.floor(Math.random() * 2000) + 1,
    "ip_address": "192.168.1.1",
    "subnet_mask": "255.255.255.0",
    "gateway": "192.168.1.1",
    "interfaces": [
      {
        "name": "GigabitEthernet0/0",
        "type": "Ethernet",
        "throughput_in_bytes": Math.floor(Math.random() * 2000) + 1,
        "mac_address": "00:11:22:33:44:55",
        "settings": {
          "speed": "1000Mbps",
          "duplex": "Full",
          "mtu": Math.floor(Math.random() * 2000) + 1
        }
      },
      {
        "name": "GigabitEthernet0/1",
        "type": "Ethernet",
        "throughput_in_bytes": Math.floor(Math.random() * 3000) + 1000,
        "mac_address": "00:11:22:33:44:56",
        "settings": {
          "speed": "10000Mbps",
          "duplex": "Full",
          "mtu": Math.floor(Math.random() * 1500) + 1500
        }
      },
      {
        "name": "FastEthernet1/0",
        "type": "Ethernet",
        "throughput_in_bytes": Math.floor(Math.random() * 1000) + 500,
        "mac_address": "00:11:22:33:44:57",
        "settings": {
          "speed": "100Mbps",
          "duplex": "Half",
          "mtu": Math.floor(Math.random() * 1000) + 1000
        }
      }
    ]
  },
  "security": {
    "encryption": true,
    "firewall": [
      {
        "enabled": true,
        "rules": {
          "inbound": "allow 80,443; deny 22,23",
          "outbound": "allow all"
        }
      },
      {
        "enabled": false,
        "rules": {
          "inbound": "deny all",
          "outbound": "allow 80,443"
        }
      }
    ]
  }
};

export { network_device_schema, message };
