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

import java.io.File;

public final class StroomCoreClientTestFileUtil {
    private static final File TEST_DATA_DIR;

    static {
        final File dir = new File("../stroom-core-client/src/test/resources");
        if (!dir.isDirectory()) {
            throw new RuntimeException("Test data directory not found: " + dir.getAbsolutePath());
        }
        TEST_DATA_DIR = dir;
    }

    private StroomCoreClientTestFileUtil() {
        // Utility class.
    }

    public static File getTestResourcesDir() {
        return TEST_DATA_DIR;
    }
}
