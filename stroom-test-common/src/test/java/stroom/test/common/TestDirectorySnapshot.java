package stroom.test.common;

import stroom.test.common.DirectorySnapshot.Snapshot;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.stream.Collectors;

class TestDirectorySnapshot {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestDirectorySnapshot.class);

    @Test
    void test(@TempDir final Path path) throws IOException {
        createFile(path.resolve("file=_a0"));
        createFile(path.resolve("a1/file=_a1"));
        createFile(path.resolve("a1/a2/file_a2"));
        createFile(path.resolve("a1/a2/a3/file_a3"));

        createFile(path.resolve("file=_b0"));
        createFile(path.resolve("b1/file=_b1"));
        createFile(path.resolve("b1/b2/file_b2"));
        createFile(path.resolve("b1/b2/b3/file_b3"));

        final Snapshot snapshot1 = DirectorySnapshot.of(path, true);
        final Snapshot snapshot2 = DirectorySnapshot.of(path, true);

        LOGGER.info("snapshot1:\n{}", snapshot1.stream()
                .map(Objects::toString)
                .collect(Collectors.joining("\n")));

        Assertions.assertThat(snapshot2)
                .isEqualTo(snapshot1);
    }

    private static void createFile(final Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "This is " + file, StandardOpenOption.CREATE);
    }
}
