import uuid
from datetime import datetime, timezone
from typing import Dict, Any
import random
from faker import Faker

fake = Faker()

def generate_device_name():
    """Generate a realistic device name."""
    prefix = random.choice(['DESKTOP', 'LAPTOP', 'MOBILE', 'TABLET'])
    suffix = fake.bothify(text='??-####')
    return f"{prefix}-{suffix}"

def generate_device_data() -> Dict[str, Any]:
    """Generate mock data for resources_device table."""
    now = datetime.now(timezone.utc)
    return {
        'id': str(uuid.uuid4()),
        'created_at': now,
        'last_modified_at': now,
        'is_deleted': False,
        'deleted_at': None,
        'code': str(uuid.uuid4()),
        'name': generate_device_name(),
        'local_ip': str(fake.ipv4_private()),
        'model_name': fake.random_element(['iPhone', 'Samsung', 'Pixel', 'MacBook', 'ThinkPad']),
        'model_number': fake.bothify(text='??###??'),
        'last_connected_location': fake.city(),
        'last_connected': now,
        'public_access_key': fake.sha1(),
        'unique_identifier': str(uuid.uuid4()),
        'access_mode': random.choice(['READ', 'WRITE', 'ADMIN']),
        'device_access_revoked': False,
        'device_type': random.choice(['MOBILE', 'DESKTOP', 'TABLET']),
        'os_version': fake.numerify(text='##.#.#'),
        'auth_request': fake.bothify(text='????####'),
        'is_craas': False,
        'is_zta': True,
        'is_guest': False,
        'license_type': 'STANDARD',
        'connected': True,
        'is_notification_enabled': True,
        'is_posture_valid': True
    }

def generate_device_group_data() -> Dict[str, Any]:
    """Generate mock data for resources_devicegroup table."""
    now = datetime.now(timezone.utc)
    return {
        'id': str(uuid.uuid4()),
        'created_at': now,
        'last_modified_at': now,
        'is_deleted': False,
        'deleted_at': None,
        'code': str(uuid.uuid4()),
        'name': f"Group_{fake.word()}",
        'description': fake.text(max_nb_chars=200),
        'workspace_id': random.randint(1, 100),
        'is_default': False,
        'type': random.choice(['STATIC', 'DYNAMIC'])
    }

def generate_service_data() -> Dict[str, Any]:
    """Generate mock data for resources_service table."""
    timestamp = datetime.now(timezone.utc)
    timestamp_hex = hex(int(timestamp.timestamp()))[2:]
    service_name = f"Service_{fake.word()}_{timestamp_hex}_{uuid.uuid4().hex[:6]}"
    domain = f"{service_name.lower().replace(' ', '-')}.example.com"
    return {
        'id': str(uuid.uuid4()),
        'created_at': timestamp,
        'last_modified_at': timestamp,
        'is_deleted': False,
        'deleted_at': None,
        'code': service_name.lower().replace(" ", "_"),
        'name': service_name,
        'description': fake.sentence(),
        'workspace_id': random.randint(1, 100),
        'domain_name': domain,
        'url': f"https://{domain}",
        'type': random.choice(['http', 'https', 'tcp', 'udp']),
        'port': random.randint(1024, 65535),
        'protocol': random.choice(['tcp', 'udp']),
        'status': 'active',
        'run_as_agentless': False,
        'authentication_method': 'none',
        'is_active': True,
        'path': '/',
        'is_url_hidden': False,
        'is_sub_domain_registered': False,
        'is_port_forwarded': False,
        'is_discovered': False
    }

def generate_project_data() -> Dict[str, Any]:
    """Generate mock data for resources_project table."""
    timestamp = datetime.now(timezone.utc)
    timestamp_hex = hex(int(timestamp.timestamp()))[2:]
    project_name = f"Project_{fake.word()}_{timestamp_hex}_{uuid.uuid4().hex[:6]}"
    return {
        'id': str(uuid.uuid4()),
        'created_at': timestamp,
        'last_modified_at': timestamp,
        'is_deleted': False,
        'deleted_at': None,
        'code': project_name.lower().replace(" ", "_"),
        'name': project_name,
        'description': fake.sentence(),
        'workspace_id': random.randint(1, 100),
        'is_default': random.choice([True, False])
    }

