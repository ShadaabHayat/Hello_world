# Stage 1: Build k6 with kafka extension
FROM golang:1.23-alpine3.21 as builder

# Install build dependencies
RUN apk add --no-cache git gcc musl-dev

# Install xk6
RUN go install go.k6.io/xk6/cmd/xk6@v0.14.1

# Build k6 with extensions
WORKDIR /build
RUN xk6 build \
    --with github.com/mostafa/xk6-kafka@v0.30.0 \
    --with github.com/grafana/xk6-dashboard@latest

# Stage 2: Runtime
FROM alpine:3.21

# Install bash
RUN apk add --no-cache bash

# Copy k6 binary from builder
COPY --from=builder /build/k6 /usr/local/bin/k6

# Create directory for scripts
WORKDIR /app

# Copy scripts and any other necessary files
COPY scripts/ /app/scripts/

# Make run-all.sh executable
RUN chmod +x /app/scripts/run-all.sh

# Set entrypoint to run all scripts
ENTRYPOINT ["/app/scripts/run-all.sh"]
