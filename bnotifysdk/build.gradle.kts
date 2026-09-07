plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("maven-publish")
}
val SDKVersion = "1.0.3" // Define once for consistency

android {
    namespace = "com.convex.bnotifysdk"
    compileSdk = 36

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("build/generated/source/bnotify")
        }
    }

    buildFeatures {
        buildConfig = true   // ensures BuildConfig is generated
    }
}

publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = "com.github.bnotify"
            artifactId = "bnotifysdk"
            version = SDKVersion

            afterEvaluate {
                from(components["release"])
            }
        }
    }
    repositories {
        maven {
            url = uri("$rootDir/build/repo")
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.squareup.retrofit2:retrofit:3.0.0")

    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")
    implementation("com.github.bumptech.glide:okhttp3-integration:4.16.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("io.socket:socket.io-client:2.1.0") {
        // Excluding org.json which conflicts with Android's built-in org.json
        exclude("org.json","json");
    }

    // Firebase Messaging (no google-services plugin required)
    implementation(platform("com.google.firebase:firebase-bom:34.1.0"))
    implementation("com.google.firebase:firebase-messaging")       // Use main module
    implementation("com.google.firebase:firebase-installations")   // Use main module
    // WorkManager (Kotlin + coroutines support)
    implementation("androidx.work:work-runtime-ktx:2.10.3")
}