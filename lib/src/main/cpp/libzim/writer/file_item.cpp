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

#include <jni.h>
#include "org_kiwix_libzim_writer_FileItem.h"

#include <utils.h>

#include <memory>
#include <string>

#include <zim/writer/item.h>

#define CLASSNAME "org/kiwix/libzim/writer/FileItem"
#define NATIVE_TYPE zim::writer::Item
#define TYPENAME libzim_writer_FileItem
#include <macros.h>
#include "writer_utils.h"

METHOD(void, setNativeFileItem, jbyteArray path, jbyteArray mimetype, jbyteArray title,
       jintArray hintKeys, jlongArray hintValues, jbyteArray filePath)
{
  std::shared_ptr<zim::writer::Item> item = std::make_shared<zim::writer::FileItem>(
    BYTES_TO_C(path),
    BYTES_TO_C(mimetype),
    BYTES_TO_C(title),
    HINTS_TO_C(hintKeys, hintValues),
    BYTES_TO_C(filePath));
  SET_PTR(item);
} CATCH_WRITER_EXCEPTION()
