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
import org.apache.commons.lang3.RandomUtils;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityTemplate;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
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
public final class ErrorDataFeedClient {

    private static final String ARG_URL = "url";
    private static final String ARG_COMPRESSION = "compression";
    private static final String ZIP = "zip";
    private static final String GZIP = "gzip";

    private static final String BUFFER_SIZE = "bufferSize";
    private static final String WRITE_SIZE = "writeSize";

    private ErrorDataFeedClient() {
        // Private constructor.
    }

    private static byte[] buildDataBuffer(final int size) {
        final StringBuilder builder = new StringBuilder();
        while (builder.length() < size) {
            builder.append(((short) (RandomUtils.nextInt(0, 20))) + 'a');
        }
        return builder.toString().getBytes(StreamUtil.DEFAULT_CHARSET);

    }

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

            final long startTime = System.currentTimeMillis();

            final HttpPost httpPost = new HttpPost(url);
            httpPost.addHeader("Content-Type", "application/audit");

            // Also add all our command options.
            argsMap.forEach(httpPost::addHeader);

            final int bufferSize = argsMap.containsKey(BUFFER_SIZE)
                    ? Integer.parseInt(argsMap.get(BUFFER_SIZE))
                    : 100;
            final int writeSize = argsMap.containsKey(WRITE_SIZE)
                    ? Integer.parseInt(argsMap.get(WRITE_SIZE))
                    : 1000;


            httpPost.setEntity(new EntityTemplate(
                    -1,
                    ContentType.create("application/audit"),
                    null,
                    outputStream -> {
                        // Using Zip Compression we just have 1 file (called 1)
                        if (ZIP.equalsIgnoreCase(argsMap.get(ARG_COMPRESSION))) {
                            System.out.println("Using ZIP");
                            try (final ZipArchiveOutputStream out = ZipUtil.createOutputStream(outputStream)) {
                                out.putArchiveEntry(new ZipArchiveEntry("1"));
                                try {
                                    write(out, bufferSize, writeSize);
                                } finally {
                                    out.closeArchiveEntry();
                                }
                            }

                        } else if (GZIP.equalsIgnoreCase(argsMap.get(ARG_COMPRESSION))) {
                            System.out.println("Using GZIP");
                            try (final GzipCompressorOutputStream out =
                                    new GzipCompressorOutputStream(outputStream)) {
                                write(out, bufferSize, writeSize);
                            }
                        } else {
                            try (final BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(
                                    outputStream)) {
                                write(bufferedOutputStream, bufferSize, writeSize);
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
                return code;
            });
        }
    }

    private static void write(final OutputStream out,
                              final int bufferSize,
                              final int writeSize) throws IOException {
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
    }
}
