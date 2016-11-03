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

package stroom.util;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.net.ssl.HttpsURLConnection;

import org.apache.commons.lang.math.RandomUtils;

import stroom.util.io.StreamUtil;

/**
 * <p>
 * Runnable Java Program that can act as a test client to the data feed.
 * </p>
 *
 * <p>
 * Duplicate class with no reuse to allow copy to be placed in documentation.
 * </p>
 */
public final class ErrorDataFeedClient {
    private static final String ARG_URL = "url";
    private static final String ARG_COMPRESSION = "compression";
    private static final String ZIP = "zip";
    private static final String GZIP = "gzip";

    private static final String CHUNK_SIZE = "chunkSize";
    private static final String BUFFER_SIZE = "bufferSize";
    private static final String WRITE_SIZE = "writeSize";

    private ErrorDataFeedClient() {
        // Private constructor.
    }

    private static byte[] buildDataBuffer(final int size) {
        final StringBuilder builder = new StringBuilder();
        while (builder.length() < size) {
            builder.append(((short) (RandomUtils.nextInt(20))) + 'a');
        }
        return builder.toString().getBytes(StreamUtil.DEFAULT_CHARSET);

    }

    public static void main(final String[] args) {
        try {
            final HashMap<String, String> argsMap = new HashMap<String, String>();
            for (int i = 0; i < args.length; i++) {
                final String[] split = args[i].split("=");
                if (split.length > 1) {
                    argsMap.put(split[0], split[1]);
                } else {
                    argsMap.put(split[0], "");
                }
            }

            final String urlS = argsMap.get(ARG_URL);

            final long startTime = System.currentTimeMillis();

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

            // Also add all our command options
            for (final Entry<String, String> entry : argsMap.entrySet()) {
                connection.addRequestProperty(entry.getKey(), entry.getValue());
            }

            final int bufferSize = argsMap.containsKey(BUFFER_SIZE) ? Integer.parseInt(argsMap.get(BUFFER_SIZE)) : 100;
            final int chunkSize = argsMap.containsKey(CHUNK_SIZE) ? Integer.parseInt(argsMap.get(CHUNK_SIZE)) : 100;
            final int writeSize = argsMap.containsKey(WRITE_SIZE) ? Integer.parseInt(argsMap.get(WRITE_SIZE)) : 1000;

            connection.setChunkedStreamingMode(chunkSize);
            connection.connect();

            OutputStream out = connection.getOutputStream();
            // Using Zip Compression we just have 1 file (called 1)
            if (ZIP.equalsIgnoreCase(argsMap.get(ARG_COMPRESSION))) {
                final ZipOutputStream zout = new ZipOutputStream(out);
                zout.putNextEntry(new ZipEntry("1"));
                out = zout;
                System.out.println("Using ZIP");
            }
            if (GZIP.equalsIgnoreCase(argsMap.get(ARG_COMPRESSION))) {
                out = new GZIPOutputStream(out);
                System.out.println("Using GZIP");
            }

            // Write the output
            final byte[] buffer = buildDataBuffer(bufferSize);
            int writtenSize = 0;
            while (writtenSize < writeSize) {
                out.write(buffer, 0, bufferSize);
                out.flush();
                writtenSize += bufferSize;
            }
            if (writtenSize >= writeSize) {
                throw new IOException("Test Error");
            }

            out.close();

            final int response = connection.getResponseCode();
            final String msg = connection.getResponseMessage();

            connection.disconnect();

            System.out.println(
                    "Client Got Response " + response + " in " + (System.currentTimeMillis() - startTime) + "ms");
            if (msg != null && msg.length() > 0) {
                System.out.println(msg);
            }

        } catch (final Exception ex) {
            ex.printStackTrace();
        }
    }
}
