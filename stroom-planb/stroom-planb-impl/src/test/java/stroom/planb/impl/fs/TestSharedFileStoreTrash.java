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
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestSharedFileStoreTrash {

    private static final String UUID = "doc-uuid-1";

    @TempDir
    Path sharedRoot;

    @Test
    void movesEveryStageIntoOneEntry() throws IOException {
        writeStageData(PlanBConstants.HOLDING_DIR_NAME, "0000/data.mdb");
        writeStageData(PlanBConstants.PROCESSING_DIR_NAME, "0000/12_99/data.mdb");
        writeStageData(PlanBConstants.ARCHIVE_DIR_NAME, "0000/2026-08-26/data.mdb");

        SharedFileStoreTrash.trashDoc(sharedRoot, UUID);

        for (final String stage : PlanBConstants.STAGE_DIR_NAMES) {
            assertThat(sharedRoot.resolve(stage).resolve(UUID)).doesNotExist();
        }

        final List<Path> entries = trashEntries();
        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().getFileName().toString()).startsWith(UUID + "-");
        assertThat(entries.getFirst().resolve(PlanBConstants.HOLDING_DIR_NAME + "/0000/data.mdb")).exists();
        assertThat(entries.getFirst().resolve(PlanBConstants.PROCESSING_DIR_NAME + "/0000/12_99/data.mdb"))
                .exists();
        assertThat(entries.getFirst()
                .resolve(PlanBConstants.ARCHIVE_DIR_NAME + "/0000/2026-08-26/data.mdb")).exists();
    }

    @Test
    void skipsStagesWithNoData() throws IOException {
        writeStageData(PlanBConstants.ARCHIVE_DIR_NAME, "0000/2026-08-26/data.mdb");

        SharedFileStoreTrash.trashDoc(sharedRoot, UUID);

        final List<Path> entries = trashEntries();
        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().resolve(PlanBConstants.ARCHIVE_DIR_NAME)).exists();
        assertThat(entries.getFirst().resolve(PlanBConstants.HOLDING_DIR_NAME)).doesNotExist();
        assertThat(entries.getFirst().resolve(PlanBConstants.PROCESSING_DIR_NAME)).doesNotExist();
    }

    @Test
    void docWithNoDataCreatesNoTrashEntry() {
        SharedFileStoreTrash.trashDoc(sharedRoot, UUID);

        assertThat(sharedRoot.resolve(PlanBConstants.TRASH_DIR_NAME)).doesNotExist();
    }

    @Test
    void leavesOtherDocsAlone() throws IOException {
        writeStageData(PlanBConstants.HOLDING_DIR_NAME, "0000/data.mdb");
        final Path otherDoc = sharedRoot
                .resolve(PlanBConstants.HOLDING_DIR_NAME)
                .resolve("doc-uuid-2")
                .resolve("0000");
        Files.createDirectories(otherDoc);
        Files.writeString(otherDoc.resolve("data.mdb"), "keep");

        SharedFileStoreTrash.trashDoc(sharedRoot, UUID);

        assertThat(otherDoc.resolve("data.mdb")).exists();
    }

    private void writeStageData(final String stage, final String relativePath) throws IOException {
        final Path file = sharedRoot.resolve(stage).resolve(UUID).resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "data");
    }

    private List<Path> trashEntries() throws IOException {
        final Path trashDir = sharedRoot.resolve(PlanBConstants.TRASH_DIR_NAME);
        if (!Files.isDirectory(trashDir)) {
            return List.of();
        }
        try (final Stream<Path> stream = Files.list(trashDir)) {
            return stream.toList();
        }
    }
}
