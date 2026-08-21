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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Records when a periodic maintenance operation last ran for one shard, as an ISO-8601
 * instant in a marker file inside the canonical shared shard directory.
 *
 * <p>Each operation has its own marker and its own configured check interval, so the retention
 * and compaction schedules are independent — they share only the per-minute merge tick that
 * polls them, and will usually fall due on different cycles.
 *
 * <p>Reads are safe outside the shard cluster lock. A missing or unreadable marker reports
 * {@code null}, which callers treat as due: a shard that has never run does so on its
 * first poll, and a corrupt marker fails open rather than stalling maintenance forever.
 * The marker is only written once a run completes, so an operation that throws is retried
 * on the next tick rather than waiting out a whole interval.
 */
final class OperationMarker {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(OperationMarker.class);

    private final String fileName;

    OperationMarker(final String fileName) {
        this.fileName = fileName;
    }

    Instant lastRun(final Path sharedShardsDocDir, final int shardIndex) {
        final Path file = file(sharedShardsDocDir, shardIndex);
        if (!Files.exists(file)) {
            return null;
        }
        try {
            return Instant.parse(Files.readString(file, StandardCharsets.UTF_8).trim());
        } catch (final Exception e) {
            LOGGER.warn("Could not read {}, treating as due: {}", file, e.getMessage());
            return null;
        }
    }

    void recordRun(final Path sharedShardsDocDir, final int shardIndex) {
        final Path file = file(sharedShardsDocDir, shardIndex);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, Instant.now().toString(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            LOGGER.error("Failed to write {}", file, e);
        }
    }

    private Path file(final Path sharedShardsDocDir, final int shardIndex) {
        return sharedShardsDocDir
                .resolve(PlanBConstants.formatShardIndex(shardIndex))
                .resolve(fileName);
    }
}
