#!/bin/bash

# Test script for secret check optimization

echo "🔍 Testing optimized secret check..."

# Common secret patterns to check for
SECRET_PATTERNS=(
  "password\s*=\s*['\"][^'\"]+['\"]"
  "mysql[_-]?password"
)

# Files to exclude from secret checking
EXCLUDE_PATTERNS=(
  "src/main/resources/application-*.yml"
  "src/main/java/**/Constants.java"
  "src/main/java/**/ConfigConstants.java"
  "src/main/java/**/DatabaseConfigConstants.java"
)

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

VIOLATIONS=()

# Test the specific files that were causing issues
echo "📁 Testing specific files..."

# Test application-mysql.yml
if [ -f "src/main/resources/application-mysql.yml" ]; then
  echo "Testing application-mysql.yml..."
  if grep -qiE "mysql[_-]?password" "src/main/resources/application-mysql.yml" 2>/dev/null; then
    matching_line=$(grep -iE "mysql[_-]?password" "src/main/resources/application-mysql.yml" 2>/dev/null | head -1)
    if is_real_secret "$matching_line"; then
      echo "⚠️  Potential secret found in: src/main/resources/application-mysql.yml"
      echo "   Content: $matching_line"
      VIOLATIONS+=("src/main/resources/application-mysql.yml:mysql-password")
    else
      echo "ℹ️  Skipping template/placeholder in: src/main/resources/application-mysql.yml"
      echo "   Content: $matching_line"
    fi
  fi
fi

# Test DatabaseConfigConstants.java
if [ -f "src/main/java/com/alibaba/cloud/ai/manus/tool/database/DatabaseConfigConstants.java" ]; then
  echo "Testing DatabaseConfigConstants.java..."
  if grep -qiE "password\s*=\s*['\"][^'\"]+['\"]" "src/main/java/com/alibaba/cloud/ai/manus/tool/database/DatabaseConfigConstants.java" 2>/dev/null; then
    matching_line=$(grep -iE "password\s*=\s*['\"][^'\"]+['\"]" "src/main/java/com/alibaba/cloud/ai/manus/tool/database/DatabaseConfigConstants.java" 2>/dev/null | head -1)
    if is_real_secret "$matching_line"; then
      echo "⚠️  Potential secret found in: src/main/java/com/alibaba/cloud/ai/manus/tool/database/DatabaseConfigConstants.java"
      echo "   Content: $matching_line"
      VIOLATIONS+=("src/main/java/com/alibaba/cloud/ai/manus/tool/database/DatabaseConfigConstants.java:password-pattern")
    else
      echo "ℹ️  Skipping template/placeholder in: src/main/java/com/alibaba/cloud/ai/manus/tool/database/DatabaseConfigConstants.java"
      echo "   Content: $matching_line"
    fi
  fi
fi

# Report results
if [ ${#VIOLATIONS[@]} -eq 0 ]; then
  echo "✅ No real secrets found! Template files and constants are properly excluded."
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
  exit 1
fi
