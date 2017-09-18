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

package stroom.util.test;

import stroom.util.io.FileUtil;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public abstract class FileSystemTestUtil {
    private static final String CONFIG_PATH = "../stroom-config";

    private static final Path configDir = Paths.get(CONFIG_PATH);
    private static final Path configXSDDir = configDir.resolve("xsd");
    private static final long TEST_PREFIX = System.currentTimeMillis();
    private static long testSuffix = 0;
    private FileSystemTestUtil() {
        // Utility
    }

    /**
     * @return a unique string for testing
     */
    public static synchronized String getUniqueTestString() {
        testSuffix++;
        return TEST_PREFIX + "_" + testSuffix;
    }

    public static Path getConfigXSDDir() {
        if (!Files.isDirectory(configXSDDir)) {
            throw new RuntimeException("Directory not found: " + FileUtil.getCanonicalPath(configXSDDir));
        }
        return configXSDDir;
    }
}
