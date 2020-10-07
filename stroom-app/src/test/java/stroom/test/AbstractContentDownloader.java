package stroom.test;

import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.shared.Version;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public abstract class AbstractContentDownloader {

    private static String buildFilename(final String contentPackName, final Version version) {
        return buildReleaseName(contentPackName, version) + ".zip";
    }

    private static String buildReleaseName(final String contentPackName, final Version version) {
        return contentPackName + "-v" + version.toString();
    }

    static Path buildDestFilePath(final String contentPackName, final Version version, final Path destDir) {
        String filename = buildFilename(contentPackName, version);
        return destDir.resolve(filename);
    }

    static URL buildFileUrl(final String contentPackName, final Version version) {
        String releaseName = buildReleaseName(contentPackName, version);
        String fileName = buildFilename(contentPackName, version);

        String urlStr = ContentPackDownloader.URL_PREFIX + releaseName + "/" + fileName;

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

    static void downloadFile(final URL fileUrl, final Path destFilename) {
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

            // Atomically move the downloaded file to the destination so that concurrent tests don't overwrite the file.
            Files.move(tempFile, destFilename);
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
