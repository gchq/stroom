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

package stroom.planb.impl.data.shard;

import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.PlanBConstants;
import stroom.planb.impl.PlanBPaths;
import stroom.task.api.ExecutorProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The startup sweep of local generation dirs, which runs from {@link ShardManager}'s constructor.
 */
class TestShardManagerSweep {

    @TempDir
    Path tempDir;

    /**
     * Eviction and the sweep both delete a generation dir and leave its parent, so without cleanup one
     * empty dir would remain per archive bucket ever queried and never go away.
     */
    @Test
    void sweepRemovesGenerationDirsAndTheirEmptyParents() throws IOException {
        final Path planbRoot = tempDir.resolve("planb");
        final Path cache = planbRoot.resolve(PlanBConstants.ARCHIVE_CACHE_DIR_NAME);

        // A bucket cached by a previous run: generation dir with data in it.
        final Path stale = cache.resolve("uuid-a_0_2026-08-20_04");
        Files.createDirectories(stale.resolve("1787297298212_gen"));
        Files.writeString(stale.resolve("1787297298212_gen").resolve(PlanBConstants.DATA_FILE_NAME), "x");

        // A bucket whose generation dir was already evicted, leaving the parent behind.
        final Path evicted = cache.resolve("uuid-a_1_2026-08-20_05");
        Files.createDirectories(evicted);

        // A RestStoreShard holds its data.mdb directly under the identity dir and is authoritative.
        final Path restShard = planbRoot.resolve(PlanBConstants.SHARDS_DIR_NAME).resolve("uuid-b");
        Files.createDirectories(restShard);
        Files.writeString(restShard.resolve(PlanBConstants.DATA_FILE_NAME), "x");

        newShardManager(planbRoot);

        assertThat(stale).as("cached bucket from a previous run is reaped").doesNotExist();
        assertThat(evicted).as("parent left by an eviction is reaped").doesNotExist();
        assertThat(restShard.resolve(PlanBConstants.DATA_FILE_NAME))
                .as("an authoritative local shard is left alone").exists();
    }

    private ShardManager newShardManager(final Path planbRoot) {
        return new ShardManager(
                null,
                null,
                null,
                null,
                null,
                () -> PlanBConfig.builder().build(),
                new PlanBPaths(planbRoot),
                null,
                null,
                mock(ExecutorProvider.class),
                null);
    }
}
