/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.proxy.app.handler.ZipEntryGroup.Entry;
import stroom.proxy.repo.FeedKey;
import stroom.proxy.repo.FeedKey.FeedKeyInterner;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class TestZipEntryGroup extends StroomUnitTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestZipEntryGroup.class);

    private static final int ENTRIES = 100;

    @Test
    void test() throws IOException {
        final String data;
        final FeedKeyInterner interner = FeedKey.createInterner();
        final FeedKey feedKey = interner.intern("test_feed", "test_type");

        // Write data
        try (final StringWriter writer = new StringWriter()) {
            for (int i = 0; i < ENTRIES; i++) {
                final ZipEntryGroup zipEntryGroup = new ZipEntryGroup(feedKey);
                zipEntryGroup.setManifestEntry(new Entry(i + ".mf", 123));
                zipEntryGroup.setMetaEntry(new Entry(i + ".meta", 234));
                zipEntryGroup.setContextEntry(new Entry(i + ".ctx", 345));
                zipEntryGroup.setDataEntry(new Entry(i + ".dat", 456));
                zipEntryGroup.write(writer);
            }
            writer.flush();
            data = writer.toString();
            LOGGER.debug("data:\n{}", data);
        }

        // Read data
        try (final Stream<String> linesStream = data.lines()) {
            final AtomicInteger counter = new AtomicInteger();
            linesStream.forEach(line -> {
                // Use the interner, so we get the shared FeedKey obj on deser
                final ZipEntryGroup zipEntryGroup = ZipEntryGroup.read(line, interner);
                assertThat(zipEntryGroup.getFeedName())
                        .isEqualTo(feedKey.feed())
                        .isSameAs(feedKey.feed());
                assertThat(zipEntryGroup.getTypeName())
                        .isEqualTo(feedKey.type())
                        .isSameAs(feedKey.type());
                assertThat(zipEntryGroup.getFeedKey())
                        .isEqualTo(feedKey)
                        .isSameAs(feedKey);
                assertThat(zipEntryGroup.getManifestEntry().getName())
                        .isEqualTo(counter.get() + ".mf");
                assertThat(zipEntryGroup.getManifestEntry().getUncompressedSize())
                        .isEqualTo(123);
                assertThat(zipEntryGroup.getMetaEntry().getName())
                        .isEqualTo(counter.get() + ".meta");
                assertThat(zipEntryGroup.getMetaEntry().getUncompressedSize())
                        .isEqualTo(234);
                assertThat(zipEntryGroup.getContextEntry().getName())
                        .isEqualTo(counter.get() + ".ctx");
                assertThat(zipEntryGroup.getContextEntry().getUncompressedSize())
                        .isEqualTo(345);
                assertThat(zipEntryGroup.getDataEntry().getName())
                        .isEqualTo(counter.get() + ".dat");
                assertThat(zipEntryGroup.getDataEntry().getUncompressedSize())
                        .isEqualTo(456);

                assertThat(zipEntryGroup.getTotalUncompressedSize())
                        .isEqualTo(123 + 234 + 345 + 456);
                counter.incrementAndGet();
            });

            assertThat(counter)
                    .hasValue(ENTRIES);
        }
    }

    @Test
    void test2(@TempDir final Path tempDir) throws IOException {
        final String data;
        final FeedKeyInterner interner = FeedKey.createInterner();
        final FeedKey feedKey = interner.intern("test_feed", "test_type");

        // Write data
        try (final StringWriter writer = new StringWriter()) {
            // Wack in a few blank lines to make sure it copes with it
            writer.write("\n");
            writer.write("\n");
            for (int i = 0; i < ENTRIES; i++) {
                final ZipEntryGroup zipEntryGroup = new ZipEntryGroup(feedKey);
                zipEntryGroup.setManifestEntry(new Entry(i + ".mf", 123));
                zipEntryGroup.setMetaEntry(new Entry(i + ".meta", 234));
                zipEntryGroup.setContextEntry(new Entry(i + ".ctx", 345));
                zipEntryGroup.setDataEntry(new Entry(i + ".dat", 456));
                zipEntryGroup.write(writer);
            }
            // Wack in a few blank lines to make sure it copes with it
            writer.write("\n");
            writer.write("\n");
            writer.flush();
            data = writer.toString();
            LOGGER.debug("data:\n{}", data);
        }

        final Path entriesFile = tempDir.resolve("proxy.entries");
        Files.writeString(entriesFile, data, StandardOpenOption.CREATE);

        // Read data
        final List<ZipEntryGroup> zipEntryGroups = ZipEntryGroup.read(entriesFile, interner);
        final AtomicInteger counter = new AtomicInteger();
        for (final ZipEntryGroup zipEntryGroup : zipEntryGroups) {
            // Use the interner, so we get the shared FeedKey obj on deser
            assertThat(zipEntryGroup.getFeedName())
                    .isEqualTo(feedKey.feed())
                    .isSameAs(feedKey.feed());
            assertThat(zipEntryGroup.getTypeName())
                    .isEqualTo(feedKey.type())
                    .isSameAs(feedKey.type());
            assertThat(zipEntryGroup.getFeedKey())
                    .isEqualTo(feedKey)
                    .isSameAs(feedKey);
            assertThat(zipEntryGroup.getManifestEntry().getName())
                    .isEqualTo(counter.get() + ".mf");
            assertThat(zipEntryGroup.getManifestEntry().getUncompressedSize())
                    .isEqualTo(123);
            assertThat(zipEntryGroup.getMetaEntry().getName())
                    .isEqualTo(counter.get() + ".meta");
            assertThat(zipEntryGroup.getMetaEntry().getUncompressedSize())
                    .isEqualTo(234);
            assertThat(zipEntryGroup.getContextEntry().getName())
                    .isEqualTo(counter.get() + ".ctx");
            assertThat(zipEntryGroup.getContextEntry().getUncompressedSize())
                    .isEqualTo(345);
            assertThat(zipEntryGroup.getDataEntry().getName())
                    .isEqualTo(counter.get() + ".dat");
            assertThat(zipEntryGroup.getDataEntry().getUncompressedSize())
                    .isEqualTo(456);

            assertThat(zipEntryGroup.getTotalUncompressedSize())
                    .isEqualTo(123 + 234 + 345 + 456);
            counter.incrementAndGet();
        }
        assertThat(counter)
                .hasValue(ENTRIES);
    }
}
