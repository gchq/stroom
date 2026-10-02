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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestSharedFileStoreCleaner {

    private static final String LIVE_UUID = "live-doc-uuid";
    private static final String DEAD_UUID = "dead-doc-uuid";

    @TempDir
    Path sharedRoot;

    // --- orphan detection ---------------------------------------------------

    @Test
    void orphanedArchiveIsSweptUp() throws IOException {
        final Path archive = expiredStageDir(PlanBConstants.ARCHIVE_DIR_NAME, DEAD_UUID);

        cleanerWithLive(LIVE_UUID).exec();

        // Trashed and drained in the same run.
        assertThat(archive).doesNotExist();
        assertThat(trashIsEmpty()).isTrue();
    }

    @Test
    void everyStageOfAnOrphanIsSweptUp() throws IOException {
        for (final String stage : PlanBConstants.STAGE_DIR_NAMES) {
            expiredStageDir(stage, DEAD_UUID);
        }

        cleanerWithLive(LIVE_UUID).exec();

        for (final String stage : PlanBConstants.STAGE_DIR_NAMES) {
            assertThat(sharedRoot.resolve(stage).resolve(DEAD_UUID)).doesNotExist();
        }
    }

    @Test
    void liveDocIsKept() throws IOException {
        final Path holding = expiredStageDir(PlanBConstants.HOLDING_DIR_NAME, LIVE_UUID);

        cleanerWithLive(LIVE_UUID).exec();

        assertThat(holding).exists();
    }

    @Test
    void recentActivityInAnyStageKeepsEveryStage() throws IOException {
        final Path archive = expiredStageDir(PlanBConstants.ARCHIVE_DIR_NAME, DEAD_UUID);
        // Left at its real modification time, so it is inside the grace period.
        final Path processing = stageDir(PlanBConstants.PROCESSING_DIR_NAME, DEAD_UUID);

        cleanerWithLive(LIVE_UUID).exec();

        assertThat(processing).exists();
        assertThat(archive).exists();
    }

    @Test
    void strayFileIsNotTreatedAsADoc() throws IOException {
        final Path holdingDir = sharedRoot.resolve(PlanBConstants.HOLDING_DIR_NAME);
        Files.createDirectories(holdingDir);
        final Path strayFile = holdingDir.resolve("README.txt");
        Files.writeString(strayFile, "not a doc");
        backdate(strayFile);

        cleanerWithLive(LIVE_UUID).exec();

        assertThat(strayFile).exists();
    }

    // --- failing closed ----------------------------------------------------

    @Test
    void nothingIsSweptWhenADocStoreCannotEnumerate() throws IOException {
        final Path archive = expiredStageDir(PlanBConstants.ARCHIVE_DIR_NAME, DEAD_UUID);
        final SharedFileStoreCleaner cleaner = new SharedFileStoreCleaner(Set.of(
                () -> Map.of(sharedRoot, Set.of(LIVE_UUID)),
                () -> {
                    throw new RuntimeException("doc store unavailable");
                }));

        cleaner.exec();

        assertThat(archive).exists();
    }

    @Test
    void nothingIsSweptWhenARootReportsNoLiveDocs() throws IOException {
        final Path archive = expiredStageDir(PlanBConstants.ARCHIVE_DIR_NAME, DEAD_UUID);

        new SharedFileStoreCleaner(Set.of(() -> Map.of(sharedRoot, Set.of()))).exec();

        assertThat(archive).exists();
    }

    @Test
    void rootWithNoLiveDocsStillHasItsTrashDrained() throws IOException {
        final Path trashEntry = sharedRoot
                .resolve(PlanBConstants.TRASH_DIR_NAME)
                .resolve(DEAD_UUID + "-1")
                .resolve(PlanBConstants.HOLDING_DIR_NAME);
        Files.createDirectories(trashEntry);

        new SharedFileStoreCleaner(Set.of(() -> Map.of(sharedRoot, Set.of()))).exec();

        assertThat(trashIsEmpty()).isTrue();
    }

    // --- trash drain -------------------------------------------------------

    @Test
    void existingTrashIsDrained() throws IOException {
        final Path trashEntry = sharedRoot
                .resolve(PlanBConstants.TRASH_DIR_NAME)
                .resolve(DEAD_UUID + "-1")
                .resolve(PlanBConstants.ARCHIVE_DIR_NAME)
                .resolve("0000");
        Files.createDirectories(trashEntry);
        Files.writeString(trashEntry.resolve("data.mdb"), "dead");

        cleanerWithLive(LIVE_UUID).exec();

        assertThat(trashIsEmpty()).isTrue();
    }

    // --- startup tmp cleanup -----------------------------------------------

    @Test
    void startupDeletesAnOldTmpBatchDir() throws IOException {
        final Path tmpBatch = batchDir(LIVE_UUID, "12_99" + PlanBConstants.TMP_DIR_SUFFIX);
        Files.setLastModifiedTime(tmpBatch, FileTime.from(Instant.now().minus(Duration.ofMinutes(10))));

        cleanerWithLive(LIVE_UUID).startup();

        assertThat(tmpBatch).doesNotExist();
    }

    @Test
    void startupKeepsARecentTmpBatchDirAndCompletedBatches() throws IOException {
        final Path recentTmp = batchDir(LIVE_UUID, "12_99" + PlanBConstants.TMP_DIR_SUFFIX);
        final Path complete = batchDir(LIVE_UUID, "13_99");
        Files.setLastModifiedTime(complete, FileTime.from(Instant.now().minus(Duration.ofHours(2))));

        cleanerWithLive(LIVE_UUID).startup();

        assertThat(recentTmp).exists();
        assertThat(complete).exists();
    }

    @Test
    void startupDoesNothingWhenADocStoreCannotEnumerate() throws IOException {
        final Path tmpBatch = batchDir(LIVE_UUID, "12_99" + PlanBConstants.TMP_DIR_SUFFIX);
        Files.setLastModifiedTime(tmpBatch, FileTime.from(Instant.now().minus(Duration.ofMinutes(10))));

        new SharedFileStoreCleaner(Set.of(() -> {
            throw new RuntimeException("doc store unavailable");
        })).startup();

        assertThat(tmpBatch).exists();
    }

    // --- helpers -----------------------------------------------------------

    private SharedFileStoreCleaner cleanerWithLive(final String... uuids) {
        return new SharedFileStoreCleaner(Set.of(() -> Map.of(sharedRoot, Set.of(uuids))));
    }

    // A stage directory for one doc, backdated past the orphan grace period. The backdating has to be
    // the last thing done to it, as creating anything underneath bumps its modification time again.
    private Path expiredStageDir(final String stage, final String uuid) throws IOException {
        final Path dir = stageDir(stage, uuid);
        backdate(dir);
        return dir;
    }

    private Path stageDir(final String stage, final String uuid) throws IOException {
        final Path dir = sharedRoot.resolve(stage).resolve(uuid);
        Files.createDirectories(dir.resolve("0000"));
        return dir;
    }

    private Path batchDir(final String uuid, final String batchName) throws IOException {
        final Path dir = sharedRoot
                .resolve(PlanBConstants.PROCESSING_DIR_NAME)
                .resolve(uuid)
                .resolve("0000")
                .resolve(batchName);
        Files.createDirectories(dir);
        return dir;
    }

    private void backdate(final Path path) throws IOException {
        Files.setLastModifiedTime(path,
                FileTime.from(Instant.now().minus(SharedFileStoreCleaner.ORPHAN_GRACE_PERIOD)
                        .minus(Duration.ofMinutes(1))));
    }

    private boolean trashIsEmpty() throws IOException {
        final Path trashDir = sharedRoot.resolve(PlanBConstants.TRASH_DIR_NAME);
        if (!Files.isDirectory(trashDir)) {
            return true;
        }
        try (final var stream = Files.list(trashDir)) {
            return stream.findAny().isEmpty();
        }
    }
}
