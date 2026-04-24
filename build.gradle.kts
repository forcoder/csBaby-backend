// 客服小秘 - Android项目构建配置
plugins {
    id("com.android.application") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.21" apply false
    id("com.google.dagger.hilt.android") version "2.50" apply false
    id("com.google.devtools.ksp") version "1.9.21-1.0.16" apply false
    id("org.jetbrains.kotlin.kapt") version "1.9.21" apply false
}

// 自动版本递增任务
tasks.register("incrementVersion") {
    group = "build"
    description = "Automatically increment version code and version name"
    
    doLast {
        val propsFile = file("gradle.properties")
        if (!propsFile.exists()) {
            logger.error("gradle.properties not found")
            return@doLast
        }
        
        val props = java.util.Properties()
        propsFile.reader().use { reader ->
            props.load(reader)
        }
        
        val currentCode = props.getProperty("APP_VERSION_CODE")?.toIntOrNull() ?: 0
        val currentName = props.getProperty("APP_VERSION_NAME") ?: "1.0.0"
        
        // 递增版本码
        val newCode = currentCode + 1
        
        // 递增版本名的补丁号
        val parts = currentName.split(".")
        val newName = if (parts.size >= 3) {
            // 1.1.1 -> 1.1.2
            parts.dropLast(1).plus((parts.last().toIntOrNull() ?: 0) + 1).joinToString(".")
        } else if (parts.size == 2) {
            // 1.1 -> 1.2
            parts.dropLast(1).plus((parts.last().toIntOrNull() ?: 0) + 1).joinToString(".")
        } else {
            // 1 -> 2
            (currentName.toIntOrNull() ?: 0 + 1).toString()
        }
        
        // 更新 Properties
        props.setProperty("APP_VERSION_CODE", newCode.toString())
        props.setProperty("APP_VERSION_NAME", newName)
        
        // 写回文件
        propsFile.writer().use { writer ->
            props.store(writer, null)
        }
        
        logger.lifecycle("Version: v$currentName ($currentCode) -> v$newName ($newCode)")
        logger.lifecycle("gradle.properties updated")
    }
}

// 在编译任务前自动执行版本递增
tasks.matching { 
    it.name in listOf("assembleDebug", "assembleRelease") 
}.configureEach {
    if (!project.hasProperty("skipVersionIncrement")) {
        dependsOn("incrementVersion")
    }
}

// 在编译任务后自动执行上传
tasks.matching { 
    it.name in listOf("assembleDebug", "assembleRelease") 
}.configureEach {
    if (!project.hasProperty("skipUpload")) {
        doLast {
            val uploadScript = file("upload-to-shzl.py")
            if (uploadScript.exists()) {
                logger.lifecycle("Executing upload script...")
                exec {
                    commandLine("python", uploadScript.absolutePath)
                    workingDir(projectDir)
                }
            } else {
                logger.warn("Upload script not found: ${uploadScript.absolutePath}")
            }
        }
    }
}
