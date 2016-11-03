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

import java.io.File;

public abstract class FileSystemTestUtil {
    private static final String CONFIG_PATH = "../stroom-config";

    private static final File configDir = new File(CONFIG_PATH);
    private static final File configXSDDir = new File(configDir, "xsd");
    private static final File configXSLTDir = new File(configDir, "xslt");

    public static final char SEPERATOR_CHAR = '/';

    private FileSystemTestUtil() {
        // Utility
    }

    private static final long TEST_PREFIX = System.currentTimeMillis();
    private static long testSuffix = 0;

    /**
     * @return a unique string for testing
     */
    public static synchronized String getUniqueTestString() {
        testSuffix++;
        return TEST_PREFIX + "_" + testSuffix;
    }

    public static File getConfigDir() {
        if (!configDir.isDirectory()) {
            throw new RuntimeException("Directory not found: " + configDir.getAbsolutePath());
        }
        return configDir;
    }

    public static File getConfigXSDDir() {
        if (!configXSDDir.isDirectory()) {
            throw new RuntimeException("Directory not found: " + configXSDDir.getAbsolutePath());
        }
        return configXSDDir;
    }

    public static File getConfigXSLTDir() {
        if (!configXSLTDir.isDirectory()) {
            throw new RuntimeException("Directory not found: " + configXSLTDir.getAbsolutePath());
        }
        return configXSLTDir;
    }
}
