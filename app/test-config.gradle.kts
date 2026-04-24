// Test Configuration for csBaby Customer Service Assistant

// Core Testing Dependencies
dependencies {
    // Unit Testing Frameworks
    testImplementation "junit:junit:4.13.2"
    testImplementation "org.mockito:mockito-core:5.7.0"
    testImplementation "org.mockito.kotlin:mockito-kotlin:5.2.1"

    // Android Testing Libraries
    androidTestImplementation "androidx.test.ext:junit:1.1.5"
    androidTestImplementation "androidx.test.espresso:espresso-core:3.5.1"
    androidTestImplementation "androidx.compose.ui:ui-test-junit4:1.5.4"
    androidTestImplementation "androidx.test:runner:1.5.2"
    androidTestImplementation "androidx.test:rules:1.5.0"

    // Coroutine Testing
    testImplementation "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3"

    // MockWebServer for network testing
    androidTestImplementation "com.squareup.okhttp3:mockwebserver:4.12.0"

    // Truth assertions library
    testImplementation "com.google.truth:truth:1.1.5"
    androidTestImplementation "com.google.truth:truth:1.1.5"

    // Hilt testing
    kaptAndroidTest "com.google.dagger:hilt-android-compiler:2.50"
    androidTestImplementation "com.google.dagger:hilt-android-testing:2.50"
    kaptTest "com.google.dagger:hilt-android-compiler:2.50"
    testImplementation "com.google.dagger:hilt-android-testing:2.50"

    // Room testing
    androidTestImplementation "androidx.room:room-testing:2.6.1"

    // Instrumentation Testing
    androidTestImplementation "androidx.navigation:navigation-testing:2.7.6"
}

// Test Coverage Configuration
android {
    defaultConfig {
        testInstrumentationRunner "com.csbaby.kefu.CustomTestRunner"
        testCoverageEnabled true
    }

    buildTypes {
        debug {
            testCoverageEnabled true
        }
    }

    testOptions {
        unitTests {
            includeAndroidResources = true
        }
        animationsDisabled = true
    }
}

// Test Execution Configuration
tasks.register("runAllTests") {
    group = "verification"
    description = "Run all tests including unit, integration, and UI tests"

    dependsOn(":app:testDebugUnitTest")
    dependsOn(":app:connectedAndroidTest")
}

tasks.register("runSecurityTests") {
    group = "verification"
    description = "Run only security-related tests"

    dependsOn(":app:testDebugUnitTest") {
        filter { it.contains("SecurityTests") }
    }
}

tasks.register("runFunctionalTests") {
    group = "verification"
    description = "Run only functional tests"

    dependsOn(":app:testDebugUnitTest") {
        filter { it.contains("FunctionalTests") }
    }
}

// Code Coverage Report Generation
tasks.register("generateCoverageReport") {
    group = "verification"
    description = "Generate code coverage report"

    doLast {
        exec {
            commandLine("bash", "-c", "./gradlew :app:jacocoTestReport")
        }
    }
}