# src/config_loader.py

import os
import yaml
import logging

logger = logging.getLogger(__name__)

def load_ownership_map():
    path = os.getenv("OWNERSHIP_MAP_PATH", "/app/src/config/producer_owners.yaml")

    try:
        with open(path, 'r') as f:
            data = yaml.safe_load(f)
            logger.info(f"Loaded ownership map from {path}")
            return data
    except Exception as e:
        logger.error(f"Failed to load ownership map from {path}: {e}")
        return {}
