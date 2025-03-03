import os
import psycopg2
from psycopg2.extras import DictCursor
from typing import Dict, Any

# Database configurations from environment variables
RESOURCES_DB_CONFIG = {
    'dbname': os.getenv('resource_db', 'postgres'),
    'user': os.getenv('resource_user', 'postgres'),
    'password': os.getenv('resource_password', '1234'),
    'host': os.getenv('resource_host', 'localhost'),
    'port': os.getenv('resource_port', '5433')
}

AUTH_DB_CONFIG = {
    'dbname': os.getenv('auth_db', 'postgres'),
    'user': os.getenv('auth_user', 'postgres'),
    'password': os.getenv('auth_password', '1234'),
    'host': os.getenv('auth_host', 'localhost'),
    'port': os.getenv('auth_port', '5433')
}

# Global database connections
resources_db_conn = None
resources_db_cursor = None
auth_db_conn = None
auth_db_cursor = None

def connect_to_dbs():
    """Create new database connections."""
    global resources_db_conn, resources_db_cursor, auth_db_conn, auth_db_cursor
    try:
        if resources_db_conn is None or resources_db_conn.closed:
            resources_db_conn = psycopg2.connect(**RESOURCES_DB_CONFIG)
            resources_db_cursor = resources_db_conn.cursor(cursor_factory=DictCursor)
            print("Successfully connected to the resources database")

        if auth_db_conn is None or auth_db_conn.closed:
            auth_db_conn = psycopg2.connect(**AUTH_DB_CONFIG)
            auth_db_cursor = auth_db_conn.cursor(cursor_factory=DictCursor)
            print("Successfully connected to the auth database")
    except Exception as e:
        print(f"Failed to connect to databases: {e}")
        raise

def ensure_db_connections():
    """Ensure database connections are active."""
    global resources_db_conn, resources_db_cursor, auth_db_conn, auth_db_cursor
    try:
        if resources_db_conn is None or resources_db_conn.closed:
            connect_to_dbs()
        if auth_db_conn is None or auth_db_conn.closed:
            connect_to_dbs()
        # Test the connections
        resources_db_cursor.execute("SELECT 1")
        auth_db_cursor.execute("SELECT 1")
    except (psycopg2.OperationalError, psycopg2.InterfaceError):
        connect_to_dbs()

def close_db_connections():
    """Close all database connections."""
    global resources_db_conn, resources_db_cursor, auth_db_conn, auth_db_cursor
    if resources_db_cursor:
        resources_db_cursor.close()
    if resources_db_conn:
        resources_db_conn.close()
        print("Resources database connection closed")
    if auth_db_cursor:
        auth_db_cursor.close()
    if auth_db_conn:
        auth_db_conn.close()
        print("Auth database connection closed")

def insert_device(data: Dict[str, Any]) -> None:
    """Insert a new device record."""
    ensure_db_connections()
    try:
        query = """
            INSERT INTO resources_device (
                id, created_at, last_modified_at, is_deleted, deleted_at,
                code, name, local_ip, model_name, model_number,
                last_connected_location, last_connected, public_access_key,
                unique_identifier, access_mode, device_access_revoked,
                device_type, os_version, auth_request, is_craas, is_zta,
                is_guest, license_type, connected, is_notification_enabled,
                is_posture_valid
            ) VALUES (
                %(id)s, %(created_at)s, %(last_modified_at)s, %(is_deleted)s,
                %(deleted_at)s, %(code)s, %(name)s, %(local_ip)s,
                %(model_name)s, %(model_number)s, %(last_connected_location)s,
                %(last_connected)s, %(public_access_key)s,
                %(unique_identifier)s, %(access_mode)s,
                %(device_access_revoked)s, %(device_type)s, %(os_version)s,
                %(auth_request)s, %(is_craas)s, %(is_zta)s, %(is_guest)s,
                %(license_type)s, %(connected)s, %(is_notification_enabled)s,
                %(is_posture_valid)s
            )
        """
        resources_db_cursor.execute(query, data)
        resources_db_conn.commit()
    except Exception as e:
        print(f"Error inserting device: {e}")
        if resources_db_conn:
            resources_db_conn.rollback()
        raise

