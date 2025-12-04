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

package stroom.core.tools;

import stroom.util.io.StreamUtil;
import stroom.util.zip.ZipUtil;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.io.entity.EntityTemplate;

import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>
 * Runnable Java Program that can act as a test client to the data feed.
 * </p>
 * <p>
 * <p>
 * Duplicate class with no reuse to allow copy to be placed in documentation.
 * </p>
 */
public final class DataFeedClient {

    private static final String ARG_URL = "url";
    private static final String ARG_INPUTFILE = "inputfile";
    private static final String ARG_COMPRESSION = "compression";
    private static final String ARG_INPUT_COMPRESSION = "inputcompression";
    private static final String ZIP = "zip";
    private static final String GZIP = "gzip";

    private static final int BUFFER_SIZE = 1024;

    private DataFeedClient() {
        // Private constructor.
    }

    /**
     * @param args program args
     */
    public static void main(final String[] args) throws Exception {
        try (final CloseableHttpClient httpClient = HttpClients.createDefault()) {
            final Map<String, String> argsMap = new HashMap<>();
            for (int i = 0; i < args.length; i++) {
                final String[] split = args[i].split("=");
                if (split.length > 1) {
                    argsMap.put(split[0], split[1]);
                } else {
                    argsMap.put(split[0], "");
                }
            }

            final String url = argsMap.get(ARG_URL);
            final String inputFileS = argsMap.get(ARG_INPUTFILE);

            final long startTime = System.currentTimeMillis();

            System.out.println("Using url=" + url + " and inputFile=" + inputFileS);

            final Path inputFile = Paths.get(inputFileS);

            final HttpPost httpPost = new HttpPost(url);
            httpPost.addHeader("Content-Type", "application/audit");

            // Also add all our command options.
            argsMap.forEach(httpPost::addHeader);

            // Here we allow for the input file already being compressed.
            if (argsMap.containsKey(ARG_INPUT_COMPRESSION)) {
                httpPost.addHeader(ARG_COMPRESSION, argsMap.get(ARG_INPUT_COMPRESSION));
            }

            httpPost.setEntity(new EntityTemplate(
                    -1,
                    ContentType.create("application/audit"),
                    null,
                    outputStream -> {
                        try (final InputStream inputStream = Files.newInputStream(inputFile)) {
                            // Using Zip Compression we just have 1 file (called 1)
                            if (ZIP.equalsIgnoreCase(argsMap.get(ARG_COMPRESSION))) {
                                System.out.println("Using ZIP");
                                try (final ZipArchiveOutputStream out = ZipUtil.createOutputStream(outputStream)) {
                                    out.putArchiveEntry(new ZipArchiveEntry("1"));
                                    try {
                                        // Write the output
                                        StreamUtil.streamToStream(inputStream, out);
                                    } finally {
                                        out.closeArchiveEntry();
                                    }
                                }

                            } else if (GZIP.equalsIgnoreCase(argsMap.get(ARG_COMPRESSION))) {
                                System.out.println("Using GZIP");
                                try (final GzipCompressorOutputStream out =
                                        new GzipCompressorOutputStream(outputStream)) {
                                    // Write the output
                                    StreamUtil.streamToStream(inputStream, out);
                                }
                            } else {
                                try (final BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(
                                        outputStream)) {
                                    StreamUtil.streamToStream(inputStream, bufferedOutputStream);
                                }
                            }
                        }
                    }));

            httpClient.execute(httpPost, response -> {

                final int code = response.getCode();
                final String msg = response.getReasonPhrase();

                System.out.println(
                        "Client Got Response " + code + " in " + (System.currentTimeMillis() - startTime) + "ms");
                if (msg != null && !msg.isEmpty()) {
                    System.out.println(msg);
                }

                System.out.println();
                System.out.println("RESPONSE HEADER");
                System.out.println("===============");

                for (final Header entry : response.getHeaders()) {
                    final String line = entry.getName() +
                                        ":" +
                                        entry.getValue();
                    System.out.println(line);
                }

                System.out.println();
                System.out.println("RESPONSE BODY");
                System.out.println("=============");

                final String errR = StreamUtil.streamToString(response.getEntity().getContent());
                if (errR != null) {
                    System.out.println(errR);
                }

                return code;
            });
        }
    }
}
