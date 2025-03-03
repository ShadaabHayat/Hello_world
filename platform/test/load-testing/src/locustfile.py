from locust import HttpUser, task, between, events
import data_generators
import db_operations

@events.test_start.add_listener
def on_test_start(**kwargs):
    """Connect to the databases when the test starts."""
    db_operations.connect_to_dbs()

@events.test_stop.add_listener
def on_test_stop(**kwargs):
    """Close the database connections when the test stops."""
    db_operations.close_db_connections()

class LoadTest(HttpUser):
    wait_time = between(1, 3)  # Wait between 1-3 seconds between tasks

    @task(3)
    def insert_device(self):
        """Insert a new device record."""
        try:
            data = data_generators.generate_device_data()
            db_operations.insert_device(data)
        except Exception as e:
            print(f"Error in insert_device task: {e}")

    @task(2)
    def insert_device_group(self):
        """Insert a new device group record."""
        try:
            data = data_generators.generate_device_group_data()
            db_operations.insert_device_group(data)
        except Exception as e:
            print(f"Error in insert_device_group task: {e}")

    @task(2)
    def insert_service(self):
        """Insert a new service record."""
        try:
            data = data_generators.generate_service_data()
            db_operations.insert_service(data)
        except Exception as e:
            print(f"Error in insert_service task: {e}")

    @task(2)
    def insert_project(self):
        """Insert a new project record."""
        try:
            data = data_generators.generate_project_data()
            db_operations.insert_project(data)
        except Exception as e:
            print(f"Error in insert_project task: {e}")

    @task(1)
    def insert_device_group_devices(self):
        """Insert a new device group devices record."""
        try:
            device_data = data_generators.generate_device_data()
            db_operations.insert_device(device_data)

            group_data = data_generators.generate_device_group_data()
            db_operations.insert_device_group(group_data)

            relation_data = data_generators.generate_device_group_devices_data(device_data['id'], group_data['id'])
            db_operations.insert_device_group_devices(relation_data)
        except Exception as e:
            print(f"Error in insert_device_group_devices task: {e}")

    @task(1)
    def insert_project_service(self):
        """Insert a new project service record."""
        try:
            project_data = data_generators.generate_project_data()
            db_operations.insert_project(project_data)

            service_data = data_generators.generate_service_data()
            db_operations.insert_service(service_data)

            relation_data = data_generators.generate_project_service_data(project_data['id'], service_data['id'])
            db_operations.insert_project_service(relation_data)
        except Exception as e:
            print(f"Error in insert_project_service task: {e}")

    @task(2)
    def insert_policy(self):
        """Insert a new policy record."""
        try:
            data = data_generators.generate_policy_data()
            db_operations.insert_policy(data)
        except Exception as e:
            print(f"Error in insert_policy task: {e}")

    @task(2)
    def insert_user(self):
        """Insert a new user record."""
        try:
            data = data_generators.generate_user_data()
            user_id = db_operations.insert_user(data)

            if user_id:
                access_group_data = data_generators.generate_user_access_group_data(user_id)
                db_operations.insert_user_access_group(access_group_data)
        except Exception as e:
            print(f"Error in insert_user task: {e}")
