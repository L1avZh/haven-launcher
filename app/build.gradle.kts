plugins { id("com.android.application") }

android {
    namespace = "dev.l1avzh.haven"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.l1avzh.haven"
        minSdk = 26
        targetSdk = 34
        versionCode = 7
        versionName = "1.6"
        // Filament ships native code. These two cover every TV box and stick we know of (the test box is 32-bit only).
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Debug keystore keeps the signature stable so later builds install as updates.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    // Renders the 3D character (Apache License 2.0). Only the core renderer and the glTF loader, not the utility library.
    // Their metadata pulls in a newer Kotlin standard library than this project's compiler accepts; the Java API needs none.
    implementation("com.google.android.filament:filament-android:1.69.5") { exclude(group = "org.jetbrains.kotlin") }
    implementation("com.google.android.filament:gltfio-android:1.69.5") { exclude(group = "org.jetbrains.kotlin") }

    // Test-only: JVM unit tests for the pure logic (request gate, caches, preference formats, JSON parsing).
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
