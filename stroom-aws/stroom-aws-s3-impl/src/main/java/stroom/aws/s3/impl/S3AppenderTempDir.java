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

package stroom.aws.s3.impl;

import stroom.util.io.FileUtil;
import stroom.util.io.TempDirProvider;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Singleton
public class S3AppenderTempDir {

    private final Path tempDir;

    @Inject
    S3AppenderTempDir(final TempDirProvider tempDirProvider) {
        try {
            tempDir = tempDirProvider.get().resolve("s3_appender");
            Files.createDirectories(tempDir);
            FileUtil.deleteContents(tempDir);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Path createTempFile() {
        return tempDir.resolve(UUID.randomUUID() + ".tmp");
    }
}
