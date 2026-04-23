@echo off
chcp 65001 >nul
echo ========================================
echo 客服小秘 - 编译 + 版本升级 + 上传
echo ========================================
echo.

:: 设置环境变量
set JAVA_HOME=D:\jdk19
set ANDROID_HOME=D:\Android\SDK
set ANDROID_SDK_ROOT=D:\Android\SDK
set PATH=%JAVA_HOME%\bin;%PATH%
set GRADLE_OPTS=-Xmx4096m

cd /d D:\workspace\workbuddy\csBaby

:: 1. 编译 APK（Gradle 会自动执行 incrementVersion 递增版本号）
echo [1/2] 编译 APK（自动递增版本号）...
call gradlew.bat assembleDebug --no-daemon
if errorlevel 1 (
    echo.
    echo [错误] 编译失败
    pause
    exit /b 1
)

echo.
echo [成功] 编译完成

:: 2. 上传到 shz.al
echo.
echo [2/2] 上传到 shz.al...
powershell -ExecutionPolicy Bypass -File "upload-to-shzl.ps1"
if errorlevel 1 (
    echo.
    echo [错误] 上传失败
    pause
    exit /b 1
)

echo.
echo ========================================
echo 全部完成！
echo ========================================
if exist "version-info.json" (
    echo.
    echo 最新版本信息：
    powershell -NoProfile -Command "$v = Get-Content 'version-info.json' -Raw | ConvertFrom-Json; Write-Host ('  版本: v' + $v.versionName + ' (' + $v.versionCode + ')'); Write-Host ('  大小: ' + [math]::Round($v.fileSize/1MB,2) + ' MB'); Write-Host ('  MD5: ' + $v.md5); Write-Host ('  下载: ' + $v.downloadUrl)"
)
echo.
pause
