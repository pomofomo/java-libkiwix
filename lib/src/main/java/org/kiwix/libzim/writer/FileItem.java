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
 * An item whose content is read from a file on the local filesystem.
 *
 * <p>Maps {@code zim::writer::FileItem}. The file is opened when the item is
 * added to the {@link Creator} and read later, possibly from a libzim worker
 * thread: it must not be modified or removed before
 * {@link Creator#finishZimCreation()} returns.
 */
public final class FileItem extends Item
{
  private final String filePath;

  /**
   * @param path the path of the entry (e.g. {@code "images/logo.png"})
   * @param mimetype the mimetype of the content (e.g. {@code "image/png"})
   * @param title the title of the entry (may be empty)
   * @param hints the hints for this entry, may be {@code null}
   * @param filePath the path of the file on the local filesystem
   */
  public FileItem(String path, String mimetype, String title,
                  Map<Hint, Long> hints, String filePath)
  {
    super(path, mimetype, title);
    this.filePath = filePath;
    setNativeFileItem(utf8(path, "path"), utf8(mimetype, "mimetype"),
                      utf8(title, "title"), hintKeys(hints), hintValues(hints),
                      utf8(filePath, "filePath"));
  }

  /** The path of the file on the local filesystem. */
  public String getFilePath() { return filePath; }

  private native void setNativeFileItem(byte[] path, byte[] mimetype, byte[] title,
                                        int[] hintKeys, long[] hintValues,
                                        byte[] filePath);
}
