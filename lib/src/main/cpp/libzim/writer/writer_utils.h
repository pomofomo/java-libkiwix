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

// Helpers shared by the libzim writer wrappers.
// Include it after `macros.h`.

#ifndef _LIBZIM_WRITER_UTILS_H
#define _LIBZIM_WRITER_UTILS_H

#include <vector>

#include <zim/error.h>
#include <zim/writer/item.h>

// Build the hints from the parallel arrays of keys (`Hint.value`) and values.
inline zim::writer::Hints jni2hints(JNIEnv* env, jintArray keys, jlongArray values)
{
  zim::writer::Hints hints;
  const jsize length = env->GetArrayLength(keys);
  if (length != env->GetArrayLength(values)) {
    throw std::invalid_argument("hint keys and values do not match");
  }
  std::vector<jint> cKeys(length);
  std::vector<jlong> cValues(length);
  if (length > 0) {
    env->GetIntArrayRegion(keys, 0, length, cKeys.data());
    env->GetLongArrayRegion(values, 0, length, cValues.data());
  }
  for (jsize i = 0; i < length; i++) {
    hints[static_cast<zim::writer::HintKeys>(cKeys[i])] = static_cast<uint64_t>(cValues[i]);
  }
  return hints;
}
#define HINTS_TO_C(KEYS, VALUES) jni2hints(env, KEYS, VALUES)

// Map the errors of the libzim writer to java exceptions.
// Most specific exceptions first.
#define CATCH_WRITER_EXCEPTION(RET) \
catch(const zim::InvalidEntry& e) { \
  throwException(env, "org/kiwix/libzim/writer/InvalidEntryException", e.what()); \
  return RET; \
} catch(const zim::CreatorStateError& e) { \
  throwException(env, "org/kiwix/libzim/writer/CreatorStateException", e.what()); \
  return RET; \
} catch(const zim::CreatorError& e) { \
  throwException(env, "org/kiwix/libzim/writer/ZimCreatorException", e.what()); \
  return RET; \
} catch (const NativeHandleDisposedException& e) { \
  throwException(env, "java/lang/IllegalStateException", e.what()); \
  return RET; \
} catch (const std::invalid_argument& e) { \
  throwException(env, "java/lang/IllegalArgumentException", e.what()); \
  return RET; \
} catch(const std::exception& e) { \
  throwException(env, "org/kiwix/libzim/writer/ZimCreatorException", e.what()); \
  return RET; \
} catch(...) { \
  throwException(env, "org/kiwix/libzim/writer/ZimCreatorException", "Unknown error"); \
  return RET; \
}

#endif // _LIBZIM_WRITER_UTILS_H
