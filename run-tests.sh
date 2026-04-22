#!/bin/bash

# Comprehensive Testing Script for csBaby Customer Service Assistant

echo "=== csBaby Customer Service Assistant - Testing Framework ==="
echo "Starting comprehensive testing..."

# Check if Gradle is available
if ! command -v gradle &> /dev/null && ! command -v ./gradlew &> /dev/null; then
    echo "ERROR: Gradle not found. Please install Gradle or use the included gradlew script."
    exit 1
fi

# Function to run unit tests
run_unit_tests() {
    echo "Running Unit Tests..."
    echo "======================"

    if [ -f "./gradlew" ]; then
        ./gradlew :app:testDebugUnitTest --stacktrace
    else
        gradle :app:testDebugUnitTest --stacktrace
    fi

    local result=$?
    if [ $result -eq 0 ]; then
        echo "✅ Unit tests passed!"
    else
        echo "❌ Unit tests failed!"
        return $result
    fi
}

# Function to run functional tests
run_functional_tests() {
    echo ""
    echo "Running Functional Tests..."
    echo "==========================="

    if [ -f "./gradlew" ]; then
        ./gradlew :app:testDebugUnitTest --tests "*FunctionalTests*" --stacktrace
    else
        gradle :app:testDebugUnitTest --tests "*FunctionalTests*" --stacktrace
    fi

    local result=$?
    if [ $result -eq 0 ]; then
        echo "✅ Functional tests passed!"
    else
        echo "❌ Functional tests failed!"
        return $result
    fi
}

# Function to run security tests
run_security_tests() {
    echo ""
    echo "Running Security Tests..."
    echo "========================="

    if [ -f "./gradlew" ]; then
        ./gradlew :app:testDebugUnitTest --tests "*SecurityTests*" --stacktrace
    else
        gradle :app:testDebugUnitTest --tests "*SecurityTests*" --stacktrace
    fi

    local result=$?
    if [ $result -eq 0 ]; then
        echo "✅ Security tests passed!"
    else
        echo "❌ Security tests failed!"
        return $result
    fi
}

# Function to check Android emulator
check_emulator() {
    echo ""
    echo "Checking Android Emulator Status..."
    echo "=================================="

    # Check if emulator is running
    adb devices | grep -q "device$"

    if [ $? -eq 0 ]; then
        echo "✅ Android emulator is running"
        return 0
    else
        echo "⚠️  No Android emulator detected"
        echo "   For UI and integration tests, please start an emulator:"
        echo "   adb devices"
        echo "   or start with Android Studio"
        return 1
    fi
}

# Function to run UI tests (if emulator is available)
run_ui_tests() {
    echo ""
    echo "Running UI Tests..."
    echo "==================="

    # Check for emulator first
    if ! check_emulator; then
        echo "Skipping UI tests - no emulator available"
        return 0
    fi

    echo "Starting connected Android test suite..."
    if [ -f "./gradlew" ]; then
        ./gradlew :app:connectedAndroidTest --stacktrace
    else
        gradle :app:connectedAndroidTest --stacktrace
    fi

    local result=$?
    if [ $result -eq 0 ]; then
        echo "✅ UI tests passed!"
    else
        echo "❌ UI tests failed!"
        return $result
    fi
}

# Function to generate test reports
generate_reports() {
    echo ""
    echo "Generating Test Reports..."
    echo "========================="

    if [ -f "./gradlew" ]; then
        ./gradlew :app:jacocoTestReport --stacktrace
    else
        gradle :app:jacocoTestReport --stacktrace
    fi

    local result=$?
    if [ $result -eq 0 ]; then
        echo "✅ Test reports generated successfully!"
        echo "   Reports location: app/build/reports/tests/"
        echo "   Coverage report: app/build/reports/jacoco/"
    else
        echo "❌ Failed to generate test reports"
        return $result
    fi
}

# Function to clean test results
clean_test_results() {
    echo ""
    echo "Cleaning previous test results..."
    echo "================================="

    rm -rf app/build/reports/
    rm -rf app/build/test-results/
    echo "✅ Previous test results cleaned"
}

# Main execution
main() {
    local start_time=$(date +%s)

    echo "Testing started at $(date)"
    echo ""

    # Clean previous results
    clean_test_results

    # Run test suites in order
    run_unit_tests
    local unit_result=$?

    run_functional_tests
    local functional_result=$?

    run_security_tests
    local security_result=$?

    # Only run UI tests if unit tests pass
    if [ $unit_result -eq 0 ]; then
        run_ui_tests
        local ui_result=$?
    else
        local ui_result=1
        echo "Skipping UI tests due to unit test failures"
    fi

    # Generate reports
    generate_reports

    # Calculate total duration
    local end_time=$(date +%s)
    local duration=$((end_time - start_time))

    echo ""
    echo "=== TEST SUMMARY ==="
    echo "Duration: ${duration} seconds"
    echo "Unit Tests: $([ $unit_result -eq 0 ] && echo "PASSED" || echo "FAILED")"
    echo "Functional Tests: $([ $functional_result -eq 0 ] && echo "PASSED" || echo "FAILED")"
    echo "Security Tests: $([ $security_result -eq 0 ] && echo "PASSED" || echo "FAILED")"
    echo "UI Tests: $([ $ui_result -eq 0 ] && echo "PASSED" || echo "FAILED")"

    # Overall result
    if [ $unit_result -eq 0 ] && [ $functional_result -eq 0 ] && [ $security_result -eq 0 ]; then
        echo ""
        echo "🎉 ALL TESTS PASSED! The application is ready for deployment."
        exit 0
    else
        echo ""
        echo "⚠️  Some tests failed. Please review the output above for details."
        exit 1
    fi
}

# Help function
show_help() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  --help      Show this help message"
    echo "  --unit      Run only unit tests"
    echo "  --functional Run only functional tests"
    echo "  --security  Run only security tests"
    echo "  --ui        Run only UI tests (requires emulator)"
    echo "  --clean     Clean test results before running"
    echo "  --report    Generate test reports only"
    echo ""
    echo "Examples:"
    echo "  $0                    # Run all tests"
    echo "  $0 --unit             # Run only unit tests"
    echo "  $0 --functional       # Run only functional tests"
    echo "  $0 --clean            # Clean and run all tests"
}

# Parse command line arguments
case "$1" in
    "--help")
        show_help
        exit 0
        ;;
    "--unit")
        run_unit_tests
        exit $?
        ;;
    "--functional")
        run_functional_tests
        exit $?
        ;;
    "--security")
        run_security_tests
        exit $?
        ;;
    "--ui")
        check_emulator && run_ui_tests
        exit $?
        ;;
    "--clean")
        clean_test_results
        exit 0
        ;;
    "--report")
        generate_reports
        exit $?
        ;;
    "")
        main
        ;;
    *)
        echo "Unknown option: $1"
        show_help
        exit 1
        ;;
esac