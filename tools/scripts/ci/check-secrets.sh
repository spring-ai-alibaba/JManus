#!/usr/bin/env bash
#
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
#

set -e

echo "🔍 Checking for secrets and sensitive information..."

# Common secret patterns to check for
SECRET_PATTERNS=(
  "password\s*=\s*['\"][^'\"]+['\"]"
  "secret\s*=\s*['\"][^'\"]+['\"]"
  "api[_-]?key\s*=\s*['\"][^'\"]+['\"]"
  "access[_-]?token\s*=\s*['\"][^'\"]+['\"]"
  "private[_-]?key\s*=\s*['\"][^'\"]+['\"]"
  "aws[_-]?access[_-]?key"
  "aws[_-]?secret[_-]?key"
  "github[_-]?token"
  "jwt[_-]?secret"
  "database[_-]?password"
  "db[_-]?password"
  "mysql[_-]?password"
  "postgres[_-]?password"
  "redis[_-]?password"
  "mongodb[_-]?password"
  "encryption[_-]?key"
  "signing[_-]?key"
  "auth[_-]?secret"
  "session[_-]?secret"
  "cookie[_-]?secret"
)

# Files to exclude from secret checking
EXCLUDE_PATTERNS=(
  "node_modules/"
  ".git/"
  "target/"
  "dist/"
  "build/"
  ".github/workflows/"
  "*.log"
  "*.tmp"
  "*.temp"
  "h2-data/"
  "extensions/"
  "src/main/resources/application-*.yml"
  "src/main/java/**/Constants.java"
  "src/main/java/**/ConfigConstants.java"
  "src/main/java/**/DatabaseConfigConstants.java"
)

VIOLATIONS=()

# Function to check if file should be excluded
should_exclude() {
  local file="$1"
  for pattern in "${EXCLUDE_PATTERNS[@]}"; do
    if [[ "$file" == *"$pattern"* ]]; then
      return 0
    fi
  done
  return 1
}

# Function to check if content is a real secret (not template/placeholder)
is_real_secret() {
  local content="$1"
  # Check if it's a placeholder/template value
  if [[ "$content" =~ (your_|placeholder|example|test|demo|default|localhost|127\.0\.0\.1|changeme|password123|admin123) ]]; then
    return 1
  fi
  # Check if it's a constant definition (Java)
  if [[ "$content" =~ (public\s+static\s+final|private\s+static\s+final|PROP_|CONFIG_) ]]; then
    return 1
  fi
  # Check if it's a comment or documentation
  if [[ "$content" =~ (^[\s]*//|^[\s]*\*|^[\s]*#) ]]; then
    return 1
  fi
  return 0
}

# Find all relevant files
echo "📁 Scanning files for secrets..."
while IFS= read -r -d '' file; do
  if should_exclude "$file"; then
    continue
  fi

  # Check each pattern
  for pattern in "${SECRET_PATTERNS[@]}"; do
    if grep -qiE "$pattern" "$file" 2>/dev/null; then
      # Get the matching line and check if it's a real secret
      matching_line=$(grep -iE "$pattern" "$file" 2>/dev/null | head -1)
      if is_real_secret "$matching_line"; then
        echo "⚠️  Potential secret found in: $file"
        echo "   Pattern: $pattern"
        echo "   Content: $matching_line"
        VIOLATIONS+=("$file:$pattern")
      else
        echo "ℹ️  Skipping template/placeholder in: $file"
        echo "   Pattern: $pattern"
        echo "   Content: $matching_line"
      fi
    fi
  done
done < <(find . -type f \( -name "*.java" -o -name "*.js" -o -name "*.ts" -o -name "*.vue" -o -name "*.yml" -o -name "*.yaml" -o -name "*.properties" -o -name "*.xml" -o -name "*.json" -o -name "*.md" \) -print0)

# Check for hardcoded credentials in specific file types
echo "🔍 Checking configuration files..."

# Check application.yml and application-*.yml files (but skip template files)
for config_file in src/main/resources/application*.yml; do
  if [ -f "$config_file" ]; then
    # Skip application-*.yml files as they are templates
    if [[ "$config_file" == *"application-"* ]]; then
      echo "ℹ️  Skipping template file: $config_file"
      continue
    fi

    if grep -qiE "(password|secret|key|token)\s*:\s*['\"][^'\"]+['\"]" "$config_file"; then
      # Check if it's a real secret
      matching_line=$(grep -iE "(password|secret|key|token)\s*:\s*['\"][^'\"]+['\"]" "$config_file" | head -1)
      if is_real_secret "$matching_line"; then
        echo "⚠️  Potential hardcoded credentials in: $config_file"
        echo "   Content: $matching_line"
        VIOLATIONS+=("$config_file:hardcoded-credentials")
      else
        echo "ℹ️  Skipping template/placeholder in: $config_file"
        echo "   Content: $matching_line"
      fi
    fi
  fi
done

# Check for common environment variable patterns that might contain secrets
echo "🔍 Checking for environment variable patterns..."
if grep -r "process\.env\." . --include="*.js" --include="*.ts" --include="*.vue" 2>/dev/null | grep -v node_modules | grep -v ".git" | grep -E "(SECRET|PASSWORD|KEY|TOKEN)"; then
  echo "⚠️  Found environment variable usage that might contain secrets"
  VIOLATIONS+=("env-vars:potential-secrets")
fi

# Report results
if [ ${#VIOLATIONS[@]} -eq 0 ]; then
  echo "✅ No secrets or sensitive information found!"
else
  echo ""
  echo "🚨 SECRETS DETECTED!"
  echo "=================="
  echo ""
  echo "The following potential secrets were found:"
  echo ""
  for violation in "${VIOLATIONS[@]}"; do
    echo "  - $violation"
  done
  echo ""
  echo "## 🔒 Security Recommendations:"
  echo ""
  echo "1. **Never commit secrets to version control**"
  echo "2. **Use environment variables** for sensitive data:"
  echo "   \`\`\`yaml"
  echo "   password: \${DB_PASSWORD:defaultPassword}"
  echo "   \`\`\`"
  echo ""
  echo "3. **Use external secret management** (e.g., HashiCorp Vault, AWS Secrets Manager)"
  echo ""
  echo "4. **Use .env files** (and add them to .gitignore):"
  echo "   \`\`\`bash"
  echo "   echo '.env' >> .gitignore"
  echo "   \`\`\`"
  echo ""
  echo "5. **Use GitHub Secrets** for CI/CD pipelines"
  echo ""
  echo "Please remove or properly secure these secrets before committing."
  exit 1
fi
