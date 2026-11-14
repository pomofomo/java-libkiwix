/*
 * Copyright (C) 2026 Kiwix contributors
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU  General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301, USA.
 */
package org.kiwix.libzim.writer;

import org.kiwix.libzim.IllustrationInfo;

import java.util.Map;

/**
 * Creates a ZIM file. Wraps {@code zim::writer::Creator}.
 *
 * <p>Typical use (Kotlin):
 * <pre>{@code
 * Creator().use { creator ->
 *   creator.configIndexing(true, "eng").startZimCreation("out.zim")
 *   creator.addMetadata("Title", "My ZIM")
 *   creator.addItem(StringItem("index.html", "text/html", "Home", null, "<html>…</html>"))
 *   creator.setMainPath("index.html")
 *   creator.finishZimCreation()
 * }
 * }</pre>
 *
 * <h2>Life cycle</h2>
 * <ol>
 *   <li>Configure the creator with the {@code config*} methods. They return
 *       {@code this} so they can be chained. Calling them after
 *       {@link #startZimCreation(String)} throws {@link IllegalStateException}.</li>
 *   <li>{@link #startZimCreation(String)}: libzim writes to
 *       {@code path + ".tmp"}.</li>
 *   <li>Add entries, metadata and illustrations. Calling these methods before
 *       {@link #startZimCreation(String)} throws {@link IllegalStateException}.</li>
 *   <li>{@link #finishZimCreation()}: libzim writes the indexes and renames the
 *       temporary file to {@code path}. Any call made after it throws
 *       {@link IllegalStateException}.</li>
 *   <li>{@link #close()} releases the native creator.</li>
 * </ol>
 *
 * <h2>Aborting</h2>
 * Closing (or disposing) a creator whose ZIM creation was started but not
 * finished, including after an error, aborts the creation: libzim removes the
 * {@code .tmp} file and no file is left at {@code path}. {@link #close()}
 * never finishes the creation on its own, so a {@code use}/try-with-resources
 * block that exits with an exception leaves nothing behind.
 *
 * <h2>Errors</h2>
 * libzim errors are thrown as {@link ZimCreatorException} or one of its
 * subclasses. libzim writes clusters and indexes in worker threads; an error
 * there is reported by a later call on the creator, after which every call
 * throws {@link CreatorStateException}.
 *
 * <h2>Threading</h2>
 * A creator is not thread safe: it must be used from a single thread at a
 * time. (Calls are serialized to avoid native crashes, but interleaving calls
 * from several threads is not supported.) libzim uses its own worker threads
 * internally ({@link #configNbWorkers(int)}); they never call into the JVM.
 *
 * <h2>Strings</h2>
 * All strings (paths, titles, metadata, content) are given to libzim encoded
 * in UTF-8.
 *
 * <h2>Platforms</h2>
 * The writer needs a libzim built with writer support. This is the case of the
 * Linux (desktop JVM) build; the libzim prebuilt binaries for Android are
 * compiled without the writer, so the native methods of this package are
 * not available there.
 */
public class Creator implements AutoCloseable
{
  private enum State { CONFIGURING, STARTED, FINISHED }

  private State state = State.CONFIGURING;

  /** Creates a new creator, with libzim's default configuration. */
  public Creator()
  {
    setNativeCreator();
  }

  // ---- Configuration (before startZimCreation) ----

  /** Whether libzim prints progress information on stdout. Default: false. */
  public synchronized Creator configVerbose(boolean verbose)
  {
    checkConfiguring();
    nativeConfigVerbose(verbose);
    return this;
  }

  /** The compression used for compressed clusters. Default: {@link Compression#ZSTD}. */
  public synchronized Creator configCompression(Compression compression)
  {
    checkConfiguring();
    nativeConfigCompression(Item.requireNonNull(compression, "compression").value);
    return this;
  }

  /**
   * The target size of a cluster, in bytes. libzim closes a cluster once its
   * content reaches this size. Default: 2 MiB.
   */
  public synchronized Creator configClusterSize(long targetSize)
  {
    checkConfiguring();
    if (targetSize <= 0) {
      throw new IllegalArgumentException("targetSize must be positive");
    }
    nativeConfigClusterSize(targetSize);
    return this;
  }

