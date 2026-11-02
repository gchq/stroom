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

package stroom.ai.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TestTableDownloader {

    @TempDir
    Path tempDir;

    @Test
    void theRowFetchedBeyondTheMostAskedForIsLeftOut() throws Exception {
        final Path file = tempDir.resolve("table.md");
        Files.writeString(file, "| A | B |\n| --- | --- |\n| 1 | 2 |\n| 3 | 4 |\n| 5 | 6 |\n");

        final TableDownloader.Downloaded downloaded = TableDownloader.trim(file, 2);

        assertThat(downloaded).isEqualTo(new TableDownloader.Downloaded(2, 2, true));
        assertThat(Files.readAllLines(file)).containsExactly("| A | B |", "| --- | --- |", "| 1 | 2 |",
                "| 3 | 4 |");
    }

    @Test
    void aTableWithinTheMostAskedForIsKeptAsItIs() throws Exception {
        final Path file = tempDir.resolve("table.md");
        Files.writeString(file, "| A |\n| --- |\n| 1 |\n");

        assertThat(TableDownloader.trim(file, 5)).isEqualTo(new TableDownloader.Downloaded(1, 1, false));
        assertThat(Files.readAllLines(file)).hasSize(3);
    }

    @Test
    void columnsAreCountedIgnoringEscapedPipes() {
        assertThat(TableDownloader.columnCount("| A | B | C |")).isEqualTo(3);
        assertThat(TableDownloader.columnCount("| A \\| B | C |")).isEqualTo(2);
        assertThat(TableDownloader.columnCount("")).isZero();
    }
}
