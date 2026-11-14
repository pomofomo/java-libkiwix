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

package org.kiwix.test.libzim;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.kiwix.libzim.Archive;
import org.kiwix.libzim.Entry;
import org.kiwix.libzim.EntryIterator;
import org.kiwix.libzim.EntryNotFoundException;
import org.kiwix.libzim.IllustrationInfo;
import org.kiwix.libzim.Query;
import org.kiwix.libzim.Search;
import org.kiwix.libzim.SearchIterator;
import org.kiwix.libzim.Searcher;
import org.kiwix.libzim.SuggestionItem;
import org.kiwix.libzim.SuggestionIterator;
import org.kiwix.libzim.SuggestionSearch;
import org.kiwix.libzim.SuggestionSearcher;
import org.kiwix.libzim.writer.Compression;
import org.kiwix.libzim.writer.Creator;
import org.kiwix.libzim.writer.CreatorStateException;
import org.kiwix.libzim.writer.FileItem;
import org.kiwix.libzim.writer.Hint;
import org.kiwix.libzim.writer.InvalidEntryException;
import org.kiwix.libzim.writer.StringItem;
import org.kiwix.libzim.writer.ZimCreatorException;

/**
 * Tests of the libzim writer bindings (org.kiwix.libzim.writer).
 *
 * Each test writes a ZIM file in a temporary directory and reads it back
 * with the reader bindings (org.kiwix.libzim.Archive).
 */
public class TestCreator
{
  static {
    System.loadLibrary("zim");
    System.loadLibrary("zim_wrapper");
  }

  // Run from lib/src/test (see the `runTests` gradle task).
  private static final String PNG_PATH = "small_zimfile_data/favicon.png";

  private File tmpDir;

  @Before
  public void setUp() throws IOException
  {
    tmpDir = Files.createTempDirectory("libzim-writer-test").toFile();
  }

  @After
  public void tearDown() throws IOException
  {
    try (Stream<java.nio.file.Path> paths = Files.walk(tmpDir.toPath())) {
      paths.sorted(Comparator.reverseOrder()).map(java.nio.file.Path::toFile).forEach(File::delete);
    }
  }

  private String zimPath(String name)
  {
    return new File(tmpDir, name).getPath();
  }

  private static Map<Hint, Long> frontArticle(boolean front)
  {
    Map<Hint, Long> hints = new EnumMap<>(Hint.class);
    hints.put(Hint.FRONT_ARTICLE, front ? 1L : 0L);
    return hints;
  }

  private static String html(String title, String body)
  {
    return "<html><head><title>" + title + "</title></head>"
         + "<body><h1>" + title + "</h1><p>" + body + "</p></body></html>";
  }

  private static byte[] content(Entry entry)
  {
    return entry.getItem(true).getData().getData();
  }

  private static byte[] readFile(String path) throws IOException
  {
    return Files.readAllBytes(new File(path).toPath());
  }

  private static boolean contains(byte[] haystack, byte[] needle)
  {
    outer:
    for (int i = 0; i + needle.length <= haystack.length; i++) {
      for (int j = 0; j < needle.length; j++) {
        if (haystack[i + j] != needle[j]) {
          continue outer;
        }
      }
      return true;
    }
    return false;
  }

  private static byte[] concat(byte[] a, byte[] b)
  {
    byte[] r = Arrays.copyOf(a, a.length + b.length);
    System.arraycopy(b, 0, r, a.length, b.length);
    return r;
  }

  // A minimal creator, with just a main page and the mandatory metadata.
  private Creator startSmallZim(String path) throws ZimCreatorException
  {
    Creator creator = new Creator().configNbWorkers(1);
    creator.startZimCreation(path);
    creator.addMetadata("Title", "Small");
    creator.addItem(new StringItem("index.html", "text/html", "Home", frontArticle(true),
                                   html("Home", "Hello")));
    creator.setMainPath("index.html");
    return creator;
  }

