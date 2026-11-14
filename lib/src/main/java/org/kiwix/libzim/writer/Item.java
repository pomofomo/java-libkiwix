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

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * An entry with content to add to a ZIM file with {@link Creator#addItem(Item)}.
 *
 * <p>The concrete items are {@link StringItem} (content held in memory) and
 * {@link FileItem} (content read from a file on disk). Both are implemented
 * natively, so libzim never calls back into the JVM while writing.
 *
 * <p>Once added to a {@link Creator}, the creator keeps its own reference to
 * the native item: the Java object can be disposed or garbage collected
 * independently.
 */
public abstract class Item
{
  private final String path;
  private final String mimetype;
  private final String title;

  // Package private: only the items of this package can be added to a Creator.
  Item(String path, String mimetype, String title)
  {
    this.path = requireNonNull(path, "path");
    this.mimetype = requireNonNull(mimetype, "mimetype");
    this.title = requireNonNull(title, "title");
  }

  /** The path (url) of the entry, without namespace. */
  public String getPath() { return path; }

  /** The mimetype of the entry content. */
  public String getMimetype() { return mimetype; }

  /** The title of the entry. */
  public String getTitle() { return title; }

  // ---- Helpers shared with the other classes of this package. ----

  static <T> T requireNonNull(T value, String name)
  {
    if (value == null) {
      throw new NullPointerException(name + " must not be null");
    }
    return value;
  }

  // Strings are given to the native side as (standard) UTF-8 bytes:
  // JNI's GetStringUTFChars returns *modified* UTF-8, which wrongly encodes
  // characters outside of the BMP (emoji, ...) and NUL.
  static byte[] utf8(String value, String name)
  {
    return requireNonNull(value, name).getBytes(StandardCharsets.UTF_8);
  }

  static int[] hintKeys(Map<Hint, Long> hints)
  {
    if (hints == null) {
      return new int[0];
    }
    int[] keys = new int[hints.size()];
    int i = 0;
    for (Map.Entry<Hint, Long> hint : hints.entrySet()) {
      keys[i++] = requireNonNull(hint.getKey(), "hint key").value;
    }
    return keys;
  }

  static long[] hintValues(Map<Hint, Long> hints)
  {
    if (hints == null) {
      return new long[0];
    }
    long[] values = new long[hints.size()];
    int i = 0;
    for (Map.Entry<Hint, Long> hint : hints.entrySet()) {
      values[i++] = requireNonNull(hint.getValue(), "hint value");
    }
    return values;
  }

  @Override
  protected void finalize() { dispose(); }

///--------- The wrapper thing
  // To delete our native wrapper
  public native void dispose();

  // A pointer (as a long) to a native Handle
  private long nativeHandle;
}
