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

package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.receive.common.StroomStreamException;
import stroom.util.shared.FeedKey;
import stroom.util.zip.ZipUtil;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestReceivedZip {

    private static final FeedKey HEADER_FEED = FeedKey.of("HEADER_FEED", "Raw Events");

    @TempDir
    Path dir;

    @Test
    void testAZipOfPlainFilesIsDataWithMetaFromTheHeaders() throws IOException {
        final Map<String, String> entries = new LinkedHashMap<>();
        entries.put("app.log", "one");
        entries.put("2023-11-15.xyz.1001", "two");
        final Path zip = zip(entries);

        try (final ZipFile zipFile = ZipUtil.createZipFile(zip)) {
            final ReceivedZip received = ReceivedZip.index(zipFile, headers());

            assertThat(received.groups()).hasSize(2);
            assertThat(received.feedKeys()).containsExactly(HEADER_FEED);
            assertThat(received.groups().get(0).data().getName()).isEqualTo("app.log");
            assertThat(received.groups().get(1).data().getName()).isEqualTo("2023-11-15.xyz.1001");
            assertThat(received.groups().get(0).meta().get(StandardHeaderArguments.FEED)).isEqualTo("HEADER_FEED");
            assertThat(received.groups().get(0).meta().get(StandardHeaderArguments.RECEIPT_ID)).isEqualTo("rid-1");

            final Path out = dir.resolve("out.zip");
            final List<ZipEntryGroup> written = received.copyTo(zipFile, received.feedKeys(), out);

            assertThat(ZipUtil.pathList(out))
                    .containsExactly("0000000001.meta", "0000000001.dat", "0000000002.meta", "0000000002.dat");
            assertThat(written).hasSize(2);
            assertThat(written.get(0).getDataEntry().getName()).isEqualTo("0000000001.dat");
            assertThat(written.get(0).getDataEntry().getUncompressedSize()).isEqualTo(3);
            assertThat(content(out, "0000000002.dat")).isEqualTo("two");
        }
    }

    @Test
    void testSidecarsAttachToTheDataEntrySharingTheirBaseNameWhateverTheOrder() throws IOException {
        final Map<String, String> entries = new LinkedHashMap<>();
        entries.put("001.dat", "data-1");
        entries.put("001.ctx", "context-1");
        entries.put("001.hdr", "Feed:META_FEED\nType:Events\n");
        entries.put("002.mf", "manifest");
        entries.put("002.log", "data-2");
        entries.put("002.meta", "Feed:META_FEED\nType:Events\n");
        final Path zip = zip(entries);

        try (final ZipFile zipFile = ZipUtil.createZipFile(zip)) {
            assertThatThrownBy(() -> ReceivedZip.index(zipFile, headers()))
                    .as("a manifest belongs to the whole zip, so one on the second group is a format error")
                    .isInstanceOf(StroomStreamException.class)
                    .extracting(e -> ((StroomStreamException) e).getStroomStatusCode())
                    .isEqualTo(StroomStatusCode.INVALID_FORMAT);
        }

        entries.remove("002.mf");
        entries.put("000.mf", "manifest");
        final Path ordered = zipInOrder(List.of("000.mf", "001.dat", "001.ctx", "001.hdr", "002.log", "002.meta"),
                entries);
        try (final ZipFile zipFile = ZipUtil.createZipFile(ordered)) {
            final ReceivedZip received = ReceivedZip.index(zipFile, headers());

            assertThat(received.feedKeys()).containsExactly(FeedKey.of("META_FEED", "Events"));
            assertThat(received.groups()).hasSize(2);
            final ReceivedZip.Group first = received.groups().get(0);
            assertThat(first.data().getName()).isEqualTo("001.dat");
            assertThat(first.context().getName()).isEqualTo("001.ctx");
            assertThat(first.meta().get(StandardHeaderArguments.FEED)).isEqualTo("META_FEED");
            assertThat(first.meta().get(StandardHeaderArguments.RECEIPT_ID))
                    .as("the headers' receipt id is merged under the entry's own meta")
                    .isEqualTo("rid-1");

            final Path out = dir.resolve("ordered-out.zip");
            received.copyTo(zipFile, received.feedKeys(), out);
            assertThat(ZipUtil.pathList(out)).containsExactly(
                    "0000000001.meta", "0000000001.ctx", "0000000001.dat",
                    "0000000002.meta", "0000000002.dat");
        }
    }

    @Test
    void testFeedKeysAreDistinctInOrderOfFirstAppearanceAndCopyingASubsetRenumbersFromOne() throws IOException {
        final Map<String, String> entries = new LinkedHashMap<>();
        entries.put("1.meta", "Feed:A\n");
        entries.put("1.dat", "a1");
        entries.put("2.meta", "Feed:B\n");
        entries.put("2.dat", "b1");
        entries.put("3.meta", "Feed:A\n");
        entries.put("3.dat", "a2");
        final Path zip = zip(entries);

        try (final ZipFile zipFile = ZipUtil.createZipFile(zip)) {
            final ReceivedZip received = ReceivedZip.index(zipFile, headers());
            assertThat(received.feedKeys())
                    .containsExactly(FeedKey.of("A", "Raw Events"), FeedKey.of("B", "Raw Events"));

            final Path out = dir.resolve("subset.zip");
            final List<ZipEntryGroup> written = received.copyTo(zipFile, Set.of(FeedKey.of("B", "Raw Events")), out);
            assertThat(ZipUtil.pathList(out)).containsExactly("0000000001.meta", "0000000001.dat");
            assertThat(content(out, "0000000001.dat")).isEqualTo("b1");
            assertThat(written).hasSize(1);
            assertThat(written.get(0).getFeedKey()).isEqualTo(FeedKey.of("B", "Raw Events"));
        }
    }

    @Test
    void testASidecarWithNoDataEntryIsOmittedAndAZipWithNoDataIsRejected() throws IOException {
        final Map<String, String> entries = new LinkedHashMap<>();
        entries.put("001.meta", "Feed:A\n");
        entries.put("001.dat", "a1");
        entries.put("002.meta", "Feed:B\n");
        final Path zip = zip(entries);
        try (final ZipFile zipFile = ZipUtil.createZipFile(zip)) {
            final ReceivedZip received = ReceivedZip.index(zipFile, headers());
            assertThat(received.groups()).hasSize(1);
            assertThat(received.feedKeys()).containsExactly(FeedKey.of("A", "Raw Events"));
        }

        final Path onlyMeta = zip(Map.of("001.meta", "Feed:A\n"));
        try (final ZipFile zipFile = ZipUtil.createZipFile(onlyMeta)) {
            assertThatThrownBy(() -> ReceivedZip.index(zipFile, headers()))
                    .isInstanceOf(StroomStreamException.class)
                    .hasMessageContaining("no data entries");
        }
    }

    @Test
    void testAMetaEntryOverItsBoundIsRefusedNamingTheEntry() throws IOException {
        final Map<String, String> entries = new LinkedHashMap<>();
        final String padding = "x".repeat((int) ReceivedZip.MAX_META_ENTRY_SIZE.getBytes());
        entries.put("001.meta", "Feed:A\nPadding:" + padding + "\n");
        entries.put("001.dat", "a1");
        final Path zip = zip(entries);
        try (final ZipFile zipFile = ZipUtil.createZipFile(zip)) {
            assertThatThrownBy(() -> ReceivedZip.index(zipFile, headers()))
                    .isInstanceOf(StroomStreamException.class)
                    .hasMessageContaining("001.meta")
                    .extracting(e -> ((StroomStreamException) e).getStroomStatusCode())
                    .isEqualTo(StroomStatusCode.CONTENT_TOO_LARGE);
        }
    }

    @Test
    void testEntriesInDirectoriesGroupByTheirFullPathAndTheDirectoryItselfIsSkipped() throws IOException {
        final Map<String, String> entries = new LinkedHashMap<>();
        entries.put("a/b/001.dat", "deep");
        entries.put("a/b/001.meta", "Feed:DEEP\n");
        entries.put("a/001.dat", "shallow");
        final Path zip = dir.resolve("dirs.zip");
        try (final ZipWriter writer = new ZipWriter(zip, LocalByteBuffer.get())) {
            writer.writeDir("a/");
            writer.writeDir("a/b/");
            for (final Map.Entry<String, String> entry : entries.entrySet()) {
                writer.writeString(entry.getKey(), entry.getValue());
            }
        }
        try (final ZipFile zipFile = ZipUtil.createZipFile(zip)) {
            final ReceivedZip received = ReceivedZip.index(zipFile, headers());
            assertThat(received.groups()).hasSize(2);
            assertThat(received.groups().get(0).meta().get(StandardHeaderArguments.FEED)).isEqualTo("DEEP");
            assertThat(received.groups().get(1).feedKey()).isEqualTo(HEADER_FEED);

            final Path out = dir.resolve("dirs-out.zip");
            received.copyTo(zipFile, received.feedKeys(), out);
            assertThat(ZipUtil.pathList(out))
                    .containsExactly("0000000001.meta", "0000000001.dat", "0000000002.meta", "0000000002.dat");
            assertThat(content(out, "0000000001.dat")).isEqualTo("deep");
        }
    }

    @Test
    void testDuplicateEntryNamesAreRejectedRatherThanOneLost() throws IOException {
        final Path zip = dir.resolve("dup.zip");
        try (final ZipArchiveOutputStream out = ZipUtil.createOutputStream(Files.newOutputStream(zip))) {
            for (int i = 0; i < 2; i++) {
                out.putArchiveEntry(new ZipArchiveEntry("001.dat"));
                out.write("x".getBytes(StandardCharsets.UTF_8));
                out.closeArchiveEntry();
            }
        }
        try (final ZipFile zipFile = ZipUtil.createZipFile(zip)) {
            assertThatThrownBy(() -> ReceivedZip.index(zipFile, headers()))
                    .isInstanceOf(StroomStreamException.class)
                    .hasMessageContaining("more than one entry named");
        }
    }

    @Test
    void testAnEntryThatCannotBeReadIsRejectedNotSkipped() throws IOException {
        final Map<String, String> entries = new LinkedHashMap<>();
        entries.put("001.dat", "plain");
        entries.put("002.dat", "secret");
        final Path zip = zip(entries);
        // commons-compress will not write an encrypted entry, so mark one as encrypted after the
        // fact: the general purpose flag's low bit, in both the local header and the central
        // directory, which is what a reader consults.
        final byte[] raw = Files.readAllBytes(zip);
        final byte[] name = "002.dat".getBytes(StandardCharsets.UTF_8);
        int patched = 0;
        for (int i = 0; i + name.length <= raw.length; i++) {
            if (java.util.Arrays.equals(raw, i, i + name.length, name, 0, name.length)) {
                if (i >= 30 && raw[i - 30] == 'P' && raw[i - 29] == 'K' && raw[i - 28] == 3 && raw[i - 27] == 4) {
                    raw[i - 24] |= 1;
                    patched++;
                } else if (i >= 46 && raw[i - 46] == 'P' && raw[i - 45] == 'K'
                           && raw[i - 44] == 1 && raw[i - 43] == 2) {
                    raw[i - 38] |= 1;
                    patched++;
                }
            }
        }
        assertThat(patched).as("local header and central directory entry").isEqualTo(2);
        Files.write(zip, raw);
        try (final ZipFile zipFile = ZipUtil.createZipFile(zip)) {
            assertThatThrownBy(() -> ReceivedZip.index(zipFile, headers()))
                    .isInstanceOf(StroomStreamException.class)
                    .hasMessageContaining("cannot be read")
                    .extracting(e -> ((StroomStreamException) e).getStroomStatusCode())
                    .isEqualTo(StroomStatusCode.COMPRESSED_STREAM_INVALID);
        }
    }

    private static AttributeMap headers() {
        final AttributeMap attributeMap = new AttributeMap();
        AttributeMapUtil.addFeedAndType(attributeMap, HEADER_FEED.feed(), HEADER_FEED.type());
        attributeMap.put(StandardHeaderArguments.RECEIPT_ID, "rid-1");
        return attributeMap;
    }

    private Path zip(final Map<String, String> entries) throws IOException {
        return zipInOrder(List.copyOf(entries.keySet()), entries);
    }

    private Path zip(final Map<String, String> entries, final String name) throws IOException {
        final Path zip = dir.resolve(name);
        try (final ZipWriter writer = new ZipWriter(zip, LocalByteBuffer.get())) {
            for (final Map.Entry<String, String> entry : entries.entrySet()) {
                writer.writeString(entry.getKey(), entry.getValue());
            }
        }
        return zip;
    }

    private Path zipInOrder(final List<String> order, final Map<String, String> entries) throws IOException {
        final Map<String, String> ordered = new LinkedHashMap<>();
        order.forEach(name -> ordered.put(name, entries.get(name)));
        return zip(ordered, "zip-" + ordered.size() + "-" + Math.abs(order.hashCode()) + ".zip");
    }

    private static String content(final Path zip, final String entryName) throws IOException {
        try (final ZipFile zipFile = ZipUtil.createZipFile(zip);
                final InputStream in = zipFile.getInputStream(zipFile.getEntry(entryName))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