  @Test
  public void testCreateAndRead() throws Exception
  {
    String path = zimPath("test.zim");
    byte[] png = readFile(PNG_PATH);

    try (Creator creator = new Creator()) {
      creator.configVerbose(false)
             .configCompression(Compression.ZSTD)
             .configClusterSize(1024 * 1024)
             .configNbWorkers(2)
             .configIndexing(false, null);
      creator.startZimCreation(path);
      assertFalse(new File(path).exists());
      assertTrue(new File(path + ".tmp").exists());

      creator.addItem(new StringItem("index.html", "text/html", "Home page", frontArticle(true),
                                     html("Home page", "Welcome")));
      for (int i = 0; i < 5; i++) {
        creator.addItem(new StringItem("pages/page" + i + ".html", "text/html", "Page " + i,
                                       frontArticle(true), html("Page " + i, "Content of page " + i)));
      }
      creator.addItem(new StringItem("style.css", "text/css", "", null, "body { color: red; }"));
      creator.addItem(new FileItem("images/favicon.png", "image/png", "", null, PNG_PATH));
      creator.addItem(new StringItem("data.bin", "application/octet-stream", "", null,
                                     new byte[] {0, 1, 2, 0, (byte) 0xff}));
      creator.addRedirection("redirect.html", "A redirection", "pages/page1.html");
      creator.addRedirection("redirect2.html", "Another redirection", "pages/page2.html",
                             frontArticle(false));
      creator.addAlias("alias.html", "An alias", "pages/page3.html");
      creator.setMainPath("index.html");
      creator.addMetadata("Title", "Test ZIM");
      creator.finishZimCreation();
    }

    assertTrue(new File(path).exists());
    assertFalse(new File(path + ".tmp").exists());

    Archive archive = new Archive(path);
    assertEquals("index.html", archive.getMainEntry().getItem(true).getPath());
    assertEquals(html("Home page", "Welcome"),
                 new String(content(archive.getMainEntry()), StandardCharsets.UTF_8));
    // Front articles: the html pages. Redirections and aliases are not, unless hinted.
    List<String> frontArticles = new ArrayList<>();
    EntryIterator byTitle = archive.iterByTitle();
    while (byTitle.hasNext()) {
      frontArticles.add(byTitle.next().getPath());
    }
    assertEquals(Arrays.asList("index.html", "pages/page0.html", "pages/page1.html",
                               "pages/page2.html", "pages/page3.html", "pages/page4.html"),
                 frontArticles);
    assertEquals(6, archive.getArticleCount());

    for (int i = 0; i < 5; i++) {
      Entry entry = archive.getEntryByPath("pages/page" + i + ".html");
      assertFalse(entry.isRedirect());
      assertEquals("Page " + i, entry.getTitle());
      assertEquals("text/html", entry.getItem(false).getMimetype());
      assertEquals(html("Page " + i, "Content of page " + i),
                   new String(content(entry), StandardCharsets.UTF_8));
    }

    Entry css = archive.getEntryByPath("style.css");
    assertEquals("text/css", css.getItem(false).getMimetype());
    assertEquals("body { color: red; }", new String(content(css), StandardCharsets.UTF_8));

    Entry image = archive.getEntryByPath("images/favicon.png");
    assertEquals("image/png", image.getItem(false).getMimetype());
    assertArrayEquals(png, content(image));
    assertEquals(png.length, image.getItem(false).getSize());

    assertArrayEquals(new byte[] {0, 1, 2, 0, (byte) 0xff},
                      content(archive.getEntryByPath("data.bin")));

    Entry redirect = archive.getEntryByPath("redirect.html");
    assertTrue(redirect.isRedirect());
    assertEquals("A redirection", redirect.getTitle());
    assertEquals("pages/page1.html", redirect.getRedirectEntry().getPath());
    assertEquals("pages/page1.html", redirect.getItem(true).getPath());
    assertTrue(archive.getEntryByPath("redirect2.html").isRedirect());

    Entry alias = archive.getEntryByPath("alias.html");
    assertFalse(alias.isRedirect());
    assertEquals("An alias", alias.getTitle());
    assertArrayEquals(content(archive.getEntryByPath("pages/page3.html")), content(alias));

    assertEquals("Test ZIM", archive.getMetadata("Title"));
    assertFalse(archive.hasFulltextIndex());
    assertTrue(archive.hasChecksum());
    assertTrue(archive.check());
    archive.dispose();
  }

