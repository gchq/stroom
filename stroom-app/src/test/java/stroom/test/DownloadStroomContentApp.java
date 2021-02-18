package stroom.test;

import stroom.test.common.util.test.ContentPackDownloader;
import stroom.util.io.FileUtil;

import java.nio.file.Paths;

public class DownloadStroomContentApp {

    public static void main(final String[] args) {
        if (args.length != 3) {
            throw new RuntimeException("Expected 1 argument that is the location of the config.");
        }
        final String contentPacksDefinition = args[0];
        final String contentPackDownload = args[1];
        final String contentPackImport = args[2];
        ContentPackDownloader.downloadPacks(
                Paths.get(FileUtil.replaceHome(contentPacksDefinition)),
                Paths.get(FileUtil.replaceHome(contentPackDownload)),
                Paths.get(FileUtil.replaceHome(contentPackImport)));
    }
}
