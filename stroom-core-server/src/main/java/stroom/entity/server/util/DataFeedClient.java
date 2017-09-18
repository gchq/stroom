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

package stroom.entity.server.util;


import stroom.util.ArgsUtil;

import javax.net.ssl.HttpsURLConnection;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * <p>
 * Runnable Java Program that can act as a test client to the data feed.
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

    public static void main(final String[] args) throws Exception {
        Map<String, String> argsMap = ArgsUtil.parse(args);

        String urlS = argsMap.get(ARG_URL);
        String inputFileS = argsMap.get(ARG_INPUTFILE);

        long startTime = System.currentTimeMillis();

        System.out.println("Using url=" + urlS + " and inputFile=" + inputFileS);

        Path inputFile = Paths.get(inputFileS);

        URL url = new URL(urlS);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        if (connection instanceof HttpsURLConnection) {
            ((HttpsURLConnection) connection).setHostnameVerifier((arg0, arg1) -> true);
        }
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/audit");
        connection.setDoOutput(true);
        connection.setDoInput(true);

        // Also add all our command options
        for (String arg : argsMap.keySet()) {
            connection.addRequestProperty(arg, argsMap.get(arg));
        }

        // Here we allow for the input file already being compressed
        if (argsMap.containsKey(ARG_INPUT_COMPRESSION)) {
            connection.addRequestProperty(ARG_COMPRESSION, argsMap.get(ARG_INPUT_COMPRESSION));
        }

        connection.connect();

        try (final InputStream inputStream = Files.newInputStream(inputFile)) {
            OutputStream out = connection.getOutputStream();
            // Using Zip Compression we just have 1 file (called 1)
            if (ZIP.equalsIgnoreCase(argsMap.get(ARG_COMPRESSION))) {
                ZipOutputStream zout = new ZipOutputStream(out);
                zout.putNextEntry(new ZipEntry("1"));
                out = zout;
                System.out.println("Using ZIP");
            }
            if (GZIP.equalsIgnoreCase(argsMap.get(ARG_COMPRESSION))) {
                out = new GZIPOutputStream(out);
                System.out.println("Using GZIP");
            }

            // Write the output
            byte[] buffer = new byte[BUFFER_SIZE];
            int readSize;
            while ((readSize = inputStream.read(buffer)) != -1) {
                out.write(buffer, 0, readSize);
            }

            out.flush();
            out.close();
        }

        int response = connection.getResponseCode();
        String msg = connection.getResponseMessage();

        connection.disconnect();

        System.out
                .println("Client Got Response " + response + " in " + (System.currentTimeMillis() - startTime) + "ms");
        if (msg != null && msg.length() > 0) {
            System.out.println(msg);
        }

    }
}