  @Test
  public void testNoCompression() throws Exception
  {
    String path = zimPath("nocompress.zim");
    StringBuilder big = new StringBuilder();
    for (int i = 0; i < 1000; i++) {
      big.append("All work and no play makes Jack a dull boy. ");
    }
    try (Creator creator = new Creator()) {
      creator.configCompression(Compression.NONE).configNbWorkers(1);
      creator.startZimCreation(path);
      creator.addItem(new StringItem("big.txt", "text/plain", "Big", null, big.toString()));
      creator.finishZimCreation();
    }
    Archive archive = new Archive(path);
    assertEquals(big.toString(),
                 new String(content(archive.getEntryByPath("big.txt")), StandardCharsets.UTF_8));
    // Not compressed: the file is bigger than the content.
    assertTrue(new File(path).length() > big.length());
    archive.dispose();
  }

  @Test
  public void testMetadata() throws Exception
  {
    String path = zimPath("metadata.zim");
    String sources = "[{\"name\":\"Example\",\"url\":\"https://example.org/\",\"license\":\"CC-BY-SA-4.0\"}]";
    try (Creator creator = startSmallZim(path)) {
      creator.addMetadata("Name", "test_en_all");
      creator.addMetadata("Description", "A test ZIM file");
      creator.addMetadata("LongDescription", "A ZIM file created by the java-libkiwix writer tests");
      creator.addMetadata("Language", "eng");
      creator.addMetadata("Creator", "Kiwix");
      creator.addMetadata("Publisher", "Kiwix");
      creator.addMetadata("Date", "2026-11-14");
      creator.addMetadata("Tags", "_category:test;_pictures:no");
      creator.addMetadata("X-Pack-Format", "1");
      creator.addMetadata("X-Pack-Themes", "light,dark");
      creator.addMetadata("X-Pack-Sources", sources.getBytes(StandardCharsets.UTF_8),
                          "application/json");
      creator.finishZimCreation();
    }

    Archive archive = new Archive(path);
    assertEquals("Small", archive.getMetadata("Title"));
    assertEquals("test_en_all", archive.getMetadata("Name"));
    assertEquals("A test ZIM file", archive.getMetadata("Description"));
    assertEquals("eng", archive.getMetadata("Language"));
    assertEquals("eng", archive.getMainLanguage());
    assertEquals("2026-11-14", archive.getMetadata("Date"));
    assertEquals("_category:test;_pictures:no", archive.getMetadata("Tags"));
    assertEquals("1", archive.getMetadata("X-Pack-Format"));
    assertEquals("light,dark", archive.getMetadata("X-Pack-Themes"));
    assertEquals(sources, archive.getMetadata("X-Pack-Sources"));
    assertEquals("application/json", archive.getMetadataItem("X-Pack-Sources").getMimetype());
    assertEquals("text/plain;charset=utf-8", archive.getMetadataItem("X-Pack-Format").getMimetype());

    List<String> keys = Arrays.asList(archive.getMetadataKeys());
    for (String key : new String[] {"Title", "Name", "Description", "LongDescription",
                                    "Language", "Creator", "Publisher", "Date", "Tags",
                                    "X-Pack-Format", "X-Pack-Themes", "X-Pack-Sources"}) {
      assertTrue("Missing metadata " + key, keys.contains(key));
    }
    try {
      archive.getMetadata("X-Pack-Missing");
      fail("A missing metadata must raise EntryNotFoundException");
    } catch (EntryNotFoundException e) {
      // expected
    }
    archive.dispose();
  }

  @Test
  public void testIllustration() throws Exception
  {
    String path = zimPath("illustration.zim");
    byte[] png = readFile(PNG_PATH);
    byte[] png2 = Arrays.copyOf(png, png.length); // Content is not checked by libzim
    png2[png2.length - 1] ^= 1;
    IllustrationInfo info = new IllustrationInfo();
    info.width = 48;
    info.height = 48;
    info.scale = 2;
    try (Creator creator = startSmallZim(path)) {
      creator.addIllustration(48, png);
      creator.addIllustration(info, png2);
      creator.finishZimCreation();
    }

    Archive archive = new Archive(path);
    assertTrue(archive.hasIllustration(48));
    assertFalse(archive.hasIllustration(96));
    assertArrayEquals(png, archive.getIllustrationItem(48).getData().getData());
    assertArrayEquals(png, archive.getMetadataItem("Illustration_48x48@1").getData().getData());
    assertEquals("image/png", archive.getIllustrationItem(48).getMimetype());
    assertArrayEquals(png2, archive.getIllustrationItem(info).getData().getData());
    assertArrayEquals(png2, archive.getMetadataItem("Illustration_48x48@2").getData().getData());
    archive.dispose();
  }

