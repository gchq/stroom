/*
 * Copyright 2024 Crown Copyright
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
import stroom.proxy.repo.FeedKeyInterner;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.FeedKey;

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

    /**
     * Entry sizes are whatever the zip declared, so two large claims must not wrap the total
     * negative - a negative total makes PreAggregator's byte limit unreachable and the aggregate
     * then never closes on size.
     */
    @Test
    void testTheTotalSaturatesInsteadOfOverflowingToNegative() {
        final FeedKey feedKey = FeedKey.of("test_feed", "test_type");
        final ZipEntryGroup zipEntryGroup = new ZipEntryGroup(feedKey);
        zipEntryGroup.setMetaEntry(new Entry("1.meta", Long.MAX_VALUE));
        zipEntryGroup.setDataEntry(new Entry("1.dat", Long.MAX_VALUE));

        // Plain addition here would give -2.
        assertThat(zipEntryGroup.getTotalUncompressedSize())
                .isEqualTo(Long.MAX_VALUE);
    }

    /**
     * All four slots populated, so every term in the sum is exercised rather than just one pair.
     */
    @Test
    void testTheTotalSaturatesAcrossAllFourEntryTypes() {
        final FeedKey feedKey = FeedKey.of("test_feed", "test_type");
        final ZipEntryGroup zipEntryGroup = new ZipEntryGroup(feedKey);
        final long quarter = Long.MAX_VALUE / 2;
        zipEntryGroup.setManifestEntry(new Entry("1.mf", quarter));
        zipEntryGroup.setMetaEntry(new Entry("1.meta", quarter));
        zipEntryGroup.setContextEntry(new Entry("1.ctx", quarter));
        zipEntryGroup.setDataEntry(new Entry("1.dat", quarter));

        assertThat(zipEntryGroup.getTotalUncompressedSize())
                .isEqualTo(Long.MAX_VALUE);
    }

    /**
     * A negative declared size must not <em>reduce</em> the running total, which is what made
     * the byte limit recede as entries were added.
     */
    @Test
    void testANegativeEntrySizeDoesNotReduceTheTotal() {
        final FeedKey feedKey = FeedKey.of("test_feed", "test_type");
        final ZipEntryGroup zipEntryGroup = new ZipEntryGroup(feedKey);
        zipEntryGroup.setMetaEntry(new Entry("1.meta", 100));
        zipEntryGroup.setDataEntry(new Entry("1.dat", -1_000));

        assertThat(zipEntryGroup.getTotalUncompressedSize())
                .isEqualTo(100);
    }

    @Test
    void testTheOrdinaryTotalIsUnchanged() {
        final FeedKey feedKey = FeedKey.of("test_feed", "test_type");
        final ZipEntryGroup zipEntryGroup = new ZipEntryGroup(feedKey);
        zipEntryGroup.setManifestEntry(new Entry("1.mf", 123));
        zipEntryGroup.setMetaEntry(new Entry("1.meta", 234));
        zipEntryGroup.setContextEntry(new Entry("1.ctx", 345));
        zipEntryGroup.setDataEntry(new Entry("1.dat", 456));

        assertThat(zipEntryGroup.getTotalUncompressedSize())
                .isEqualTo(123 + 234 + 345 + 456);
    }

    @Test
    void test() throws IOException {
        final String data;
        final FeedKeyInterner interner = FeedKeyInterner.create();
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
        final FeedKeyInterner interner = FeedKeyInterner.create();
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
