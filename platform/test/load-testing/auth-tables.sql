create table users_user
(
   id                 serial
       primary key,
   password           varchar(128)             not null,
   last_login         timestamp with time zone,
   created_on         timestamp with time zone not null,
   last_modified_at   timestamp with time zone not null,
   is_deleted         boolean                  not null,
   status             varchar(25)              not null,
   email              varchar(254)             not null
       unique,
   first_name         varchar(128)             not null,
   last_name          varchar(128),
   is_staff           boolean                  not null,
   is_password_set    boolean                  not null,
   is_superuser       boolean                  not null,
   verification_code  varchar(6),
   role_id            integer,
   profile_image      varchar(100),
   deleted_at         timestamp with time zone,
   is_2fa             boolean                  not null,
   secret_2fa_key     varchar(32)
       unique,
   is_synced          boolean                  not null,
   scim_external_id   varchar(254)             not null,
   scim_username      varchar(254)             not null,
   is_domain_approved boolean                  not null,
   is_xcd_admin       boolean                  not null
);


alter table users_user
   owner to postgres;


create index users_user_email_243f6e77_like
   on users_user (email varchar_pattern_ops);


create index users_user_role_id_854f2687
   on users_user (role_id);


create index users_user_secret_2fa_key_24a42bbe_like
   on users_user (secret_2fa_key varchar_pattern_ops);


create table users_useraccessgroups
(
   id               serial
       primary key,
   created_on       timestamp with time zone not null,
   last_modified_at timestamp with time zone not null,
   is_deleted       boolean                  not null,
   deleted_at       timestamp with time zone,
   access_group_id  uuid,
   user_id          integer                  not null
       constraint users_useraccessgroups_user_id_0051a578_fk_users_user_id
           references users_user
           deferrable initially deferred,
   is_default       boolean                  not null,
   constraint users_useraccessgroups_user_id_access_group_id_7f516027_uniq
       unique (user_id, access_group_id)
);


alter table users_useraccessgroups
   owner to postgres;


create index users_useraccessgroups_user_id_0051a578
   on users_useraccessgroups (user_id);
