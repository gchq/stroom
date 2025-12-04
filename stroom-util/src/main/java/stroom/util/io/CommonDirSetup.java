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

package stroom.util.io;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class CommonDirSetup {

    public static void setup() {
        try {
            final Path tempDir = Files.createTempDirectory("temp");
            System.setProperty(TempDirProviderImpl.PROP_STROOM_TEMP, FileUtil.getCanonicalPath(tempDir));
            final Path homeDir = Files.createTempDirectory("home");
            System.setProperty(HomeDirProviderImpl.PROP_STROOM_HOME, FileUtil.getCanonicalPath(homeDir));

        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
