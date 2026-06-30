/*
 * Copyright 2016-2026 Crown Copyright
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
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;

public final class SharedFileStoreWriter {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SharedFileStoreWriter.class);

    private SharedFileStoreWriter() {
        // Utility class
    }

    /**
     * Copies a local LMDB shard directory containing data.mdb to the shared file store
     * using the NFS-safe write-to-temp-then-rename protocol.
     *
     * @param localLmdbDir   The local LMDB environment directory.
     * @param sharedTarget   The target directory on the shared store.
     * @throws IOException   If any filesystem operation fails.
     */
    public static void copyToSharedStore(final Path localLmdbDir, final Path sharedTarget) throws IOException {
        final Path tmpTarget = sharedTarget.resolveSibling(
                sharedTarget.getFileName().toString() + PlanBConstants.TMP_DIR_SUFFIX);

        try {
            // 1. Create .tmp directory
            Files.createDirectories(tmpTarget);

            // 2. Copy the data.mdb file (LMDB data)
            final Path sourceData = localLmdbDir.resolve(PlanBConstants.DATA_FILE_NAME);
            final Path targetData = tmpTarget.resolve(PlanBConstants.DATA_FILE_NAME);
            if (Files.exists(sourceData)) {
                Files.copy(sourceData, targetData, StandardCopyOption.REPLACE_EXISTING);
            } else {
                throw new IOException("Source " + PlanBConstants.DATA_FILE_NAME + " not found in: " + localLmdbDir);
            }

            // 3. Write completion marker (.complete) inside the temp directory first
            final Path completeMarker = tmpTarget.resolve(PlanBConstants.COMPLETE_FILE_NAME);
            Files.writeString(completeMarker, Instant.now().toString(), StandardCharsets.UTF_8);

            // 4. Perform move (same parent directory = safe on NFS)
            try {
                Files.move(tmpTarget, sharedTarget, StandardCopyOption.ATOMIC_MOVE);
            } catch (final AtomicMoveNotSupportedException e) {
                LOGGER.warn("Atomic move not supported. Falling back to standard move/replace.");
                Files.move(tmpTarget, sharedTarget, StandardCopyOption.REPLACE_EXISTING);
            }

        } catch (final Exception e) {
            // Cleanup target on failure to avoid leaving corrupt/half-written shards
            try {
                FileUtil.deleteDir(tmpTarget);
            } catch (final Exception ex) {
                LOGGER.error("Failed to clean up temporary directory: {}", tmpTarget, ex);
            }
            throw e;
        }
    }
}
