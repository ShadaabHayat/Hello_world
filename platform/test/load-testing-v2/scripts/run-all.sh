#!/bin/bash
mkdir -p /reports
chmod 777 /reports
k6 run /app/scripts/main.js
echo "All tests completed"
