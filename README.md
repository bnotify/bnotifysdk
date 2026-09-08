# BNotify Android SDK

Push notifications + in-app messaging SDK for Android (`minSdk` 24).

[![Release](https://jitpack.io/v/bnotify/bnotifysdk.svg)](https://jitpack.io/#bnotify/bnotifysdk)

## Modules

| Module | Description |
|--------|-------------|
| `bnotifysdk` | Android library (AAR) |
| `bnotifyplugin` | Gradle plugin (config code generation) |
| `app` | Sample / demo app (not published) |

## JitPack

**Step 1 — repositories** (`settings.gradle.kts`):

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Also add the same `maven { url = uri("https://jitpack.io") }` under `pluginManagement.repositories` if you use the Gradle plugin.

**Step 2 — dependency** (`app/build.gradle.kts`):

```kotlin
dependencies {
    implementation("com.github.bnotify.bnotifysdk:bnotifysdk:1.0.5")
}
```

**Plugin (optional):**

```kotlin
// project build.gradle.kts
buildscript {
    repositories { maven { url = uri("https://jitpack.io") } }
    dependencies {
        classpath("com.github.bnotify.bnotifysdk:bnotifyplugin:1.0.5")
    }
}

// app module
apply(plugin = "com.github.bnotifysdk.bnotifyplugin")
```

Exact coordinates are confirmed on the [JitPack project page](https://jitpack.io/#bnotify/bnotifysdk) after a successful build.

## Docs

- [integration.md](integration.md) — host-app integration guide
- [documentation.md](documentation.md) — architecture reference

## License

Copyright © Convex Interactive. All rights reserved.
