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

package stroom.proxy.app;

import stroom.data.zip.StroomZipFileType;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.app.handler.FileGroup;
import stroom.proxy.app.handler.ForwardFileConfig;
import stroom.proxy.app.handler.ForwardFileQueueConfig;
import stroom.proxy.repo.AggregatorConfig;
import stroom.test.common.TestUtil;
import stroom.util.concurrent.UniqueId;
import stroom.util.date.DateUtil;
import stroom.util.io.FileName;
import stroom.util.io.PathCreator;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.time.StroomDuration;

import jakarta.inject.Inject;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.assertj.core.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.stream.Stream;

public class MockFileDestination {

    private static final Logger LOGGER = LoggerFactory.getLogger(MockFileDestination.class);

    private final PathCreator pathCreator;

    @Inject
    public MockFileDestination(final PathCreator pathCreator) {
        this.pathCreator = pathCreator;
    }

    static ForwardFileConfig createForwardFileConfig() {
        return new ForwardFileConfig(
                true,
                false,
                "My forward file",
                "forward_dest",
                null,
                new ForwardFileQueueConfig(),
                null,
                null,
                null);
    }

    /**
     * A count of all the meta files in the {@link ForwardFileConfig} locations.
     */
    private long getForwardFileMetaCount(final Config config) {
        final List<ForwardFileConfig> forwardConfigs = NullSafe.getOrElseGet(
                config,
                Config::getProxyConfig,
                ProxyConfig::getForwardFileDestinations,
                Collections::emptyList);

        if (!forwardConfigs.isEmpty()) {
            return forwardConfigs.stream()
                    .mapToLong(forwardConfig -> {
                        if (!forwardConfig.getPath().isBlank()) {
                            try (final Stream<Path> pathStream = Files.walk(
                                    pathCreator.toAppPath(forwardConfig.getPath()))) {
                                return pathStream
                                        .filter(path -> path.toString().endsWith(".meta"))
                                        .count();
                            } catch (final IOException e) {
                                throw new RuntimeException(e);
                            }
                        } else {
                            return 0L;
                        }
                    })
                    .sum();
        } else {
            return 0L;
        }
    }

    /**
     * Get all the files in the directories specified in {@link ForwardFileConfig}.
     * The dir will contain .meta and .zip pairs. Each pair is one item in the returned list.
     * Each .zip will contain one or more sets of .dat/.meta/.ctx/etc.
     */
    private List<ForwardFileItem> getForwardFiles(final Config config) {
        final List<ForwardFileConfig> forwardConfigs = NullSafe.getOrElseGet(
                config,
                Config::getProxyConfig,
                ProxyConfig::getForwardFileDestinations,
                Collections::emptyList);

        return forwardConfigs.stream()
                .flatMap(forwardFileConfig -> {
                    final Path forwardDir = pathCreator.toAppPath(forwardFileConfig.getPath());
                    final List<ForwardFileItem> forwardFileItems = new ArrayList<>();
                    try (final Stream<Path> stream = Files.walk(forwardDir)) {
                        stream.forEach(path -> {
                            if (path.getFileName().toString().endsWith(".meta")) {
                                final Path dir = path.getParent();
                                final FileGroup fileGroup = new FileGroup(dir);
                                if (!Files.exists(fileGroup.getMeta())) {
                                    LOGGER.info("Meta does not exist. dir: {}", dir);
                                }
                                final String zipFileName = fileGroup.getZip().getFileName().toString();
                                final String baseName = zipFileName.substring(0, zipFileName.indexOf('.'));
                                final List<ZipItem> zipItems = new ArrayList<>();
                                final String metaContent;
                                try {
                                    metaContent = Files.readString(fileGroup.getMeta());

                                    try (final ZipArchiveInputStream zipArchiveInputStream =
                                            new ZipArchiveInputStream(
                                                    new BufferedInputStream(
                                                            Files.newInputStream(fileGroup.getZip())))) {
                                        ZipArchiveEntry entry = zipArchiveInputStream.getNextEntry();
                                        while (entry != null) {
                                            if (!entry.isDirectory()) {
                                                final String zipEntryName = entry.getName();
                                                final FileName fileName = FileName.parse(zipEntryName);
                                                final StroomZipFileType zipEntryType =
                                                        StroomZipFileType.fromExtension(fileName.getExtension());
                                                final String zipEntryContent =
                                                        new String(zipArchiveInputStream.readAllBytes(),
                                                                StandardCharsets.UTF_8);
                                                zipItems.add(new ZipItem(
                                                        zipEntryType,
                                                        fileName.getBaseName(),
                                                        zipEntryContent));
                                            }
                                            entry = zipArchiveInputStream.getNextEntry();
                                        }
                                    }

                                } catch (final Exception e) {
                                    throw new RuntimeException(e);
                                }
                                forwardFileItems.add(new ForwardFileItem(
                                        zipFileName,
                                        baseName,
                                        metaContent,
                                        zipItems));
                            }
                        });
                    } catch (final IOException e) {
                        throw new UncheckedIOException(e);
                    }

                    return forwardFileItems.stream();
                })
                .toList();
    }

