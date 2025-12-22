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

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.app.handler.TestDataUtil.Item;
import stroom.proxy.app.handler.TestDataUtil.ItemGroup;
import stroom.proxy.app.handler.TestDataUtil.ProxyZipSnapshot;
import stroom.proxy.repo.FeedKey;
import stroom.proxy.repo.ProxyServices;
import stroom.test.common.DirectorySnapshot;
import stroom.test.common.DirectorySnapshot.PathSnapshot;
import stroom.test.common.DirectorySnapshot.Snapshot;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class TestZipSplitter {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestZipSplitter.class);
    public static final String FEED_1 = "test-feed-1";
    public static final String FEED_2 = "test-feed-2";
    public static final String TYPE_1 = "test-type-1";
    public static final String TYPE_2 = "test-type-2";
    public static final FeedKey FEED_KEY_1_1 = new FeedKey(FEED_1, TYPE_1);
    public static final FeedKey FEED_KEY_1_2 = new FeedKey(FEED_1, TYPE_2);
    public static final FeedKey FEED_KEY_2_1 = new FeedKey(FEED_2, TYPE_1);
    public static final FeedKey FEED_KEY_2_2 = new FeedKey(FEED_2, TYPE_2);

    @Mock
    private ProxyServices mockProxyServices;

    @Test
    void test_oneFeedKey(@TempDir final Path tempDir) throws IOException {
        final Path workingDir = tempDir.resolve("working");
        final Path consumerDir = tempDir.resolve("consumer");
        Files.createDirectories(workingDir);
        Files.createDirectories(consumerDir);

        final NumberedDirProvider numberedDirProvider = new NumberedDirProvider(workingDir);

        final Path zipDir = Files.createTempDirectory("temp");
        final FileGroup fileGroup = new FileGroup(zipDir);

        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put("Foo", "Bar");
        final Set<FeedKey> feedKeys = Set.of(FEED_KEY_1_1);
        TestDataUtil.writeZip(
                new FileGroup(zipDir),
                2,
                attributeMap,
                feedKeys,
                null);
        final Path testZipFile = fileGroup.getZip();
        AttributeMapUtil.write(attributeMap, fileGroup.getMeta());

        assertThat(testZipFile)
                .isRegularFile();
        assertThat(fileGroup.getParentDir())
                .isDirectory()
                .isNotEmptyDirectory();

        final List<Path> consumedPaths = new ArrayList<>();
        final AtomicLong counter = new AtomicLong();
        ZipSplitter.splitZipByFeed(
                fileGroup.getParentDir(),
                numberedDirProvider,
                consumedPath -> {
                    try {
                        final Path destPath = consumerDir.resolve(
                                NumericFileNameUtil.create(counter.incrementAndGet()));
                        Files.move(consumedPath, destPath);
                        consumedPaths.add(destPath);
                    } catch (final IOException ex) {
                        throw new RuntimeException(ex);
                    }
                });

        assertThat(fileGroup.getParentDir())
                .doesNotExist();
        assertThat(workingDir)
                .isEmptyDirectory();
        assertThat(consumerDir)
                .isNotEmptyDirectory();
        // One feed keys, so one zip
        assertThat(consumedPaths)
                .hasSize(1);

        final Snapshot snapshot = DirectorySnapshot.of(consumerDir);
        LOGGER.info("snapshot of {}\n{}", consumerDir, snapshot);

        final List<Path> consumedZips = snapshot.pathSnapshots()
                .stream()
                .map(PathSnapshot::path)
                .map(path -> consumerDir.resolve(path).toAbsolutePath())
                .filter(path -> path.getFileName().toString().endsWith(".zip"))
                .toList();
        assertThat(consumedZips)
                .hasSize(1);

        for (final Path consumedZip : consumedZips) {
            final ProxyZipSnapshot proxyZipSnapshot = ProxyZipSnapshot.of(consumedZip);
            LOGGER.info("Contents of {}\n{}", consumedZip, proxyZipSnapshot);
        }

        final List<Path> subDirs = FileUtil.listChildDirs(consumerDir);
        assertThat(subDirs)
                .hasSize(1);

        for (final Path subDir : subDirs) {
            LOGGER.info("subDir: {}", subDir);

            final AttributeMap meta = TestDataUtil.getMeta(subDir);
            assertThat(meta)
                    .isNotNull();

            final List<ZipEntryGroup> entries = TestDataUtil.getEntries(subDir);
            assertThat(entries)
                    .isNotNull()
                    .isNotEmpty();

            final Path zipFilePath = TestDataUtil.getZipFile(subDir);

            final ProxyZipSnapshot proxyZipSnapshot = ProxyZipSnapshot.of(zipFilePath);
            final String feed = meta.get(StandardHeaderArguments.FEED);
            final String type = meta.get(StandardHeaderArguments.TYPE);
            final FeedKey feedKey = FeedKey.of(feed, type);
            if (feedKeys.contains(feedKey)) {
                // Two item groups per zip
                assertThat(entries)
                        .hasSize(2);
                // Entries in entries file are all for right feedKey
                assertThat(
                        entries.stream()
                                .map(ZipEntryGroup::getFeedKey)
                                .allMatch(aFeedKey -> aFeedKey.equals(feedKey)))
                        .isTrue();

                // Two item groups per zip
                assertThat(proxyZipSnapshot.getItemGroups())
                        .hasSize(2);
                // Meta entries in zip file are all for right feedKey
                assertThat(proxyZipSnapshot.getItemGroups()
                        .stream()
                        .map(ItemGroup::meta)
                        .map(Item::content)
                        .allMatch(attrMap -> feedKey.feed().equals(attrMap.get(StandardHeaderArguments.FEED))
                                             && feedKey.type().equals(attrMap.get(StandardHeaderArguments.TYPE))))
                        .isTrue();
            } else {
                Assertions.fail("Unexpected feedKey in meta " + feedKey);
            }
        }
    }

    @Test
    void test_twoFeeds(@TempDir final Path tempDir) throws IOException {
        final Path workingDir = tempDir.resolve("working");
        final Path consumerDir = tempDir.resolve("consumer");
        Files.createDirectories(workingDir);
        Files.createDirectories(consumerDir);

        final NumberedDirProvider numberedDirProvider = new NumberedDirProvider(workingDir);

        final Path zipDir = Files.createTempDirectory("temp");
        final FileGroup fileGroup = new FileGroup(zipDir);

        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put("Foo", "Bar");
        final Set<FeedKey> feedKeys = Set.of(FEED_KEY_1_1, FEED_KEY_2_2);
        TestDataUtil.writeZip(
                new FileGroup(zipDir),
                2,
                attributeMap,
                feedKeys,
                null);
        final Path testZipFile = fileGroup.getZip();
        AttributeMapUtil.write(attributeMap, fileGroup.getMeta());

        assertThat(testZipFile)
                .isRegularFile();
        assertThat(fileGroup.getParentDir())
                .isDirectory()
                .isNotEmptyDirectory();

        final List<Path> consumedPaths = new ArrayList<>();
        final AtomicLong counter = new AtomicLong();
        ZipSplitter.splitZipByFeed(
                fileGroup.getParentDir(),
                numberedDirProvider,
                consumedPath -> {
                    try {
                        final Path destPath = consumerDir.resolve(
                                NumericFileNameUtil.create(counter.incrementAndGet()));
                        Files.move(consumedPath, destPath);
                        consumedPaths.add(destPath);
                    } catch (final IOException ex) {
                        throw new RuntimeException(ex);
                    }
                });

        assertThat(fileGroup.getParentDir())
                .doesNotExist();
        assertThat(workingDir)
                .isEmptyDirectory();
        assertThat(consumerDir)
                .isNotEmptyDirectory();
        // Two feed keys, so two zips
        assertThat(consumedPaths)
                .hasSize(2);

        final Snapshot snapshot = DirectorySnapshot.of(consumerDir);
        LOGGER.info("snapshot of {}\n{}", consumerDir, snapshot);

        final List<Path> consumedZips = snapshot.pathSnapshots()
                .stream()
                .map(PathSnapshot::path)
                .map(path -> consumerDir.resolve(path).toAbsolutePath())
                .filter(path -> path.getFileName().toString().endsWith(".zip"))
                .toList();
        assertThat(consumedZips)
                .hasSize(2);

        for (final Path consumedZip : consumedZips) {
            final ProxyZipSnapshot proxyZipSnapshot = ProxyZipSnapshot.of(consumedZip);
            LOGGER.info("Contents of {}\n{}", consumedZip, proxyZipSnapshot);
        }

        final List<Path> subDirs = FileUtil.listChildDirs(consumerDir);
        assertThat(subDirs)
                .hasSize(2);

        for (final Path subDir : subDirs) {
            LOGGER.info("subDir: {}", subDir);

            final AttributeMap meta = TestDataUtil.getMeta(subDir);
            assertThat(meta)
                    .isNotNull();

            final List<ZipEntryGroup> entries = TestDataUtil.getEntries(subDir);
            assertThat(entries)
                    .isNotNull()
                    .isNotEmpty();

            final Path zipFilePath = TestDataUtil.getZipFile(subDir);

            final ProxyZipSnapshot proxyZipSnapshot = ProxyZipSnapshot.of(zipFilePath);
            final String feed = meta.get(StandardHeaderArguments.FEED);
            final String type = meta.get(StandardHeaderArguments.TYPE);
            final FeedKey feedKey = FeedKey.of(feed, type);
            if (feedKeys.contains(feedKey)) {
                // Two item groups per zip
                assertThat(entries)
                        .hasSize(2);
                // Entries in entries file are all for right feedKey
                assertThat(
                        entries.stream()
                                .map(ZipEntryGroup::getFeedKey)
                                .allMatch(aFeedKey -> aFeedKey.equals(feedKey)))
                        .isTrue();

                // Two item groups per zip
                assertThat(proxyZipSnapshot.getItemGroups())
                        .hasSize(2);
                // Meta entries in zip file are all for right feedKey
                assertThat(proxyZipSnapshot.getItemGroups()
                        .stream()
                        .map(ItemGroup::meta)
                        .map(Item::content)
                        .allMatch(attrMap -> feedKey.feed().equals(attrMap.get(StandardHeaderArguments.FEED))
                                             && feedKey.type().equals(attrMap.get(StandardHeaderArguments.TYPE))))
                        .isTrue();
            } else {
                Assertions.fail("Unexpected feedKey in meta " + feedKey);
            }
        }
    }

    @Test
    void test_twoFeeds_twoTypes(@TempDir final Path tempDir) throws IOException {
        final Path workingDir = tempDir.resolve("working");
        final Path consumerDir = tempDir.resolve("consumer");
        Files.createDirectories(workingDir);
        Files.createDirectories(consumerDir);

        final NumberedDirProvider numberedDirProvider = new NumberedDirProvider(workingDir);

        final Path zipDir = Files.createTempDirectory("temp");
        final FileGroup fileGroup = new FileGroup(zipDir);

        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put("Foo", "Bar");
        final Set<FeedKey> feedKeys = Set.of(FEED_KEY_1_1, FEED_KEY_1_2, FEED_KEY_2_1, FEED_KEY_2_2);
        TestDataUtil.writeZip(
                new FileGroup(zipDir),
                2,
                attributeMap,
                feedKeys,
                null);
        final Path testZipFile = fileGroup.getZip();
        AttributeMapUtil.write(attributeMap, fileGroup.getMeta());

        assertThat(testZipFile)
                .isRegularFile();
        assertThat(fileGroup.getParentDir())
                .isDirectory()
                .isNotEmptyDirectory();

        final List<Path> consumedPaths = new ArrayList<>();
        final AtomicLong counter = new AtomicLong();
        ZipSplitter.splitZipByFeed(
                fileGroup.getParentDir(),
                numberedDirProvider,
                consumedPath -> {
                    try {
                        final Path destPath = consumerDir.resolve(
                                NumericFileNameUtil.create(counter.incrementAndGet()));
                        Files.move(consumedPath, destPath);
                        consumedPaths.add(destPath);
                    } catch (final IOException ex) {
                        throw new RuntimeException(ex);
                    }
                });

        assertThat(fileGroup.getParentDir())
                .doesNotExist();
        assertThat(workingDir)
                .isEmptyDirectory();
        assertThat(consumerDir)
                .isNotEmptyDirectory();
        // Four feed keys, so four zips
        assertThat(consumedPaths)
                .hasSize(4);

        final Snapshot snapshot = DirectorySnapshot.of(consumerDir);
        LOGGER.info("snapshot of {}\n{}", consumerDir, snapshot);

        final List<Path> consumedZips = snapshot.pathSnapshots()
                .stream()
                .map(PathSnapshot::path)
                .map(path -> consumerDir.resolve(path).toAbsolutePath())
                .filter(path -> path.getFileName().toString().endsWith(".zip"))
                .toList();
        assertThat(consumedZips)
                .hasSize(4);

        for (final Path consumedZip : consumedZips) {
            final ProxyZipSnapshot proxyZipSnapshot = ProxyZipSnapshot.of(consumedZip);
            LOGGER.info("Contents of {}\n{}", consumedZip, proxyZipSnapshot);
        }

        final List<Path> subDirs = FileUtil.listChildDirs(consumerDir);
        assertThat(subDirs)
                .hasSize(4);

        for (final Path subDir : subDirs) {
            LOGGER.info("subDir: {}", subDir);

            final AttributeMap meta = TestDataUtil.getMeta(subDir);
            assertThat(meta)
                    .isNotNull();

            final List<ZipEntryGroup> entries = TestDataUtil.getEntries(subDir);
            assertThat(entries)
                    .isNotNull()
                    .isNotEmpty();

            final Path zipFilePath = TestDataUtil.getZipFile(subDir);

            final ProxyZipSnapshot proxyZipSnapshot = ProxyZipSnapshot.of(zipFilePath);
            final String feed = meta.get(StandardHeaderArguments.FEED);
            final String type = meta.get(StandardHeaderArguments.TYPE);
            final FeedKey feedKey = FeedKey.of(feed, type);

            if (feedKeys.contains(feedKey)) {
                // Two item groups per zip
                assertThat(entries)
                        .hasSize(2);
                // Entries in entries file are all for right feedKey
                assertThat(
                        entries.stream()
                                .map(ZipEntryGroup::getFeedKey)
                                .allMatch(aFeedKey -> aFeedKey.equals(feedKey)))
                        .isTrue();

                // Two item groups per zip
                assertThat(proxyZipSnapshot.getItemGroups())
                        .hasSize(2);
                // Meta entries in zip file are all for right feedKey
                assertThat(proxyZipSnapshot.getItemGroups()
                        .stream()
                        .map(ItemGroup::meta)
                        .map(Item::content)
                        .allMatch(attrMap -> feedKey.feed().equals(attrMap.get(StandardHeaderArguments.FEED))
                                             && feedKey.type().equals(attrMap.get(StandardHeaderArguments.TYPE))))
                        .isTrue();
            } else {
                Assertions.fail("Unexpected feedKey in meta " + feedKey);
            }
        }
    }

    @Test
    void test_twoFeeds_twoTypes_oneDisallowed(@TempDir final Path tempDir) throws IOException {
        final Path workingDir = tempDir.resolve("working");
        final Path consumerDir = tempDir.resolve("consumer");
        Files.createDirectories(workingDir);
        Files.createDirectories(consumerDir);

        final NumberedDirProvider numberedDirProvider = new NumberedDirProvider(workingDir);

        final Path zipDir = Files.createTempDirectory("temp");
        final FileGroup fileGroup = new FileGroup(zipDir);

        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put("Foo", "Bar");
        final Set<FeedKey> feedKeys = Set.of(FEED_KEY_1_1, FEED_KEY_1_2, FEED_KEY_2_1, FEED_KEY_2_2);
        // Only 3 feedKeys are allowed, so only those 3 should end up in the consumed zips
        final Set<FeedKey> allowedFeedKeys = Set.of(FEED_KEY_1_1, FEED_KEY_1_2, FEED_KEY_2_1);
        TestDataUtil.writeZip(
                new FileGroup(zipDir),
                2,
                attributeMap,
                feedKeys,
                allowedFeedKeys);
        final Path testZipFile = fileGroup.getZip();
        AttributeMapUtil.write(attributeMap, fileGroup.getMeta());

        assertThat(testZipFile)
                .isRegularFile();
        assertThat(fileGroup.getParentDir())
                .isDirectory()
                .isNotEmptyDirectory();

        final List<Path> consumedPaths = new ArrayList<>();
        final AtomicLong counter = new AtomicLong();
        ZipSplitter.splitZipByFeed(
                fileGroup.getParentDir(),
                numberedDirProvider,
                consumedPath -> {
                    try {
                        final Path destPath = consumerDir.resolve(
                                NumericFileNameUtil.create(counter.incrementAndGet()));
                        Files.move(consumedPath, destPath);
                        consumedPaths.add(destPath);
                    } catch (final IOException ex) {
                        throw new RuntimeException(ex);
                    }
                });

        assertThat(fileGroup.getParentDir())
                .doesNotExist();
        assertThat(workingDir)
                .isEmptyDirectory();
        assertThat(consumerDir)
                .isNotEmptyDirectory();
        // Three feed keys, so three zips
        assertThat(consumedPaths)
                .hasSize(3);

        final Snapshot snapshot = DirectorySnapshot.of(consumerDir);
        LOGGER.info("snapshot of {}\n{}", consumerDir, snapshot);

        final List<Path> consumedZips = snapshot.pathSnapshots()
                .stream()
                .map(PathSnapshot::path)
                .map(path -> consumerDir.resolve(path).toAbsolutePath())
                .filter(path -> path.getFileName().toString().endsWith(".zip"))
                .toList();
        assertThat(consumedZips)
                .hasSize(3);

        for (final Path consumedZip : consumedZips) {
            final ProxyZipSnapshot proxyZipSnapshot = ProxyZipSnapshot.of(consumedZip);
            LOGGER.info("Contents of {}\n{}", consumedZip, proxyZipSnapshot);
        }

        final List<Path> subDirs = FileUtil.listChildDirs(consumerDir);
        assertThat(subDirs)
                .hasSize(3);

        for (final Path subDir : subDirs) {
            LOGGER.info("subDir: {}", subDir);

            final AttributeMap meta = TestDataUtil.getMeta(subDir);
            assertThat(meta)
                    .isNotNull();

            final List<ZipEntryGroup> entries = TestDataUtil.getEntries(subDir);
            assertThat(entries)
                    .isNotNull()
                    .isNotEmpty();

            final Path zipFilePath = TestDataUtil.getZipFile(subDir);

            final ProxyZipSnapshot proxyZipSnapshot = ProxyZipSnapshot.of(zipFilePath);
            final String feed = meta.get(StandardHeaderArguments.FEED);
            final String type = meta.get(StandardHeaderArguments.TYPE);
            final FeedKey feedKey = FeedKey.of(feed, type);

            if (allowedFeedKeys.contains(feedKey)) {
                // Two item groups per zip
                assertThat(entries)
                        .hasSize(2);
                // Entries in entries file are all for right feedKey
                assertThat(
                        entries.stream()
                                .map(ZipEntryGroup::getFeedKey)
                                .allMatch(aFeedKey -> aFeedKey.equals(feedKey)))
                        .isTrue();

                // Two item groups per zip
                assertThat(proxyZipSnapshot.getItemGroups())
                        .hasSize(2);
                // Meta entries in zip file are all for right feedKey
                assertThat(proxyZipSnapshot.getItemGroups()
                        .stream()
                        .map(ItemGroup::meta)
                        .map(Item::content)
                        .allMatch(attrMap -> feedKey.feed().equals(attrMap.get(StandardHeaderArguments.FEED))
                                             && feedKey.type().equals(attrMap.get(StandardHeaderArguments.TYPE))))
                        .isTrue();
            } else {
                Assertions.fail("Unexpected feedKey in meta " + feedKey);
            }
        }
    }
}
