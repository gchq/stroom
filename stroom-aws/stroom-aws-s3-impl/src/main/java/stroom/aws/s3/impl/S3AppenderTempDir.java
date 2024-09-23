package stroom.aws.s3.impl;

import stroom.util.io.FileUtil;
import stroom.util.io.TempDirProvider;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Singleton
public class S3AppenderTempDir {

    private final Path tempDir;

    @Inject
    S3AppenderTempDir(final TempDirProvider tempDirProvider) {
        try {
            tempDir = tempDirProvider.get().resolve("s3_appender");
            Files.createDirectories(tempDir);
            FileUtil.deleteContents(tempDir);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Path createTempFile() {
        return tempDir.resolve(UUID.randomUUID() + ".tmp");
    }
}