    /**
     * Assert all the {@link UniqueId}s contained in the stored aggregates
     */
    void assertReceiptIds(final Config config,
                          final List<UniqueId> expectedReceiptIds) {
        final List<UniqueId> actualReceiptIds = getForwardFiles(config)
                .stream()
                .map(ForwardFileItem::getContainedReceiptIds)
                .flatMap(Collection::stream)
                .toList();

        Assertions.assertThat(actualReceiptIds)
                .containsExactlyInAnyOrderElementsOf(expectedReceiptIds);
    }

    void assertFileContents(final Config config) {
        assertFileContents(config, 4);
    }

    void assertReceivedItemCount(final Config config, final int count) {
        TestUtil.waitForIt(
                () -> {
                    final long actualCount = getForwardFiles(config)
                            .stream()
                            .map(ForwardFileItem::zipItems)
                            .flatMap(Collection::stream)
                            .filter(zipItem -> zipItem.type.equals(StroomZipFileType.META))
                            .count();
                    return actualCount;
                },
                (long) count,
                () -> "Forwarded file pairs count",
                Duration.ofMinutes(2),
                Duration.ofSeconds(1),
                Duration.ofSeconds(5));

        final long actualCount = getForwardFiles(config)
                .stream()
                .map(ForwardFileItem::zipItems)
                .flatMap(Collection::stream)
                .filter(zipItem -> zipItem.type.equals(StroomZipFileType.META))
                .count();
        Assertions.assertThat(actualCount)
                .isEqualTo(count);
    }

    void assertMaxItemsPerAggregate(final Config config) {
        final List<ForwardFileItem> forwardFiles = getForwardFiles(config);
        final List<Long> aggItemCounts = forwardFiles.stream()
                .map(ForwardFileItem::zipItems)
                .map(zipItems -> zipItems.stream()
                        .filter(zipItem -> zipItem.type().equals(StroomZipFileType.META))
                        .count())
                .toList();

        final AggregatorConfig aggregatorConfig = config.getProxyConfig().getAggregatorConfig();
        final int maxItemsPerAggregate = aggregatorConfig.getMaxItemsPerAggregate();

        // Each agg should be no bigger than configured max
        for (final Long itemCount : aggItemCounts) {
            Assertions.assertThat(itemCount)
                    .isLessThanOrEqualTo(maxItemsPerAggregate);
        }

        final StroomDuration aggregationFrequency = aggregatorConfig.getAggregationFrequency();
        final List<Duration> aggAges = forwardFiles.stream()
                .map(ForwardFileItem::zipItems)
                .map(zipItems -> {
                    final LongSummaryStatistics stats = zipItems.stream()
                            .filter(zipItem -> zipItem.type().equals(StroomZipFileType.META))
                            .map(zipItem -> zipItem.getContentAsAttributeMap()
                                    .get(StandardHeaderArguments.RECEIVED_TIME))
                            .mapToLong(DateUtil::parseNormalDateTimeString)
                            .summaryStatistics();
                    return Duration.between(
                            Instant.ofEpochMilli(stats.getMin()),
                            Instant.ofEpochMilli(stats.getMax()));
                })
                .toList();

        // Each agg should have a receipt time range no wider than the configured max agg age
        for (final Duration aggAge : aggAges) {
            Assertions.assertThat(aggAge)
                    .isLessThanOrEqualTo(aggregationFrequency.getDuration());
        }
    }

