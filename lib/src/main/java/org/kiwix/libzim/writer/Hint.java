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

/**
 * Hints given to the {@link Creator} about how an entry should be handled.
 *
 * <p>Mirrors {@code zim::writer::HintKeys}. A hint is passed as a
 * {@code Map<Hint, Long>}; a value of {@code 0} means "no", any other
 * value means "yes".
 */
public enum Hint
{
  /** Whether the content of the item should be compressed. */
  COMPRESS(0),
  /**
   * Whether the entry is a "front article": a page shown to the user, which is
   * listed in the title index and returned by suggestions.
   */
  FRONT_ARTICLE(1);

  final int value;

  Hint(int value) { this.value = value; }
}
