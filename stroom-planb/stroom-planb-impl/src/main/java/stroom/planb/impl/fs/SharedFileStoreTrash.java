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
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Retires all of one document's data on a shared file store by renaming each of its
 * {@link PlanBConstants#STAGE_DIR_NAMES} directories into
 * {@code <root>/trash/<docUuid>-<millis>/<stage>/}, which {@link SharedFileStoreCleaner} drains on its
 * next run.
 *
 * <p>Renaming rather than deleting keeps this to one directory operation whatever the subtree holds —
 * an archive tree can be very large — and puts the data out of reach of queries and the merge at once,
 * both of which find a document's data by listing these directories.
 *
 * <p>Failures are logged, not thrown. Both callers reach here only once the document's config has
 * gone, so there is nothing to roll back, and a stage left behind is found again as an orphan on the
 * next sweep.
 */
public final class SharedFileStoreTrash {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SharedFileStoreTrash.class);

    private SharedFileStoreTrash() {
    }

    /**
     * Moves every stage directory {@code docUuid} has under {@code sharedRoot} into one trash entry,
     * skipping stages it has no directory for.
     */
    public static void trashDoc(final Path sharedRoot, final String docUuid) {
        // Named once outside the loop so all of the document's stages land in the same entry.
        final Path trashEntry = sharedRoot
                .resolve(PlanBConstants.TRASH_DIR_NAME)
                .resolve(docUuid + "-" + System.currentTimeMillis());

        for (final String stage : PlanBConstants.STAGE_DIR_NAMES) {
            final Path src = sharedRoot.resolve(stage).resolve(docUuid);
            if (!Files.exists(src)) {
                continue;
            }
            try {
                Files.createDirectories(trashEntry);
                Files.move(src, trashEntry.resolve(stage), StandardCopyOption.ATOMIC_MOVE);
                LOGGER.debug("Moved {} data for doc {} to trash: {}", stage, docUuid, trashEntry);
            } catch (final NoSuchFileException e) {
                // Another node moved it between the check and the rename.
                LOGGER.debug(() -> "Already moved by another node: " + src);
            } catch (final IOException e) {
                LOGGER.warn(() -> "Could not move " + src + " to trash: " + e.getMessage()
                                  + " — housekeeping will retry it as an orphan");
            }
        }
    }
}
