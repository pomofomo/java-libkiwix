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
 * Thrown when libzim fails to create a ZIM file.
 *
 * <p>This is the base class of all the errors reported by the native
 * {@code zim::writer::Creator} (it maps {@code zim::CreatorError} and any other
 * native error raised while writing). Errors happening in libzim's worker
 * threads are reported by a later call on the {@link Creator}.
 */
public class ZimCreatorException extends Exception
{
  public ZimCreatorException(String message)
  {
    super(message);
  }
}
