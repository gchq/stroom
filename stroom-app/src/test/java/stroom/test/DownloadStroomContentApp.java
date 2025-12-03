/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
