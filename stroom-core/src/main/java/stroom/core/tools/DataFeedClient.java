/*
 * Copyright 2016 Crown Copyright
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.net.ssl.HttpsURLConnection;

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
    public static void main(final String[] args) {
        try {
            final HashMap<String, String> argsMap = new HashMap<>();
            for (int i = 0; i < args.length; i++) {
                final String[] split = args[i].split("=");
                if (split.length > 1) {
                    argsMap.put(split[0], split[1]);
                } else {
                    argsMap.put(split[0], "");
                }
            }

            final String urlS = argsMap.get(ARG_URL);
            final String inputFileS = argsMap.get(ARG_INPUTFILE);

            final long startTime = System.currentTimeMillis();

            System.out.println("Using url=" + urlS + " and inputFile=" + inputFileS);

            final Path inputFile = Paths.get(inputFileS);

            final URL url = new URL(urlS);
            final HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            if (connection instanceof HttpsURLConnection) {
                ((HttpsURLConnection) connection).setHostnameVerifier((arg0, arg1) -> {
                    System.out.println("HostnameVerifier - " + arg0);
                    return true;
                });
            }
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/audit");
            connection.setDoOutput(true);
            connection.setDoInput(true);

            // Also add all our command options.
            argsMap.entrySet().forEach(entry -> connection.addRequestProperty(entry.getKey(), entry.getValue()));

            // Here we allow for the input file already being compressed.
            if (argsMap.containsKey(ARG_INPUT_COMPRESSION)) {
                connection.addRequestProperty(ARG_COMPRESSION, argsMap.get(ARG_INPUT_COMPRESSION));
            }

            connection.connect();

            try (final InputStream inputStream = Files.newInputStream(inputFile)) {
                // Using Zip Compression we just have 1 file (called 1)
                if (ZIP.equalsIgnoreCase(argsMap.get(ARG_COMPRESSION))) {
                    System.out.println("Using ZIP");
                    try (final ZipArchiveOutputStream out = ZipUtil.createOutputStream(connection.getOutputStream())) {
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
                            new GzipCompressorOutputStream(connection.getOutputStream())) {
                        // Write the output
                        StreamUtil.streamToStream(inputStream, out);
                    }
                } else {
                    try (final OutputStream out = connection.getOutputStream()) {
                        // Write the output
                        StreamUtil.streamToStream(inputStream, out);
                    }
                }
            }

            final int response = connection.getResponseCode();

            final String msg = connection.getResponseMessage();

            System.out.println(
                    "Client Got Response " + response + " in " + (System.currentTimeMillis() - startTime) + "ms");
            if (msg != null && !msg.isEmpty()) {
                System.out.println(msg);
            }

            System.out.println();
            System.out.println("RESPONSE HEADER");
            System.out.println("===============");

            for (final Map.Entry<String, List<String>> entry : connection.getHeaderFields().entrySet()) {
                final StringBuilder line = new StringBuilder();
                if (entry.getKey() != null) {
                    line.append(entry.getKey());
                    line.append(":");
                }
                boolean doneOne = false;
                for (final String val : entry.getValue()) {
                    if (doneOne) {
                        line.append(",");
                    }
                    line.append(val);
                    doneOne = true;
                }
                System.out.println(line);
            }

            System.out.println();
            System.out.println("RESPONSE BODY");
            System.out.println("=============");

            final String errR = StreamUtil.streamToString(connection.getErrorStream());
            if (errR != null) {
                System.out.println(errR);
            } else {
                final String outR = StreamUtil.streamToString(connection.getInputStream());
                if (outR != null) {
                    System.out.println(outR);
                }
            }

            connection.disconnect();

        } catch (final RuntimeException | IOException e) {
            e.printStackTrace();
        }
    }
}
