#!/bin/bash

# Simple UI build script
# 1) Remove static/ui directory with git rm -r
# 2) Copy built content to new static directory
# 3) Add new ui directory

set -e  # Exit immediately on error

# Get the absolute path of the script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR"

echo "Starting simple UI build process..."

# Step 1: Remove static/ui directory with git rm -r
echo "Step 1: Removing static/ui directory with git rm -r..."
if [ -d "$PROJECT_ROOT/src/main/resources/static/ui" ]; then
    git rm -r "$PROJECT_ROOT/src/main/resources/static/ui"
    echo "✓ static/ui directory removed"
else
    echo "ℹ static/ui directory does not exist, skipping removal"
fi

# Step 2: Build frontend and copy to static directory
echo "Step 2: Building frontend and copying to static directory..."

# Enter ui-vue3 directory and build
cd "$PROJECT_ROOT/ui-vue3"

# Install dependencies if needed
if [ ! -d "node_modules" ]; then
    echo "Installing dependencies..."
    pnpm install
fi

# Build the project
echo "Building frontend..."
pnpm build

# Create static directory if it doesn't exist
mkdir -p "$PROJECT_ROOT/src/main/resources/static"

# Copy built ui directory to static
echo "Copying ui directory to static..."
cp -r "$PROJECT_ROOT/ui-vue3/ui" "$PROJECT_ROOT/src/main/resources/static/"

echo "✓ UI files copied to static directory"

# Step 3: Add new ui directory to git
echo "Step 3: Adding new ui directory to git..."
cd "$PROJECT_ROOT"
git add src/main/resources/static/ui

echo "✓ New ui directory added to git"

echo ""
echo "=== Build Completed ==="
echo "Frontend files successfully deployed to: $PROJECT_ROOT/src/main/resources/static/ui/"
echo "You can now commit the changes with: git commit -m 'Update UI'"
