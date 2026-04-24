#!/usr/bin/env python3
from hermes_tools import read_file, patch

# Read the current build.gradle.kts
content = read_file("app/build.gradle.kts")

# Add Room dependencies after the existing dependencies
new_content = content.replace(
    "dependencies {\n\n    implementation(\"androidx.core:core-ktx:1.12.0\")",
    """dependencies {

    implementation("androidx.core:core-ktx:1.12.0")

    // Room database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    annotationProcessor("androidx.room:room-compiler:2.6.1")

    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Hilt dependency injection
    implementation("com.google.dagger:hilt-android:2.50")"""
)

print(new_content)