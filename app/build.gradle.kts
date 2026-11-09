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

    // Test-only: JVM unit tests for the pure logic (request gate, caches, preference formats, JSON parsing).
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
