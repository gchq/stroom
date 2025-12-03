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

package stroom.statistics.impl.sql.filter;

import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class StroomStatisticsServerTestFileUtil {
    private static final Path TEST_RESOURCES_DIR;
//    private static final Path TEST_OUTPUT_DIR;

    private static final String PROJECT_NAME = "stroom-statistics-impl";

    static {
        final Path dir = Paths.get("../" + PROJECT_NAME + "/src/test/resources");
        if (!Files.isDirectory(dir)) {
            throw new RuntimeException("Test data directory not found: " + FileUtil.getCanonicalPath(dir));
        }
        TEST_RESOURCES_DIR = dir;

//        dir = Paths.get("../" + PROJECT_NAME + "/test-output");
//        if (!Files.isDirectory(dir)) {
//            try {
//                Files.createDirectories(dir);
//            } catch (final IOException e) {
//                throw new RuntimeException("Test output directory not found: " + FileUtil.getCanonicalPath(dir));
//            }
//        }
//
//        TEST_OUTPUT_DIR = dir;
    }

    private StroomStatisticsServerTestFileUtil() {
        // Utility class.
    }

    public static Path getTestResourcesDir() {
        return TEST_RESOURCES_DIR;
    }

//    public static Path getTestOutputDir() {
//        return TEST_OUTPUT_DIR;
//    }
//
//    public static Path getFile(final String path) {
//        final Path file = getTestResourcesDir().resolve(path);
//        if (!Files.isRegularFile(file)) {
//            throw new RuntimeException("File not found: " + FileUtil.getCanonicalPath(file));
//        }
//        return file;
//    }

    public static String getString(final String path) {
        final Path file = getTestResourcesDir().resolve(path);
        if (!Files.isRegularFile(file)) {
            throw new RuntimeException("File not found: " + FileUtil.getCanonicalPath(file));
        }
        return StreamUtil.fileToString(file);
    }
}
