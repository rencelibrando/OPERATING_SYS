import org.gradle.api.tasks.Copy
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.ktlint)
}

// Configure Java compatibility
java {
    sourceCompatibility = JavaVersion.VERSION_18
    targetCompatibility = JavaVersion.VERSION_18
}

kotlin {
    jvm {
        compilations.all {
            compilerOptions.configure {
                jvmTarget.set(JvmTarget.JVM_18)
            }
        }
    }
    jvmToolchain(18)
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.collection)
            implementation(libs.kamel.image)

            // Supabase
            implementation(libs.supabase.postgrest)
            implementation(libs.supabase.gotrue)
            implementation(libs.supabase.realtime)
            implementation(libs.supabase.functions)
            implementation(libs.supabase.storage)

            // Ktor
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)

            // Serialization & DateTime
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)

            implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.androidx.collection)

            // MP3 audio support
            implementation("com.googlecode.soundlibs:jlayer:1.0.1.4")
            implementation("com.googlecode.soundlibs:mp3spi:1.9.5.4")
        }
    }
}

// Task to copy .env file to JAR resources before packaging
tasks.named("jvmProcessResources", Copy::class) {
    val envFile = layout.projectDirectory.file(".env").asFile
    val rootEnvFile = rootProject.layout.projectDirectory.file(".env").asFile
    val projectRootEnvFile = File(rootProject.layout.projectDirectory.asFile.parentFile, ".env")

    val envFileToInclude =
        when {
            envFile.exists() -> envFile
            rootEnvFile.exists() -> rootEnvFile
            projectRootEnvFile.exists() -> projectRootEnvFile
            else -> null
        }

    if (envFileToInclude != null) {
        from(envFileToInclude) {
            into(".")
            rename { ".env" }
        }
        // Also copy as env.config
        from(envFileToInclude) {
            into(".")
            rename { "env.config" }
        }
        doFirst {
            println("Copying .env file to JAR resources: ${envFileToInclude.absolutePath}")
        }
    } else {
        doFirst {
            println("WARNING: No .env file found to include in JAR resources")
        }
    }
}

ktlint {
    android.set(false)
    ignoreFailures.set(false)
    filter {
        exclude("**/build/**")
        exclude("**/generated/**")
    }
}

// Task to copy icons from composeResources to the app resources directory
val copyIconsToResources by tasks.registering(Copy::class) {
    group = "distribution"
    description = "Copies icons from composeResources to app resources"

    val iconsDir = project.layout.projectDirectory.dir("src/jvmMain/composeResources/drawable")
    val resourcesDirProvider = layout.buildDirectory.dir("compose/resources")

    from(iconsDir) {
        include("*.png")
        include("*.ico")
        into("drawable")
    }
    into(resourcesDirProvider)
}

// Task to copy the Python backend into the resources used by the installer
val copyBackendToResources by tasks.registering(Copy::class) {
    group = "distribution"
    description = "Copies the backend Python directory into the installer resources"

    // Backend lives at the repo root:./backend relative to this module
    val backendDir = rootProject.layout.projectDirectory.asFile.parentFile.resolve("backend")

    // Only run if the backend exists
    onlyIf { backendDir.exists() }

    // Destination: compose resources directory used by nativeDistributions.appResourcesRootDir
    val resourcesDirProvider = layout.buildDirectory.dir("compose/resources")
    into(resourcesDirProvider)

    // Copy backend (excluding venv / caches) into resources/backend
    from(backendDir) {
        exclude("venv/**")
        exclude("__pycache__/**")
        exclude("*.pyc")
        exclude("**/__pycache__/**")
        into("backend")
    }
}

// Ensure backend and icons are copied before packaging installers (MSI/DMG/DEB)
tasks.matching { task ->
    task.name.startsWith("packageRelease") &&
        (
            task.name.contains("Msi", ignoreCase = true) ||
                task.name.contains("Dmg", ignoreCase = true) ||
                task.name.contains("Deb", ignoreCase = true)
        )
}.configureEach {
    dependsOn(copyIconsToResources, copyBackendToResources)
}

compose.desktop {
    application {
        mainClass = "org.example.project.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "WordBridge"
            packageVersion = "1.0.0"
            description = "Language Learning Assistant with AI-powered conversations"

            // Bundle a minimal JRE so the app runs on machines without Java installed
            includeAllModules = true

            // Optional: set vendor for Windows installer metadata
            vendor = "WordBridge"

            // Add modules commonly required for TLS and other functionality
            modules("jdk.unsupported", "jdk.crypto.ec")

            // Include extra resources (backend + icons) from build/compose/resources
            val resourcesDir = layout.buildDirectory.dir("compose/resources")
            appResourcesRootDir.set(resourcesDir)

            // Add a license file if you have one (optional)
            // licenseFile.set(project.file("LICENSE.txt"))

            windows {
                // Show a console window for debugging (set to false for release builds)
                console = false

                // Create Start Menu entry and Desktop shortcut
                menu = true
                shortcut = true

                // Set the application icon used for shortcuts and installer UI
                iconFile.set(project.file("src/jvmMain/composeResources/drawable/app.ico"))

                // Keep this GUID constant across releases to enable in-place upgrades
                // This is important for Windows Update to recognize upgrades
                upgradeUuid = "5a3e6f7e-4a2c-4c87-9c9a-9b2d1c1f4c55"

                // Windows installer metadata
                menuGroup = "WordBridge"
                dirChooser = true
                perUserInstall = false // Install for all users (requires admin)

                // Optional: Add Windows registry entries
                // See: https://github.com/JetBrains/compose-multiplatform/blob/master/components/tooling/native-distributions/src/commonMain/kotlin/org/jetbrains/compose/desktop/application/dsl/NativeDistribution.kt
            }
        }
        buildTypes {
            release {
                proguard {
                    isEnabled.set(false)
                    configurationFiles.from("proguard-rules.pro")
                }
            }
        }
    }
}

// Custom task to run admin app
tasks.register<JavaExec>("runAdmin") {
    group = "application"
    description = "Runs the WordBridge Admin application"
    mainClass.set("org.example.project.admin.AdminMainKt")

    val jvmMain = kotlin.jvm().compilations.getByName("main")
    val runtimeClasspath = configurations.getByName(jvmMain.runtimeDependencyConfigurationName)

    classpath(
        jvmMain.output.classesDirs,
        runtimeClasspath,
    )

    dependsOn("jvmProcessResources", "jvmMainClasses")


    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(18))
        },
    )
}
