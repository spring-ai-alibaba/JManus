#
# Copyright 2024-2025 the original author or authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

##@ Docker

DOCKER_REGISTRY := ghcr.io
DOCKER_ORG := spring-ai-alibaba
DOCKER_IMAGE_NAME := lynxe
DOCKER_CONTAINER_NAME := lynxe-container

# Build the Docker image
.PHONY: docker-build
docker-build: ## Build Docker image
	@$(LOG_TARGET)
	@docker build -t $(DOCKER_REGISTRY)/$(DOCKER_ORG)/$(DOCKER_IMAGE_NAME):latest -f deploy/Dockerfile .

# Build and run
.PHONY: docker-build-run
docker-build-run: ## Build and run Docker container
docker-build-run: docker-build docker-run

# Run default image (ghcr.io/spring-ai-aliabba/lynxe:latest)
.PHONY: docker-run
docker-run: ## Run Docker container
	@$(LOG_TARGET)
	@docker run --name $(DOCKER_CONTAINER_NAME) -p 18080:18080 -d $(DOCKER_REGISTRY)/$(DOCKER_ORG)/$(DOCKER_IMAGE_NAME):latest

# Remove image
.PHONY: docker-rmi
docker-rmi: ## Remove Docker image
	@$(LOG_TARGET)
	@docker rmi -f $(DOCKER_REGISTRY)/$(DOCKER_ORG)/$(DOCKER_IMAGE_NAME):latest
