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

package stroom.planb.impl.fs;

import stroom.meta.shared.Meta;
import stroom.planb.impl.PlanBConstants;
import stroom.planb.impl.db.PartDestination;
import stroom.planb.impl.db.WrittenPart;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Singleton;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Transfers a {@link WrittenPart} to the shared filesystem's {@code processing/}
 * directory via {@link SharedFileStoreWriter#copyToSharedStore}, then removes
 * the local writer directory on success.
 *
 * <p>This class is stateless and therefore thread-safe.
 */
@Singleton
public class SharedFileStorePartDestination implements PartDestination {

    private static final LambdaLogger LOGGER =
            LambdaLoggerFactory.getLogger(SharedFileStorePartDestination.class);

    @Override
    public boolean transfer(final WrittenPart part, final Meta meta) {
        // <sharedPath>/processing/<docUuid>/<shardIndex>/<metaId>_<ts>/
        final Path processingTarget = Path.of(
                Objects.requireNonNull(part.doc().getSharedPath(),
                        "sharedPath unexpectedly null for shared-store part"))
                .resolve(PlanBConstants.PROCESSING_DIR_NAME)
                .resolve(part.doc().getUuid())
                .resolve(PlanBConstants.formatShardIndex(part.shardIndex()))
                .resolve(meta.getId() + "_" + System.currentTimeMillis());

        LOGGER.debug(() -> LogUtil.message(
                "Publishing writer dir {} to processing dir {}",
                part.localWriterDir(), processingTarget));
        try {
            SharedFileStoreWriter.copyToSharedStore(part.localWriterDir(), processingTarget);
            // Remove this part's local writer dir now that its data is in the
            // processing dir. The parent writer dir is deleted by
            // DefaultBatchDestination once all parts are done.
            FileUtil.deleteDir(part.localWriterDir());
            return true;
        } catch (final Exception e) {
            LOGGER.error(() -> LogUtil.message(
                    "Failed to publish writer dir {} (doc={}, meta={}) to processing dir {}; "
                    + "writer dir will be retained for operator inspection",
                    part.localWriterDir(), part.doc().getUuid(),
                    meta.getId(), processingTarget), e);
            return false;
        }
    }
}
