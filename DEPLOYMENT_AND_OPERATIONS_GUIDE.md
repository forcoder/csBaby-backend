# Android应用部署和操作手册

## 1. 部署前准备

### 1.1 环境要求
- **操作系统**: macOS/Linux (推荐), Windows 10+ (WSL2)
- **Java版本**: JDK 17 或更高版本
- **Android SDK**: API Level 34, Build Tools 34.0.0+
- **Gradle**: Gradle Wrapper (自动管理版本)
- **内存要求**: 至少 8GB RAM (推荐 16GB)

### 1.2 配置检查清单

```bash
# 验证环境配置
./gradlew doctor

# 检查项目结构
./gradlew projects

# 验证依赖解析
./gradlew :app:dependencies --configuration implementation
```

### 1.3 签名密钥配置

#### 1.3.1 创建Release签名密钥
```bash
# 生成PKCS12格式的签名密钥
keytool -genkeypair \
    -v \
    -keystore keystore/csbaby-release.p12 \
    -alias csbaby-release \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -storetype PKCS12 \
    -storepass <your-store-password> \
    -keypass <your-key-password> \
    -dname "CN=客服小秘,O=CSBaby,C=CN"
```

#### 1.3.2 CI/CD密钥配置
在GitHub Secrets中设置以下密钥：
- `SIGNING_KEYSTORE_BASE64`: Base64编码的P12文件内容
- `SIGNING_STORE_PASSWORD`: 密钥库密码
- `SIGNING_KEY_ALIAS`: 密钥别名（csbaby-release）
- `SIGNING_KEY_PASSWORD`: 密钥密码

```bash
# 将P12文件转换为Base64
base64 -i keystore/csbaby-release.p12 > keystore/base64.txt
```

## 2. 构建流程

### 2.1 本地构建

#### 2.1.1 Debug构建（快速迭代）
```bash
# 清理并构建Debug APK
./gradlew clean assembleDebug

# 指定内存参数
./gradlew assembleDebug \
    --no-daemon \
    -Dorg.gradle.jvmargs="-Xmx2048m -Xms512m" \
    --console=plain
```

#### 2.1.2 Release构建（生产发布）
```bash
# 构建签名Release APK
./gradlew assembleRelease \
    -PSIGNING_STORE_PASSWORD="your-store-password" \
    -PSIGNING_KEY_ALIAS="csbaby-release" \
    -PSIGNING_KEY_PASSWORD="your-key-password" \
    --no-daemon \
    -Dorg.gradle.jvmargs="-Xmx4096m -Xms1024m" \
    --console=plain
```

#### 2.1.3 构建变体
```bash
# 构建所有变体
./gradlew build

# 仅构建特定变体
./gradlew assembleProductFlavorDebug
./gradlew assembleProductFlavorRelease
```

### 2.2 CI/CD构建

#### 2.2.1 GitHub Actions工作流程
```yaml
# .github/workflows/enhanced-cd.yml
name: Enhanced CI/CD

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  # 完整的CI/CD流水线
  ci-cd-pipeline:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Setup JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'microsoft'

      - name: Cache dependencies
        uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*') }}

      - name: Build and Test
        run: ./gradlew build --console=plain

      - name: Upload artifacts
        uses: actions/upload-artifact@v4
        with:
          name: app-artifacts
          path: app/build/outputs/apk/
```

## 3. 发布流程

### 3.1 版本管理

#### 3.1.1 语义化版本控制
- **主版本号** (Major): 不兼容的API更改
- **次版本号** (Minor): 向后兼容的功能性新增
- **修订号** (Patch): 向后兼容的问题修正

#### 3.1.2 版本递增脚本
```bash
#!/bin/bash
# scripts/version-manager.sh

# 使用方式: ./scripts/version-manager.sh {patch|minor|major}
# 示例: ./scripts/version-manager.sh patch

case "$1" in
    "patch")
        NEW_PATCH=$(($(grep APP_VERSION_NAME gradle.properties | cut -d'=' -f2 | cut -d'.' -f3) + 1))
        NEW_VERSION="${CURRENT_MAJOR}.${CURRENT_MINOR}.$NEW_PATCH"
        ;;
    "minor")
        NEW_MINOR=$(( $(grep APP_VERSION_NAME gradle.properties | cut -d'=' -f2 | cut -d'.' -f2) + 1 ))
        NEW_PATCH=0
        NEW_VERSION="$CURRENT_MAJOR.$NEW_MINOR.$NEW_PATCH"
        ;;
    "major")
        NEW_MAJOR=$(( $(grep APP_VERSION_NAME gradle.properties | cut -d'=' -f2 | cut -d'.' -f1) + 1 ))
        NEW_MINOR=0
        NEW_PATCH=0
        NEW_VERSION="$NEW_MAJOR.0.0"
        ;;
    *)
        echo "Usage: $0 {patch|minor|major}"
        exit 1
        ;;
esac

# 更新版本文件
sed -i "s/APP_VERSION_NAME=.*/APP_VERSION_NAME=$NEW_VERSION/" gradle.properties
sed -i "s/APP_VERSION_CODE=.*/APP_VERSION_CODE=$(( $(grep APP_VERSION_CODE gradle.properties | cut -d'=' -f2) + 1))/" gradle.properties

echo "Version updated to: $NEW_VERSION"
```

### 3.2 自动化发布

#### 3.2.1 GitHub Release流程
```bash
# 创建Git标签
git tag v1.1.105

# 推送标签到远程仓库
git push origin v1.1.105

# 手动触发release.yml工作流
gh workflow run release.yml
```

