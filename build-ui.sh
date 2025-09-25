#!/bin/bash

# Build frontend UI and deploy to Spring Boot static resources directory
# For macOS

set -e  # Exit immediately on error

# Get the absolute path of the script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR"

# Define color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Output colored logs
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if necessary commands exist
check_commands() {
    log_info "Checking necessary commands..."

    if ! command -v pnpm &> /dev/null; then
        log_error "pnpm is not installed, please install pnpm first"
        log_info "Installation command: npm install -g pnpm"
        exit 1
    fi

    log_success "Command check completed"
}

# Check directory structure
check_directories() {
    log_info "Checking project directory structure..."

    if [ ! -d "$PROJECT_ROOT/ui-vue3" ]; then
        log_error "ui-vue3 directory does not exist: $PROJECT_ROOT/ui-vue3"
        exit 1
    fi

    if [ ! -f "$PROJECT_ROOT/ui-vue3/package.json" ]; then
        log_error "ui-vue3/package.json file does not exist"
        exit 1
    fi

    if [ ! -d "$PROJECT_ROOT/src/main/resources" ]; then
        log_error "Spring Boot resources directory does not exist: $PROJECT_ROOT/src/main/resources"
        exit 1
    fi

    log_success "Directory structure check completed"
}

# Step 1: Delete ui-vue3/ui directory
delete_ui_vue3_ui() {
    log_info "Step 1: Deleting ui-vue3/ui directory..."
    
    UI_VUE3_UI_DIR="$PROJECT_ROOT/ui-vue3/ui"
    
    if [ -d "$UI_VUE3_UI_DIR" ]; then
        log_info "Removing existing ui-vue3/ui directory..."
        rm -rf "$UI_VUE3_UI_DIR"
        log_success "ui-vue3/ui directory deleted"
    else
        log_info "ui-vue3/ui directory does not exist, skipping deletion"
    fi
}

# Step 2: Delete src/main/resources/static directory
delete_static_directory() {
    log_info "Step 2: Deleting src/main/resources/static directory..."
    
    STATIC_DIR="$PROJECT_ROOT/src/main/resources/static"
    
    if [ -d "$STATIC_DIR" ]; then
        log_info "Removing existing static directory..."
        rm -rf "$STATIC_DIR"
        log_success "static directory deleted"
    else
        log_info "static directory does not exist, skipping deletion"
    fi
}

# Step 3: Enter ui-vue3 and run pnpm build
build_frontend() {
    log_info "Step 3: Building frontend project..."
    
    cd "$PROJECT_ROOT/ui-vue3"
    
    # Check if node_modules exists, install dependencies if not
    if [ ! -d "node_modules" ]; then
        log_warning "node_modules does not exist, installing dependencies..."
        pnpm install
    fi
    
    # Run build command
    log_info "Running pnpm build..."
    pnpm build
    
    # Check build result
    if [ ! -d "ui" ]; then
        log_error "Build failed, ui directory does not exist , the directory is: $PROJECT_ROOT/ui-vue3/ui"
        exit 1
    fi
    
    log_success "Frontend project build completed"
}

# Step 4: Copy ui-vue3/ui to src/main/resources/static/ui
copy_ui_files() {
    log_info "Step 4: Copying ui files to static directory..."
    
    SOURCE_DIR="$PROJECT_ROOT/ui-vue3/ui"
    TARGET_DIR="$PROJECT_ROOT/src/main/resources/static"
    
    if [ ! -d "$SOURCE_DIR" ]; then
        log_error "Source directory does not exist: $SOURCE_DIR"
        exit 1
    fi
    
    # Create target directory
    mkdir -p "$TARGET_DIR"
    
    # Copy ui directory to static
    log_info "Copying ui directory to static..."
    cp -r "$SOURCE_DIR" "$TARGET_DIR/"
    
    # Verify copy result
    if [ -d "$TARGET_DIR/ui" ]; then
        log_success "UI files copied successfully"
        log_info "Copied files:"
        ls -la "$TARGET_DIR/ui"
    else
        log_error "File copy failed, target directory is empty"
        exit 1
    fi
}

# Show build summary
show_summary() {
    log_success "=== Build Completed ==="
    log_info "Frontend files successfully deployed to: $PROJECT_ROOT/src/main/resources/static/ui/"
    log_info "You can now run the Spring Boot application"
    log_info ""
    log_info "Example startup commands:"
    log_info "  mvn spring-boot:run"
    log_info "  or"
    log_info "  java -jar target/spring-ai-alibaba-jmanus-*.jar"
}

# Main function
main() {
    log_info "Starting frontend build and deployment process..."
    log_info "Project root directory: $PROJECT_ROOT"
    echo ""

    check_commands
    check_directories
    delete_ui_vue3_ui
    delete_static_directory
    build_frontend
    copy_ui_files
    show_summary

    log_success "All steps completed!"
}

# Error handling
trap 'log_error "Error occurred during script execution, exit code: $?"' ERR

# Run main function
main "$@"
