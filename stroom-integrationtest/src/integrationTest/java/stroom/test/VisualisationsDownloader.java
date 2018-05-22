package stroom.test;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.io.FileUtil;
import stroom.util.shared.Version;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

public class VisualisationsDownloader extends AbstractContentDownloader {

    private static final Logger LOGGER = LoggerFactory.getLogger(VisualisationsDownloader.class);

    private static final String URL_PREFIX = "https://github.com/gchq/stroom-visualisations-dev/releases/download/";
    private static final String RELEASE_PREFIX = "visualisations-production";

    public static Path downloadVisualisations(final Version version,
                                              final Path destDir) {
        return downloadVisualisations(version, destDir, ConflictMode.KEEP_EXISTING);
    }

    public static Path downloadVisualisations(final Version version,
                                              final Path destDir,
                                              final ConflictMode conflictMode) {
        Preconditions.checkNotNull(version);
        Preconditions.checkNotNull(destDir);
        Preconditions.checkNotNull(conflictMode);
        Preconditions.checkArgument(Files.isDirectory(destDir));

        Path destFilePath = buildDestFilePath(RELEASE_PREFIX, version, destDir);
        boolean destFileExists = Files.isRegularFile(destFilePath);

        if (destFileExists && conflictMode.equals(ConflictMode.KEEP_EXISTING)) {
            LOGGER.debug("Requested contentPack {} already exists in {}, keeping existing",
                    RELEASE_PREFIX,
                    FileUtil.getCanonicalPath(destFilePath));
            return destFilePath;
        }

        if (destFileExists && conflictMode.equals(ConflictMode.OVERWRITE_EXISTING)) {
            LOGGER.debug("Requested contentPack {} already exists in {}, overwriting existing",
                    RELEASE_PREFIX,
                    FileUtil.getCanonicalPath(destFilePath));
            try {
                Files.delete(destFilePath);
            } catch (final IOException e) {
                throw new UncheckedIOException(String.format("Unable to remove existing content pack %s",
                        FileUtil.getCanonicalPath(destFilePath)), e);
            }
        }

        URL fileUrl = buildFileUrl(RELEASE_PREFIX, version);
        LOGGER.info("Downloading contentPack {} from {} to {}",
                RELEASE_PREFIX,
                fileUrl.toString(),
                FileUtil.getCanonicalPath(destFilePath));

        downloadFile(fileUrl, destFilePath);

        return destFilePath;
    }

}
