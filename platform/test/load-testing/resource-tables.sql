create table resources_device
(
   id                        uuid                     not null
       primary key,
   created_at                timestamp with time zone not null,
   last_modified_at          timestamp with time zone not null,
   is_deleted                boolean                  not null,
   deleted_at                timestamp with time zone,
   code                      varchar(255)             not null,
   name                      varchar(255)             not null,
   local_ip                  inet,
   model_name                varchar(255)             not null,
   model_number              varchar(255),
   last_connected_location   varchar(255)             not null,
   last_connected            timestamp with time zone,
   public_access_key         varchar(44)              not null,
   created_by_id             bigint,
   workspace_id              integer,
   platform_os               varchar(255),
   is_notification_enabled   boolean                  not null,
   device_notification_token varchar(255),
   unique_identifier         varchar(256)             not null,
   access_mode               varchar(20)              not null,
   device_access_revoked     boolean                  not null,
   device_type               varchar(20),
   os_version                varchar(50),
   last_login_user           integer,
   last_login_at             timestamp with time zone,
   user_last_login_at        timestamp with time zone,
   preshared_key             varchar(128),
   private_key               varchar(128),
   dns_status                varchar(20),
   private_ip                inet,
   public_ip                 inet,
   auth_request              varchar(8)               not null,
   compliance                varchar(15),
   is_craas                  boolean                  not null,
   is_zta                    boolean                  not null,
   mac_address               varchar(17),
   mac_oui                   varchar(255),
   ipv6_address              inet,
   mdm_id                    varchar(40),
   raw_mac_address           varchar(12),
   is_guest                  boolean                  not null,
   model_serial_number       varchar(255),
   created_by_email          varchar(255),
   license_type              varchar(20)              not null,
   alias                     varchar(31),
   description               varchar(255),
   is_posture_valid          boolean default true,
   connected                 boolean                  not null,
   constraint resources_device_created_by_id_mac_address_d146c577_uniq
       unique (created_by_id, mac_address)
);


alter table resources_device
   owner to postgres;


create index resources_device_id_index
   on resources_device (id);


create table resources_devicegroup
(
   id               uuid                     not null
       primary key,
   created_at       timestamp with time zone not null,
   last_modified_at timestamp with time zone not null,
   is_deleted       boolean                  not null,
   deleted_at       timestamp with time zone,
   code             varchar(255)             not null,
   name             varchar(255)             not null,
   description      text,
   workspace_id     integer                  not null,
   is_default       boolean                  not null,
   mac_oui          varchar(50)[],
   meta_data        jsonb,
   type             varchar(100)
);


alter table resources_devicegroup
   owner to postgres;


create table resources_devicegroup_devices
(
   id             serial
       primary key,
   devicegroup_id uuid not null
       constraint resources_devicegrou_devicegroup_id_739896fb_fk_resources
           references resources_devicegroup
           deferrable initially deferred,
   device_id      uuid not null
       constraint resources_devicegrou_device_id_28a5afee_fk_resources
           references resources_device
           deferrable initially deferred,
   constraint resources_devicegroup_de_devicegroup_id_device_id_5005b219_uniq
       unique (devicegroup_id, device_id)
);


alter table resources_devicegroup_devices
   owner to postgres;


create index resources_devicegroup_devices_devicegroup_id_739896fb
   on resources_devicegroup_devices (devicegroup_id);


create index resources_devicegroup_devices_device_id_28a5afee
   on resources_devicegroup_devices (device_id);


create table resources_service
(
   id                       uuid                     not null
       primary key,
   created_at               timestamp with time zone not null,
   last_modified_at         timestamp with time zone not null,
   is_deleted               boolean                  not null,
   deleted_at               timestamp with time zone,
   code                     varchar(255)             not null,
   name                     varchar(255)             not null,
   alias                    varchar(255),
   domain_name              varchar(512),
   url                      varchar(512),
   description              text,
   port                     integer                  not null,
   protocol                 varchar(20),
   status                   varchar(25)              not null,
   run_as_agentless         boolean                  not null,
   authentication_method    varchar(25)              not null,
   workspace_id             integer,
   is_active                boolean                  not null,
   sub_domain_url           varchar(512),
   last_status_update       timestamp with time zone,
   forwarded_port           integer,
   is_port_forwarded        boolean                  not null,
   path                     varchar(512)             not null,
   local_ip                 inet,
   is_url_hidden            boolean                  not null,
   connection_last_updated  timestamp with time zone,
   is_sub_domain_registered boolean                  not null,
   type                     varchar(32),
   azure_load_balancer_id   uuid,

   gcp_load_balancer_id     uuid,

   host_id                  uuid,

   load_balancer_id         uuid,

   is_discovered            boolean                  not null,
   constraint resources_service_host_id_name_port_7db7f65d_uniq
       unique (host_id, name, port)
);


alter table resources_service
   owner to postgres;


create index resources_service_azure_load_balancer_id_a32c9294
   on resources_service (azure_load_balancer_id);


create index resources_service_gcp_load_balancer_id_e34b374e
   on resources_service (gcp_load_balancer_id);


create index resources_service_host_id_7619f6b9
   on resources_service (host_id);


create index resources_service_load_balancer_id_5908025e
   on resources_service (load_balancer_id);


create table resources_project
(
   id               uuid                     not null
       primary key,
   created_at       timestamp with time zone not null,
   last_modified_at timestamp with time zone not null,
   is_deleted       boolean                  not null,
   deleted_at       timestamp with time zone,
   code             varchar(255)             not null,
   name             varchar(255)             not null,
   description      text,
   workspace_id     integer,
   is_default       boolean                  not null,
   constraint resources_project_workspace_id_name_7815df53_uniq
       unique (workspace_id, name)
);


alter table resources_project
   owner to postgres;


create table resources_projectservice
(
   id               uuid                     not null
       primary key,
   created_at       timestamp with time zone not null,
   last_modified_at timestamp with time zone not null,
   is_deleted       boolean                  not null,
   deleted_at       timestamp with time zone,
   workspace_id     integer,
   project_id       uuid                     not null
       constraint resources_projectser_project_id_69ea58d7_fk_resources
           references resources_project
           deferrable initially deferred,
   service_id       uuid                     not null
       constraint resources_projectser_service_id_f23429d9_fk_resources
           references resources_service
           deferrable initially deferred
);


alter table resources_projectservice
   owner to postgres;


create index resources_projectservice_project_id_69ea58d7
   on resources_projectservice (project_id);


create index resources_projectservice_service_id_f23429d9
   on resources_projectservice (service_id);


create table policies_policy
(
   id                            uuid                     not null
       primary key,
   created_at                    timestamp with time zone not null,
   last_modified_at              timestamp with time zone not null,
   is_deleted                    boolean                  not null,
   deleted_at                    timestamp with time zone,
   code                          varchar(255)             not null,
   name                          varchar(255)             not null,
   description                   text,
   workspace_id                  integer                  not null,
   state                         varchar(20)              not null,
   last_status_update            timestamp with time zone,
   network_enabled               boolean                  not null,
   service_enabled               boolean                  not null,
   is_xiq_synced                 boolean                  not null,
   workspace_all_devices_allowed boolean                  not null,
   workspace_all_users_allowed   boolean                  not null,
   is_agentbased                 boolean                  not null,
   is_agentless                  boolean                  not null,
   rank                          integer,
   allow_all                     boolean                  not null
);


alter table policies_policy
   owner to postgres;
