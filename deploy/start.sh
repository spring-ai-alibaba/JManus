#!/bin/bash
# Copyright 2024-2025 the original author or authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Color definitions
RESET='\033[0m'
BOLD='\033[1m'
DIM='\033[2m'
CYAN='\033[36m'
GREEN='\033[32m'
YELLOW='\033[33m'
BLUE='\033[34m'
MAGENTA='\033[35m'

# Print banner
echo -e "${CYAN}${BOLD}"
cat << "EOF"
     ██╗███╗   ███╗ █████╗ ███╗   ██╗██╗   ██╗███████╗
     ██║████╗ ████║██╔══██╗████╗  ██║██║   ██║██╔════╝
     ██║██╔████╔██║███████║██╔██╗ ██║██║   ██║███████╗
██   ██║██║╚██╔╝██║██╔══██║██║╚██╗██║██║   ██║╚════██║
╚█████╔╝██║ ╚═╝ ██║██║  ██║██║ ╚████║╚██████╔╝███████║
 ╚════╝ ╚═╝     ╚═╝╚═╝  ╚═╝╚═╝  ╚═══╝ ╚═════╝ ╚══════╝
EOF
echo -e "${RESET}"
echo -e "${DIM}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RESET}"
echo -e "${BLUE}${BOLD}  Spring AI Alibaba - Web Automation Platform${RESET}"
echo -e "${DIM}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RESET}"
echo ""

# System Information
echo -e "${MAGENTA}${BOLD}📊 System Information${RESET}"
echo -e "${CYAN}  ├─${RESET} Platform     : ${GREEN}$TARGETPLATFORM${RESET}"
echo -e "${CYAN}  ├─${RESET} Architecture : ${GREEN}$TARGETARCH${RESET}"
echo -e "${CYAN}  ├─${RESET} Java Version : ${GREEN}$(java -version 2>&1 | head -1)${RESET}"
echo -e "${CYAN}  └─${RESET} Playwright   : ${GREEN}$(playwright --version)${RESET}"
echo ""

# Browser Information
echo -e "${MAGENTA}${BOLD}🌐 Browser Configuration${RESET}"
echo -e "${CYAN}  ├─${RESET} Checking browser installation..."
if [ -d "/root/.cache/ms-playwright" ]; then
    BROWSER_COUNT=$(ls -1 /root/.cache/ms-playwright 2>/dev/null | wc -l)
    echo -e "${CYAN}  ├─${RESET} Found ${GREEN}${BROWSER_COUNT}${RESET} browser(s) installed"

    CHROME_PATH=$(find /root/.cache/ms-playwright -name "chrome*" -type f 2>/dev/null | head -1)
    if [ -n "$CHROME_PATH" ]; then
        echo -e "${CYAN}  └─${RESET} Chromium     : ${GREEN}✓ Ready${RESET}"
    else
        echo -e "${CYAN}  └─${RESET} Chromium     : ${YELLOW}⚠ Not found${RESET}"
    fi
else
    echo -e "${CYAN}  └─${RESET} Status       : ${YELLOW}⚠ Browser cache not found${RESET}"
fi
echo ""

# Environment Setup
echo -e "${MAGENTA}${BOLD}⚙️  Environment Setup${RESET}"
echo -e "${CYAN}  ├─${RESET} Setting Playwright browser path..."
export PLAYWRIGHT_BROWSERS_PATH=/root/.cache/ms-playwright
echo -e "${CYAN}  ├─${RESET} PLAYWRIGHT_BROWSERS_PATH=${GREEN}${PLAYWRIGHT_BROWSERS_PATH}${RESET}"
echo -e "${CYAN}  ├─${RESET} Configuring Docker environment..."
export DOCKER_ENV=true
echo -e "${CYAN}  └─${RESET} DOCKER_ENV=${GREEN}${DOCKER_ENV}${RESET}"
echo ""

# Application Startup
echo -e "${MAGENTA}${BOLD}🚀 Starting Application${RESET}"
echo -e "${CYAN}  ├─${RESET} Profile      : ${GREEN}h2, docker${RESET}"
echo -e "${CYAN}  ├─${RESET} JVM Options  : ${GREEN}${JAVA_OPTS}${RESET}"
echo -e "${CYAN}  └─${RESET} Working Dir  : ${GREEN}/app/extracted${RESET}"
echo ""
echo -e "${DIM}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RESET}"
echo -e "${YELLOW}${BOLD}⏳ Launching JManus...${RESET}"
echo -e "${DIM}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RESET}"
echo ""

cd /app/extracted
exec java $JAVA_OPTS -cp /app/extracted/BOOT-INF/classes:/app/extracted/BOOT-INF/lib/*:. com.alibaba.cloud.ai.example.manus.OpenManusSpringBootApplication --spring.profiles.active=h2,docker
