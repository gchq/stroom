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

package stroom.statistics.common.pipeline.filter;

import stroom.util.io.StreamUtil;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

public final class StroomStatisticsServerTestFileUtil {
    private static final File TEST_RESOURCES_DIR;
    private static final File TEST_OUTPUT_DIR;

    private static final String PROJECT_NAME = "stroom-statistics-server";

    static {
        File dir = new File("../" + PROJECT_NAME + "/src/test/resources");
        if (!dir.isDirectory()) {
            throw new RuntimeException("Test data directory not found: " + dir.getAbsolutePath());
        }
        TEST_RESOURCES_DIR = dir;

        dir = new File("../" + PROJECT_NAME + "/test-output");
        if (!dir.isDirectory()) {
            dir.mkdirs();
            if (!dir.isDirectory()) {
                throw new RuntimeException("Test output directory not found: " + dir.getAbsolutePath());
            }
        }

        TEST_OUTPUT_DIR = dir;
    }

    private StroomStatisticsServerTestFileUtil() {
        // Utility class.
    }

    public static File getTestResourcesDir() {
        return TEST_RESOURCES_DIR;
    }

    public static File getTestOutputDir() {
        return TEST_OUTPUT_DIR;
    }

    public static File getFile(final String path) {
        final File file = new File(getTestResourcesDir(), path);
        if (!file.isFile()) {
            throw new RuntimeException("File not found: " + file.getAbsolutePath());
        }
        return file;
    }

    public static InputStream getInputStream(final String path) {
        final File file = new File(getTestResourcesDir(), path);
        if (!file.isFile()) {
            throw new RuntimeException("File not found: " + file.getAbsolutePath());
        }
        try {
            return new BufferedInputStream(new FileInputStream(file));
        } catch (final FileNotFoundException e) {
            throw new RuntimeException("File not found: " + file.getAbsolutePath());
        }
    }

    public static String getString(final String path) {
        final InputStream is = getInputStream(path);
        return StreamUtil.streamToString(is);
    }
}
