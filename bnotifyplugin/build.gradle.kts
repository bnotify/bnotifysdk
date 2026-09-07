plugins {
    `kotlin-dsl` // Required for custom Gradle plugins
    `java-gradle-plugin`
    `maven-publish`
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
}

val pluginVersion = "1.0.3" // Define once for consistency

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation(gradleApi())
    implementation("com.android.tools.build:gradle:8.11.1")
    implementation("com.google.code.gson:gson:2.13.1")
}

gradlePlugin {
    plugins {
        create("bnotifyplugin") {
            id = "com.github.bnotifysdk.bnotifyplugin"
//            id = "com.github.bnotifysdk.bnotifyplugin" // Match your package
            implementationClass = "com.convex.bnotifyplugin.MainGradlePlugin"
            version = pluginVersion// Add version here
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("pluginMaven") {
            groupId = "com.github.bnotify"
            artifactId = "bnotifyplugin"
            version = pluginVersion

            artifact(tasks.register("pluginMarker", Jar::class) {
                archiveClassifier.set("plugin-marker")
            })
        }
    }
    repositories {
        maven {
            url = uri("$rootDir/build/repo")
        }
    }
}

sourceSets["main"].java.srcDirs("src/main/kotlin")

tasks.withType<ProcessResources> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