  @Test
  public void testUuid() throws Exception
  {
    String path = zimPath("uuid.zim");
    byte[] uuid = new byte[16];
    for (int i = 0; i < 16; i++) {
      uuid[i] = (byte) (0x10 * (i % 16) + i);
    }
    uuid[15] = (byte) 0xff;
    try (Creator creator = startSmallZim(path)) {
      creator.setUuid(uuid);
      creator.finishZimCreation();
    }
    Archive archive = new Archive(path);
    assertEquals("00112233-4455-6677-8899-aabbccddeeff", archive.getUuid());
    archive.dispose();

    // Without setUuid, a random uuid is used.
    String path2 = zimPath("uuid2.zim");
    try (Creator creator = startSmallZim(path2)) {
      creator.finishZimCreation();
    }
    Archive archive2 = new Archive(path2);
    assertNotEquals("00112233-4455-6677-8899-aabbccddeeff", archive2.getUuid());
    assertNotEquals("00000000-0000-0000-0000-000000000000", archive2.getUuid());
    archive2.dispose();
  }

  @Test
  public void testIndexing() throws Exception
  {
    String path = zimPath("indexed.zim");
    try (Creator creator = new Creator()) {
      creator.configIndexing(true, "eng").configNbWorkers(2);
      creator.startZimCreation(path);
      creator.addMetadata("Language", "eng");
      creator.addItem(new StringItem("photosynthesis.html", "text/html", "Photosynthesis",
                                     frontArticle(true),
                                     html("Photosynthesis",
                                          "Plants convert sunlight into chemical energy using chlorophyll.")));
      creator.addItem(new StringItem("volcano.html", "text/html", "Volcano", frontArticle(true),
                                     html("Volcano", "A rupture in the crust where magma erupts.")));
      creator.addItem(new StringItem("hidden.html", "text/html", "Hidden zebra", frontArticle(false),
                                     html("Hidden zebra", "Not a front article.")));
      creator.setMainPath("volcano.html");
      creator.finishZimCreation();
    }

    Archive archive = new Archive(path);
    assertTrue(archive.hasFulltextIndex());
    assertTrue(archive.hasTitleIndex());

    Searcher searcher = new Searcher(archive);
    Search search = searcher.search(new Query("chlorophyll"));
    assertEquals(1, search.getEstimatedMatches());
    SearchIterator results = search.getResults(0, 10);
    assertTrue(results.hasNext());
    assertEquals("photosynthesis.html", results.getPath());
    assertEquals("Photosynthesis", results.getTitle());
    assertEquals(0, searcher.search(new Query("unicorn")).getEstimatedMatches());

    SuggestionSearcher suggestionSearcher = new SuggestionSearcher(archive);
    SuggestionSearch suggestion = suggestionSearcher.suggest("volc");
    assertEquals(1, suggestion.getEstimatedMatches());
    SuggestionIterator suggestions = suggestion.getResults(0, 10);
    assertTrue(suggestions.hasNext());
    SuggestionItem item = suggestions.next();
    assertEquals("Volcano", item.getTitle());
    assertEquals("volcano.html", item.getPath());
    // Not a front article: not suggested.
    assertEquals(0, suggestionSearcher.suggest("zebra").getEstimatedMatches());
    archive.dispose();
  }