#### 3.2.2 shz.al上传配置
确保以下Secrets已设置：
- `SHZAL_PASSWORD`: shz.al账户密码
- `SIGNING_KEYSTORE_BASE64`: 签名密钥的Base64编码
- `SIGNING_STORE_PASSWORD`: 密钥库密码
- `SIGNING_KEY_ALIAS`: 密钥别名
- `SIGNING_KEY_PASSWORD`: 密钥密码

## 4. 监控和维护

### 4.1 性能监控配置

#### 4.1.1 启动时间监控
```kotlin
// MainActivity.kt
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val startTime = System.currentTimeMillis()

    // 初始化完成后的性能跟踪
    lifecycleScope.launch {
        delay(1000) // 等待关键初始化完成
        AppPerformanceMonitorProvider.get(this@MainActivity)
            .trackStartup(startTime, "main_activity")
    }
}
```

#### 4.1.2 内存监控
```kotlin
// 定期内存监控（每5分钟）
val memoryMonitorJob = CoroutineScope(Dispatchers.Default).launch {
    while (isActive) {
        AppPerformanceMonitorProvider.get(context)
            .trackMemoryUsage("background_task")

        delay(5 * 60 * 1000) // 5分钟
    }
}
```

### 4.2 OTA更新机制

#### 4.2.1 更新检查配置
```kotlin
// infrastructure/update/UpdateChecker.kt
class UpdateChecker @Inject constructor(
    private val apiService: ApiService,
    private val packageManager: PackageManager
) {
    suspend fun checkForUpdates(): UpdateInfo? {
        return try {
            val currentVersion = getInstalledVersion()
            val latestVersion = apiService.getLatestVersion()

            if (latestVersion.code > currentVersion.code) {
                UpdateInfo(
                    hasUpdate = true,
                    currentVersion = currentVersion,
                    latestVersion = latestVersion,
                    downloadUrl = latestVersion.downloadUrl
                )
            } else null
        } catch (e: Exception) {
            Timber.e(e, "Failed to check for updates")
            null
        }
    }

    suspend fun performBackgroundCheck() {
        // 在后台定期检查更新
        workRequest = PeriodicWorkRequestBuilder<UpdateWorker>(24, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "update_check",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
```

### 4.3 错误报告集成

#### 4.3.1 Crashlytics配置
```kotlin
// infrastructure/monitoring/CrashReporter.kt
fun setupCrashReporting() {
    if (BuildConfig.DEBUG) {
        Timber.plant(Timber.DebugTree())
    } else {
        // Firebase Crashlytics集成
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)

        // 自定义异常处理
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            crashReporter.recordException(exception, "uncaught_exception")
            // 记录设备信息
            recordDeviceInfo()
            // 延迟重启应用
            Handler(Looper.getMainLooper()).postDelayed({
                Process.killProcess(Process.myPid())
            }, 3000)
        }
    }
}
```

## 5. 故障排除

### 5.1 常见构建问题

#### 5.1.1 Gradle同步失败
```bash
# 清理Gradle缓存
rm -rf ~/.gradle/caches/
./gradlew clean

# 重新同步项目
./gradlew --refresh-dependencies
```

#### 5.1.2 签名问题
```bash
# 验证APK签名
apksigner verify --verbose app/build/outputs/apk/release/app-release.apk

# 检查密钥库
keytool -list -v -keystore keystore/csbaby-release.p12 -storetype PKCS12
```

### 5.2 性能问题诊断

#### 5.2.1 构建时间过长
```bash
# 启用构建扫描
./gradlew build --scan

# 分析依赖冲突
./gradlew :app:dependencies --configuration implementation --all

# 启用并行编译
./gradlew build --parallel --daemon
```

#### 5.2.2 APK过大
```bash
# 分析APK组成
./gradlew assembleRelease
./apkanalyzer apk summary app/build/outputs/apk/release/app-release.apk

# 检查未使用的资源
./gradlew clean
./gradlew build --dry-run
```

## 6. 安全最佳实践

### 6.1 密钥管理
- **永远不要**将密钥提交到版本控制系统
- 使用环境变量或CI/CD Secrets管理敏感信息
- 定期轮换签名密钥
- 限制密钥访问权限

### 6.2 安全构建配置
```kotlin
android {
    buildTypes {
        release {
            // 禁用调试信息
            isDebuggable = false
            // 启用代码混淆
            isMinifyEnabled = true
            // 启用资源压缩
            isShrinkResources = true
            // 移除日志
            buildConfigField "boolean", "LOGGING_ENABLED", "false"
        }
    }
}
```

### 6.3 安全测试
```bash
# 静态代码分析
./gradlew lintDebug

# 安全漏洞扫描
dependency-check --project "客服小秘" --scan .

# 权限分析
./gradlew permissionsReport
```

## 7. 回滚策略

### 7.1 版本回滚
```bash
# 查看版本历史
git log --oneline --decorate --tags

# 回滚到特定版本
git checkout tags/v1.1.104

# 创建热修复分支
git checkout -b hotfix/v1.1.105-fix main
```

### 7.2 紧急恢复
```bash
# 快速构建上一个稳定版本
./gradlew assembleRelease -PreleaseVersion=v1.1.104

# 回滚构建配置
git stash && git checkout HEAD~1 -- app/build.gradle.kts
```

## 8. 文档和资源

### 8.1 相关文档
- [ANDROID_PERFORMANCE_OPTIMIZATION_STRATEGY.md](ANDROID_PERFORMANCE_OPTIMIZATION_STRATEGY.md)
- [CI/CD工作流说明](#)
- [性能监控指南](#)
- [安全最佳实践](#)

### 8.2 工具链
- **Android Studio**: 官方IDE
- **Gradle**: 构建系统
- **Firebase**: 崩溃报告和性能监控
- **shz.al**: 文件分发服务

---

**手册版本**: 1.0  
**最后更新**: 2026年4月30日  
**维护者**: DevOps工程师团队