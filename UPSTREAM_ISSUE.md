# Draft issue for kiwix/java-libkiwix

> Draft only. Not posted. To be opened on https://github.com/kiwix/java-libkiwix/issues
> after approval.

**Title:** Add bindings for the libzim writer (zim::writer::Creator)

---

## Use case

java-libkiwix can only *read* ZIM files today, and there is no other ZIM writer
for the JVM. We are building a Kotlin desktop (JVM) application that needs to
write ZIM files: HTML pages, images, standard and custom (`X-…`) metadata, an
illustration, and the full-text and title indexes.

The alternatives are not great:
- python-libzim works, but means shipping Python with a desktop app;
- zimwriterfs cannot write custom metadata, and means shipping/forking a CLI.

Bindings for `zim::writer::Creator` in java-libkiwix would make the libzim
writer usable from Java and Kotlin, with the same build and native libraries
as the reader.

## Proposed Java API

A new package, `org.kiwix.libzim.writer`, following the existing binding
pattern (`nativeHandle`, `dispose()`, one `.cpp` file per class):

```java
public class Creator implements AutoCloseable {
  public Creator();
  // Configuration, before startZimCreation(). Chainable, like the C++ API.
  public Creator configVerbose(boolean verbose);
  public Creator configCompression(Compression c);          // enum Compression { NONE, ZSTD }
  public Creator configClusterSize(long bytes);
  public Creator configIndexing(boolean indexing, String language); // ISO 639-3, e.g. "eng"
  public Creator configNbWorkers(int n);

  public void startZimCreation(String path) throws ZimCreatorException;
  public void setMainPath(String path);
  public void setUuid(byte[] uuid16);                       // exactly 16 bytes
  public void addItem(Item item) throws ZimCreatorException; // StringItem | FileItem
  public void addMetadata(String name, String content) throws ZimCreatorException; // text/plain;charset=utf-8
  public void addMetadata(String name, byte[] content, String mimetype) throws ZimCreatorException;
  public void addIllustration(int size, byte[] png) throws ZimCreatorException;    // Illustration_<size>x<size>@1
  public void addIllustration(IllustrationInfo info, byte[] content) throws ZimCreatorException; // existing org.kiwix.libzim.IllustrationInfo
  public void addRedirection(String path, String title, String targetPath[, Map<Hint, Long> hints]) throws ZimCreatorException;
  public void addAlias(String path, String title, String targetPath[, Map<Hint, Long> hints]) throws ZimCreatorException;
  public void finishZimCreation() throws ZimCreatorException;
  @Override public void close();                            // = dispose(); unfinished -> aborted, no file left
  public native void dispose();
}

public enum Hint { COMPRESS, FRONT_ARTICLE }

public abstract class Item { /* package-private constructor: only the classes below */ }
public final class StringItem extends Item {
  public StringItem(String path, String mimetype, String title, Map<Hint, Long> hints, byte[] content);
  public StringItem(String path, String mimetype, String title, Map<Hint, Long> hints, String content);
}
public final class FileItem extends Item {
  public FileItem(String path, String mimetype, String title, Map<Hint, Long> hints, String filePath);
}

// Errors
public class ZimCreatorException extends Exception {}             // zim::CreatorError and other native errors
public class InvalidEntryException extends ZimCreatorException {}  // zim::InvalidEntry (duplicate path, ...)
public class CreatorStateException extends ZimCreatorException {}  // zim::CreatorStateError
```

Kotlin usage is the natural `Creator().use { … }`.

Semantics:
- `config*` after `startZimCreation()`, and any call after `finishZimCreation()`,
  throw `IllegalStateException` (also any call before `startZimCreation()` that
  needs a started creation, which would otherwise crash in libzim).
- `close()` never finishes the creation. Closing an unfinished creator aborts it
  and libzim removes the `.tmp` file, so a `use {}` block that exits with an
  exception leaves nothing behind. (This deliberately differs from python-libzim,
  whose `__exit__` always finishes.)
- `setUuid` is exposed (python-libzim does not expose it).
- Strings are passed to native code as standard UTF-8 bytes, not through
  `GetStringUTFChars` (modified UTF-8), so emoji and other non-BMP characters
  round-trip correctly in paths, titles, metadata and content.

## Threading

Phase 1 (this proposal) only has natively implemented items (`StringItem` and
`FileItem` map to libzim's own `StringItem` and `FileItem`). libzim calls
`ContentProvider::feed()` and the indexer from its worker threads, but with
these items no native thread ever calls into the JVM: no `AttachCurrentThread`,
no global references, no Java callbacks. The default HTML indexer extracts the
title and text of `text/html` items.

The `Creator` itself is not thread-safe (as in C++); this is documented, and the
Java methods are `synchronized` so concurrent misuse cannot crash the JVM.

Errors from libzim workers surface on a later call (at the latest
`finishZimCreation()`), as `ZimCreatorException`; after that every call throws
`CreatorStateException`.

A possible phase 2 (separate PR, only if wanted) would allow subclassing `Item`
in Java (`getContentProvider()`, `getIndexData()`), with a native trampoline
holding a global reference and attaching libzim's worker threads to the JVM.

## Platforms

The writer is available in the libzim Linux build, but the libzim Android
prebuilts from download.openzim.org are built with `LIBZIM_WITHOUT_WRITER` (no
writer symbols, no `zim/writer/*.h` headers). So the proposal compiles the
writer bindings into `libzim_wrapper` for the Linux build only; the Android
build is unchanged.

## Questions for maintainers

1. Is a separate package, `org.kiwix.libzim.writer`, OK with you?
2. Android: the Java classes currently ship in the AAR (they are plain Java), but
   their native methods are only compiled for Linux, so using them on Android
   throws `UnsatisfiedLinkError`. Would you rather (a) keep it like that,
   (b) exclude the package from the AAR, or (c) enable the writer in the
   Android libzim builds (kiwix-build) so it works there too?
3. Exceptions: are new checked exceptions (`ZimCreatorException` and
   subclasses) fine, or do you prefer reusing `java.lang.Exception` like some of
   the reader bindings do?
4. Is there interest in a phase 2 (Java-implemented items)?

We will send a PR with the implementation and JUnit tests (create, read back
with `Archive`, metadata, illustrations, UUID, indexing/search, Unicode,
abort/no-leftover-file and error paths).