def generate_device_group_devices_data(device_id: uuid.UUID, group_id: uuid.UUID) -> Dict[str, Any]:
    """Generate mock data for resources_devicegroup_devices table."""
    return {
        'device_id': str(device_id),
        'devicegroup_id': str(group_id)
    }

def generate_project_service_data(project_id: uuid.UUID, service_id: uuid.UUID) -> Dict[str, Any]:
    """Generate mock data for resources_projectservice table."""
    timestamp = datetime.now(timezone.utc)
    return {
        'id': str(uuid.uuid4()),
        'created_at': timestamp,
        'last_modified_at': timestamp,
        'is_deleted': False,
        'deleted_at': None,
        'workspace_id': random.randint(1, 100),
        'project_id': str(project_id),
        'service_id': str(service_id)
    }

def generate_policy_data() -> Dict[str, Any]:
    """Generate mock data for policies_policy table."""
    now = datetime.now(timezone.utc)
    return {
        'id': str(uuid.uuid4()),
        'created_at': now,
        'last_modified_at': now,
        'is_deleted': False,
        'deleted_at': None,
        'code': str(uuid.uuid4()),
        'name': f"Policy_{fake.word()}",
        'description': fake.text(max_nb_chars=200),
        'workspace_id': random.randint(1, 100),
        'state': random.choice(['ACTIVE', 'INACTIVE', 'PENDING']),
        'last_status_update': now,
        'network_enabled': random.choice([True, False]),
        'service_enabled': random.choice([True, False]),
        'is_xiq_synced': False,
        'workspace_all_devices_allowed': random.choice([True, False]),
        'workspace_all_users_allowed': random.choice([True, False]),
        'is_agentbased': random.choice([True, False]),
        'is_agentless': random.choice([True, False]),
        'rank': random.randint(1, 100),
        'allow_all': False
    }

def generate_user_data():
    """Generate mock data for users_user table."""
    now = datetime.now(timezone.utc)
    timestamp_hex = hex(int(now.timestamp()))[2:]
    unique_id = uuid.uuid4().hex[:6]
    return {
        'password': fake.password(length=12),
        'last_login': now,
        'created_on': now,
        'last_modified_at': now,
        'is_deleted': False,
        'status': random.choice(['ACTIVE', 'INACTIVE', 'PENDING']),
        'email': f"user_{timestamp_hex}_{unique_id}@example.com",
        'first_name': fake.first_name(),
        'last_name': fake.last_name(),
        'is_staff': random.choice([True, False]),
        'is_password_set': True,
        'is_superuser': False,
        'verification_code': str(random.randint(100000, 999999)),
        'role_id': random.randint(1, 3),
        'profile_image': None,
        'deleted_at': None,
        'is_2fa': False,
        'secret_2fa_key': str(uuid.uuid4())[:32],
        'is_synced': True,
        'scim_external_id': str(uuid.uuid4()),
        'scim_username': f"user_{timestamp_hex}_{unique_id}",
        'is_domain_approved': True,
        'is_xcd_admin': False,
        'jit_external_id': str(uuid.uuid4())
    }

def generate_user_access_group_data(user_id):
    """Generate mock data for users_useraccessgroups table."""
    return {
        'created_on': fake.date_time_this_year(tzinfo=timezone.utc),
        'last_modified_at': fake.date_time_this_year(tzinfo=timezone.utc),
        'is_deleted': False,
        'deleted_at': None,
        'access_group_id': str(uuid.uuid4()),
        'user_id': user_id,
        'is_default': random.choice([True, False])
    }
