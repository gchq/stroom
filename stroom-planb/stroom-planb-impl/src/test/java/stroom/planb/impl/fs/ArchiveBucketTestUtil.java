/*
 * Copyright 2026 Crown Copyright
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

package stroom.planb.impl.fs;

import stroom.planb.impl.PlanBConstants;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/// Lets a test outside this package read a published archive bucket the way the store does.
///
/// A bucket on the shared store may hold its data compressed, so it cannot be opened as an LMDB env
/// where it lies. This copies it down to local disk first, as `ArchiveStoreShard` does.
public final class ArchiveBucketTestUtil {

    private ArchiveBucketTestUtil() {
    }

    /// Copies the data in `bucketDir` down into a new directory under `parentDir`, decompressing it
    /// where it is compressed.
    ///
    /// @param bucketDir The published archive bucket to read.
    /// @param parentDir Where to create the local copy. Each call makes a new directory beneath it.
    /// @return The directory holding a plain `data.mdb`, ready to open as an env.
    /// @throws IOException If the bucket cannot be copied.
    public static Path copyDown(final Path bucketDir, final Path parentDir) throws IOException {
        final Path destDir = Files.createTempDirectory(parentDir, "bucket_copy");
        final boolean found = ArchiveData.copyDown(bucketDir, destDir.resolve(PlanBConstants.DATA_FILE_NAME));
        assertThat(found)
                .as("bucket %s holds a data file", bucketDir)
                .isTrue();
        return destDir;
    }
}
