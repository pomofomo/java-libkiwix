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
 * Thrown by every call made on a {@link Creator} after a previous error
 * (including an asynchronous error in a worker thread) has put it in an
 * error state. The ZIM file cannot be completed any more; the creator should
 * be closed, which removes the unfinished file.
 *
 * <p>Maps {@code zim::CreatorStateError}.
 */
public class CreatorStateException extends ZimCreatorException
{
  public CreatorStateException(String message)
  {
    super(message);
  }
}
