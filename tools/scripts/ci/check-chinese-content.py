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

"""
Spring AI Alibaba Chinese Content Checker
Tool for checking Chinese content in Java and frontend code for GitHub Actions
"""

import os
import re
import sys

def contains_chinese(text):
    """Check if text contains Chinese characters"""
    chinese_pattern = re.compile(r'[\u4e00-\u9fff]+')
    return bool(chinese_pattern.search(text))

def check_file(file_path, exclude_patterns=None):
    """Check a single file for Chinese content"""
    if exclude_patterns is None:
        exclude_patterns = []

    for pattern in exclude_patterns:
        if pattern in file_path:
            return []

    try:
        with open(file_path, 'r', encoding='utf-8', errors='ignore') as f:
            lines = f.readlines()

        chinese_lines = []
        for i, line in enumerate(lines, 1):
            if contains_chinese(line.strip()):
                chinese_lines.append((i, line.strip()))

        return chinese_lines
    except Exception as e:
        print(f"Error reading {file_path}: {e}")
        return []

def main():
    java_files = []
    frontend_files = []

    # Check Java files
    for root, dirs, files in os.walk('src/main/java'):
        for file in files:
            if file.endswith('.java'):
                java_files.append(os.path.join(root, file))

    # Check frontend files (excluding i18n)
    for root, dirs, files in os.walk('ui-vue3/src'):
        # Skip i18n directories
        if 'i18n' in root:
            continue
        for file in files:
            if file.endswith(('.vue', '.ts', '.js')):
                frontend_files.append(os.path.join(root, file))

    all_issues = []

    print("Checking Java files...")
    for file_path in java_files:
        issues = check_file(file_path)
        if issues:
            all_issues.append((file_path, issues))

    print("Checking frontend files...")
    for file_path in frontend_files:
        issues = check_file(file_path, ['i18n', 'direct-api-service.ts'])
        if issues:
            all_issues.append((file_path, issues))

    if all_issues:
        print("\n🚨 Chinese content found:")
        for file_path, issues in all_issues:
            print(f"\n📄 {file_path}:")
            for line_num, line_content in issues:
                print(f"  Line {line_num}: {line_content}")
        return 1
    else:
        print("✅ No Chinese content found!")
        return 0

if __name__ == "__main__":
    sys.exit(main())
