package stroom.test;

import stroom.content.ContentPack;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class ContentPackDownloader extends AbstractContentDownloader {
    public static final String URL_PREFIX = "https://github.com/gchq/stroom-content/releases/download/";
    private static final Logger LOGGER = LoggerFactory.getLogger(ContentPackDownloader.class);

    private static void download(final String url, final Path file) throws IOException {
        try (final InputStream in = new URL(url).openStream()) {
            Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void download(final String url,
                                 final Path contentPackDownloadDir,
                                 final Path contentPackImportDir) {
        try {
            final String filename = Paths.get(new URI(url).getPath()).getFileName().toString();
            final Path downloadFile = contentPackDownloadDir.resolve(filename);
            final Path importFile = contentPackImportDir.resolve(filename);
            if (Files.isRegularFile(downloadFile)) {
                LOGGER.info(url + " has already been downloaded");
            } else {
                LOGGER.info("Downloading " + url + " into " + FileUtil.getCanonicalPath(contentPackDownloadDir));
                download(url, downloadFile);
            }

            if (!Files.isRegularFile(importFile)) {
                LOGGER.info("Copying from " + downloadFile + " to " + importFile);
                StreamUtil.copyFile(downloadFile, importFile);
            }
        } catch (final IOException | URISyntaxException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private static void download(final String url,
                                 final Path contentPackDownloadDir,
                                 final Path contentPackImportDir,
                                 final String packName,
                                 final String packVer) {
        final String packUrl = url + packName + "-" + packVer + "/" + packName + "-" + packVer + ".zip";
        download(packUrl, contentPackDownloadDir, contentPackImportDir);
    }

    public static void downloadAllPacks(final String url,
                                        final Path contentPackDownloadDir,
                                        final Path contentPackImportDir) {
        download(url, contentPackDownloadDir, contentPackImportDir, "core-xml-schemas", "v2.2");
        download(url, contentPackDownloadDir, contentPackImportDir, "event-logging-xml-schema", "v3.4.2");
        download(url, contentPackDownloadDir, contentPackImportDir, "internal-dashboards", "v1.1");
        download(url, contentPackDownloadDir, contentPackImportDir, "internal-statistics-sql", "v2.1");
        download(url, contentPackDownloadDir, contentPackImportDir, "internal-statistics-stroom-stats", "v2.1");
        download(url, contentPackDownloadDir, contentPackImportDir, "standard-pipelines", "v0.2");
        download(url, contentPackDownloadDir, contentPackImportDir, "stroom-101", "v1.0");
        download(url, contentPackDownloadDir, contentPackImportDir, "stroom-logs", "v2.0-alpha.5");
        download(url, contentPackDownloadDir, contentPackImportDir, "template-pipelines", "v0.3");
        download("https://github.com/gchq/stroom-visualisations-dev/releases/download/v3.2.1/visualisations-production-v3.2.1.zip", contentPackDownloadDir, contentPackImportDir);
    }

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
