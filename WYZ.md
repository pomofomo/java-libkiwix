# wyz build of java-libkiwix

This `wyz` branch is `feature/writer-bindings` (the upstream PR adding the libzim
writer bindings) plus fork-only publishing changes. It is used by wyz until
upstream releases the writer bindings; then wyz switches back to
`org.kiwix:libkiwix:<that version>` and this branch is archived.

## Artifacts

Published to this fork's GitHub Packages by `.github/workflows/wyz.yml` when a
`v<VERSION>` tag (e.g. `v2.7.0-wyz.1`) is pushed:

| Coordinates | Content |
| --- | --- |
| `io.github.pomofomo:libkiwix-wyz-jvm:2.7.0-wyz.1` | Desktop JVM jar: `org.kiwix.libzim` (reader) + `org.kiwix.libzim.writer` + `io.github.pomofomo.libkiwix.NativeLoader`, with the Linux x86_64 natives (`libzim.so.9`, `libzim_wrapper.so`) inside |
| `io.github.pomofomo:libkiwix-wyz:2.7.0-wyz.1` | The Android AAR (same as upstream plus the writer classes, which are not usable on Android: libzim for Android has no writer) |

Version scheme: `<upstream version>-wyz.<n>`. Bump `VERSION` in
`lib/build.gradle`, commit, then tag `v<VERSION>`.

## Using it from wyz (Gradle Kotlin DSL)

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/pomofomo/java-libkiwix")
        credentials {
            // A GitHub token with the read:packages scope
            username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
            password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    // desktop (JVM) source set
    implementation("io.github.pomofomo:libkiwix-wyz-jvm:2.7.0-wyz.1")
}
```

```kotlin
import io.github.pomofomo.libkiwix.NativeLoader
import org.kiwix.libzim.writer.Creator

NativeLoader.load() // once, before using org.kiwix.libzim classes
Creator().use { creator -> /* see README.md, "Create a ZIM file" */ }
```

`NativeLoader.load()` extracts the natives to a temporary directory and loads
them. Set the system property `libkiwix.native.dir` to load `libzim.so.9` and
`libzim_wrapper.so` from another directory instead (e.g. when packaging the app
with the libraries next to it).

## Platform support

- Linux x86_64 only for now. The natives need glibc ≥ 2.34 and a recent
  `libstdc++` (they are built on Ubuntu 22.04 in CI). `libzim.so` from openZIM
  is self-contained otherwise (Xapian, ICU, zstd, lzma are linked in).
- Windows and macOS: to do. openZIM publishes libzim prebuilts for them
  (check that 9.8.2 exists for each), then `libzim_wrapper` must be built for
  each platform and added under `native/<os>-<arch>/` in `lib/jvm.gradle`.

## Local build

```bash
./install_deps.sh
./gradlew buildHeaders build runTests testJvmJar
# jar: build/wyz-jvm/libs/libkiwix-wyz-jvm-<VERSION>.jar
./gradlew publishToMavenLocal   # to try it from a local wyz build (mavenLocal())
```
