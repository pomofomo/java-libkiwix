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
package io.github.pomofomo.libkiwix;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * Loads the libzim native libraries bundled in the desktop JVM jar
 * ({@code libkiwix-wyz-jvm}).
 *
 * <p>Call {@link #load()} once, before using any {@code org.kiwix.libzim}
 * class. The libraries are extracted from the jar to a temporary directory
 * (removed when the JVM exits) and loaded with {@link System#load(String)}.
 *
 * <p>To use libraries installed elsewhere instead, set the system property
 * {@code libkiwix.native.dir} to the directory containing
 * {@code libzim.so.9} and {@code libzim_wrapper.so}.
 *
 * <p>Supported platforms: Linux x86_64.
 */
public final class NativeLoader
{
  /** System property: directory to load the native libraries from. */
  public static final String NATIVE_DIR_PROPERTY = "libkiwix.native.dir";

  // Load order matters: libzim_wrapper.so needs libzim.so.9 (its SONAME).
  private static final String[] LIBRARIES = {"libzim.so.9", "libzim_wrapper.so"};

  private static boolean loaded = false;

  private NativeLoader() {}

  /**
   * Loads the native libraries. Calling it again does nothing.
   *
   * @throws UnsatisfiedLinkError if the platform is not supported or the
   *         libraries cannot be loaded
   */
  public static synchronized void load()
  {
    if (loaded) {
      return;
    }
    String dir = System.getProperty(NATIVE_DIR_PROPERTY);
    if (dir != null && !dir.isEmpty()) {
      for (String library : LIBRARIES) {
        System.load(new File(dir, library).getAbsolutePath());
      }
    } else {
      String platform = platform();
      try {
        Path tmpDir = Files.createTempDirectory("libkiwix-wyz-");
        tmpDir.toFile().deleteOnExit();
        for (String library : LIBRARIES) {
          Path target = tmpDir.resolve(library);
          extract("/native/" + platform + "/" + library, target);
          target.toFile().deleteOnExit();
          System.load(target.toAbsolutePath().toString());
        }
      } catch (IOException e) {
        UnsatisfiedLinkError error =
            new UnsatisfiedLinkError("Cannot extract the libzim native libraries: " + e.getMessage());
        error.initCause(e);
        throw error;
      }
    }
    loaded = true;
  }

  private static String platform()
  {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
    if (os.startsWith("linux") && (arch.equals("amd64") || arch.equals("x86_64"))) {
      return "linux-x86_64";
    }
    throw new UnsatisfiedLinkError("libkiwix-wyz-jvm has no native libraries for "
                                   + os + "/" + arch + " (supported: linux/x86_64)");
  }

  private static void extract(String resource, Path target) throws IOException
  {
    try (InputStream in = NativeLoader.class.getResourceAsStream(resource)) {
      if (in == null) {
        throw new IOException("Missing resource " + resource);
      }
      Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }
}
