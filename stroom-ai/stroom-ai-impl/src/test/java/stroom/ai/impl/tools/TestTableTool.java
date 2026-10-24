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

package stroom.ai.impl.tools;

import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.impl.workspace.WorkspacePath;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestTableTool {

    @Test
    void aFileOfAKindThatIsNeverATableIsNotReadAsOne() {
        // Seen: a survey's JSON given to table, read as CSV, and refused for a stray quote on its first line.
        assertThatThrownBy(() -> TableTool.refuseUnlessATable(WorkspacePath.parse("/outputs/delinea_shapes.json")))
                .isInstanceOf(WorkspaceException.class)
                .hasMessage("/outputs/delinea_shapes.json is JSON, not a table: query it with jq. table reads .csv, "
                            + ".tsv and .md tables.");
        assertThatThrownBy(() -> TableTool.refuseUnlessATable(WorkspacePath.parse("/outputs/raw.xml")))
                .hasMessageContaining("read it with read_file or grep");
        assertThatCode(() -> TableTool.refuseUnlessATable(WorkspacePath.parse("/outputs/rows.csv")))
                .doesNotThrowAnyException();
        assertThatCode(() -> TableTool.refuseUnlessATable(WorkspacePath.parse("/attachments/12")))
                .doesNotThrowAnyException();
        assertThatCode(() -> TableTool.refuseUnlessATable(WorkspacePath.parse("/outputs/v1.2/table")))
                .doesNotThrowAnyException();
    }
}
