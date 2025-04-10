FROM hp2c/compss:tempfixagents

# Install curl and jq
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl jq && \
    rm -rf /var/lib/apt/lists/*

# Copy application files
COPY app/ /app/
WORKDIR /app

# Default command (can be overridden in deploy)
CMD ["tail", "-f", "/dev/null"]