  @Test
  public void testUnicode() throws Exception
  {
    String path = zimPath("unicode-文件-📚.zim");
    // BMP characters (CJK, accents) and characters outside of the BMP (emoji, CJK ext. B).
    String cjkPath = "维基/日本語.html";
    String cjkTitle = "日本語 été";
    String emojiPath = "emoji/😀-🌍-𠀋.html";
    String emojiTitle = "Smile 😀 𠀋";
    String emojiContent = html(emojiTitle, "🚀 中文 café \u0000 end");
    String emojiMetadata = "📚 图书馆 𠀋";

    try (Creator creator = new Creator().configNbWorkers(1)) {
      creator.startZimCreation(path);
      creator.addItem(new StringItem(cjkPath, "text/html", cjkTitle, frontArticle(true),
                                     html(cjkTitle, "中文")));
      creator.addItem(new StringItem(emojiPath, "text/html", emojiTitle, frontArticle(true),
                                     emojiContent));
      creator.addRedirection("重定向.html", "重定向", cjkPath);
      creator.addMetadata("Title", emojiMetadata);
      creator.addMetadata("X-Pack-Émoji", emojiMetadata);
      creator.setMainPath(emojiPath);
      creator.finishZimCreation();
    }
    assertTrue(new File(path).exists());

    // The reader's Archive(String) cannot open a file whose name is outside of
    // the BMP (see below): open it through its file descriptor.
    FileInputStream stream = new FileInputStream(path);
    Archive archive = new Archive(stream.getFD());
    // BMP characters round-trip through the String based reader API.
    Entry cjk = archive.getEntryByPath(cjkPath);
    assertEquals(cjkPath, cjk.getPath());
    assertEquals(cjkTitle, cjk.getTitle());
    assertEquals(cjkPath, archive.getEntryByTitle(cjkTitle).getPath());
    assertEquals(cjkPath, archive.getEntryByPath("重定向.html").getItem(true).getPath());

    // Contents and metadata are bytes: check them exactly.
    assertEquals(emojiContent,
                 new String(content(archive.getMainEntry()), StandardCharsets.UTF_8));
    assertArrayEquals(emojiMetadata.getBytes(StandardCharsets.UTF_8),
                      archive.getMetadataItem("Title").getData().getData());
    assertArrayEquals(emojiMetadata.getBytes(StandardCharsets.UTF_8),
                      archive.getMetadataItem("X-Pack-Émoji").getData().getData());

    // The reader's String API uses JNI's modified UTF-8, which cannot pass
    // characters outside of the BMP. Check the raw dirents instead: paths and
    // titles are stored uncompressed, followed by a NUL byte.
    byte[] zim = readFile(path);
    byte[] nul = new byte[] {0};
    assertTrue(contains(zim, concat(emojiPath.getBytes(StandardCharsets.UTF_8), nul)));
    assertTrue(contains(zim, concat(emojiTitle.getBytes(StandardCharsets.UTF_8), nul)));
    // and not the (wrong) modified UTF-8 encoding.
    byte[] modifiedUtf8Emoji = new byte[] {(byte) 0xed, (byte) 0xa0, (byte) 0xbd};
    assertFalse(contains(zim, modifiedUtf8Emoji));
    archive.dispose();
    stream.close();
  }

  @Test
  public void testAbortLeavesNoFile() throws Exception
  {
    // Closed after start, without finishing.
    String path = zimPath("abort.zim");
    Creator creator = startSmallZim(path);
    for (int i = 0; i < 100; i++) {
      creator.addItem(new StringItem("page" + i + ".html", "text/html", "Page " + i, null,
                                     html("Page " + i, "Content")));
    }
    assertTrue(new File(path + ".tmp").exists());
    creator.close();
    assertFalse(new File(path).exists());
    assertFalse(new File(path + ".tmp").exists());
    creator.close(); // closing twice is harmless

    // Exception in a try-with-resources block.
    String path2 = zimPath("abort2.zim");
    try (Creator creator2 = startSmallZim(path2)) {
      throw new IOException("Aborted by the application");
    } catch (IOException e) {
      // expected
    }
    assertFalse(new File(path2).exists());
    assertFalse(new File(path2 + ".tmp").exists());

    // Closed after a libzim error.
    String path3 = zimPath("abort3.zim");
    try (Creator creator3 = startSmallZim(path3)) {
      creator3.addItem(new StringItem("index.html", "text/html", "Duplicate", null, "Duplicate"));
      fail("A duplicated entry must raise InvalidEntryException");
    } catch (InvalidEntryException e) {
      // expected
    }
    assertFalse(new File(path3).exists());
    assertFalse(new File(path3 + ".tmp").exists());

    // Closed without starting.
    new Creator().configNbWorkers(1).close();
    assertEquals(0, tmpDir.list().length);
  }

