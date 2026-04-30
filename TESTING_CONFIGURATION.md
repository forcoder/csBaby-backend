# 客服小秘应用测试配置指南

## 1. 测试框架概览

### 1.1 支持的测试类型
- **单元测试**: JUnit + Mockito + Truth
- **集成测试**: MockWebServer + Room in-memory database
- **UI测试**: AndroidX Test + Compose UI Testing
- **性能测试**: Custom performance test framework
- **安全测试**: Security validation and penetration testing

### 1.2 测试依赖配置
```kotlin
// app/test-config.gradle.kts
dependencies {
    // Core Testing
    testImplementation "junit:junit:4.13.2"
    testImplementation "org.mockito:mockito-core:5.7.0"
    testImplementation "com.google.truth:truth:1.1.5"

    // Android Testing
    androidTestImplementation "androidx.test.ext:junit:1.1.5"
    androidTestImplementation "androidx.test.espresso:espresso-core:3.5.1"
    androidTestImplementation "androidx.compose.ui:ui-test-junit4:1.5.4"

    // Coroutines Testing
    testImplementation "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3"

    // Mocking and Web Server
    testImplementation "org.mockito.kotlin:mockito-kotlin:5.2.1"
    androidTestImplementation "com.squareup.okhttp3:mockwebserver:4.12.0"

    // Hilt Testing
    testImplementation "com.google.dagger:hilt-android-testing:2.50"
    kaptTest "com.google.dagger:hilt-android-compiler:2.50"

    // Room Testing
    androidTestImplementation "androidx.room:room-testing:2.6.1"

    // Instrumentation Testing
    androidTestImplementation "androidx.navigation:navigation-testing:2.7.6"
}
```

## 2. 测试执行命令

### 2.1 基本测试命令
```bash
# 运行所有单元测试
./gradlew testDebugUnitTest

# 运行所有集成测试
./gradlew connectedAndroidTest

# 运行特定模块的测试
./gradlew :app:testDebugUnitTest

# 生成测试报告
./gradlew jacocoTestReport
```

### 2.2 自定义测试任务
```bash
# 运行所有测试（包括单元、集成、UI）
./gradlew runAllTests

# 运行安全相关测试
./gradlew runSecurityTests

# 运行功能测试
./gradlew runFunctionalTests

# 生成代码覆盖率报告
./gradlew generateCoverageReport
```

### 2.3 CI/CD集成命令
```yaml
# GitHub Actions 示例
name: CI Pipeline
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Setup JDK
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Run Unit Tests
        run: ./gradlew testDebugUnitTest
      - name: Run Integration Tests
        run: ./gradlew connectedAndroidTest
      - name: Generate Coverage Report
        run: ./gradlew jacocoTestReport
      - name: Upload Coverage to Codecov
        uses: codecov/codecov-action@v3
        with:
          file: ./app/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml
```

## 3. 测试数据管理

### 3.1 测试数据库配置
```kotlin
// In-memory database for testing
@Database(entities = [AIModelConfigEntity::class, ReplyHistoryEntity::class], version = 1)
abstract class TestDatabase : Room.databaseBuilder(
    ApplicationProvider.getApplicationContext(),
    AppDatabase::class.java,
    "test-db"
).build()
```

### 3.2 Mock数据生成
```kotlin
object TestDataFactory {
    fun createAIModelConfig(
        id: Long = 1L,
        modelType: String = "OPENAI",
        modelName: String = "gpt-3.5-turbo",
        isDefault: Boolean = false,
        isEnabled: Boolean = true
    ): AIModelConfigEntity {
        return AIModelConfigEntity(
            id = id,
            modelType = modelType,
            modelName = modelName,
            model = modelName,
            apiKey = "sk-test-key-$id",
            apiEndpoint = when (modelType) {
                "OPENAI" -> "https://api.openai.com/v1/chat/completions"
                "CLAUDE" -> "https://api.anthropic.com/v1/messages"
                else -> "https://api.example.com/v1/chat/completions"
            },
            isDefault = isDefault,
            isEnabled = isEnabled,
            monthlyCost = if (isDefault) 5.0 else 1.0
        )
    }

    fun createReplyHistory(
        id: Long = 1L,
        sourceApp: String = "com.example.app",
        originalMessage: String = "Test message",
        styleApplied: Boolean = false
    ): ReplyHistoryEntity {
        return ReplyHistoryEntity(
            id = id,
            sourceApp = sourceApp,
            originalMessage = originalMessage,
            generatedReply = "Generated reply for $originalMessage",
            finalReply = "Final reply for $originalMessage",
            styleApplied = styleApplied,
            sendTime = System.currentTimeMillis() - (id * 1000)
        )
    }
}
```

## 4. 代码覆盖率配置

### 4.1 Jacoco配置
```xml
<!-- app/build.gradle.kts -->
android {
    buildTypes {
        debug {
            testCoverageEnabled true
        }
    }
}

tasks.register("jacocoTestReport", JacocoReport::class) {
    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }

    def fileFilter = ['**/R.class', '**/R$*.class', '**/BuildConfig.*']
    def debugTree = fileTree(dir: "${buildDir}/intermediates/javac/debug", excludes: fileFilter)
    def mainSrc = "${project.projectDir}/src/main/java"

    sourceDirectories.setFrom(files([mainSrc]))
    classDirectories.setFrom(files([debugTree]))
    executionData.setFrom(fileTree(dir: "$buildDir", includes: [
        "**/*.exec", "**/*.ec"
    ]))
}
```

### 4.2 覆盖率目标
| 组件 | 当前覆盖率 | 目标覆盖率 | 截止日期 |
|------|-----------|------------|----------|
| AI服务层 | 85% | 95% | 2024-05-15 |
| 数据访问层 | 40% | 80% | 2024-05-10 |
| 业务逻辑层 | 45% | 85% | 2024-05-20 |
| UI组件层 | 15% | 70% | 2024-05-25 |
| 网络层 | 90% | 95% | 2024-05-12 |

