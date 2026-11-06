plugins {
    id("com.android.application")
    id("com.google.android.gms.oss-licenses-plugin")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "fr.swucollection.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "fr.swucollection.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 60
        versionName = "0.8.19"
    }
    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
            val keystorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
            if (!keystorePath.isNullOrBlank() && !keystorePassword.isNullOrBlank()) {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                keyAlias = "swu-release"
                keyPassword = keystorePassword
            }
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        create("preview") {
            initWith(getByName("release"))
            applicationIdSuffix = ".test"
            versionNameSuffix = "-test.11"
            signingConfig = signingConfigs.getByName("release")
            matchingFallbacks += listOf("release")
        }
    }
    buildFeatures { compose = true }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
            it.systemProperty("robolectric.usePreinstrumentedJars", "false")
            it.testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
// The private app has its own update sequence; the official release stays frozen.
androidComponents {
    onVariants(selector().withBuildType("preview")) { variant ->
        variant.outputs.forEach { it.versionCode.set(70) }
    }
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(platform("com.google.firebase:firebase-bom:34.6.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    // CameraX exposes ListenableFuture in its API. Firestore also brings Guava's
    // empty compatibility artifact, so keep the actual Android classes explicit.
    implementation("com.google.guava:guava:33.4.8-android")
    implementation(platform("androidx.compose:compose-bom:2025.10.00"))
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.browser:browser:1.10.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.work:work-runtime-ktx:2.10.5")
    implementation("androidx.camera:camera-core:1.4.2")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.google.zxing:core:3.5.4")
    implementation("com.google.android.gms:play-services-oss-licenses:17.1.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("org.json:json:20240303")
    debugImplementation("androidx.compose.ui:ui-tooling")
}


// Resolve Android test runtimes through Gradle: older published POMs do not all
// expose the SHA-512 sidecars required by Robolectric's runtime downloader.
val robolectricRuntimeDirectory = layout.buildDirectory.dir("robolectric-runtimes")
val prepareRobolectricRuntimes = listOf(
    "28" to "9-robolectric-4913185-2",
    "34" to "14-robolectric-10818077",
    "35" to "15-robolectric-13954326"
).map { (sdk, version) ->
    val runtime = configurations.create("robolectricSdk$sdk") {
        isCanBeConsumed = false
        isCanBeResolved = true
        isTransitive = false
    }
    dependencies.add(runtime.name, "org.robolectric:android-all:$version")
    tasks.register<Copy>("prepareRobolectricSdk$sdk") {
        from(runtime)
        into(robolectricRuntimeDirectory)
    }
}
tasks.withType<Test>().configureEach {
    dependsOn(prepareRobolectricRuntimes)
    systemProperty("robolectric.dependency.dir", robolectricRuntimeDirectory.get().asFile.absolutePath)
}