def insert_device_group(data: Dict[str, Any]) -> None:
    """Insert a new device group record."""
    ensure_db_connections()
    try:
        query = """
            INSERT INTO resources_devicegroup (
                id, created_at, last_modified_at, is_deleted, deleted_at,
                code, name, description, workspace_id, is_default, type
            ) VALUES (
                %(id)s, %(created_at)s, %(last_modified_at)s, %(is_deleted)s,
                %(deleted_at)s, %(code)s, %(name)s, %(description)s,
                %(workspace_id)s, %(is_default)s, %(type)s
            )
        """
        resources_db_cursor.execute(query, data)
        resources_db_conn.commit()
    except Exception as e:
        print(f"Error inserting device group: {e}")
        if resources_db_conn:
            resources_db_conn.rollback()
        raise

def insert_service(data: Dict[str, Any]) -> None:
    """Insert a new service record."""
    ensure_db_connections()
    try:
        query = """
            INSERT INTO resources_service (
                id, created_at, last_modified_at, is_deleted, deleted_at,
                code, name, description, workspace_id, domain_name, url,
                type, port, protocol, status, run_as_agentless,
                authentication_method, is_active, path, is_url_hidden,
                is_sub_domain_registered, is_port_forwarded, is_discovered
            ) VALUES (
                %(id)s, %(created_at)s, %(last_modified_at)s, %(is_deleted)s,
                %(deleted_at)s, %(code)s, %(name)s, %(description)s,
                %(workspace_id)s, %(domain_name)s, %(url)s, %(type)s,
                %(port)s, %(protocol)s, %(status)s, %(run_as_agentless)s,
                %(authentication_method)s, %(is_active)s, %(path)s,
                %(is_url_hidden)s, %(is_sub_domain_registered)s,
                %(is_port_forwarded)s, %(is_discovered)s
            )
        """
        resources_db_cursor.execute(query, data)
        resources_db_conn.commit()
    except Exception as e:
        print(f"Error inserting service: {e}")
        if resources_db_conn:
            resources_db_conn.rollback()
        raise

def insert_project(data: Dict[str, Any]) -> None:
    """Insert a new project record."""
    ensure_db_connections()
    try:
        query = """
            INSERT INTO resources_project (
                id, created_at, last_modified_at, is_deleted, deleted_at,
                code, name, description, workspace_id, is_default
            ) VALUES (
                %(id)s, %(created_at)s, %(last_modified_at)s, %(is_deleted)s,
                %(deleted_at)s, %(code)s, %(name)s, %(description)s,
                %(workspace_id)s, %(is_default)s
            )
        """
        resources_db_cursor.execute(query, data)
        resources_db_conn.commit()
    except Exception as e:
        print(f"Error inserting project: {e}")
        if resources_db_conn:
            resources_db_conn.rollback()
        raise

def insert_device_group_devices(data: Dict[str, Any]) -> None:
    """Insert a new device group devices record."""
    ensure_db_connections()
    try:
        query = """
            INSERT INTO resources_devicegroup_devices (
                device_id, devicegroup_id
            ) VALUES (
                %(device_id)s, %(devicegroup_id)s
            )
        """
        resources_db_cursor.execute(query, data)
        resources_db_conn.commit()
    except Exception as e:
        print(f"Error inserting device group devices: {e}")
        if resources_db_conn:
            resources_db_conn.rollback()
        raise

