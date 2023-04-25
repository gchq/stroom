package stroom.proxy.app;

import stroom.data.zip.StroomZipFileType;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.app.forwarder.ForwardConfig;
import stroom.proxy.app.forwarder.ForwardFileConfig;
import stroom.proxy.repo.store.FileSet;
import stroom.proxy.repo.store.SequentialFileStore;
import stroom.test.common.TestUtil;
import stroom.util.NullSafe;
import stroom.util.io.PathCreator;
import stroom.util.logging.LogUtil;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.io.FilenameUtils;
import org.assertj.core.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Stream;
import javax.inject.Inject;

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
                "My forward file",
                "forward_dest");
    }

    /**
     * A count of all the meta files in the {@link ForwardFileConfig} locations.
     */
    private long getForwardFileMetaCount(final Config config) {
        final List<ForwardConfig> forwardConfigs = NullSafe.getOrElseGet(
                config,
                Config::getProxyConfig,
                ProxyConfig::getForwardDestinations,
                Collections::emptyList);

        if (!forwardConfigs.isEmpty()) {
            return forwardConfigs.stream()
                    .filter(forwardConfig -> forwardConfig instanceof ForwardFileConfig)
                    .map(ForwardFileConfig.class::cast)
                    .mapToLong(forwardConfig -> {
                        if (!forwardConfig.getPath().isBlank()) {
                            try (Stream<Path> pathStream = Files.walk(
                                    pathCreator.toAppPath(forwardConfig.getPath()))) {
                                return pathStream
                                        .filter(path -> path.toString().endsWith(".meta"))
                                        .count();
                            } catch (IOException e) {
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
     */
    private List<ForwardFileItem> getForwardFiles(final Config config) {
        final List<ForwardConfig> forwardConfigs = NullSafe.getOrElseGet(
                config,
                Config::getProxyConfig,
                ProxyConfig::getForwardDestinations,
                Collections::emptyList);

        return forwardConfigs.stream()
                .filter(forwardConfig -> forwardConfig instanceof ForwardFileConfig)
                .map(ForwardFileConfig.class::cast)
                .flatMap(forwardFileConfig -> {
                    final Path forwardDir = pathCreator.toAppPath(forwardFileConfig.getPath());
                    final SequentialFileStore sequentialFileStore = new SequentialFileStore(() -> forwardDir);
                    int id = 1;
                    final List<ForwardFileItem> forwardFileItems = new ArrayList<>();
                    while (true) {
                        final FileSet fileSet = sequentialFileStore.getStoreFileSet(id);
                        if (!Files.exists(fileSet.getMeta())) {
                            LOGGER.info("id {} does not exist. dir: {}", id, fileSet.getDir());
                            break;
                        }
                        final String zipFileName = fileSet.getZipFileName();
                        final String baseName = zipFileName.substring(0, zipFileName.indexOf('.'));
                        final List<ZipItem> zipItems = new ArrayList<>();
                        final String metaContent;
                        try {
                            metaContent = Files.readString(fileSet.getMeta());

                            try (final ZipFile zipFile = new ZipFile(Files.newByteChannel(fileSet.getZip()))) {
                                final Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
                                while (entries.hasMoreElements()) {
                                    final ZipArchiveEntry entry = entries.nextElement();
                                    if (!entry.isDirectory()) {
                                        final String zipEntryName = entry.getName();
                                        final String zipEntryBaseName = zipEntryName.substring(
                                                0, zipEntryName.indexOf('.'));
                                        final String zipEntryExt = FilenameUtils.getExtension(zipEntryName);
                                        final StroomZipFileType zipEntryType =
                                                StroomZipFileType.fromExtension("." + zipEntryExt);
                                        final String zipEntryContent = new String(
                                                zipFile.getInputStream(entry).readAllBytes(),
                                                StandardCharsets.UTF_8);
                                        zipItems.add(new ZipItem(
                                                zipEntryType,
                                                zipEntryBaseName,
                                                zipEntryContent));
                                    }
                                }
                            }
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                        id++;
                        forwardFileItems.add(new ForwardFileItem(
                                zipFileName,
                                baseName,
                                metaContent,
                                zipItems));
                    }
                    return forwardFileItems.stream();
                })
                .toList();
    }


    void assertFileContents(final Config config) {
        assertFileContents(config, 4);
    }

    void assertFileContents(final Config config, final int count) {
        TestUtil.waitForIt(
                () -> getForwardFileMetaCount(config),
                (long) count,
                () -> "Forwarded file pairs count",
                Duration.ofSeconds(10),
                Duration.ofMillis(100),
                Duration.ofSeconds(1));

        final List<ForwardFileItem> forwardFileItems = getForwardFiles(config);

        // Check number of forwarded files.
        Assertions.assertThat(forwardFileItems).hasSize(count);

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
        Arrays.fill(sizes, 7);
        sizes[sizes.length - 1] = 3;
        sizes[sizes.length - 2] = 3;

        Assertions.assertThat(forwardFileItems.stream()
                        .map(forwardFileItem -> forwardFileItem.zipItems().size())
                        .toList())
                .containsExactlyInAnyOrder(sizes);

        // Check zip contents.
        final List<String> expectedFiles = List.of(
                "001.mf",
                "001.meta",
                "001.dat",
                "002.meta",
                "002.dat",
                "003.meta",
                "003.dat",
                "004.meta",
                "004.dat");
        assertForwardFileItemContent(forwardFileItems, expectedFiles);
    }


    private void assertForwardFileItemContent(final List<ForwardFileItem> forwardFileItems,
                                              final List<String> expectedFiles) {
        forwardFileItems.forEach(forwardFileItem -> {
            for (int i = 0; i < forwardFileItem.zipItems().size(); i++) {
                final ZipItem zipItem = forwardFileItem.zipItems().get(i);
                final String expectedName = expectedFiles.get(i);
                final String actualName = zipItem.baseName() + zipItem.type().getExtension();
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
