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

import java.util.Map;

/**
 * An item whose content is held in memory.
 *
 * <p>Maps {@code zim::writer::StringItem}. Items with the {@code text/html}
 * mimetype are indexed (title and text) when indexing is enabled on the
 * {@link Creator}.
 */
public final class StringItem extends Item
{
  /**
   * @param path the path of the entry (e.g. {@code "A/index.html"} or {@code "index.html"})
   * @param mimetype the mimetype of the content (e.g. {@code "text/html"})
   * @param title the title of the entry (may be empty)
   * @param hints the hints for this entry, may be {@code null}
   * @param content the content of the entry
   */
  public StringItem(String path, String mimetype, String title,
                    Map<Hint, Long> hints, byte[] content)
  {
    super(path, mimetype, title);
    setNativeStringItem(utf8(path, "path"), utf8(mimetype, "mimetype"),
                        utf8(title, "title"), hintKeys(hints), hintValues(hints),
                        requireNonNull(content, "content"));
  }

  /**
   * @param path the path of the entry (e.g. {@code "A/index.html"} or {@code "index.html"})
   * @param mimetype the mimetype of the content (e.g. {@code "text/html"})
   * @param title the title of the entry (may be empty)
   * @param hints the hints for this entry, may be {@code null}
   * @param content the content of the entry, stored encoded in UTF-8
   */
  public StringItem(String path, String mimetype, String title,
                    Map<Hint, Long> hints, String content)
  {
    this(path, mimetype, title, hints, utf8(content, "content"));
  }

  private native void setNativeStringItem(byte[] path, byte[] mimetype, byte[] title,
                                          int[] hintKeys, long[] hintValues,
                                          byte[] content);
}
