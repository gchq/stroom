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

package stroom.headless;

import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class StroomHeadlessTestFileUtil {

    private static Path PROJECT_DIR;
    private static Path TEST_RESOURCES_DIR;
    private static Path TEST_OUTPUT_DIR;

    private StroomHeadlessTestFileUtil() {
        // Utility class.
    }

    public static Path getProjectDir() {
        if (PROJECT_DIR == null) {
            PROJECT_DIR = ProjectPathUtil.resolveDir("stroom-headless");
        }
        return PROJECT_DIR;
    }

    public static Path getTestResourcesDir() {
        if (TEST_RESOURCES_DIR == null) {
            final Path dir = getProjectDir().resolve("src/test/resources");
            if (!Files.isDirectory(dir)) {
                throw new RuntimeException("Test data directory not found: " + FileUtil.getCanonicalPath(dir));
            }
            TEST_RESOURCES_DIR = dir;
        }
        return TEST_RESOURCES_DIR;
    }

    public static Path getTestOutputDir() {
        if (TEST_OUTPUT_DIR == null) {
            final Path dir = getProjectDir().resolve("test-output");
            try {
                Files.createDirectories(dir);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
            TEST_OUTPUT_DIR = dir;
        }
        return TEST_OUTPUT_DIR;
    }

    public static Path getTestResourcesFile(final String path) {
        final Path file = getTestResourcesDir().resolve(path);
        if (!Files.isRegularFile(file)) {
            throw new RuntimeException("File not found: " + FileUtil.getCanonicalPath(file));
        }
        return file;
    }

    public static InputStream getInputStream(final String path) {
        final Path file = getTestResourcesFile(path);
        try {
            return new BufferedInputStream(Files.newInputStream(file));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static OutputStream getOutputStream(final Path dir, final String path) {
        final Path file = dir.resolve(path);
        try {
            final Path parent = file.getParent();
            Files.createDirectories(parent);
            return new BufferedOutputStream(Files.newOutputStream(file));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String getString(final String path) {
        final InputStream is = getInputStream(path);
        return StreamUtil.streamToString(is);
    }
}
