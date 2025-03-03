#!/bin/bash

# Get the directory where the script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Change to the project directory where pom.xml is located
cd "$SCRIPT_DIR"

# Run checkstyle only on the staged files
mvn checkstyle:check -Dcheckstyle.includes="**/*.java" -Dcheckstyle.sourceDirectory="src"

# Store the exit code
result=$?

# Exit with the maven command's exit code
exit $result
