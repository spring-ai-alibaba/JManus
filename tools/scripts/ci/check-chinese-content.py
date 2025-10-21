#!/usr/bin/env python3
# -*- coding: utf-8 -*-

#
# Copyright 2024-2026 the original author or authors.
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
#

"""
Spring AI Alibaba JManus Chinese Content Checker
Unified tool for checking Chinese content in Java and frontend code for GitHub Actions
"""

import os
import re
import sys
import argparse
from pathlib import Path
from typing import List, Dict, Set
from collections import defaultdict

class ChineseContentChecker:
    def __init__(self, check_java: bool = True, check_frontend: bool = True):
        # Detect Chinese characters, excluding Chinese punctuation to avoid false positives
        self.chinese_pattern = re.compile(r'[\u4e00-\u9fff]')
        # Chinese punctuation detection
        self.chinese_punctuation = re.compile(r'，。！？；：""（）【】《》')

        # Exclude common English phrases to avoid false positives
        self.exclude_patterns = [
            r'\bAS IS\b',  # "AS IS" in Apache License
            r'\bIS NULL\b',  # "IS NULL" in SQL
            r'\bIS NOT\b',  # "IS NOT" in SQL
            r'@author\s+\w+',  # Author information
            r'@time\s+\d{4}/\d{1,2}/\d{1,2}',  # Time information
        ]

        # Exclude i18n configuration files
        self.exclude_files = [
            'zh.ts',  # Chinese i18n file
            'en.ts',  # English i18n file (may contain Chinese in comments)
            'index.ts',  # i18n index file
            'type.ts',  # i18n type definitions
            'sortI18n.ts',  # i18n sorting utility
            'plan-act-api-service.ts',  # API service file with Chinese content
        ]

        # Exclude directories
        self.exclude_dirs = [
            'node_modules',
            'dist',
            'build',
            '.git',
            'coverage',
        ]

        self.issues = []
        self.check_java = check_java
        self.check_frontend = check_frontend

    def should_exclude_file(self, file_path: Path) -> bool:
        """Check if file should be excluded from checking"""
        # Check if it's an i18n configuration file
        if file_path.name in self.exclude_files:
            return True

        # Check if it's in i18n directory
        if 'i18n' in file_path.parts:
            return True

        # Check if it's in excluded directories
        for exclude_dir in self.exclude_dirs:
            if exclude_dir in file_path.parts:
                return True

        return False

    def has_real_chinese_content(self, text: str) -> bool:
        """Check if text contains real Chinese content (excluding false positives)"""
        # First check if there are Chinese characters or Chinese punctuation
        if not (self.chinese_pattern.search(text) or self.chinese_punctuation.search(text)):
            return False

        # Exclude common English phrases
        for pattern in self.exclude_patterns:
            if re.search(pattern, text, re.IGNORECASE):
                # If matched exclude pattern, further check if it really contains Chinese
                temp_text = re.sub(pattern, '', text, flags=re.IGNORECASE)
                if not (self.chinese_pattern.search(temp_text) or self.chinese_punctuation.search(temp_text)):
                    return False

        return True

    def check_file(self, file_path: Path) -> List[Dict]:
        """Check single file for Chinese content, return list of issues"""
        issues = []

        try:
            with open(file_path, 'r', encoding='utf-8', errors='ignore') as f:
                lines = f.readlines()

                in_multiline_comment = False
                in_template_section = False

                for line_num, line in enumerate(lines, 1):
                    original_line = line.rstrip()
                    line_stripped = line.strip()

                    if not line_stripped:
                        continue

                    # Check if contains real Chinese content
                    if not self.has_real_chinese_content(line_stripped):
                        continue

                    # Analyze the type of location where Chinese content appears
                    content_type = self._analyze_content_type(line_stripped, in_multiline_comment, in_template_section)

                    # Update multiline comment status
                    if '/*' in line_stripped:
                        in_multiline_comment = True
                    if '*/' in line_stripped:
                        in_multiline_comment = False

                    # Update template section status for Vue files
                    if file_path.suffix == '.vue':
                        if '<template>' in line_stripped:
                            in_template_section = True
                        elif '</template>' in line_stripped:
                            in_template_section = False

                    issues.append({
                        'file': str(file_path),
                        'line': line_num,
                        'content': original_line,
                        'type': content_type,
                        'message': f"Found Chinese content in {content_type}"
                    })

        except Exception as e:
            print(f"Warning: Unable to read file {file_path}: {e}", file=sys.stderr)

        return issues

    def _analyze_content_type(self, line: str, in_multiline_comment: bool, in_template_section: bool) -> str:
        """Analyze the type of Chinese content location"""
        if in_multiline_comment or line.startswith('/*'):
            return "multiline comment"

        if line.startswith('//'):
            return "single line comment"

        if '//' in line:
            comment_part = line[line.find('//'):]
            if self.has_real_chinese_content(comment_part):
                return "inline comment"

        # Check Vue template content
        if in_template_section:
            return "Vue template"

        # Check string literals
        string_matches = re.finditer(r'"([^"]*)"', line)
        for match in string_matches:
            if self.has_real_chinese_content(match.group(1)):
                return "string literal"

        # Check template literals
        template_matches = re.finditer(r'`([^`]*)`', line)
        for match in template_matches:
            if self.has_real_chinese_content(match.group(1)):
                return "template literal"

        # Check character literals
        char_matches = re.finditer(r"'([^']*)'", line)
        for match in char_matches:
            if self.has_real_chinese_content(match.group(1)):
                return "character literal"

        # Check identifiers
        temp_line = re.sub(r'"[^"]*"', '', line)  # Remove strings
        temp_line = re.sub(r"'[^']*'", '', temp_line)  # Remove characters
        temp_line = re.sub(r'`[^`]*`', '', temp_line)  # Remove template literals
        temp_line = re.sub(r'//.*$', '', temp_line)  # Remove single line comments

        if self.has_real_chinese_content(temp_line):
            return "identifier or code"

        return "unknown location"

    def check_java_files(self) -> bool:
        """Check Java files for Chinese content"""
        java_dir = Path('src/main/java')
        if not java_dir.exists():
            print(f"::notice::Java directory does not exist: {java_dir}")
            return True

        java_files = list(java_dir.rglob('*.java'))
        if not java_files:
            print(f"::notice::No Java files found in {java_dir}")
            return True

        print(f"::notice::Found {len(java_files)} Java files, starting check...")

        for java_file in java_files:
            file_issues = self.check_file(java_file)
            self.issues.extend(file_issues)

        return True

    def check_frontend_files(self) -> bool:
        """Check frontend files for Chinese content"""
        frontend_dir = Path('ui-vue3/src')
        if not frontend_dir.exists():
            print(f"::notice::Frontend directory does not exist: {frontend_dir}")
            return True

        # Find Vue, TypeScript, and JavaScript files
        frontend_files = []
        for pattern in ['**/*.vue', '**/*.ts', '**/*.js', '**/*.jsx', '**/*.tsx']:
            frontend_files.extend(list(frontend_dir.rglob(pattern)))

        # Filter out excluded files
        frontend_files = [f for f in frontend_files if not self.should_exclude_file(f)]

        if not frontend_files:
            print(f"::notice::No frontend files found in {frontend_dir}")
            return True

        print(f"::notice::Found {len(frontend_files)} frontend files, starting check...")

        for frontend_file in frontend_files:
            file_issues = self.check_file(frontend_file)
            self.issues.extend(file_issues)

        return True

    def check_all(self) -> bool:
        """Check all configured file types"""
        if self.check_java:
            self.check_java_files()
        
        if self.check_frontend:
            self.check_frontend_files()

        return len(self.issues) == 0

    def report_issues(self) -> None:
        """Report discovered issues"""
        if not self.issues:
            print("::notice::✅ No Chinese content found!")
            return

        print(f"::error::❌ Found {len(self.issues)} Chinese content issues")

        # Group issues by file
        files_with_issues = defaultdict(list)
        for issue in self.issues:
            files_with_issues[issue['file']].append(issue)

        for file_path, file_issues in files_with_issues.items():
            print(f"::error file={file_path}::File contains {len(file_issues)} Chinese content issues")

            for issue in file_issues:
                print(f"::error file={file_path},line={issue['line']}::{issue['message']}: {issue['content'][:100]}")

        # Output modification suggestions
        print("\n::notice::Modification suggestions:")
        print("::notice::1. Change Chinese comments to English comments")
        print("::notice::2. Extract Chinese strings to i18n configuration files")
        print("::notice::3. Change Chinese identifiers to English identifiers")
        print("::notice::4. Use i18n keys like $t('key') instead of hardcoded Chinese text")
        print("::notice::5. For test data, consider using English or placeholders")

def main():
    parser = argparse.ArgumentParser(description='Check Chinese content in Java and frontend code')
    parser.add_argument('--java-only', '-j',
                       action='store_true',
                       help='Check only Java files')
    parser.add_argument('--frontend-only', '-f',
                       action='store_true',
                       help='Check only frontend files')
    parser.add_argument('--fail-on-found',
                       action='store_true',
                       help='Return non-zero exit code when Chinese content is found')

    args = parser.parse_args()

    # Determine what to check
    check_java = not args.frontend_only
    check_frontend = not args.java_only

    try:
        checker = ChineseContentChecker(check_java=check_java, check_frontend=check_frontend)
        is_clean = checker.check_all()
        checker.report_issues()

        if args.fail_on_found and not is_clean:
            print(f"::error::Check failed: Found {len(checker.issues)} Chinese content issues")
            return 1

        return 0

    except Exception as e:
        print(f"::error::Error occurred during check: {e}")
        return 1

if __name__ == "__main__":
    sys.exit(main())