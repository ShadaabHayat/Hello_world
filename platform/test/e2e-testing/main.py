
import pytest

from tests import *

@pytest.fixture(autouse=True)
def test_slow_down_tests():
    yield
    time.sleep(0)

if __name__ == "__main__":
    pytest.main([__file__])

def pytest_sessionfinish(session, exitstatus):
    cleanup.delete_all()
