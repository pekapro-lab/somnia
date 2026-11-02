import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Clave Pekapro compartida: ruta del .properties en PEKAPRO_KEYSTORE_PROPS (nunca en el repo).
val ksPropsFile = file(System.getenv("PEKAPRO_KEYSTORE_PROPS") ?: "/workspace/secure/keystore.properties")
val ksProps = Properties().apply { if (ksPropsFile.exists()) ksPropsFile.inputStream().use { load(it) } }

android {
    namespace = "com.pekapro.somnia"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pekapro.somnia"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"
    }

    signingConfigs {
        create("release") {
            val envKs = System.getenv("PEKAR_KEYSTORE_FILE")
            if (envKs != null) {
                // CI: secretos de GitHub Actions
                storeFile = file(envKs)
                storePassword = System.getenv("PEKAR_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PEKAR_KEY_ALIAS")
                keyPassword = System.getenv("PEKAR_KEY_PASSWORD")
            } else if (ksPropsFile.exists()) {
                storeFile = File(ksPropsFile.parentFile, File(ksProps.getProperty("storeFile")).name)
                storePassword = ksProps.getProperty("storePassword")
                keyAlias = ksProps.getProperty("keyAlias")
                keyPassword = ksProps.getProperty("keyPassword")
            }
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    lint { abortOnError = false; checkReleaseBuilds = false }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.media:media:1.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

gradle.taskGraph.whenReady {
    if (allTasks.any { it.name.contains("Release") } && android.signingConfigs.getByName("release").storeFile == null) {
        throw GradleException("Falta la clave de firma Pekapro: no se publica una APK firmada con la clave debug.")
    }
}
