package stroom.test.common.util.test;

import stroom.content.ContentPack;
import stroom.content.ContentPackCollection;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LogUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

public class ContentPackDownloader {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContentPackDownloader.class);
    public static final String CONTENT_PACK_DOWNLOAD_DIR = "~/.stroom/contentPackDownload";

    private static void download(final String url,
                                 final Path file) throws IOException {
        try (final InputStream in = new URL(url).openStream()) {
            Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void download(final ContentPack contentPack,
                                 final Path contentPackDownloadDir,
                                 final Path contentPackImportDir) {

        final Path downloadFile = buildDestFilePath(contentPack, contentPackDownloadDir);
        final Path importFile = buildDestFilePath(contentPack, contentPackImportDir);

        ensureDirectoryExists(contentPackImportDir);

        if (!Files.isRegularFile(importFile)) {

            // Do the download (if it is not there already)
            downloadContentPack(contentPack, contentPackDownloadDir);

            LOGGER.info("Copying from " + downloadFile + " to " + importFile);
            try {
                StreamUtil.copyFile(downloadFile, importFile);
            } catch (IOException e) {
                throw new RuntimeException(LogUtil.message("Error copying {} to {}: {}",
                        downloadFile, importFile, e.getMessage()), e);
            }
        } else {
            LOGGER.info("File {} already exists", importFile.toAbsolutePath().normalize());
        }
    }

    private static void ensureDirectoryExists(final Path contentPackDownloadDir) {
        try {
            Files.createDirectories(contentPackDownloadDir);
        } catch (IOException e) {
            throw new RuntimeException(LogUtil.message("Error ensuring {} exists: {}",
                    contentPackDownloadDir, e.getMessage()), e);
        }
    }

    public static synchronized void downloadPacks(final Path contentPacksDefinition,
                                                  final Path contentPackDownloadDir,
                                                  final Path contentPackImportDir) {
        LOGGER.info("Downloading content packs using definition {}, with download dir {} and import dir {}",
                contentPacksDefinition.toAbsolutePath(),
                contentPackDownloadDir.toAbsolutePath(),
                contentPackImportDir.toAbsolutePath());
        try {
            Files.createDirectories(contentPackDownloadDir);
        } catch (IOException e) {
            LOGGER.error("Error ensuring {} exists: {}", contentPackDownloadDir.toAbsolutePath(), e.getMessage(), e);
        }

        try {
            Files.createDirectories(contentPackImportDir);
        } catch (IOException e) {
            LOGGER.error("Error ensuring {} exists: {}", contentPackImportDir.toAbsolutePath(), e.getMessage(), e);
        }

        try {
            final ObjectMapper mapper = new ObjectMapper();
            final ContentPackCollection contentPacks = mapper.readValue(
                    contentPacksDefinition.toFile(),
                    ContentPackCollection.class);
            contentPacks.getContentPacks().forEach(contentPack ->
                    download(contentPack, contentPackDownloadDir, contentPackImportDir));
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    public static Path downloadContentPack(final ContentPack contentPack, final Path destDir) {
        return downloadContentPack(contentPack, destDir, ConflictMode.KEEP_EXISTING);
    }

    /**
     * synchronized to avoid multiple test threads downloading the same pack concurrently
     */
    public static synchronized Path downloadContentPack(final ContentPack contentPack,
                                                        final Path destDir,
                                                        final ConflictMode conflictMode) {
        Preconditions.checkNotNull(contentPack);
        Preconditions.checkNotNull(destDir);
        Preconditions.checkNotNull(conflictMode);
        Preconditions.checkArgument(Files.isDirectory(destDir));

        final Path destFilePath = buildDestFilePath(contentPack, destDir);
        final Path lockFilePath = buildLockFilePath(contentPack, destDir);

        ensureDirectoryExists(destDir);

        FileUtil.doUnderFileLock(lockFilePath, () -> {
            // Now we have the lock for this zip file we can see if we need to download it or not

            boolean destFileExists = Files.isRegularFile(destFilePath);

            if (destFileExists && conflictMode.equals(ConflictMode.KEEP_EXISTING)) {
                LOGGER.debug("Requested contentPack {} already exists in {}, keeping existing",
                        contentPack.getName(),
                        FileUtil.getCanonicalPath(destFilePath));
            } else {
                if (destFileExists && conflictMode.equals(ConflictMode.OVERWRITE_EXISTING)) {
                    LOGGER.debug("Requested contentPack {} already exists in {}, overwriting existing",
                            contentPack.getName(),
                            FileUtil.getCanonicalPath(destFilePath));
                    try {
                        Files.delete(destFilePath);
                        destFileExists = false;
                    } catch (final IOException e) {
                        throw new UncheckedIOException(String.format("Unable to remove existing content pack %s",
                                FileUtil.getCanonicalPath(destFilePath)), e);
                    }
                }

                if (destFileExists) {
                    LOGGER.info("ContentPack {} already exists {}",
                            contentPack.getName(),
                            FileUtil.getCanonicalPath(destFilePath));
                } else {
                    final URL fileUrl = getUrl(contentPack);
                    LOGGER.info("Downloading contentPack {} from {} to {}",
                            contentPack.getName(),
                            fileUrl.toString(),
                            FileUtil.getCanonicalPath(destFilePath));

                    downloadFile(fileUrl, destFilePath);
                }
            }
        });

        return destFilePath;
    }

    private static URL getUrl(final ContentPack contentPack) {
        try {
            return new URL(contentPack.getUrl());
        } catch (MalformedURLException e) {
            throw new RuntimeException("Url " +
                    contentPack.getUrl() +
                    " for content pack " +
                    contentPack.getName() +
                    " and version " +
                    contentPack.getVersion() +
                    " is badly formed", e);
        }
    }

    static Path buildDestFilePath(final ContentPack contentPack, final Path destDir) {
        final String filename = contentPack.toFileName();
        return destDir.resolve(filename);
    }

    static Path buildLockFilePath(final ContentPack contentPack, final Path destDir) {
        final String filename = contentPack.toFileName();
        return destDir.resolve(filename + ".lock");
    }

    private static boolean isRedirected(Map<String, List<String>> header) {
        for (String hv : header.get(null)) {
            if (hv.contains(" 301 ")
                    || hv.contains(" 302 ")) {
                return true;
            }
        }
        return false;
    }

    private static void downloadFile(final URL fileUrl, final Path destFilename) {
        URL effectiveUrl = fileUrl;
        try {
            HttpURLConnection http = (HttpURLConnection) effectiveUrl.openConnection();
            Map<String, List<String>> header = http.getHeaderFields();
            while (isRedirected(header)) {
                effectiveUrl = new URL(header.get("Location").get(0));
                http = (HttpURLConnection) effectiveUrl.openConnection();
                header = http.getHeaderFields();
            }

            // Create a temp file as the download destination to avoid overwriting an existing file.
            final Path tempFile = Files.createTempFile("stroom", "download");
            try (final OutputStream fos = new BufferedOutputStream(Files.newOutputStream(tempFile))) {
                StreamUtil.streamToStream(http.getInputStream(), fos);
            }

            // Atomically move the downloaded file to the destination so that
            // concurrent tests don't overwrite the file.
            try {
                Files.move(tempFile, destFilename);
            } catch (FileAlreadyExistsException e) {
                // Don't see why we should get here as the methods are synchronized
                LOGGER.warn("Unable to move {} to {} as file already exists, ignoring the error.",
                        tempFile.toAbsolutePath().normalize(),
                        destFilename.toAbsolutePath().normalize(),
                        e);
            }

        } catch (final IOException e) {
            throw new UncheckedIOException(String.format("Error downloading url %s to %s",
                    fileUrl.toString(), FileUtil.getCanonicalPath(destFilename)), e);
        }
    }

    public enum ConflictMode {
        OVERWRITE_EXISTING,
        KEEP_EXISTING
    }
}
