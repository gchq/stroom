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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

public class StroomTestUtil {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

    public static Path createRootTestDir(final Path tempDir) throws IOException {
        return tempDir;
    }

    public static Path createPerThreadTestDir(final Path path) throws IOException {
        if (!Files.isDirectory(path)) {
            throw new IOException("The parent directory '" + FileUtil.getCanonicalPath(path) + "' does not exist");
        }

        final Path dir = path.resolve(String.valueOf(Thread.currentThread().getId()));
        FileUtil.deleteAll(dir);
        Files.createDirectories(dir);
        return dir;
    }
}
