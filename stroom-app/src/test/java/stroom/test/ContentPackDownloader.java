package stroom.test;

import stroom.content.ContentPack;
import stroom.util.io.FileUtil;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

public class ContentPackDownloader extends AbstractContentDownloader {

    public static final String URL_PREFIX = "https://github.com/gchq/stroom-content/releases/download/";
    private static final Logger LOGGER = LoggerFactory.getLogger(ContentPackDownloader.class);

    public static Path downloadContentPack(final ContentPack contentPack, final Path destDir) {
        return downloadContentPack(contentPack, destDir, ConflictMode.KEEP_EXISTING);
    }

    public static Path downloadContentPack(final ContentPack contentPack,
                                           final Path destDir,
                                           final ConflictMode conflictMode) {
        Preconditions.checkNotNull(contentPack);
        Preconditions.checkNotNull(destDir);
        Preconditions.checkNotNull(conflictMode);
        Preconditions.checkArgument(Files.isDirectory(destDir));

        Path destFilePath = buildDestFilePath(contentPack.getNameAsStr(), contentPack.getVersion(), destDir);
        boolean destFileExists = Files.isRegularFile(destFilePath);

        if (destFileExists && conflictMode.equals(ConflictMode.KEEP_EXISTING)) {
            LOGGER.debug("Requested contentPack {} already exists in {}, keeping existing",
                    contentPack.getNameAsStr(),
                    FileUtil.getCanonicalPath(destFilePath));
            return destFilePath;
        }

        if (destFileExists && conflictMode.equals(ConflictMode.OVERWRITE_EXISTING)) {
            LOGGER.debug("Requested contentPack {} already exists in {}, overwriting existing",
                    contentPack.getNameAsStr(),
                    FileUtil.getCanonicalPath(destFilePath));
            try {
                Files.delete(destFilePath);
            } catch (final IOException e) {
                throw new UncheckedIOException(String.format("Unable to remove existing content pack %s",
                        FileUtil.getCanonicalPath(destFilePath)), e);
            }
        }

        URL fileUrl = buildFileUrl(contentPack.getNameAsStr(), contentPack.getVersion());
        LOGGER.info("Downloading contentPack {} from {} to {}",
                contentPack.getNameAsStr(),
                fileUrl.toString(),
                FileUtil.getCanonicalPath(destFilePath));

        downloadFile(fileUrl, destFilePath);

        return destFilePath;
    }

}
