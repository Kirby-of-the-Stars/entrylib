plugins {
    val kotlinVersion = "1.8.0"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion

    id("com.github.gmazzo.buildconfig") version "3.1.0"
    id("net.mamoe.mirai-console") version "2.16.0"
}

group = "com.billyang"
version = "1.3.2"

repositories {
    if (System.getenv("CI")?.toBoolean() != true) {
        maven("https://maven.aliyun.com/repository/public") // 阿里云国内代理仓库
    }
    mavenCentral()
}

buildConfig {
    className("BuildConstants")
    packageName("org.example.mirai.plugin")
    useKotlinOutput()

    buildConfigField("String", "VERSION", "\"${project.version}\"")
}

mirai {
    noTestCore = true
    setupConsoleTestRuntime {
        // 移除 mirai-core 依赖
        classpath = classpath.filter {
            !it.nameWithoutExtension.startsWith("mirai-core-jvm")
        }
    }
}

dependencies {

    val overflowVersion = "1.0.3"
    compileOnly("top.mrxiaom.mirai:overflow-core-api:$overflowVersion")
    testConsoleRuntime("top.mrxiaom.mirai:overflow-core:$overflowVersion")
    implementation(fileTree(mapOf("dir" to "lib", "include" to listOf("*.jar"))))
}
