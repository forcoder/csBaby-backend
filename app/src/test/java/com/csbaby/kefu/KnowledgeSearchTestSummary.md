# Knowledge Search Integration Tests

This directory contains comprehensive test suites for verifying the knowledge base search improvements in the WorkBuddy application.

## Test Coverage Overview

### Unit Tests (`KnowledgeSearchUnitTest.kt`)
**Purpose**: Test individual components and logic without Android dependencies

1. **KeywordMatcher Fuzzy Matching Logic (Test 16)**
   - Verifies Chinese character reordering functionality
   - Tests "取消订单" ↔ "订单被取消" matching patterns

2. **Search Result Prioritization Logic (Test 17)**
   - Ensures high-priority rules appear first
   - Validates priority-based sorting

3. **Rule Target Type Filtering Logic (Test 18)**
   - Tests contact, group, property-specific rule filtering
   - Verifies context-aware rule application

4. **Template Variable Replacement Logic (Test 19)**
   - Tests {name}, {product} variable substitution
   - Validates dynamic content generation

5. **Import Format Detection Logic (Test 20)**
   - Tests CSV, JSON, Excel format detection
   - Verifies MIME type and extension parsing

6. **Export Functionality Verification (Test 21)**
   - Tests JSON and CSV export capabilities
   - Validates data serialization

7. **Error Handling in Import Operations (Test 22)**
   - Tests invalid JSON handling
   - Validates empty file processing
   - Checks CSV parsing error recovery

8. **Category Management and Filtering (Test 23)**
   - Tests category retrieval and filtering
   - Verifies category-specific rule loading

9. **Search Performance with Large Datasets (Test 24)**
   - Tests search performance with 1000+ rules
   - Validates response time requirements (< 1 second)

10. **Rule Validation and Sanitization (Test 25)**
    - Tests empty keyword validation
    - Validates reply template requirements
    - Checks negative priority rejection

### Integration Tests (`KnowledgeSearchIntegrationTest.kt`)
**Purpose**: Test complete workflows and component interactions

1. **Floating Window Popup Search Real-time Trigger (Test 1)**
   - Verifies instant search without submit button
   - Tests immediate UI updates

2. **Debounce Works (Test 2)**
   - Verifies 300ms debounce delay
   - Tests rapid typing scenarios

3. **Empty Input Clears Results Silently (Test 3)**
   - Tests silent clearing of search results
   - Verifies no toast messages on clear

4. **Fuzzy Matching - Chinese Reordering (Test 4)**
   - Tests "取消订单" matching "订单被取消"
   - Validates multiple reordered variations

5. **Short Keywords No Fuzzy Matching (Test 5)**
   - Tests behavior with < 3 character keywords
   - Verifies noise reduction

6. **Fuzzy Match Confidence Ordering (Test 6)**
   - Ensures direct matches have higher priority
   - Validates confidence scoring

7. **ViewModel Refresh After Import (Test 7)**
   - Tests immediate UI updates after import
   - Validates data synchronization

8. **ViewModel Refresh After Clear (Test 8)**
   - Tests empty state display after clearing
   - Validates refresh mechanism

9. **ViewModel Refresh After Delete (Test 9)**
   - Tests rule removal from UI immediately
   - Validates deletion workflow

10. **ViewModel Refresh After Toggle (Test 10)**
    - Tests enable/disable state changes
    - Validates real-time UI updates

11. **Page Navigation State Persistence (Test 11)**
    - Tests data persistence across screen switches
    - Validates flow collection continuity

12. **Save/Edit Rule Immediate Update (Test 12)**
    - Tests new rule creation workflow
    - Validates existing rule editing

13. **Multiple Format Import Support (Test 13)**
    - Tests JSON and CSV import robustness
    - Validates format detection

14. **Hybrid Search Engine Integration (Test 14)**
    - Tests keyword + semantic search combination
    - Validates engine initialization

15. **Context-Aware Rule Filtering (Test 15)**
    - Tests property-specific rule application
    - Validates conversation context filtering

### UI Tests (`KnowledgeSearchUITest.kt`)
**Purpose**: Test user interface behavior and interactions

1. **UI Search Field Real-time Updates (Test 36)**
   - Tests search field responsiveness
   - Validates real-time result updates

2. **Search Results Display and Interaction (Test 37)**
   - Tests result list rendering
   - Validates item selection

3. **Import/Export Button Functionality (Test 38)**
   - Tests import/export button states
   - Validates file picker integration

