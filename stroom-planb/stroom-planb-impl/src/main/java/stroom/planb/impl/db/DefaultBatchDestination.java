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

package stroom.planb.impl.db;

import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Dispatches each {@link WrittenPart} in a batch to its configured
 * {@link PartDestination}, then cleans up the root writer directory.
 *
 * <p>This class contains no delivery logic; all destination-specific behaviour
 * lives in the {@link PartDestination} implementations. Adding a new
 * destination type (e.g. S3) requires only a new {@link PartDestination}
 * implementation — this class does not change.
 *
 * <p>This class is stateless and therefore thread-safe.
 */
@Singleton
public class DefaultBatchDestination implements BatchDestination {

    private static final LambdaLogger LOGGER =
            LambdaLoggerFactory.getLogger(DefaultBatchDestination.class);

    @Override
    public void publish(final WrittenBatch batch) throws IOException {
        boolean allOk = false;
        try {
            boolean ok = true;
            for (final WrittenPart part : batch.parts()) {
                if (!part.destination().transfer(part, batch.meta())) {
                    ok = false;
                }
            }
            allOk = ok;
        } finally {
            cleanUpWriterDir(batch.writerDir(), allOk);
        }
    }

    private static void cleanUpWriterDir(final Path writerDir, final boolean allOk) {
        try {
            if (allOk) {
                FileUtil.deleteDir(writerDir);
            } else {
                LOGGER.error(() -> LogUtil.message(
                        "Publish failed; retaining writer directory {} for operator inspection",
                        writerDir));
            }
        } catch (final Exception e) {
            LOGGER.error(() -> LogUtil.message(
                    "Failed to clean up writer directory {}", writerDir), e);
        }
    }
}
