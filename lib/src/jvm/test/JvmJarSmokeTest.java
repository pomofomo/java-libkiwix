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
import io.github.pomofomo.libkiwix.NativeLoader;
import org.kiwix.libzim.Archive;
import org.kiwix.libzim.writer.Creator;
import org.kiwix.libzim.writer.StringItem;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Smoke test of the desktop JVM jar: run with only the jar on the classpath
 * and no java.library.path, it writes a ZIM file and reads it back.
 */
public class JvmJarSmokeTest
{
  public static void main(String[] args) throws Exception
  {
    NativeLoader.load();
    NativeLoader.load(); // idempotent
    File dir = Files.createTempDirectory("libkiwix-wyz-smoke").toFile();
    File zim = new File(dir, "smoke.zim");
    String content = "<html><head><title>Smoke 🔥</title></head><body>ok</body></html>";
    try (Creator creator = new Creator().configIndexing(true, "eng").configNbWorkers(1)) {
      creator.startZimCreation(zim.getPath());
      creator.addMetadata("Title", "Smoke test");
      creator.addItem(new StringItem("index.html", "text/html", "Smoke", null, content));
      creator.setMainPath("index.html");
      creator.finishZimCreation();
    }
    Archive archive = new Archive(zim.getPath());
    String read = new String(archive.getMainEntry().getItem(true).getData().getData(),
                             StandardCharsets.UTF_8);
    boolean ok = content.equals(read)
              && "Smoke test".equals(archive.getMetadata("Title"))
              && archive.hasFulltextIndex();
    archive.dispose();
    zim.delete();
    dir.delete();
    if (!ok) {
      System.err.println("JvmJarSmokeTest FAILED");
      System.exit(1);
    }
    System.out.println("JvmJarSmokeTest OK");
  }
}
