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
#include "org_kiwix_libzim_writer_Creator.h"

#include <utils.h>

#include <memory>
#include <string>

#include <zim/zim.h>
#include <zim/uuid.h>
#include <zim/illustration.h>
#include <zim/writer/creator.h>

#define CLASSNAME "org/kiwix/libzim/writer/Creator"
#define NATIVE_TYPE zim::writer::Creator
#define TYPENAME libzim_writer_Creator
#include <macros.h>
#include "writer_utils.h"

// Destroying a `zim::writer::Creator` whose creation is not finished aborts it
// and removes the temporary file: `dispose` must not finish the creation.
DISPOSE

METHOD0(void, setNativeCreator)
{
  SET_PTR(std::make_shared<NATIVE_TYPE>());
} CATCH_WRITER_EXCEPTION()

METHOD(void, nativeConfigVerbose, jboolean verbose)
{
  THIS->configVerbose(TO_C(verbose));
} CATCH_WRITER_EXCEPTION()

METHOD(void, nativeConfigCompression, jint compression)
{
  THIS->configCompression(static_cast<zim::Compression>(compression));
} CATCH_WRITER_EXCEPTION()

METHOD(void, nativeConfigClusterSize, jlong targetSize)
{
  THIS->configClusterSize(static_cast<zim::size_type>(targetSize));
} CATCH_WRITER_EXCEPTION()

METHOD(void, nativeConfigIndexing, jboolean indexing, jbyteArray language)
{
  THIS->configIndexing(TO_C(indexing), BYTES_TO_C(language));
} CATCH_WRITER_EXCEPTION()

METHOD(void, nativeConfigNbWorkers, jint nbWorkers)
{
  THIS->configNbWorkers(static_cast<unsigned>(nbWorkers));
} CATCH_WRITER_EXCEPTION()

METHOD(void, nativeStartZimCreation, jbyteArray path)
{
  THIS->startZimCreation(BYTES_TO_C(path));
} CATCH_WRITER_EXCEPTION()

METHOD(void, nativeSetMainPath, jbyteArray mainPath)
{
  THIS->setMainPath(BYTES_TO_C(mainPath));
} CATCH_WRITER_EXCEPTION()

METHOD(void, nativeSetUuid, jbyteArray uuid)
{
  const std::string bytes = BYTES_TO_C(uuid);
  if (bytes.size() != 16) {
    throw std::invalid_argument("uuid must be exactly 16 bytes long");
  }
  THIS->setUuid(zim::Uuid(bytes.data()));
} CATCH_WRITER_EXCEPTION()

METHOD(void, nativeAddItem, jobject item)
{
  THIS->addItem(getPtr<zim::writer::Item>(env, item));
} CATCH_WRITER_EXCEPTION()

METHOD(void, nativeAddMetadata, jbyteArray name, jbyteArray content, jbyteArray mimetype)
{
  THIS->addMetadata(BYTES_TO_C(name), BYTES_TO_C(content), BYTES_TO_C(mimetype));
} CATCH_WRITER_EXCEPTION()

METHOD(void, nativeAddIllustration, jint size, jbyteArray content)
{
  THIS->addIllustration(static_cast<unsigned int>(size), BYTES_TO_C(content));
} CATCH_WRITER_EXCEPTION()

METHOD(void, nativeAddIllustrationWithInfo, jint width, jint height, jfloat scale,
       jbyteArray content)
{
  zim::IllustrationInfo ii;
  ii.width = static_cast<uint32_t>(width);
  ii.height = static_cast<uint32_t>(height);
  ii.scale = static_cast<float>(scale);
  THIS->addIllustration(ii, BYTES_TO_C(content));
} CATCH_WRITER_EXCEPTION()

METHOD(void, nativeAddRedirection, jbyteArray path, jbyteArray title, jbyteArray targetPath,
       jintArray hintKeys, jlongArray hintValues)
{
  THIS->addRedirection(BYTES_TO_C(path), BYTES_TO_C(title), BYTES_TO_C(targetPath),
                       HINTS_TO_C(hintKeys, hintValues));
} CATCH_WRITER_EXCEPTION()

METHOD(void, nativeAddAlias, jbyteArray path, jbyteArray title, jbyteArray targetPath,
       jintArray hintKeys, jlongArray hintValues)
{
  THIS->addAlias(BYTES_TO_C(path), BYTES_TO_C(title), BYTES_TO_C(targetPath),
                 HINTS_TO_C(hintKeys, hintValues));
} CATCH_WRITER_EXCEPTION()

METHOD0(void, nativeFinishZimCreation)
{
  THIS->finishZimCreation();
} CATCH_WRITER_EXCEPTION()
