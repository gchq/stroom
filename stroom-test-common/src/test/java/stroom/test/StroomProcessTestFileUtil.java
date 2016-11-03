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

package stroom.test;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;

import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;

public final class StroomProcessTestFileUtil {
    private static File PROJECT_DIR;
    private static File TEST_RESOURCES_DIR;
    private static File TEST_OUTPUT_DIR;

    private StroomProcessTestFileUtil() {
        // Utility class.
    }

    public static File getProjectDir() {
        if (PROJECT_DIR == null) {
            PROJECT_DIR = ProjectPathUtil.resolveDir("stroom-pipeline");
        }
        return PROJECT_DIR;
    }

    public static File getTestResourcesDir() {
        if (TEST_RESOURCES_DIR == null) {
            File dir = new File(getProjectDir(), "src/test/resources");
            if (!dir.isDirectory()) {
                throw new RuntimeException("Test data directory not found: " + dir.getAbsolutePath());
            }
            TEST_RESOURCES_DIR = dir;
        }
        return TEST_RESOURCES_DIR;
    }

    public static File getTestOutputDir() {
        if (TEST_OUTPUT_DIR == null) {
            File dir = new File(getProjectDir(), "test-output");
            dir.mkdir();
            if (!dir.isDirectory()) {
                throw new RuntimeException("Test output directory not found: " + dir.getAbsolutePath());
            }
            TEST_OUTPUT_DIR = dir;
        }
        return TEST_OUTPUT_DIR;
    }

    public static File getTestResourcesFile(final String path) {
        final File file = new File(getTestResourcesDir(), path);
        if (!file.isFile()) {
            throw new RuntimeException("File not found: " + file.getAbsolutePath());
        }
        return file;
    }

    public static InputStream getInputStream(final String path) {
        final File file = getTestResourcesFile(path);
        try {
            return new BufferedInputStream(new FileInputStream(file));
        } catch (final FileNotFoundException e) {
            throw new RuntimeException("File not found: " + file.getAbsolutePath());
        }
    }

    public static OutputStream getOutputStream(final File dir, final String path) {
        final File file = new File(dir, path);
        final File parent = file.getParentFile();
        FileUtil.mkdirs(parent);

        try {
            return new BufferedOutputStream(new FileOutputStream(file));
        } catch (final FileNotFoundException e) {
            throw new RuntimeException("File not found: " + file.getAbsolutePath());
        }
    }

    public static String getString(final String path) {
        final InputStream is = getInputStream(path);
        return StreamUtil.streamToString(is);
    }
}
