plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.10.2"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.20"
}

group = "com.zime.app"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    // Kotlin Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.0")
    
    // IntelliJ Platform
    intellijPlatform {
        intellijIdea("2025.2.4")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        // Compose for Desktop UI
        composeUI()

        // Required bundled plugins
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.kotlin")
        
        // Optional plugins (for enhanced features)
        // bundledPlugin("JavaScript")
        // bundledPlugin("com.intellij.modules.json")
        // bundledPlugin("org.jetbrains.plugins.yaml")
        // bundledPlugin("org.intellij.plugins.markdown")
    }
    
    // Test dependencies
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
}

intellijPlatform {
    pluginConfiguration {
        name = "Android to Flutter Porter"
        
        ideaVersion {
            sinceBuild = "252.25557"
            untilBuild = provider { null } // No upper bound
        }

        changeNotes = """
            <h3>1.0.0</h3>
            <ul>
                <li>Initial release</li>
                <li>Kotlin/Compose to Flutter/Dart conversion</li>
                <li>Claude Code integration (Hook + CLI)</li>
                <li>Rule-based and AI-assisted conversion</li>
                <li>Widget, Type, and Modifier mappings</li>
            </ul>
        """.trimIndent()
    }
    
    signing {
        // Configure signing if needed for marketplace publishing
    }
    
    publishing {
        // Configure publishing if needed
    }
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
    
    // Kotlin compilation options
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            jvmTarget = "21"
            freeCompilerArgs = listOf(
                "-Xopt-in=kotlin.RequiresOptIn",
                "-Xopt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
            )
        }
    }
    
    // Test task configuration
    test {
        useJUnitPlatform()
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