  @Test
  public void testFinalizeAbortsCreation() throws Exception
  {
    String path = zimPath("finalize.zim");
    startSmallZim(path); // never closed nor finished
    for (int i = 0; i < 50 && new File(path + ".tmp").exists(); i++) {
      System.gc();
      System.runFinalization();
      Thread.sleep(20);
    }
    assertFalse(new File(path + ".tmp").exists());
    assertFalse(new File(path).exists());
  }

  @Test
  public void testStateErrors() throws Exception
  {
    String path = zimPath("state.zim");
    Creator creator = new Creator();

    // Entries before start.
    try {
      creator.addItem(new StringItem("a.html", "text/html", "A", null, "A"));
      fail("addItem before startZimCreation must raise IllegalStateException");
    } catch (IllegalStateException e) {
      // expected
    }
    try {
      creator.addMetadata("Title", "A");
      fail("addMetadata before startZimCreation must raise IllegalStateException");
    } catch (IllegalStateException e) {
      // expected
    }
    try {
      creator.finishZimCreation();
      fail("finishZimCreation before startZimCreation must raise IllegalStateException");
    } catch (IllegalStateException e) {
      // expected
    }

    // Config after start.
    creator.startZimCreation(path);
    try {
      creator.configIndexing(true, "eng");
      fail("Config after startZimCreation must raise IllegalStateException");
    } catch (IllegalStateException e) {
      // expected
    }
    try {
      creator.startZimCreation(path);
      fail("startZimCreation twice must raise IllegalStateException");
    } catch (IllegalStateException e) {
      // expected
    }
    creator.addItem(new StringItem("a.html", "text/html", "A", null, "A"));
    creator.finishZimCreation();
    assertTrue(new File(path).exists());

    // Anything after finish.
    try {
      creator.addItem(new StringItem("b.html", "text/html", "B", null, "B"));
      fail("addItem after finishZimCreation must raise IllegalStateException");
    } catch (IllegalStateException e) {
      // expected
    }
    try {
      creator.addRedirection("c.html", "C", "a.html");
      fail("addRedirection after finishZimCreation must raise IllegalStateException");
    } catch (IllegalStateException e) {
      // expected
    }
    try {
      creator.setMainPath("a.html");
      fail("setMainPath after finishZimCreation must raise IllegalStateException");
    } catch (IllegalStateException e) {
      // expected
    }
    try {
      creator.finishZimCreation();
      fail("finishZimCreation twice must raise IllegalStateException");
    } catch (IllegalStateException e) {
      // expected
    }
    try {
      creator.configVerbose(true);
      fail("Config after finishZimCreation must raise IllegalStateException");
    } catch (IllegalStateException e) {
      // expected
    }
    creator.close();
    // Closing a finished creator keeps the file.
    assertTrue(new File(path).exists());

    // Anything after close.
    Creator closed = new Creator();
    closed.close();
    try {
      closed.configVerbose(true);
      fail("A closed creator must raise IllegalStateException");
    } catch (IllegalStateException e) {
      // expected
    }
    try {
      closed.startZimCreation(zimPath("closed.zim"));
      fail("A closed creator must raise IllegalStateException");
    } catch (IllegalStateException e) {
      // expected
    }
    assertFalse(new File(zimPath("closed.zim")).exists());
  }

  @Test
  public void testInvalidArguments() throws Exception
  {
    try (Creator creator = new Creator()) {
      try {
        creator.configNbWorkers(0);
        fail("configNbWorkers(0) must raise IllegalArgumentException");
      } catch (IllegalArgumentException e) {
        // expected
      }
      try {
        creator.configClusterSize(0);
        fail("configClusterSize(0) must raise IllegalArgumentException");
      } catch (IllegalArgumentException e) {
        // expected
      }
      try {
        creator.setUuid(new byte[15]);
        fail("A 15 bytes uuid must raise IllegalArgumentException");
      } catch (IllegalArgumentException e) {
        // expected
      }
      try {
        creator.startZimCreation(null);
        fail("A null path must raise NullPointerException");
      } catch (NullPointerException e) {
        // expected
      }
      try {
        new StringItem(null, "text/html", "", null, "content");
        fail("A null path must raise NullPointerException");
      } catch (NullPointerException e) {
        // expected
      }
      try {
        new StringItem("a.html", "text/html", "", null, (String) null);
        fail("A null content must raise NullPointerException");
      } catch (NullPointerException e) {
        // expected
      }
    }
  }

