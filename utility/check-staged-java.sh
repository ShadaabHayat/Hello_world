#!/bin/bash

# Get the directory where the script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Get list of staged Java files
files=$(git diff --cached --name-only --diff-filter=ACMR | grep "\.java$" || true)

if [ -z "$files" ]; then
    echo "No Java files are staged for commit."
    exit 0
fi

# Create a temporary directory
temp_dir=$(mktemp -d)
echo "Temporary directory: $temp_dir"

# Copy staged files to temp directory maintaining directory structure
for file in $files; do
    mkdir -p "$temp_dir/$(dirname $file)"
    git show ":$file" > "$temp_dir/$file"
done

# Change to the project directory where pom.xml is located
cd "$SCRIPT_DIR"

# Run checkstyle only on the staged files
mvn checkstyle:check -Dcheckstyle.includes="**/*.java" -Dcheckstyle.sourceDirectory="$temp_dir"

# Store the exit code
result=$?

# Clean up
rm -rf "$temp_dir"

# Exit with the maven command's exit code
exit $result
