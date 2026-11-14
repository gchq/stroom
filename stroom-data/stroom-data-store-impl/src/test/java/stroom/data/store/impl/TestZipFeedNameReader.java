/*
 * Copyright 2026 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.data.store.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestZipFeedNameReader {

    private static final String DEFAULT_FEED = "DEFAULT_FEED";

    @TempDir
    private Path tempDir;

    @Test
    void testMetaOnly() throws IOException {
        final Path zip = createZip(Map.of(
                "001.meta", "Feed:META_FEED\n",
                "001.dat", "DATA"));

        assertThat(ZipFeedNameReader.readFeedNames(zip, DEFAULT_FEED))
                .containsExactly("META_FEED");
    }

    @Test
    void testManifestOnly() throws IOException {
        final Path zip = createZip(Map.of(
                "001.mf", "Feed:MANIFEST_FEED\n",
                "001.dat", "DATA"));

        assertThat(ZipFeedNameReader.readFeedNames(zip, DEFAULT_FEED))
                .containsExactly("MANIFEST_FEED");
    }

    @Test
    void testMetaWinsOverManifest() throws IOException {
        final Path zip = createZip(Map.of(
                "001.mf", "Feed:MANIFEST_FEED\n",
                "001.meta", "Feed:META_FEED\n",
                "001.dat", "DATA"));

        assertThat(ZipFeedNameReader.readFeedNames(zip, DEFAULT_FEED))
                .containsExactly("META_FEED");
    }

    @Test
    void testManifestUsedWhenMetaHasNoFeed() throws IOException {
        final Path zip = createZip(Map.of(
                "001.mf", "Feed:MANIFEST_FEED\n",
                "001.meta", "Foo:Bar\n",
                "001.dat", "DATA"));

        assertThat(ZipFeedNameReader.readFeedNames(zip, DEFAULT_FEED))
                .containsExactly("MANIFEST_FEED");
    }

    @Test
    void testDefaultWhenMetaHasNoFeed() throws IOException {
        final Path zip = createZip(Map.of(
                "001.meta", "Foo:Bar\n",
                "001.dat", "DATA"));

        assertThat(ZipFeedNameReader.readFeedNames(zip, DEFAULT_FEED))
                .containsExactly(DEFAULT_FEED);
    }

    @Test
    void testDefaultWhenNoMetaOrManifest() throws IOException {
        final Path zip = createZip(Map.of(
                "001.dat", "DATA"));

        assertThat(ZipFeedNameReader.readFeedNames(zip, DEFAULT_FEED))
                .containsExactly(DEFAULT_FEED);
    }

    @Test
    void testFeedKeyIsCaseInsensitive() throws IOException {
        final Path zip = createZip(Map.of(
                "001.meta", "fEeD:META_FEED\n",
                "001.dat", "DATA"));

        assertThat(ZipFeedNameReader.readFeedNames(zip, DEFAULT_FEED))
                .containsExactly("META_FEED");
    }

    @Test
    void testMultipleFeedsAreDistinctAndSorted() throws IOException {
        final Map<String, String> entries = new LinkedHashMap<>();
        entries.put("001.meta", "Feed:FEED_C\n");
        entries.put("001.dat", "DATA");
        entries.put("002.meta", "Feed:FEED_A\n");
        entries.put("002.dat", "DATA");
        entries.put("003.meta", "Feed:FEED_C\n");
        entries.put("003.dat", "DATA");
        entries.put("004.mf", "Feed:FEED_B\n");
        entries.put("004.dat", "DATA");
        final Path zip = createZip(entries);

        assertThat(ZipFeedNameReader.readFeedNames(zip, DEFAULT_FEED))
                .containsExactly("FEED_A", "FEED_B", "FEED_C");
    }

    @Test
    void testStreamWithNoMetaKeepsPreviousFeed() throws IOException {
        // StreamTargetStreamHandler only changes feed when it reads a .meta/.manifest, so
        // stream 002 goes into FEED_A, not the default feed.
        final Map<String, String> entries = new LinkedHashMap<>();
        entries.put("001.meta", "Feed:FEED_A\n");
        entries.put("001.dat", "DATA");
        entries.put("002.dat", "DATA");
        final Path zip = createZip(entries);

        assertThat(ZipFeedNameReader.readFeedNames(zip, DEFAULT_FEED))
                .containsExactly("FEED_A");
    }

    @Test
    void testEmptyZip() throws IOException {
        final Path zip = createZip(Map.of());

        assertThat(ZipFeedNameReader.readFeedNames(zip, DEFAULT_FEED))
                .isEmpty();
    }

    @Test
    void testNotAZip() throws IOException {
        final Path file = tempDir.resolve("not_a.zip");
        Files.writeString(file, "This is not a zip");

        assertThatThrownBy(() -> ZipFeedNameReader.readFeedNames(file, DEFAULT_FEED))
                .isInstanceOf(IOException.class);
    }

    @Test
    void testNullDefaultFeed() throws IOException {
        final Path zip = createZip(Map.of(
                "001.dat", "DATA"));

        assertThatThrownBy(() -> ZipFeedNameReader.readFeedNames(zip, null))
                .isInstanceOf(NullPointerException.class);
    }

    private Path createZip(final Map<String, String> entries) throws IOException {
        final Path zip = Files.createTempFile(tempDir, "test", ".zip");
        try (final OutputStream outputStream = Files.newOutputStream(zip);
                final ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            for (final Map.Entry<String, String> entry : entries.entrySet()) {
                zipOutputStream.putNextEntry(new ZipEntry(entry.getKey()));
                zipOutputStream.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zipOutputStream.closeEntry();
            }
        }
        return zip;
    }
}
