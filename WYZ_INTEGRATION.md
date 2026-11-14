# Using libkiwix-wyz in an app (ZIM writer for the JVM)

A self-contained guide for an application (wyz) that needs to **write** (and read)
ZIM files from Kotlin/Java on the desktop JVM, using the `libkiwix-wyz-jvm` build
of [java-libkiwix](https://github.com/kiwix/java-libkiwix) with libzim writer
bindings, from the fork `pomofomo/java-libkiwix`.

| | |
| --- | --- |
| Artifact | `io.github.pomofomo:libkiwix-wyz-jvm:2.7.0-wyz.1` |
| Repository | `https://maven.pkg.github.com/pomofomo/java-libkiwix` (GitHub Packages) |
| Native library | libzim **9.8.2** (bundled) |
| Platforms | **Linux x86_64 only**, glibc ≥ 2.34 (Ubuntu 22.04+, Debian 12+, Fedora 35+) |
| JVM | Java 8+ bytecode; tested on Java 17 |
| License | GPL-3.0-or-later (bindings), GPL-2.0-or-later (libzim) |
| Source | branch `feature/writer-bindings`, tag `v2.7.0-wyz.1` of `pomofomo/java-libkiwix` |

There is also `io.github.pomofomo:libkiwix-wyz:2.7.0-wyz.1` (Android AAR). It
contains the same classes but **the writer does not work on Android** (openZIM's
Android libzim is built without the writer); only use it if the Android app needs
the *reader*.

---

## 1. Add the dependency

### Credentials

GitHub Packages requires authentication even to read. Use a **classic** personal
access token with the `read:packages` scope (fine-grained tokens are not
supported by the GitHub Maven registry). Keep it out of the repository, e.g. in
`~/.gradle/gradle.properties`:

```properties
gpr.user=<github-username>
gpr.key=<classic PAT with read:packages>
```

In GitHub Actions of the app repository, either store such a token as a secret,
or use `GITHUB_TOKEN` after granting the app repository read access to the
package (package page → *Package settings* → *Manage Actions access*).

### Gradle (Kotlin DSL)

`settings.gradle.kts` (or the `repositories {}` of the module):

```kotlin
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        maven {
            name = "libkiwixWyz"
            url = uri("https://maven.pkg.github.com/pomofomo/java-libkiwix")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
            }
            content { includeGroup("io.github.pomofomo") } // only look here for this group
        }
    }
}
```

Kotlin Multiplatform, desktop target only:

```kotlin
kotlin {
    jvm("desktop")
    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation("io.github.pomofomo:libkiwix-wyz-jvm:2.7.0-wyz.1")
            }
        }
    }
}
```

Plain JVM module: `implementation("io.github.pomofomo:libkiwix-wyz-jvm:2.7.0-wyz.1")`.

The artifact has no transitive dependencies. A `-sources.jar` is published, so
the IDE shows the Javadoc of every class.

**Without GitHub Packages** (quick local try): download the `libkiwix-wyz`
artifact of the
[wyz workflow run for the tag](https://github.com/pomofomo/java-libkiwix/actions/workflows/wyz.yml),
unzip it, and use `implementation(files("libs/libkiwix-wyz-jvm-2.7.0-wyz.1.jar"))`.

## 2. Load the native libraries (once)

The jar contains `native/linux-x86_64/libzim.so.9` and `libzim_wrapper.so`.
Call this once at startup, before touching any `org.kiwix.libzim` class:

```kotlin
import io.github.pomofomo.libkiwix.NativeLoader

NativeLoader.load() // idempotent and thread-safe
```

It extracts the two libraries to a temporary directory (deleted at JVM exit)
and loads them. On any other OS/arch it throws `UnsatisfiedLinkError` with a
clear message: guard the feature accordingly.

If the temp directory is not usable (e.g. mounted `noexec`), or when packaging
the libraries next to the app, extract them yourself and point the loader to
them: `-Dlibkiwix.native.dir=/path/to/dir` (containing `libzim.so.9` and
`libzim_wrapper.so`).

Compose Desktop / jpackage: nothing special to configure; the jar is packaged
like any dependency and the loader extracts the natives at runtime.

## 3. Write a ZIM file

```kotlin
import org.kiwix.libzim.writer.*
import java.io.File

fun buildZim(out: File) {
    Creator().use { creator ->
        creator
            .configIndexing(true, "eng")   // full-text + title (suggestion) index; ISO 639-3 language
            .configClusterSize(2L * 1024 * 1024)
            .configNbWorkers(4)
        creator.startZimCreation(out.path)  // writes "<out>.tmp" until finished

        // Metadata (see section 5)
        creator.addMetadata("Name", "wyz_en_mypack")
        creator.addMetadata("Title", "My pack")
        creator.addMetadata("Description", "A short description")
        creator.addMetadata("Language", "eng")
        creator.addMetadata("Creator", "wyz")
        creator.addMetadata("Publisher", "wyz")
        creator.addMetadata("Date", "2026-11-14")
        creator.addMetadata("X-Pack-Format", "1")                                   // custom, text
        creator.addMetadata("X-Pack-Sources", sourcesJson.toByteArray(), "application/json") // custom, any mimetype
        creator.addIllustration(48, File("icon-48.png").readBytes())                // Illustration_48x48@1 (PNG)

        // Entries. Paths have no namespace ("index.html", "img/a.webp").
        val front = mapOf(Hint.FRONT_ARTICLE to 1L)
        creator.addItem(StringItem("index.html", "text/html", "Home", front, homeHtml))
        creator.addItem(StringItem("pages/intro.html", "text/html", "Introduction", front, introHtml))
        creator.addItem(StringItem("img/diagram.webp", "image/webp", "", null, webpBytes))       // bytes in memory
        creator.addItem(FileItem("img/photo.webp", "image/webp", "", null, "/path/photo.webp"))  // read from disk
        creator.addRedirection("start.html", "Start", "index.html")
        creator.addAlias("intro.html", "Intro", "pages/intro.html") // same content, second path
        creator.setMainPath("index.html")

        creator.finishZimCreation()  // waits for workers, writes indexes, renames .tmp -> out
    }
}
```

Run it off the UI thread (e.g. `withContext(Dispatchers.IO)`):
`finishZimCreation()` blocks until everything is compressed and indexed.

### Abort / cancel

If the `use {}` block exits without `finishZimCreation()` (exception, user
cancel, coroutine cancellation), `close()` **aborts** the creation: libzim deletes
`<out>.tmp` and no file is left at `out`. `close()` never finishes a creation by
itself. A creator that is never closed is aborted when garbage collected, but
always use `use {}`.

## 4. API reference (package `org.kiwix.libzim.writer`)

The Javadoc in the sources jar is the reference; source:
[`lib/src/main/java/org/kiwix/libzim/writer/`](https://github.com/pomofomo/java-libkiwix/tree/v2.7.0-wyz.1/lib/src/main/java/org/kiwix/libzim/writer).
Working examples of every call:
[`TestCreator.java`](https://github.com/pomofomo/java-libkiwix/blob/v2.7.0-wyz.1/lib/src/test/org/kiwix/test/libzim/TestCreator.java).

### `Creator : AutoCloseable` (wraps `zim::writer::Creator`)

| Method | Notes |
| --- | --- |
| `Creator()` | Default config. |
| `configVerbose(Boolean): Creator` | Progress on stdout. Default false. |
| `configCompression(Compression): Creator` | `ZSTD` (default) or `NONE`. |
| `configClusterSize(Long): Creator` | Target cluster size in bytes, > 0. Default 2 MiB. |
| `configIndexing(Boolean, String?): Creator` | Full-text + title index of `text/html` items; language = ISO 639-3 (`"eng"`, `"fra"`, …). Default off. |
| `configNbWorkers(Int): Creator` | Worker threads, ≥ 1. Default 4. |
| `startZimCreation(path: String)` | Creates `path.tmp`. Throws `ZimCreatorException` if it can't (e.g. missing directory). |
| `addItem(item: Item)` | `StringItem` or `FileItem`. |
| `addMetadata(name: String, content: String)` | `text/plain;charset=utf-8`. |
| `addMetadata(name: String, content: ByteArray, mimetype: String)` | Any mimetype. |
| `addIllustration(size: Int, png: ByteArray)` | Square PNG → `Illustration_<size>x<size>@1`. |
| `addIllustration(info: org.kiwix.libzim.IllustrationInfo, content: ByteArray)` | `width`, `height`, `scale` (e.g. 48×48@2). |
| `addRedirection(path, title, targetPath[, hints])` | Redirects to `targetPath`. Dangling ones are dropped at finish. |
| `addAlias(path, title, targetPath[, hints])` | Another path with the *same content* as an already added entry. |
| `setMainPath(path: String)` | Main page. Any time before finish. |
| `setUuid(uuid: ByteArray)` | Exactly 16 bytes. Random by default. |
| `finishZimCreation()` | Completes the file. |
| `close()` / `dispose()` | Releases; aborts if unfinished. Safe to call twice. |

Config methods return the creator (chainable) and must be called **before**
`startZimCreation()`.

### Items

| Class | Constructor |
| --- | --- |
| `StringItem` | `StringItem(path, mimetype, title, hints: Map<Hint, Long>?, content: ByteArray)` and the same with `content: String` (stored as UTF-8) |
| `FileItem` | `FileItem(path, mimetype, title, hints: Map<Hint, Long>?, filePath: String)` — the file is opened when added and read later by a worker: don't modify/delete it before `finishZimCreation()` returns |

`Item` is abstract with a package-private constructor: you cannot implement your
own items (no Java callbacks from libzim threads). Getters: `getPath()`,
`getMimetype()`, `getTitle()`, `FileItem.getFilePath()`. An item can be
dropped/disposed once added.

### Hints and enums

- `Hint.FRONT_ARTICLE` (`1L` = yes, `0L` = no): a page users see; listed in the
  title index and suggestions, counted by `Archive.getArticleCount()`. Without
  the hint, libzim treats `text/html` items as front articles and everything
  else (including redirections and aliases) as not.
- `Hint.COMPRESS` (`1L`/`0L`): force (no) compression; by default libzim decides
  from the mimetype.
- `Compression.ZSTD`, `Compression.NONE`.

### Errors

| Exception | When |
| --- | --- |
| `ZimCreatorException` (checked, base class) | Any libzim writer error, e.g. cannot create the file, missing `FileItem` file, or an error in a worker thread (message starts with `Asynchronous error`). Worker errors are thrown by a *later* call, at the latest `finishZimCreation()`. |
| `InvalidEntryException : ZimCreatorException` | Duplicate path or metadata name, alias to a missing entry. The entry is rejected, **the creator stays usable**. |
| `CreatorStateException : ZimCreatorException` | Every call after a worker error: the file can't be completed; close the creator (which removes the `.tmp`). |
| `IllegalStateException` | Wrong order: config after start, add before start, anything after finish, any call after close. |
| `IllegalArgumentException` | Bad values: UUID not 16 bytes, `nbWorkers < 1`, size ≤ 0, … |
| `NullPointerException` | A null argument where not allowed (only `hints` and `configIndexing`'s language may be null). |

Kotlin ignores checked exceptions; Java callers must handle `ZimCreatorException`.

### Threading

A `Creator` must be used from **one thread at a time** (calls are serialized
internally so misuse can't crash the JVM, but interleaving is unsupported).
libzim uses its own worker threads (`configNbWorkers`); they never call into
the JVM. Different `Creator`s can run in parallel.

### Strings

Paths, titles, metadata and content are passed to libzim as real UTF-8:
emoji and all Unicode round-trip correctly in the written file.

## 5. ZIM content conventions worth following

- **Paths**: no leading `/`, no namespace prefix. Links in HTML should be
  relative (`pages/intro.html`, `../img/a.webp`).
- **Mandatory metadata** (checked by `zimcheck` and expected by Kiwix readers):
  `Name`, `Title` (≤ 30 chars), `Description` (≤ 80 chars), `Language`
  (ISO 639-3, comma-separated if several), `Creator`, `Publisher`, `Date`
  (`YYYY-MM-DD`), and a 48×48 PNG illustration (`addIllustration(48, png)`).
  Optional: `LongDescription`, `Tags`, `License`, `Source`, `Flavour`, `Scraper`.
- **Custom metadata**: use an `X-` prefix (e.g. `X-Pack-Format`); for structured
  values store JSON with `addMetadata(name, bytes, "application/json")`.
- **Search**: only `text/html` items are indexed (the item title, and the text
  extracted from the HTML), and only with `configIndexing(true, lang)`.

## 6. Reading a ZIM back (package `org.kiwix.libzim`)

The reader is the regular java-libkiwix API, in the same jar:

```kotlin
import org.kiwix.libzim.*

val archive = Archive(path)
archive.getMetadata("Title")                         // String
archive.getMetadataItem("X-Pack-Sources").data.data  // ByteArray (exact bytes)
archive.metadataKeys                                  // Array<String>
archive.mainEntry.getItem(true).path
archive.getEntryByPath("pages/intro.html").getItem(true).data.data
archive.getIllustrationItem(48).data.data
archive.uuid; archive.hasFulltextIndex(); archive.check() // checksum
Searcher(archive).search(Query("word")).estimatedMatches
SuggestionSearcher(archive).suggest("intro").getResults(0, 10)
archive.dispose()
```

**Known reader limitation:** the reader's `String` parameters/returns use JNI's
modified UTF-8, so characters outside the Basic Multilingual Plane (emoji, some
rare CJK) do not survive `String`-based reader calls: `getEntryByPath("😀.html")`
won't find the entry, `Entry.getPath()`/`getTitle()` may return wrong text for
such names, and `Archive(String)` can't open a file whose *name* contains them
(use `Archive(FileInputStream(path).fd)` instead). Contents and metadata read as
bytes (`getData().getData()`) are exact. The written ZIM is correct either way;
other readers (Kiwix apps, libzim, python-libzim) see the right names. Prefer
ASCII/BMP paths if the app itself must look entries up by path.

## 7. Updating

New builds are published as `2.7.0-wyz.<n>` (or `<new upstream>-wyz.<n>`); bump
the version in the app. When upstream java-libkiwix releases the writer
bindings, switch to `org.kiwix:libkiwix:<version>` (Android/AAR) — the
`org.kiwix.libzim.writer` API is intended to stay the same, but the desktop jar
and `NativeLoader` exist only in this fork.