def insert_project_service(data: Dict[str, Any]) -> None:
    """Insert a new project service record."""
    ensure_db_connections()
    try:
        query = """
            INSERT INTO resources_projectservice (
                id, created_at, last_modified_at, is_deleted, deleted_at,
                workspace_id, project_id, service_id
            ) VALUES (
                %(id)s, %(created_at)s, %(last_modified_at)s, %(is_deleted)s,
                %(deleted_at)s, %(workspace_id)s, %(project_id)s, %(service_id)s
            )
        """
        resources_db_cursor.execute(query, data)
        resources_db_conn.commit()
    except Exception as e:
        print(f"Error inserting project service: {e}")
        if resources_db_conn:
            resources_db_conn.rollback()
        raise

def insert_policy(data: Dict[str, Any]) -> None:
    """Insert a new policy record."""
    ensure_db_connections()
    try:
        query = """
            INSERT INTO policies_policy (
                id, created_at, last_modified_at, is_deleted, deleted_at,
                code, name, description, workspace_id, state, last_status_update,
                network_enabled, service_enabled, is_xiq_synced,
                workspace_all_devices_allowed, workspace_all_users_allowed,
                is_agentbased, is_agentless, rank, allow_all
            ) VALUES (
                %(id)s, %(created_at)s, %(last_modified_at)s, %(is_deleted)s,
                %(deleted_at)s, %(code)s, %(name)s, %(description)s,
                %(workspace_id)s, %(state)s, %(last_status_update)s,
                %(network_enabled)s, %(service_enabled)s, %(is_xiq_synced)s,
                %(workspace_all_devices_allowed)s, %(workspace_all_users_allowed)s,
                %(is_agentbased)s, %(is_agentless)s, %(rank)s, %(allow_all)s
            )
        """
        resources_db_cursor.execute(query, data)
        resources_db_conn.commit()
    except Exception as e:
        print(f"Error inserting policy: {e}")
        if resources_db_conn:
            resources_db_conn.rollback()
        raise

def insert_user(data: Dict[str, Any]) -> None:
    """Insert a new user record."""
    ensure_db_connections()
    try:
        query = """
            INSERT INTO users_user (
                password, last_login, created_on, last_modified_at, is_deleted,
                status, email, first_name, last_name, is_staff, is_password_set,
                is_superuser, verification_code, role_id, profile_image,
                deleted_at, is_2fa, secret_2fa_key, is_synced, scim_external_id,
                scim_username, is_domain_approved, is_xcd_admin, jit_external_id
            ) VALUES (
                %(password)s, %(last_login)s, %(created_on)s, %(last_modified_at)s,
                %(is_deleted)s, %(status)s, %(email)s, %(first_name)s,
                %(last_name)s, %(is_staff)s, %(is_password_set)s,
                %(is_superuser)s, %(verification_code)s, %(role_id)s,
                %(profile_image)s, %(deleted_at)s, %(is_2fa)s,
                %(secret_2fa_key)s, %(is_synced)s, %(scim_external_id)s,
                %(scim_username)s, %(is_domain_approved)s, %(is_xcd_admin)s,
                %(jit_external_id)s
            )
        """
        auth_db_cursor.execute(query, data)
        auth_db_conn.commit()
    except Exception as e:
        print(f"Error inserting user: {e}")
        if auth_db_conn:
            auth_db_conn.rollback()
        raise

def insert_user_access_group(data: Dict[str, Any]) -> None:
    """Insert a new user access group record."""
    ensure_db_connections()
    try:
        query = """
            INSERT INTO users_useraccessgroups (
                created_on, last_modified_at, is_deleted, deleted_at,
                access_group_id, user_id, is_default
            ) VALUES (
                %(created_on)s, %(last_modified_at)s, %(is_deleted)s,
                %(deleted_at)s, %(access_group_id)s, %(user_id)s, %(is_default)s
            )
        """
        auth_db_cursor.execute(query, data)
        auth_db_conn.commit()
    except Exception as e:
        print(f"Error inserting user access group: {e}")
        if auth_db_conn:
            auth_db_conn.rollback()
        raise