## 5. 质量门禁配置

### 5.1 构建时检查
```yaml
# .github/workflows/ci.yml
quality-gates:
  minimum-coverage: 75%
  maximum-failure-count: 0
  static-analysis-warnings: 5
  performance-regression-threshold: 10%
```

### 5.2 代码审查标准
```markdown
## PR Checklist for Testing

- [ ] 新增代码有对应的单元测试
- [ ] 修改现有功能更新了相关测试
- [ ] 测试覆盖率不低于项目平均水平
- [ ] 所有自动化测试通过
- [ ] 性能测试无显著退化
- [ ] 安全测试通过
```

## 6. 测试最佳实践

### 6.1 单元测试规范
```kotlin
@Test
fun `method description should be clear and specific`() = runTest {
    // Given - Setup test data and conditions
    val input = createTestData()
    val dependencies = mockDependencies()

    // When - Execute the method under test
    val result = systemUnderTest.method(input)

    // Then - Verify expected outcomes
    assertEquals(expectedValue, result)
    verify(dependencies).expectedInteraction()
}
```

### 6.2 集成测试规范
```kotlin
@Test
fun `integration test with real dependencies`() = runTest {
    // Given - Setup with actual database and network
    val db = createInMemoryDatabase()
    val client = createMockWebServerClient()

    // When - Test end-to-end flow
    val result = aiService.processRequest(testRequest)

    // Then - Verify complete workflow
    assertTrue(result.isSuccess)
    verifyDatabaseState(db, expectedState)
}
```

### 6.3 性能测试规范
```kotlin
@Test
fun `performance test with measurable metrics`() = runTest {
    // Measure baseline performance
    val startTime = System.nanoTime()
    repeat(ITERATION_COUNT) {
        performOperation()
    }
    val duration = System.nanoTime() - startTime

    // Assert performance requirements
    assertTrue("Operation should complete within time limit",
        duration < MAX_ALLOWED_DURATION)
    assertTrue("Memory usage should be reasonable",
        getUsedMemory() < MAX_MEMORY_USAGE)
}
```

## 7. 故障排除指南

### 7.1 常见问题解决
```markdown
#### Test Database Issues
- **Problem**: Room database not found
- **Solution**: Ensure @Database annotation is present and entities are defined

#### MockWebServer Issues
- **Problem**: No responses received from server
- **Solution**: Check that mock responses are enqueued before test execution

#### Performance Test Failures
- **Problem**: Flaky timing-based tests
- **Solution**: Use fixed time simulation or increase tolerance thresholds
```

### 7.2 调试技巧
```bash
# Run single test with detailed output
./gradlew test --info --tests "*AIModelConfigDaoTest*"

# Debug test execution
./gradlew testDebugUnitTest --debug

# Clean and rebuild test classes
./gradlew clean testDebugUnitTest
```

## 8. 持续集成配置

### 8.1 GitHub Actions完整配置
```yaml
# .github/workflows/android.yml
name: Android CI

on:
  push:
    branches: [ main, develop ]
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

    - name: Run Unit Tests
      run: ./gradlew testDebugUnitTest

    - name: Run Integration Tests
      uses: reactivecircus/android-emulator-runner@v2
      with:
        api-level: 29
        script: ./gradlew connectedAndroidTest

    - name: Generate Coverage Report
      run: ./gradlew jacocoTestReport

    - name: Upload Coverage to Codecov
      uses: codecov/codecov-action@v3
      with:
        files: ./app/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml

    - name: Upload Test Reports
      uses: actions/upload-artifact@v3
      if: always()
      with:
        name: test-reports
        path: |
          app/build/reports/tests/
          app/build/reports/jacoco/
```

### 8.2 GitLab CI配置
```yaml
# .gitlab-ci.yml
stages:
  - test
  - coverage
  - deploy

unit_tests:
  stage: test
  script:
    - ./gradlew testDebugUnitTest
  artifacts:
    paths:
      - app/build/reports/tests/

integration_tests:
  stage: test
  image: cirrusci/android-sdk:30
  script:
    - ./gradlew connectedAndroidTest

coverage_report:
  stage: coverage
  script:
    - ./gradlew jacocoTestReport
    - sed -i 's/\/home\/runner\/work\/.*\///g' app/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml
    - bash <(curl -s https://codecov.io/bash)
```

## 9. 监控和报告

### 9.1 测试指标监控
```kotlin
data class TestMetrics(
    val totalTests: Int,
    val passedTests: Int,
    val failedTests: Int,
    val skippedTests: Int,
    val executionTimeMs: Long,
    val coveragePercentage: Float
)

interface TestMetricsCollector {
    suspend fun collectMetrics(): TestMetrics
    fun exportToJson(): String
    fun sendToMonitoring(metrics: TestMetrics)
}
```

### 9.2 自动化报告生成
```bash
#!/bin/bash
# generate-test-report.sh

echo "=== 测试执行摘要 ==="
./gradlew testDebugUnitTest --console=plain

echo ""
echo "=== 代码覆盖率报告 ==="
./gradlew jacocoTestReport --console=plain

echo ""
echo "=== 测试报告位置 ==="
echo "- 单元测试报告: app/build/reports/tests/testDebugUnitTest/"
echo "- 覆盖率报告: app/build/reports/jacoco/jacocoTestReport/"
echo "- HTML覆盖率: app/build/reports/jacoco/jacocoTestReport/html/index.html"
```

---

**文档版本**: 1.0
**最后更新**: 2024-01-15
**维护者**: QA工程师团队