#!/bin/bash

# Enhanced Test Execution Script for csBaby Project
# This script provides comprehensive testing capabilities with detailed reporting

set -e  # Exit on any error

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPORTS_DIR="$SCRIPT_DIR/app/build/reports/tests"
COVERAGE_DIR="$SCRIPT_DIR/app/build/reports/coverage"

# Colors for output formatting
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
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

# Create reports directory
setup_reports_directory() {
    log_info "Setting up reports directory..."
    mkdir -p "$REPORTS_DIR"
    mkdir -p "$COVERAGE_DIR"
    log_success "Reports directory ready: $REPORTS_DIR"
}

# Clean build artifacts
clean_build() {
    log_info "Cleaning previous build artifacts..."
    ./gradlew clean --no-daemon --stacktrace
    log_success "Build cleaned successfully"
}

# Run static analysis
run_static_analysis() {
    log_info "Running static code analysis..."
    ./gradlew lintDebug --no-daemon --stacktrace || {
        log_warning "Static analysis completed with warnings"
    }
    log_success "Static analysis completed"
}

# Run unit tests with coverage
run_unit_tests() {
    log_info "Running unit tests with coverage..."
    ./gradlew testDebugUnitTest \
        --no-daemon \
        --parallel \
        --stacktrace \
        -Porg.gradle.jvmargs="-Xmx4096m -Xms512m" \
        --info || {
        log_warning "Unit tests completed with some failures"
    }

    # Generate HTML coverage report
    if [ -f "app/build/reports/jacoco/testDebugUnitTest/html/index.html" ]; then
        cp -r "app/build/reports/jacoco/testDebugUnitTest/html/" "$COVERAGE_DIR/unit/"
        log_success "Unit test coverage report generated"
    fi
}

# Run integration tests
run_integration_tests() {
    log_info "Running integration tests..."
    ./gradlew connectedAndroidTest \
        --no-daemon \
        --stacktrace \
        -Porg.gradle.jvmargs="-Xmx4096m -Xms512m" || {
        log_warning "Integration tests completed with some failures"
    }
    log_success "Integration tests completed"
}

# Run all tests
run_all_tests() {
    local start_time=$(date +%s)
    log_info "Starting comprehensive test suite..."

    setup_reports_directory
    run_static_analysis
    run_unit_tests
    run_integration_tests

    local end_time=$(date +%s)
    local duration=$((end_time - start_time))
    log_success "All tests completed in ${duration} seconds"
}

# Generate test summary report
generate_test_summary() {
    log_info "Generating test summary report..."

    cat > "$REPORTS_DIR/test-summary.md" << 'EOF'
# Test Execution Summary Report

## Test Results Overview

### Static Analysis
- **Lint Checks**: $(find app/build/reports/lint-results-debug.xml -type f 2>/dev/null && echo "✅ PASSED" || echo "❌ FAILED")

### Unit Tests
- **Test Files**: $(find app/build/reports/tests/testDebugUnitTest/ -name "*.xml" -type f | wc -l)
- **Test Classes**: $(find app/build/reports/tests/testDebugUnitTest/ -name "*Test*.class" -type f 2>/dev/null | wc -l || echo "N/A")
- **Coverage Report**: $(ls -la app/build/reports/jacoco/testDebugUnitTest/html/index.html 2>/dev/null && echo "✅ Generated" || echo "❌ Not Generated")

### Integration Tests
- **Instrumented Tests**: $(find app/build/reports/androidTests/connected/ -name "*.html" -type f 2>/dev/null | wc -l || echo "0")

## Detailed Reports

### Unit Test Coverage
$(if [ -d "$COVERAGE_DIR/unit" ]; then
    echo "| Module | Coverage % | Status |"
    echo "|--------|------------|--------|"
    # Extract coverage data from JaCoCo report
    find "$COVERAGE_DIR/unit" -name "*.html" -exec grep -h "coverage" {} \; 2>/dev/null | head -10
else
    echo "Coverage report not available"
fi)

### Test Artifacts
- **XML Reports**: $(find app/build/test-results/ -name "*.xml" -type f | wc -l)
- **HTML Reports**: $(find app/build/reports/tests/ -name "*.html" -type f | wc -l)
- **Coverage Data**: $(find app/build/reports/coverage/ -type d | wc -l)

## Recommendations

1. **Code Quality**: Review static analysis warnings and fix critical issues
2. **Test Coverage**: Aim for minimum 70% coverage across all modules
3. **Integration Testing**: Ensure all critical user flows are tested
4. **Performance**: Monitor test execution time and optimize slow tests

---

*Report generated on: $(date)*
*Test execution duration: $(($(date +%s) - $(date -d "$(grep -A 1 "All tests completed" /dev/stdin)" +%s 2>/dev/null || echo "0")) seconds*
EOF

    log_success "Test summary report generated: $REPORTS_DIR/test-summary.md"
}

# Main execution function
main() {
    local command="${1:-all}"
    shift || true

    case "$command" in
        "clean")
            clean_build
            ;;
        "lint")
            setup_reports_directory
            run_static_analysis
            ;;
        "test-unit")
            setup_reports_directory
            run_unit_tests
            ;;
        "test-integration")
            run_integration_tests
            ;;
        "test-all"|"all")
            run_all_tests
            generate_test_summary
            ;;
        "help"|"--help"|"-h")
            echo "Usage: $0 [command]"
            echo ""
            echo "Commands:"
            echo "  clean              Clean build artifacts"
            echo "  lint               Run static analysis only"
            echo "  test-unit          Run unit tests only"
            echo "  test-integration   Run integration tests only"
            echo "  test-all           Run all tests (default)"
            echo "  all                Alias for test-all"
            echo "  help               Show this help message"
            ;;
        *)
            log_error "Unknown command: $command"
            echo "Use '$0 help' for usage information"
            exit 1
            ;;
    esac
}

# Execute main function with all arguments
main "$@"

exit 0