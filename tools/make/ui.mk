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

##@ UI

# Build ui
.PHONY: ui-build
ui-build: ## Build the UI
	@$(LOG_TARGET)
	@bash -c "if [ -s \"$$HOME/.nvm/nvm.sh\" ]; then export NVM_DIR=\"$$HOME/.nvm\" && [ -s \"$$NVM_DIR/nvm.sh\" ] && \. \"$$NVM_DIR/nvm.sh\" && nvm use 22 > /dev/null 2>&1 || true; fi; cd ui-vue3 && pnpm install && pnpm run build"

# Run ui
.PHONY: ui-run
ui-run: ## Run the UI
	@$(LOG_TARGET)
	@bash -c "if [ -s \"$$HOME/.nvm/nvm.sh\" ]; then export NVM_DIR=\"$$HOME/.nvm\" && [ -s \"$$NVM_DIR/nvm.sh\" ] && \. \"$$NVM_DIR/nvm.sh\" && nvm use 22 > /dev/null 2>&1 || true; fi; cd ui-vue3 && pnpm install && pnpm run dev"

# Rebuild ui
.PHONY: ui-rebuild
ui-rebuild: ## Rebuild the UI
	@$(LOG_TARGET)
	@bash -c "if [ -s \"$$HOME/.nvm/nvm.sh\" ]; then export NVM_DIR=\"$$HOME/.nvm\" && [ -s \"$$NVM_DIR/nvm.sh\" ] && \. \"$$NVM_DIR/nvm.sh\" && nvm use 22 > /dev/null 2>&1 || true; fi; cd ui-vue3 && pnpm install && pnpm run build"

# UI lint
.PHONY: ui-lint
ui-lint: ## Lint the UI code
	@$(LOG_TARGET)
	@bash -c "if [ -s \"$$HOME/.nvm/nvm.sh\" ]; then export NVM_DIR=\"$$HOME/.nvm\" && [ -s \"$$NVM_DIR/nvm.sh\" ] && \. \"$$NVM_DIR/nvm.sh\" && nvm use 22 > /dev/null 2>&1 || true; fi; cd ui-vue3 && pnpm install --no-frozen-lockfile && pnpm run lint"

# Deploy UI to static directory
.PHONY: ui-deploy
ui-deploy: ## Build UI and deploy to static directory
	@$(LOG_TARGET)
	@bash -c "if [ -s \"$$HOME/.nvm/nvm.sh\" ]; then export NVM_DIR=\"$$HOME/.nvm\" && [ -s \"$$NVM_DIR/nvm.sh\" ] && \. \"$$NVM_DIR/nvm.sh\" && nvm use 22 > /dev/null 2>&1 || true; fi; cd ui-vue3 && pnpm install && pnpm run build"
	@echo "Removing existing static UI directory..."
	rm -rf src/main/resources/static/ui
	@echo "Copying built UI to static directory..."
	cp -r ui-vue3/ui src/main/resources/static/
	@echo "UI deployment completed successfully!"
