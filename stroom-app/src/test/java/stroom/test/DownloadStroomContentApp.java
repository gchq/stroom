package stroom.test;

import stroom.test.common.util.test.ContentPackZipDownloader;
import stroom.util.io.FileUtil;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Paths;

public class DownloadStroomContentApp {

    public static void main(final String[] args) {
        if (args.length != 3) {
            throw new RuntimeException("Expected 1 argument that is the location of the config.");
        }
        final String contentPacksDefinition = args[0];
        final String contentPackDownload = args[1];
        final String contentPackImport = args[2];
        try (final CloseableHttpClient httpClient = HttpClients.createDefault()) {
            ContentPackZipDownloader.downloadZipPacks(
                    Paths.get(FileUtil.replaceHome(contentPacksDefinition)),
                    Paths.get(FileUtil.replaceHome(contentPackDownload)),
                    Paths.get(FileUtil.replaceHome(contentPackImport)),
                    httpClient);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
