import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.ktlint)
}

kotlin {
    jvm {
        compilations.all {
            compilerOptions.configure {
                jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            }
        }
    }
    
    // Ensure we build with a JDK that supports jpackage and runtime bundling
    jvmToolchain(18)
    
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
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
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
        }
    }
}

ktlint {
	android.set(false)
	ignoreFailures.set(false)
	disabledRules.set(setOf("standard:argument-list-wrapping"))
	filter {
		exclude("**/build/**")
		exclude("**/generated/**")
	}
}


compose.desktop {
    application {
        mainClass = "org.example.project.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "org.example.project"
            packageVersion = "1.0.0"
            // Bundle a minimal JRE so the app runs on machines without Java installed
            includeAllModules = true
            // Optional: set vendor for Windows installer metadata
            vendor = "ExampleOrg"
            // Add modules commonly required for TLS and other functionality
            modules("jdk.unsupported", "jdk.crypto.ec")
            windows {
                // Hide console window for release builds
                console = false
                // Create Start Menu entry and Desktop shortcut
                menu = true
                shortcut = true
                // Set the application icon used for shortcuts and installer UI
                // Points to your existing icon location
                iconFile.set(project.file("src/jvmMain/composeResources/drawable/app.ico"))
                // Keep this GUID constant across releases to enable in-place upgrades
                upgradeUuid = "5a3e6f7e-4a2c-4c87-9c9a-9b2d1c1f4c55"
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
