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

import stroom.data.zip.StroomZipFileType;
import stroom.proxy.app.DataDirProvider;
import stroom.proxy.app.ProxyConfig;
import stroom.proxy.app.handler.PreAggregator.Part;
import stroom.proxy.repo.AggregatorConfig;
import stroom.proxy.repo.FeedKey;
import stroom.proxy.repo.ProxyServices;
import stroom.test.common.MockMetrics;
import stroom.test.common.TestUtil;
import stroom.test.common.TestUtil.TimedCase;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.time.StroomDuration;
import stroom.util.zip.ZipUtil;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class TestPreAggregator extends StroomUnitTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestPreAggregator.class);

    private static final int MAX_ITEMS_PER_AGGREGATE = 3;
    public static final FeedKey FEED_KEY = new FeedKey("test-feed", "test-type");

    @Mock
    private ProxyServices proxyServices;

    @Test
    void testSimple() throws IOException {
        final int inputZipCount = 10;
        final int entryCountPerZip = 1;
        test(entryCountPerZip, inputZipCount, true);
    }

    @Test
    void testSimple2() throws IOException {
        final int inputZipCount = 1;
        final int entryCountPerZip = 3;
        test(entryCountPerZip, inputZipCount, true);
    }

    @Test
    void testSplit() throws IOException {
        final int inputZipCount = 10;
        final int entryCountPerZip = 10;
        test(entryCountPerZip, inputZipCount, true);
    }

    @Test
    void testSplitManyExamples() throws IOException {
        for (int inputZipCount = 1; inputZipCount < 10; inputZipCount++) {
            for (int entryCountPerZip = 1; entryCountPerZip < 10; entryCountPerZip++) {
                test(entryCountPerZip, inputZipCount, true);
            }
        }
    }

    @Test
    void testNoSplit() throws IOException {
        final int inputZipCount = 10;
        final int entryCountPerZip = 10;
        test(entryCountPerZip, inputZipCount, false);
    }

    @Test
    void testNoSplitManyExamples() throws IOException {
        for (int inputZipCount = 1; inputZipCount < 10; inputZipCount++) {
            for (int entryCountPerZip = 1; entryCountPerZip < 10; entryCountPerZip++) {
                test(entryCountPerZip, inputZipCount, false);
            }
        }
    }

    private List<ExpectedOutput> createExpectedOutput(final int inputZipCount,
                                                      final int entryCountPerZip,
                                                      final boolean splitSources) {
        final List<ExpectedOutput> expectedOutputs = new ArrayList<>();
        int currentEntryCount = 0;
        List<Integer> entryCounts = new ArrayList<>();

        if (splitSources) {
            // Simulate the splitting and aggregation we expect to occur within the pre aggregator.
            for (int zipCount = 1; zipCount <= inputZipCount; zipCount++) {
                int currentEntryCountPerZip = 0;
                for (int entryCount = 1; entryCount <= entryCountPerZip; entryCount++) {
                    currentEntryCountPerZip++;
                    currentEntryCount++;
                    if (currentEntryCount == MAX_ITEMS_PER_AGGREGATE) {
                        currentEntryCount = 0;

                        entryCounts.add(currentEntryCountPerZip);
                        expectedOutputs.add(new ExpectedOutput(entryCounts));
                        entryCounts = new ArrayList<>();

                        currentEntryCountPerZip = 0;
                    }
                }
                if (currentEntryCountPerZip > 0) {
                    entryCounts.add(currentEntryCountPerZip);
                }
            }

        } else {
            // Simulate aggregating after allowing overflow.
            for (int zipCount = 1; zipCount <= inputZipCount; zipCount++) {
                entryCounts.add(entryCountPerZip);
                currentEntryCount += entryCountPerZip;
                if (currentEntryCount >= MAX_ITEMS_PER_AGGREGATE) {
                    currentEntryCount = 0;
                    expectedOutputs.add(new ExpectedOutput(entryCounts));
                    entryCounts = new ArrayList<>();
                }
            }
        }
        return expectedOutputs;
    }


    private void test(final int entryCountPerZip,
                      final int inputZipCount,
                      final boolean splitSources) throws IOException {
        // Simulate the splitting and aggregation we expect to occur within the pre aggregator.
        final List<ExpectedOutput> expectedOutputs =
                createExpectedOutput(inputZipCount, entryCountPerZip, splitSources);

        final Path dataDir = Files.createTempDirectory("data");
        final DataDirProvider dataDirProvider = () -> dataDir;
        final CleanupDirQueue cleanupDirQueue = new CleanupDirQueue(dataDirProvider);
        final ProxyConfig proxyConfig = getProxyConfig(splitSources);
        final PreAggregator preAggregator = new PreAggregator(
                cleanupDirQueue,
                dataDirProvider,
                proxyServices,
                proxyConfig::getAggregatorConfig, new MockMetrics());

        final AtomicInteger aggregateCount = new AtomicInteger();
        preAggregator.setDestination(preAggregateDir -> {
            try {
                final int aggregateNo = aggregateCount.getAndIncrement();
                assertThat(expectedOutputs.size()).isGreaterThan(aggregateNo);
                final ExpectedOutput expectedOutput = expectedOutputs.get(aggregateNo);
                // Aggregate dir.
                assertThat(preAggregateDir.getFileName().toString()).isEqualTo("test-feed__test-type");
                // Test contents.
                try (final Stream<Path> stream = Files.list(preAggregateDir)) {
                    final AtomicInteger zipCount = new AtomicInteger();
                    stream.sorted().forEach(path -> {
                        try {
                            final int zipNo = zipCount.getAndIncrement();
                            assertThat(expectedOutput.entryCounts.size()).isGreaterThan(zipNo);
                            final int expectedEntryCount = expectedOutput.entryCounts.get(zipNo);
                            final FileGroup fileGroup = new FileGroup(path);
                            final List<String> actualList = ZipUtil.pathList(fileGroup.getZip());
                            final List<String> expectedList = createExpectedList(expectedEntryCount);
                            assertThat(actualList).isEqualTo(expectedList);
                        } catch (final IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
                    assertThat(zipCount.get()).isEqualTo(expectedOutput.entryCounts.size());
                }
                // In normal use the supplied dir would be moved ready for new a new aggregate to be created so simulate
                // by deleting it.
                FileUtil.deleteDir(preAggregateDir);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        final Path path = Files.createTempDirectory("temp");
        final NumberedDirProvider numberedDirProvider = new NumberedDirProvider(path);
        for (int i = 0; i < inputZipCount; i++) {
            final Path dir = numberedDirProvider.get();
            final FileGroup fileGroup = new FileGroup(dir);
            TestDataUtil.writeFileGroup(fileGroup, 1, entryCountPerZip, FEED_KEY);
            preAggregator.addDir(dir);
        }

        assertThat(aggregateCount.get()).isEqualTo(expectedOutputs.size());
    }

    private List<String> createExpectedList(final int entryCount) {
        final List<String> expectedList = new ArrayList<>();
        for (int i = 1; i <= entryCount; i++) {
            final String baseName = NumericFileNameUtil.create(i);
            expectedList.add(baseName + StroomZipFileType.META.getDotExtension());
            expectedList.add(baseName + StroomZipFileType.DATA.getDotExtension());
        }
        return expectedList;
    }

    protected ProxyConfig getProxyConfig(final boolean splitSources) {
        return ProxyConfig.builder()
                .aggregatorConfig(AggregatorConfig.builder()
                        .maxUncompressedByteSizeString("1G")
                        .aggregationFrequency(StroomDuration.ofDays(1))
                        .maxItemsPerAggregate(MAX_ITEMS_PER_AGGREGATE)
                        .splitSources(splitSources)
                        .build())
                .build();
    }

    /**
     * Using records takes ~x5 as long
     */
    @Disabled // manual only
    @Test
    void testPerf() {
        TestUtil.comparePerformance(
                3,
                10_000_000,
                LOGGER::info,
                TimedCase.of(
                        "primitives",
                        (round, iterations) -> {
                            long items = round;
                            long byteSize = round;
                            for (int i = 0; i < iterations; i++) {
                                items++;
                                byteSize += 1;
                            }
                            LOGGER.info("items: {}, byteSize: {}", items, byteSize);
                        }),
                TimedCase.of(
                        "records",
                        (round, iterations) -> {
                            Part part = new Part(round, round, Collections.emptyList());
                            for (int i = 0; i < iterations; i++) {
                                part = new Part(
                                        part.items() + 1,
                                        part.bytes() + 1,
                                        Collections.emptyList());
                            }
                            LOGGER.info("part: {}", part);
                        })
        );
    }


    // --------------------------------------------------------------------------------


    private record ExpectedOutput(List<Integer> entryCounts) {

    }
}
