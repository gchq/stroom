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

package stroom.ai.impl.workspace;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;

class TestTableProfiler {

    private static final String TABLE = """
            | Host | Count | Time | Message |
            | --- | --- | --- | --- |
            | fw1 | 10 | 2026-10-01T10:00:00Z | login failed |
            | fw2 | 2.5 | 2026-10-02T11:00:00Z | login failed |
            | fw1 | -3 | 2026-10-03T12:00:00Z |  |
            | fw1 | 7 | 2026-10-04T13:00:00Z | a \\| b |
            """;

    @Test
    void testProfile() throws IOException {
        final String profile = TableProfiler.profile(new StringReader(TABLE), "Firewall events");

        assertThat(profile)
                .startsWith("Profile of Firewall events: 4 rows, 4 columns.")
                // Host: text, 2 distinct, fw1 most common.
                .contains("| Host | text | 0 | 2 | fw1 | fw2 | fw1 (3) |")
                // Count: numbers, min and max numerically.
                .contains("| Count | number | 0 | 4 | -3 | 10 | all different |")
                .contains("| Time | date/time | 0 | 4 | 2026-10-01T10:00:00Z | 2026-10-04T13:00:00Z |")
                // Message: one empty value, and an escaped pipe kept escaped.
                .contains("| Message | text | 1 | 2 |")
                .contains("login failed (2)")
                .contains("First 3 rows:")
                .contains("| fw2 | 2.5 |")
                .doesNotContain("| fw1 | 7 |");
    }

    @Test
    void testProfile_empty() throws IOException {
        assertThat(TableProfiler.profile(new StringReader(""), "Nothing"))
                .contains("0 rows, 0 columns")
                .contains("The table is empty.");
    }

    @Test
    void testProfile_manyDistinctValues() throws IOException {
        final StringBuilder sb = new StringBuilder("| Id |\n| --- |\n");
        for (int i = 0; i < TableProfiler.MAX_TRACKED_VALUES + 50; i++) {
            sb.append("| id").append(i).append(" |\n");
        }

        assertThat(TableProfiler.profile(new StringReader(sb.toString()), "Ids"))
                .contains("| Id | text | 0 | more than " + TableProfiler.MAX_TRACKED_VALUES + " |");
    }
}