  /**
   * Whether to create a full text index and a title index. Default: false.
   *
   * @param indexing whether to index the {@code text/html} items
   * @param language the ISO 639-3 code of the content language (e.g. {@code "eng"}),
   *                 used for stemming and stop words. May be {@code null} if
   *                 {@code indexing} is false.
   */
  public synchronized Creator configIndexing(boolean indexing, String language)
  {
    checkConfiguring();
    nativeConfigIndexing(indexing, Item.utf8(language == null ? "" : language, "language"));
    return this;
  }

  /** The number of worker threads libzim uses to compress and index. Default: 4. */
  public synchronized Creator configNbWorkers(int nbWorkers)
  {
    checkConfiguring();
    if (nbWorkers < 1) {
      throw new IllegalArgumentException("nbWorkers must be at least 1");
    }
    nativeConfigNbWorkers(nbWorkers);
    return this;
  }

  // ---- Creation ----

  /**
   * Starts the creation of the ZIM file. libzim writes to {@code path + ".tmp"}
   * until {@link #finishZimCreation()} renames it to {@code path}.
   *
   * @param path the path of the ZIM file to create
   * @throws ZimCreatorException if the file cannot be created
   */
  public synchronized void startZimCreation(String path) throws ZimCreatorException
  {
    checkConfiguring();
    nativeStartZimCreation(Item.utf8(path, "path"));
    state = State.STARTED;
  }

  /**
   * Sets the path of the main entry, as given to {@link #addItem(Item)} (the
   * entry may be added before or after this call). Can be called until
   * {@link #finishZimCreation()}.
   */
  public synchronized void setMainPath(String mainPath)
  {
    checkNotFinished();
    nativeSetMainPath(Item.utf8(mainPath, "mainPath"));
  }

  /**
   * Sets the UUID of the ZIM file (a random one is used by default). Can be
   * called until {@link #finishZimCreation()}.
   *
   * @param uuid the 16 bytes of the UUID
   */
  public synchronized void setUuid(byte[] uuid)
  {
    checkNotFinished();
    if (Item.requireNonNull(uuid, "uuid").length != 16) {
      throw new IllegalArgumentException("uuid must be exactly 16 bytes long");
    }
    nativeSetUuid(uuid);
  }

  /**
   * Adds an item.
   *
   * @throws InvalidEntryException if the entry is invalid (e.g. its path is already used)
   * @throws ZimCreatorException if the item cannot be added
   */
  public synchronized void addItem(Item item) throws ZimCreatorException
  {
    checkStarted();
    Item.requireNonNull(item, "item");
    nativeAddItem(item);
  }

  /**
   * Adds a text metadata, with the {@code text/plain;charset=utf-8} mimetype.
   *
   * @param name the name of the metadata (e.g. {@code "Title"})
   * @param content the value of the metadata
   */
  public synchronized void addMetadata(String name, String content) throws ZimCreatorException
  {
    addMetadata(name, Item.utf8(content, "content"), "text/plain;charset=utf-8");
  }

  /**
   * Adds a metadata.
   *
   * @param name the name of the metadata
   * @param content the value of the metadata
   * @param mimetype the mimetype of the value
   */
  public synchronized void addMetadata(String name, byte[] content, String mimetype)
      throws ZimCreatorException
  {
    checkStarted();
    nativeAddMetadata(Item.utf8(name, "name"), Item.requireNonNull(content, "content"),
                      Item.utf8(mimetype, "mimetype"));
  }

  /**
   * Adds a square PNG illustration (stored as the {@code Illustration_<size>x<size>@1}
   * metadata).
   *
   * @param size the width and height of the illustration, in pixels (e.g. 48)
   * @param content the PNG data
   */
  public synchronized void addIllustration(int size, byte[] content) throws ZimCreatorException
  {
    checkStarted();
    if (size <= 0) {
      throw new IllegalArgumentException("size must be positive");
    }
    nativeAddIllustration(size, Item.requireNonNull(content, "content"));
  }

  /**
   * Adds an illustration.
   *
   * @param info the dimensions and scale of the illustration
   * @param content the PNG data
   */
  public synchronized void addIllustration(IllustrationInfo info, byte[] content)
      throws ZimCreatorException
  {
    checkStarted();
    Item.requireNonNull(info, "info");
    if (info.width <= 0 || info.height <= 0 || !(info.scale > 0)) {
      throw new IllegalArgumentException("width, height and scale must be positive");
    }
    nativeAddIllustrationWithInfo(info.width, info.height, info.scale,
                                  Item.requireNonNull(content, "content"));
  }

