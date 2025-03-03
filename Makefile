.PHONY: install test-unit pre-commit build lint clean coverage coverage-report coverage-check coverage-open security-scan security-scan-full security-scan-help horusec-install

install:
	cd utility && sh ./setup-pre-commit-hooks.sh

pre-commit:
	cd resources/kafka-connect/custom-smt && git ls-files -- '*.java' | xargs pre-commit run --files

test-unit:
	cd resources/kafka-connect/custom-smt && mvn test

build:
	cd resources/kafka-connect/custom-smt && mvn clean package

lint:
	cd resources/kafka-connect/custom-smt && mvn checkstyle:check

clean:
	cd resources/kafka-connect/custom-smt && mvn clean
	cd resources/kafka-connect/custom-smt && rm -rf target/

# Run tests with coverage
coverage:
	cd resources/kafka-connect/custom-smt && mvn clean jacoco:prepare-agent test jacoco:report

# Only generate coverage report (assumes tests were already run)
coverage-report:
	cd resources/kafka-connect/custom-smt && mvn jacoco:report

# Check if coverage meets minimum threshold (50%)
coverage-check:
	cd resources/kafka-connect/custom-smt && mvn verify

# Open coverage report in browser
coverage-open:
	cd resources/kafka-connect/custom-smt && open target/site/jacoco/index.html

# Security scanning targets
# Install Horusec security scanner
horusec-install:
	curl -fsSL https://raw.githubusercontent.com/ZupIT/horusec/main/deployments/scripts/install.sh | sudo bash -s latest

# Run quick security scan (without Docker)
security-scan:
	@echo "Running quick security scan..."
	@echo "Java files to be scanned:"
	@find ./resources/kafka-connect/custom-smt/src/main/java -name "*.java" -type f
	horusec start -p="./resources/kafka-connect/custom-smt/src/main/java" --disable-docker -O="security-report.json" -o="json" -i="**/target/**" --log-level=trace

# Run comprehensive security scan with all analyzers
security-scan-full:
	@echo "Running comprehensive security scan..."
	@echo "Java files to be scanned:"
	@find ./resources/kafka-connect/custom-smt/src/main/java -name "*.java" -type f
	horusec start -p="./resources/kafka-connect/custom-smt/src/main/java" \
		--information-severity=true \
		-O="security-report-full.json" \
		-o="json" \
		--disable-docker \
		--return-error \
		-i="**/target/**" \
		--log-level=trace

# Show security scan help
security-scan-help:
	horusec start --help
