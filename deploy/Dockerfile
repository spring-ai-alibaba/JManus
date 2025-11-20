# Copyright 2025 the original author or authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Multi-stage build for better layer caching and smaller final image
ARG BUILD_VERSION=4.0.6

# Runtime stage - use multi-arch base image
FROM eclipse-temurin:17-jdk-noble

# Add build arguments for platform detection
ARG TARGETPLATFORM
ARG BUILDPLATFORM
ARG TARGETOS
ARG TARGETARCH
ARG BUILD_VERSION

# Set working directory
WORKDIR /app

# Install system dependencies, Node.js and Playwright dependencies in a single layer
RUN apt-get update && \
    # Install basic dependencies
    apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        gnupg \
        lsb-release \
        software-properties-common \
        wget \
        unzip \
        xvfb \
        x11vnc \
        fluxbox \
        fonts-liberation \
        fonts-dejavu-core \
        fonts-noto-cjk && \
    # Install Node.js 18.x
    curl -fsSL https://deb.nodesource.com/setup_18.x | bash - && \
    apt-get install -y --no-install-recommends nodejs && \
    # Install Playwright browser dependencies
    apt-get install -y --no-install-recommends \
        libnss3 \
        libnspr4 \
        libatk-bridge2.0-0 \
        libdrm2 \
        libxkbcommon0 \
        libxcomposite1 \
        libxdamage1 \
        libxrandr2 \
        libgbm1 \
        libxss1 \
        libasound2t64 \
        libatspi2.0-0 \
        libgtk-3-0 \
        libgdk-pixbuf2.0-0 \
        libxshmfence1 \
        libatomic1 \
        libxslt1.1 \
        libwoff1 \
        libevent-2.1-7 \
        libopus0 \
        libwebpdemux2 \
        libharfbuzz-icu0 \
        libwebpmux3 \
        libenchant-2-2 \
        libsecret-1-0 \
        libhyphen0 \
        libegl1 \
        libgudev-1.0-0 \
        libevdev2 \
        libgles2 \
        libx264-dev && \
    # Install Playwright CLI globally
    npm install -g playwright && \
    # Clean up
    rm -rf /var/lib/apt/lists/*

# Copy JAR file
COPY target/lynxe.jar app.jar

# Install Playwright browsers, extract JAR and setup directories
RUN java -jar app.jar playwright-init && \
    # Extract JAR file for faster startup
    mkdir -p /app/extracted && \
    cd /app/extracted && \
    jar -xf /app/app.jar && \
    rm /app/app.jar && \
    # Verify browser installation and set permissions
    ls -la /root/.cache/ms-playwright/ && \
    find /root/.cache/ms-playwright -name "chrome*" -type f -exec chmod +x {} \; 2>/dev/null && \
    find /root/.cache/ms-playwright -name "headless_shell*" -type f -exec chmod +x {} \; 2>/dev/null && \
    # Create necessary directories
    mkdir -p /app/logs /app/h2-data /app/playwright && \
    # Verify installations
    java -version && \
    node --version && \
    npm --version && \
    playwright --version

# Copy application configuration files
COPY src/main/resources/application*.yml /app/extracted/BOOT-INF/classes/
COPY src/main/resources/application-docker.yml /app/extracted/BOOT-INF/classes/

# Set environment variables
ENV DISPLAY=:99 \
    PLAYWRIGHT_BROWSERS_PATH=/root/.cache/ms-playwright \
    DOCKER_ENV=true \
    JAVA_OPTS="-Xmx2g -Xms1g -XX:+UseG1GC -XX:+UseContainerSupport"

# Copy startup script
COPY deploy/start.sh /app/start.sh
RUN chmod +x /app/start.sh

# Expose port
EXPOSE 18080

# Add labels for better image management
LABEL org.opencontainers.image.title="Lynxe"
LABEL org.opencontainers.image.description="Spring AI Alibaba Lynxe - Web Automation Platform"
LABEL org.opencontainers.image.vendor="Alibaba Cloud"
LABEL org.opencontainers.image.version="latest"
LABEL org.opencontainers.image.url="https://github.com/alibaba/spring-ai-alibaba"
LABEL org.opencontainers.image.source="https://github.com/alibaba/spring-ai-alibaba"

# Set entrypoint
ENTRYPOINT ["/app/start.sh"]
