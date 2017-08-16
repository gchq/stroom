/*
 * Copyright 2017 Crown Copyright
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

package stroom.dashboard.server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Helper class for resources.
 */
public final class StreamUtil {
    private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    private StreamUtil() {
        // NA Utility
    }

    /**
     * Takes a string and writes it to a file.
     */
    public static void stringToFile(final String string, final Path path) {
        stringToFile(string, path, DEFAULT_CHARSET);
    }

    /**
     * Takes a string and writes it to a file.
     */
    public static void stringToFile(final String string, final Path path, final Charset charset) {
        try {
            if (Files.isRegularFile(path)) {
                Files.delete(path);
            }
            Files.createDirectories(path.getParent());

            try (BufferedWriter writer = Files.newBufferedWriter(path, charset)) {
                writer.write(string);
            }
        } catch (final IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Reads a file and returns it as a string.
     */
    public static String fileToString(final Path path) {
        return fileToString(path, DEFAULT_CHARSET);
    }

    /**
     * Reads a file and returns it as a string.
     */
    public static String fileToString(final Path path, final Charset charset) {
        final StringBuilder sb = new StringBuilder();

        try {
            if (Files.isRegularFile(path)) {
                try (BufferedReader reader = Files.newBufferedReader(path, charset)) {
                    final char[] buffer = new char[4096];
                    int len = 0;
                    while ((len = reader.read(buffer, 0, buffer.length)) != -1) {
                        sb.append(buffer, 0, len);
                    }
                }
            }
        } catch (final IOException ex) {
            throw new RuntimeException(ex);
        }

        return sb.toString();
    }
}