    void assertFileContents(final Config config, final int count) {
        TestUtil.waitForIt(
                () -> getForwardFileMetaCount(config),
                (long) count,
                () -> "Forwarded file pairs count",
                Duration.ofMinutes(2),
                Duration.ofSeconds(1),
                Duration.ofSeconds(5));

        final List<ForwardFileItem> forwardFileItems = getForwardFiles(config);

        // Check number of forwarded files.
        Assertions.assertThat(forwardFileItems)
                .hasSize(count);

        // Check feed names.
        final String[] feedNames = new String[count];
        for (int i = 0; i < count; i++) {
            feedNames[i++] = TestConstants.FEED_TEST_EVENTS_1;
            feedNames[i] = TestConstants.FEED_TEST_EVENTS_2;
        }

        Assertions.assertThat(forwardFileItems)
                .extracting(forwardFileItem -> forwardFileItem.getMetaAttributeMap().get(StandardHeaderArguments.FEED))
                .containsExactlyInAnyOrder(feedNames);

        // Check zip content file count.
        final Integer[] sizes = new Integer[count];
        Arrays.fill(sizes, 6);
        sizes[sizes.length - 1] = 2;
        sizes[sizes.length - 2] = 2;

        Assertions.assertThat(forwardFileItems.stream()
                        .map(forwardFileItem -> forwardFileItem.zipItems().size())
                        .toList())
                .containsExactlyInAnyOrder(sizes);

        // Check zip contents.
        final List<String> expectedFiles = List.of(
                "0000000001.meta",
                "0000000001.dat",
                "0000000002.meta",
                "0000000002.dat",
                "0000000003.meta",
                "0000000003.dat",
                "0000000004.meta",
                "0000000004.dat");
        assertForwardFileItemContent(forwardFileItems, expectedFiles);
    }


    private void assertForwardFileItemContent(final List<ForwardFileItem> forwardFileItems,
                                              final List<String> expectedFiles) {
        forwardFileItems.forEach(forwardFileItem -> {
            for (int i = 0; i < forwardFileItem.zipItems().size(); i++) {
                final ZipItem zipItem = forwardFileItem.zipItems().get(i);
                final String expectedName = expectedFiles.get(i);
                final String actualName = zipItem.baseName() + zipItem.type().getDotExtension();
                Assertions.assertThat(actualName).isEqualTo(expectedName);
                Assertions.assertThat(zipItem.content().length()).isGreaterThan(1);
            }
        });
    }


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    /**
     * Represents a meta + zip pair as created by the file forwarder
     *
     * @param name
     * @param basePath
     * @param metaContent
     * @param zipItems    One for each item in the zip
     */
    private record ForwardFileItem(String name,
                                   String basePath,
                                   String metaContent,
                                   List<ZipItem> zipItems) {

        public AttributeMap getMetaAttributeMap() {
            return AttributeMapUtil.create(metaContent);
        }

        /**
         * The {@link UniqueId} of the wrapping zip
         */
        public UniqueId getReceiptId() {
            final String receiptIdStr = AttributeMapUtil.create(metaContent)
                    .get(StandardHeaderArguments.RECEIPT_ID);
            return UniqueId.parse(receiptIdStr);
        }

        public List<UniqueId> getContainedReceiptIds() {
            return zipItems.stream()
                    .filter(zipItem ->
                            zipItem.type().equals(StroomZipFileType.META))
                    .map(ZipItem::getContentAsAttributeMap)
                    .map(attributeMap ->
                            attributeMap.get(StandardHeaderArguments.RECEIPT_ID))
                    .map(UniqueId::parse)
                    .toList();
        }
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    /**
     * Represents an item in a zip file. May be meta or data
     */
    private record ZipItem(StroomZipFileType type,
                           String baseName,
                           String content) {

        public AttributeMap getContentAsAttributeMap() {
            if (StroomZipFileType.META.equals(type)) {
                return AttributeMapUtil.create(content);
            } else {
                throw new UnsupportedOperationException(LogUtil.message(
                        "Can't convert {} to an AttributeMap", type));
            }
        }
    }
}
