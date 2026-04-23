# csBaby Customer Service Assistant - Testing Framework

## Overview

This document outlines the comprehensive testing strategy for the csBaby Customer Service Assistant Android application. The testing framework covers functional testing, security testing, and UI testing to ensure application quality and security compliance.

## Test Architecture

### 1. Unit Tests (`src/test/java/com/csbaby/kefu/`)
- **FunctionalTests.kt**: Core business logic testing
- **SecurityTests.kt**: Security vulnerability testing
- **UITests.kt**: User interface component testing

### 2. Integration Tests (Connected Android Tests)
- Device-level functionality testing
- Service interaction testing
- Permission flow testing

### 3. Security Testing Strategy

#### Permission Validation
- Verifies dangerous permissions are properly declared
- Tests runtime permission handling
- Validates service export configurations

#### Data Protection
- Tests DataStore encryption
- Verifies secure communication protocols
- Checks file system permissions

#### Input Validation
- SQL injection prevention testing
- Malicious input sanitization
- Buffer overflow protection

### 4. Functional Testing Strategy

#### Notification Monitoring
- Message capture and processing
- App selection and filtering
- Real-time notification handling

#### AI Response Generation
- Multi-model support testing
- Response caching validation
- Error handling and retry logic

#### Knowledge Base Management
- Entry creation and retrieval
- Search functionality
- Category organization

## Running Tests

### Prerequisites
- Android Studio or Android SDK
- Gradle 7.0+
- Android emulator or physical device
- Java 17+

### Test Commands

```bash
# Run all tests
./run-tests.sh

# Run specific test suites
./run-tests.sh --unit
./run-tests.sh --functional
./run-tests.sh --security
./run-tests.sh --ui

# Clean previous results and run tests
./run-tests.sh --clean

# Generate reports only
./run-tests.sh --report
```

### Individual Gradle Commands

```bash
# Unit tests
./gradlew :app:testDebugUnitTest

# Connected Android tests (requires emulator)
./gradlew :app:connectedAndroidTest

# Generate coverage report
./gradlew :app:jacocoTestReport
```

## Test Coverage Areas

### 1. Functional Tests (Priority: HIGH)

#### Notification Listener Service
- [ ] Message capture from selected apps
- [ ] Content extraction accuracy
- [ ] Real-time processing capability
- [ ] Error handling for malformed notifications

#### AI Service
- [ ] Multiple model support
- [ ] Response generation quality
- [ ] Caching mechanism efficiency
- [ ] Cost estimation accuracy
- [ ] Style analysis functionality

#### Knowledge Base
- [ ] CRUD operations
- [ ] Search algorithm performance
- [ ] Category management
- [ ] Data persistence

### 2. Security Tests (Priority: CRITICAL)

#### Permission Management
- [ ] Dangerous permission declaration
- [ ] Runtime permission requests
- [ ] Service isolation
- [ ] Export control verification

#### Data Security
- [ ] Sensitive data encryption
- [ ] Secure storage practices
- [ ] Network communication security
- [ ] Certificate pinning verification

#### Input Validation
- [ ] SQL injection prevention
- [ ] XSS protection
- [ ] Buffer overflow prevention
- [ ] Malicious input sanitization

### 3. UI Tests (Priority: MEDIUM)

#### User Interface
- [ ] Activity launch sequence
- [ ] Theme switching functionality
- [ ] Navigation between screens
- [ ] Responsive design adaptation

#### User Experience
- [ ] Permission request flow
- [ ] Loading state handling
- [ ] Error message display
- [ ] Accessibility compliance

## Test Results Analysis

### Coverage Reports
- **Location**: `app/build/reports/jacoco/`
- **Format**: HTML and XML reports
- **Metrics**: Line coverage, branch coverage, method coverage

### Performance Metrics
- Test execution time
- Memory usage during tests
- Battery consumption (for connected tests)
- Network usage patterns

### Security Assessment
- Vulnerability scan results
- Permission usage analysis
- Data exposure risk assessment

## Continuous Integration Setup

### GitHub Actions Configuration
```yaml
name: csBaby CI/CD
on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
      
      - name: Grant execute permission for gradlew
        run: chmod +x gradlew
      
      - name: Run unit tests
        run: ./gradlew :app:testDebugUnitTest
      
      - name: Run security tests
        run: ./gradlew :app:testDebugUnitTest --tests "*SecurityTests*"
      
      - name: Generate coverage report
        run: ./gradlew :app:jacocoTestReport
      
      - name: Upload coverage to Codecov
        uses: codecov/codecov-action@v3
```

## Troubleshooting

### Common Issues

1. **Emulator Not Found**
   ```
   Solution: Start Android emulator before running connected tests
   Command: emulator @your_emulator_name
   ```

2. **Gradle Build Failures**
   ```
   Solution: Clean build cache and retry
   Command: ./gradlew clean
   ```

3. **Permission Denied Errors**
   ```
   Solution: Ensure proper file permissions
   Command: chmod +x *.sh
   ```

### Debug Mode
Enable verbose logging for detailed error information:
```bash
./run-tests.sh --clean --stacktrace
```

## Maintenance

### Regular Updates
- Update test dependencies quarterly
- Review and update security test cases annually
- Add new test cases for feature additions

### Test Data Management
- Use mock data for consistent test results
- Clean temporary files after test execution
- Rotate sensitive test data regularly

### Performance Monitoring
- Track test execution times
- Monitor memory usage patterns
- Analyze flaky test failures

---

**Last Updated**: April 22, 2026  
**Version**: 1.0  
**Maintainer**: QA Team