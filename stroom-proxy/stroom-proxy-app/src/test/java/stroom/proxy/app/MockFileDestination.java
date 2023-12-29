package stroom.proxy.app;

import stroom.proxy.app.handler.FileGroup;
import stroom.proxy.app.handler.ForwardFileConfig;
import stroom.util.io.FileUtil;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class MockFileDestination {

    private final Path path;

    public MockFileDestination() {
        try {
            this.path = Files.createTempDirectory("test");
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public ForwardFileConfig getConfig() {
        return new ForwardFileConfig(
                true,
                "My forward file",
                FileUtil.getCanonicalPath(path));
    }

    /**
     * A count of all the meta files in the {@link ForwardFileConfig} locations.
     */
    public long getForwardFileMetaCount() {
        try (final Stream<Path> pathStream = Files.walk(path)) {
            return pathStream
                    .filter(path -> path.toString().endsWith(".meta"))
                    .count();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Get all the files in the directories specified in {@link ForwardFileConfig}.
     * The dir will contain .meta and .zip pairs. Each pair is one item in the returned list.
     */
    private List<Path> getForwardFiles() {
        try (final Stream<Path> pathStream = Files.walk(path)) {
            return pathStream
                    .filter(path -> path.toString().endsWith(".meta"))
                    .map(Path::getParent)
                    .distinct()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }




//    void assertFileContents(final Config config) {
//        assertFileContents(config, 4);
//    }
//
//    void assertFileContents(final Config config, final int count) {
//        TestUtil.waitForIt(
//                () -> getForwardFileMetaCount(config),
//                (long) count,
//                () -> "Forwarded file pairs count",
//                Duration.ofMinutes(1),
//                Duration.ofMillis(100),
//                Duration.ofSeconds(1));
//
//        final List<ForwardFileItem> forwardFileItems = getForwardFiles(config);
//
//        // Check number of forwarded files.
//        Assertions.assertThat(forwardFileItems).hasSize(count);
//
//        // Check feed names.
//        final String[] feedNames = new String[count];
//        for (int i = 0; i < count; i++) {
//            feedNames[i++] = TestConstants.FEED_TEST_EVENTS_1;
//            feedNames[i] = TestConstants.FEED_TEST_EVENTS_2;
//        }
//
//        Assertions.assertThat(forwardFileItems)
//                .extracting(forwardFileItem -> forwardFileItem.getMetaAttributeMap().get(StandardHeaderArguments.FEED))
//                .containsExactlyInAnyOrder(feedNames);
//
//        // Check zip content file count.
//        final Integer[] sizes = new Integer[count];
//        Arrays.fill(sizes, 7);
//        sizes[sizes.length - 1] = 3;
//        sizes[sizes.length - 2] = 3;
//
//        Assertions.assertThat(forwardFileItems.stream()
//                        .map(forwardFileItem -> forwardFileItem.zipItems().size())
//                        .toList())
//                .containsExactlyInAnyOrder(sizes);
//
//        // Check zip contents.
//        final List<String> expectedFiles = List.of(
//                "001.mf",
//                "001.meta",
//                "001.dat",
//                "002.meta",
//                "002.dat",
//                "003.meta",
//                "003.dat",
//                "004.meta",
//                "004.dat");
//        assertForwardFileItemContent(forwardFileItems, expectedFiles);
//    }


    private void assertForwardFileItemContent(final Path dir,
                                              final List<String> expectedFiles) {
        final FileGroup fileGroup = new FileGroup(dir);

        final Set<String> items = new HashSet<>();
        try (final ZipInputStream zipInputStream =
                new ZipInputStream(new BufferedInputStream(Files.newInputStream(fileGroup.getZip())))) {
            ZipEntry zipEntry = zipInputStream.getNextEntry();
            while (zipEntry != null) {
                items.add(zipEntry.getName());
                zipEntry = zipInputStream.getNextEntry();
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }

        expectedFiles.forEach(expected -> assertThat(items.remove(expected)).isTrue());
        assertThat(items.size()).isZero();
    }
}
