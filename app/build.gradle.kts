plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val appVersionName = "0.9.7"
val appVersionCode = 157

val keystoreFile: String? = System.getenv("KEYSTORE_FILE")

android {
    namespace = "com.liquid.liquidbaby"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.liquid.liquidbaby"
        minSdk = 23
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        val repo = providers.gradleProperty("githubRepo").getOrElse("")
        buildConfigField("String", "GITHUB_REPO", "\"$repo\"")
        buildConfigField("boolean", "IS_TEST", "false")
    }

    buildFeatures {
        buildConfig = true
        resValues = true
        compose = true
    }

    signingConfigs {
        create("release") {
            if (keystoreFile != null) {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            buildConfigField("boolean", "IS_TEST", "false")
            signingConfig = if (keystoreFile != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        // Separate trial app: installs next to the main app, has its own name and does not auto-update.
        // Gradle does not allow build type names starting with "test", so this one is "trial".
        create("trial") {
            initWith(getByName("release"))
            applicationIdSuffix = ".trial"
            resValue("string", "app_name", "Liquid Baby Trial")
            buildConfigField("boolean", "IS_TEST", "true")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    // Jetpack Compose (approved): one BOM keeps all Compose parts on matching versions
    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    testImplementation("junit:junit:4.13.2")
}
