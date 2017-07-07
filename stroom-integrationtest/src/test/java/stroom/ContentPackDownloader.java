package stroom;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.shared.Version;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class ContentPackDownloader {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContentPackDownloader.class);

    public static final String URL_PREFIX = "https://github.com/gchq/stroom-content/releases/download/";

    public enum ConflictMode {
        OVERWRITE_EXISTING,
        KEEP_EXISTING
    }


    public static Path downloadContentPack(final String contentPackName, final Version version, final Path destDir) {
        return downloadContentPack(contentPackName, version, destDir, ConflictMode.KEEP_EXISTING);
    }

    public static Path downloadContentPack(final String contentPackName, final Version version, final Path destDir, final ConflictMode conflictMode) {
        Preconditions.checkNotNull(contentPackName);
        Preconditions.checkNotNull(version);
        Preconditions.checkNotNull(destDir);
        Preconditions.checkNotNull(conflictMode);
        Preconditions.checkArgument(Files.isDirectory(destDir));

        Path destFilePath = buildDestFilePath(contentPackName, version, destDir);
        boolean destFileExists = Files.isRegularFile(destFilePath);

        if (destFileExists && conflictMode.equals(ConflictMode.KEEP_EXISTING)) {
            LOGGER.debug("Requested contentPack {} already exists in {}, keeping existing",
                    contentPackName,
                    destFilePath.toAbsolutePath().toString());
            return destFilePath;
        }

        if (destFileExists && conflictMode.equals(ConflictMode.OVERWRITE_EXISTING)) {
            LOGGER.debug("Requested contentPack {} already exists in {}, overwriting existing",
                    contentPackName,
                    destFilePath.toAbsolutePath().toString());
            try {
                Files.delete(destFilePath);
            } catch (IOException e) {
                throw new RuntimeException(String.format("Unable to remove existing content pack %s",
                        destFilePath.toAbsolutePath().toString()), e);
            }
        }

        URL fileUrl = buildFileUrl(contentPackName, version);
        LOGGER.info("Downloading contentPack {} from {} to {}",
                contentPackName,
                fileUrl.toString(),
                destFilePath.toAbsolutePath().toString());

        downloadFile(fileUrl, destFilePath);

        return destFilePath;
    }

    private static String buildFilename(final String contentPackName, final Version version) {
        return buildReleaseName(contentPackName, version) + ".zip";
    }

    private static String buildReleaseName(final String contentPackName, final Version version) {
        return contentPackName + "-v" + version.toString();
    }

    private static Path buildDestFilePath(final String contentPackName, final Version version, final Path destDir) {
        String filename = buildFilename(contentPackName, version);
        return destDir.resolve(filename);
    }

    private static URL buildFileUrl(final String contentPackName, final Version version) {
        String releaseName = buildReleaseName(contentPackName, version);
        String fileName = buildFilename(contentPackName, version);

        String urlStr = URL_PREFIX + releaseName + "/" + fileName;

        try {
            return new URL(urlStr);
        } catch (MalformedURLException e) {
            throw new RuntimeException(String.format("Url {} for content pack {} and version {} is badly formed",
                    urlStr, contentPackName, version.toString()), e);
        }
    }

    private static boolean isRedirected(Map<String, List<String>> header) {
        for (String hv : header.get(null)) {
            if (hv.contains(" 301 ")
                    || hv.contains(" 302 ")) return true;
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
            InputStream input = http.getInputStream();
            byte[] buffer = new byte[4096];
            int n = -1;

            try (OutputStream output = new FileOutputStream(destFilename.toFile())) {

                while ((n = input.read(buffer)) != -1) {
                    output.write(buffer, 0, n);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(String.format("Error downloading url %s to %s",
                    fileUrl.toString(), destFilename.toAbsolutePath().toString()), e);
        }
    }
}