  @Test
  public void testLibzimErrors() throws Exception
  {
    // Cannot create the file.
    try (Creator creator = new Creator()) {
      creator.startZimCreation(zimPath("no/such/dir/test.zim"));
      fail("Creating a ZIM file in a missing directory must raise ZimCreatorException");
    } catch (ZimCreatorException e) {
      // expected
    }

    // Duplicate path.
    String path = zimPath("errors.zim");
    try (Creator creator = startSmallZim(path)) {
      try {
        creator.addItem(new StringItem("index.html", "text/html", "Again", null, "Again"));
        fail("A duplicated entry must raise InvalidEntryException");
      } catch (InvalidEntryException e) {
        // expected
      }
      // The rejected entry does not prevent adding others.
      creator.addItem(new StringItem("other.html", "text/html", "Other", null, "Other"));
      try {
        creator.addMetadata("Title", "Twice");
        fail("A duplicated metadata must raise InvalidEntryException");
      } catch (InvalidEntryException e) {
        // expected
      }
      try {
        creator.addAlias("alias.html", "Alias", "missing.html");
        fail("An alias to a missing entry must raise InvalidEntryException");
      } catch (InvalidEntryException e) {
        // expected
      }
      // Dangling redirections are removed by libzim when finishing.
      creator.addRedirection("dangling.html", "Dangling", "missing.html");
      creator.finishZimCreation();
    }
    Archive errorsArchive = new Archive(path);
    assertEquals("Home", errorsArchive.getEntryByPath("index.html").getTitle());
    assertEquals("Other", errorsArchive.getEntryByPath("other.html").getTitle());
    assertEquals("Small", errorsArchive.getMetadata("Title"));
    assertFalse(errorsArchive.hasEntryByPath("alias.html"));
    assertFalse(errorsArchive.hasEntryByPath("dangling.html"));
    errorsArchive.dispose();

    // Missing file for a FileItem.
    String path2 = zimPath("errors2.zim");
    try (Creator creator = startSmallZim(path2)) {
      creator.addItem(new FileItem("missing.png", "image/png", "", null,
                                   zimPath("no-such-file.png")));
      creator.finishZimCreation();
      fail("A missing file must raise ZimCreatorException");
    } catch (ZimCreatorException e) {
      // expected
    }
    assertFalse(new File(path2).exists());
    assertFalse(new File(path2 + ".tmp").exists());

    // An item can be disposed once added, but not before.
    String path3 = zimPath("errors3.zim");
    try (Creator creator = startSmallZim(path3)) {
      StringItem added = new StringItem("added.html", "text/html", "Added", null, "Added");
      creator.addItem(added);
      added.dispose();
      StringItem disposed = new StringItem("disposed.html", "text/html", "Disposed", null, "D");
      disposed.dispose();
      try {
        creator.addItem(disposed);
        fail("Adding a disposed item must raise IllegalStateException");
      } catch (IllegalStateException e) {
        // expected
      }
      creator.finishZimCreation();
    }
    Archive archive = new Archive(path3);
    assertEquals("Added", new String(content(archive.getEntryByPath("added.html")),
                                     StandardCharsets.UTF_8));
    assertFalse(archive.hasEntryByPath("disposed.html"));
    archive.dispose();
  }

  @Test
  public void testAsynchronousError() throws Exception
  {
    // libzim reads the content of a FileItem later, in a worker thread. Make
    // that read fail by truncating the file once the item is added.
    String path = zimPath("async.zim");
    File data = new File(tmpDir, "data.bin");
    Files.write(data.toPath(), new byte[100000]);
    try (Creator creator = startSmallZim(path)) {
      creator.addItem(new FileItem("data.bin", "application/octet-stream", "", null,
                                   data.getPath()));
      try (java.io.RandomAccessFile file = new java.io.RandomAccessFile(data, "rw")) {
        file.setLength(10);
      }
      try {
        creator.finishZimCreation();
        fail("A worker error must raise ZimCreatorException");
      } catch (CreatorStateException e) {
        fail("The first error must be the worker one, not CreatorStateException");
      } catch (ZimCreatorException e) {
        assertTrue(e.getMessage(), e.getMessage().contains("Asynchronous error"));
      }
      // The creator is now in error state.
      try {
        creator.addItem(new StringItem("other.html", "text/html", "Other", null, "Other"));
        fail("A creator in error state must raise CreatorStateException");
      } catch (CreatorStateException e) {
        // expected
      }
      try {
        creator.finishZimCreation();
        fail("A creator in error state must raise CreatorStateException");
      } catch (CreatorStateException e) {
        // expected
      }
    }
    assertFalse(new File(path).exists());
    assertFalse(new File(path + ".tmp").exists());
  }