  /**
   * Adds a redirection: an entry at {@code path} pointing to the entry at
   * {@code targetPath}.
   *
   * @param hints the hints of the redirection, may be {@code null}
   */
  public synchronized void addRedirection(String path, String title, String targetPath,
                                          Map<Hint, Long> hints)
      throws ZimCreatorException
  {
    checkStarted();
    nativeAddRedirection(Item.utf8(path, "path"), Item.utf8(title, "title"),
                         Item.utf8(targetPath, "targetPath"),
                         Item.hintKeys(hints), Item.hintValues(hints));
  }

  /** Adds a redirection without hints. See {@link #addRedirection(String, String, String, Map)}. */
  public void addRedirection(String path, String title, String targetPath)
      throws ZimCreatorException
  {
    addRedirection(path, title, targetPath, null);
  }

  /**
   * Adds an alias: an entry at {@code path} sharing the content of the
   * (already added) entry at {@code targetPath}.
   *
   * @param hints the hints of the alias, may be {@code null}
   * @throws InvalidEntryException if there is no entry at {@code targetPath}
   */
  public synchronized void addAlias(String path, String title, String targetPath,
                                    Map<Hint, Long> hints)
      throws ZimCreatorException
  {
    checkStarted();
    nativeAddAlias(Item.utf8(path, "path"), Item.utf8(title, "title"),
                   Item.utf8(targetPath, "targetPath"),
                   Item.hintKeys(hints), Item.hintValues(hints));
  }

  /** Adds an alias without hints. See {@link #addAlias(String, String, String, Map)}. */
  public void addAlias(String path, String title, String targetPath)
      throws ZimCreatorException
  {
    addAlias(path, title, targetPath, null);
  }

  /**
   * Finishes the creation: waits for the workers, writes the indexes and
   * renames the temporary file to the final path. This may take a while.
   *
   * @throws ZimCreatorException if the ZIM file cannot be completed. The creator
   *         should then be closed, which removes the temporary file.
   */
  public synchronized void finishZimCreation() throws ZimCreatorException
  {
    checkStarted();
    nativeFinishZimCreation();
    state = State.FINISHED;
  }

  /**
   * Releases the native creator (same as {@link #dispose()}). If the creation
   * was started but not finished, it is aborted and the temporary file is
   * removed. Calling it several times is harmless.
   */
  @Override
  public void close()
  {
    dispose();
  }

  private void checkConfiguring()
  {
    if (state != State.CONFIGURING) {
      throw new IllegalStateException("The creator can only be configured before startZimCreation()");
    }
  }

  private void checkStarted()
  {
    if (state == State.CONFIGURING) {
      throw new IllegalStateException("startZimCreation() has not been called");
    }
    checkNotFinished();
  }

  private void checkNotFinished()
  {
    if (state == State.FINISHED) {
      throw new IllegalStateException("The ZIM creation is already finished");
    }
  }

  private native void setNativeCreator();
  private native void nativeConfigVerbose(boolean verbose);
  private native void nativeConfigCompression(int compression);
  private native void nativeConfigClusterSize(long targetSize);
  private native void nativeConfigIndexing(boolean indexing, byte[] language);
  private native void nativeConfigNbWorkers(int nbWorkers);
  private native void nativeStartZimCreation(byte[] path) throws ZimCreatorException;
  private native void nativeSetMainPath(byte[] mainPath);
  private native void nativeSetUuid(byte[] uuid);
  private native void nativeAddItem(Item item) throws ZimCreatorException;
  private native void nativeAddMetadata(byte[] name, byte[] content, byte[] mimetype)
      throws ZimCreatorException;
  private native void nativeAddIllustration(int size, byte[] content) throws ZimCreatorException;
  private native void nativeAddIllustrationWithInfo(int width, int height, float scale,
                                                    byte[] content)
      throws ZimCreatorException;
  private native void nativeAddRedirection(byte[] path, byte[] title, byte[] targetPath,
                                           int[] hintKeys, long[] hintValues)
      throws ZimCreatorException;
  private native void nativeAddAlias(byte[] path, byte[] title, byte[] targetPath,
                                     int[] hintKeys, long[] hintValues)
      throws ZimCreatorException;
  private native void nativeFinishZimCreation() throws ZimCreatorException;

  @Override
  protected void finalize() { dispose(); }

///--------- The wrapper thing
  // To delete our native wrapper. If the creation is not finished, it is
  // aborted and the temporary file removed.
  public synchronized native void dispose();

  // A pointer (as a long) to a native Handle
  private long nativeHandle;
}
