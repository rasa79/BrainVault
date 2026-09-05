import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
    alias(libs.plugins.javafx)
}

group = "com.brainvault"
version = "1.0.0"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

javafx {
    version = "25.0.4"
    modules("javafx.controls", "javafx.web")
}

dependencies {
    implementation(libs.javafx.base)
    implementation(libs.javafx.graphics)
    implementation(libs.javafx.controls)
    implementation(libs.javafx.web)
    implementation(libs.sqlite.jdbc)
    implementation(libs.flexmark.all)
    implementation(libs.snakeyaml)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.javafx)

    testImplementation(libs.junit.jupiter)
    // Gradle 9.6.1 removed automatic inclusion of the JUnit Platform launcher on the test
    // runtime classpath. Without it the test task cannot start. This is a test-only,
    // non-runtime dependency (logged in DECISIONS.md). Version matches junit-jupiter 5.14.4.
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.14.4")
}

application {
    mainClass.set("com.brainvault.ui.MainAppKt")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
    }
}

tasks.test {
    useJUnitPlatform()
}