  @Test
  public void testZimcheck() throws Exception
  {
    String zimcheck = findExecutable("zimcheck");
    if (zimcheck == null) {
      System.err.println("zimcheck not found in PATH, skipping TestCreator.testZimcheck");
      return;
    }
    // libzim 9 writes ZIM files (format 6.3) without the legacy full title
    // index that older zimcheck versions require.
    int libzimMajor = zimcheckLibzimMajorVersion(zimcheck);
    if (libzimMajor < 9) {
      System.err.println("zimcheck is linked to libzim " + libzimMajor
                         + " (< 9), skipping TestCreator.testZimcheck");
      return;
    }
    String path = zimPath("zimcheck.zim");
    byte[] png = readFile(PNG_PATH);
    try (Creator creator = new Creator()) {
      creator.configIndexing(true, "eng");
      creator.startZimCreation(path);
      creator.addMetadata("Name", "test_en_zimcheck");
      creator.addMetadata("Title", "Zimcheck test");
      creator.addMetadata("Description", "A ZIM file checked by zimcheck");
      creator.addMetadata("Language", "eng");
      creator.addMetadata("Creator", "Kiwix");
      creator.addMetadata("Publisher", "Kiwix");
      creator.addMetadata("Date", "2026-11-14");
      creator.addIllustration(48, png);
      creator.addItem(new StringItem("index.html", "text/html", "Home", frontArticle(true),
                                     "<html><head><title>Home</title></head><body>"
                                     + "<a href=\"page.html\">Page</a>"
                                     + "<img src=\"favicon.png\" alt=\"icon\"/></body></html>"));
      creator.addItem(new StringItem("page.html", "text/html", "Page", frontArticle(true),
                                     "<html><head><title>Page</title></head><body>"
                                     + "<a href=\"index.html\">Home</a></body></html>"));
      creator.addItem(new FileItem("favicon.png", "image/png", "", null, PNG_PATH));
      creator.addRedirection("home.html", "Home redirect", "index.html");
      creator.setMainPath("index.html");
      creator.finishZimCreation();
    }
    Process process = new ProcessBuilder(zimcheck, "-A", path).inheritIO().start();
    assertTrue(process.waitFor(5, TimeUnit.MINUTES));
    assertEquals(0, process.exitValue());
  }

  // Parse the "libzim X.Y.Z" line of `zimcheck --version`. Returns -1 if not found.
  private static int zimcheckLibzimMajorVersion(String zimcheck) throws Exception
  {
    Process process = new ProcessBuilder(zimcheck, "--version").redirectErrorStream(true).start();
    String output = new String(readAll(process.getInputStream()), StandardCharsets.UTF_8);
    process.waitFor(1, TimeUnit.MINUTES);
    java.util.regex.Matcher matcher =
        java.util.regex.Pattern.compile("libzim\\s+(\\d+)\\.").matcher(output);
    return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
  }

  private static byte[] readAll(java.io.InputStream in) throws IOException
  {
    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
    byte[] buffer = new byte[4096];
    int n;
    while ((n = in.read(buffer)) != -1) {
      out.write(buffer, 0, n);
    }
    return out.toByteArray();
  }

  private static String findExecutable(String name)
  {
    String pathEnv = System.getenv("PATH");
    if (pathEnv == null) {
      return null;
    }
    for (String dir : pathEnv.split(File.pathSeparator)) {
      File candidate = new File(dir, name);
      if (candidate.isFile() && candidate.canExecute()) {
        return candidate.getPath();
      }
    }
    return null;
  }
}