4. **Rule Management Buttons (Test 39)**
   - Tests add rule dialog
   - Validates cancel functionality

5. **Category Filter Functionality (Test 40)**
   - Tests category dropdown interaction
   - Validates filter application

6. **Empty State Display (Test 41)**
   - Tests empty state visibility
   - Validates message display

7. **Loading State During Operations (Test 42)**
   - Tests progress indicator appearance
   - Validates operation completion feedback

8. **Toast Message Display (Test 43)**
   - Tests notification system
   - Validates operation feedback

9. **Edit Rule Dialog Functionality (Test 44)**
   - Tests rule editing workflow
   - Validates update confirmation

10. **Delete Confirmation Dialog (Test 45)**
    - Tests delete confirmation flow
    - Validates rule removal

11. **Toggle Rule Enable/Disable (Test 46)**
    - Tests enable/disable switching
    - Validates state persistence

12. **Navigation Between Tabs/Screens (Test 47)**
    - Tests tab navigation
    - Validates state preservation

13. **Search History and Suggestions (Test 48)**
    - Tests search suggestions
    - Validates history management

14. **Responsive Layout for Different Screen Sizes (Test 49)**
    - Tests orientation changes
    - Validates responsive design

15. **Accessibility Features (Test 50)**
    - Tests screen reader compatibility
    - Validates content descriptions

## Running Tests

### Unit Tests Only
```bash
./gradlew testDebugUnitTest
```

### Instrumentation Tests Only
```bash
./gradlew connectedAndroidTest
```

### All Tests
```bash
./gradlew test connectedAndroidTest
```

### Specific Test Classes
```bash
# Run only integration tests
./gradlew testDebugUnitTest --tests "*IntegrationTest"

# Run only unit tests
./gradlew testDebugUnitTest --tests "*UnitTest"

# Run only UI tests
./gradlew connectedAndroidTest --tests "*UITest"
```

## Test Data

The following test data files are provided:

- `test_data/rules.json`: Sample rules in JSON format for import testing
- `test_data/import_rules.csv`: Sample CSV data for import testing

## Configuration

Test configuration can be modified in:
- `test_config.properties`: Main test configuration
- Test data files in `test_data/` directory

## Dependencies

Required testing dependencies (already configured in build.gradle.kts):

- JUnit 4
- Mockito Core
- Mockito Kotlin
- Espresso Core
- Compose Testing
- Truth Assertions
- Robolectric
- Coroutines Test

## Test Environment

- **Target SDK**: 34
- **Min SDK**: 24
- **Testing Framework**: JUnit 4 + AndroidX Test
- **Mocking**: Mockito + Mockito-Kotlin
- **UI Testing**: Espresso
- **Coroutines**: kotlinx-coroutines-test

## Expected Test Execution Time

- Unit Tests: ~2-5 minutes
- Integration Tests: ~5-10 minutes
- UI Tests: ~10-15 minutes (device/emulator dependent)
- Total: ~15-30 minutes

## Test Coverage Metrics

These tests cover:
- ✅ Real-time search functionality
- ✅ Fuzzy matching algorithms
- ✅ Debouncing mechanisms
- ✅ ViewModel refresh logic
- ✅ Data persistence across navigation
- ✅ Multiple import formats
- ✅ Error handling and recovery
- ✅ UI interaction flows
- ✅ Performance with large datasets
- ✅ Accessibility compliance
- ✅ Template variable replacement
- ✅ Context-aware filtering
- ✅ Export functionality
- ✅ Rule validation and sanitization

## Troubleshooting

### Common Issues

1. **Test fails due to missing resources**
   Solution: Ensure all resource files are in correct directories

2. **Mockito errors in tests**
   Solution: Add `@RunWith(AndroidJUnit4::class)` annotation

3. **Coroutine test failures**
   Solution: Use `runTest` from kotlinx-coroutines-test

4. **UI test device connection issues**
   Solution: Ensure emulator/device is running and accessible

### Debug Information

For detailed test execution information, add this to your gradle.properties:
```properties
org.gradle.console=verbose
org.gradle.logging.level=info
```

## Continuous Integration

These tests are designed to work with CI/CD pipelines. The test suite can be executed with:
```bash
./gradlew clean testDebugUnitTest connectedAndroidTest --continue
```

The `--continue` flag ensures that all tests run even if some fail, providing comprehensive coverage reporting.