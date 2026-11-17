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

package stroom.dataconnection.impl;

import stroom.dataconnection.shared.DataColumn;
import stroom.dataconnection.shared.DataColumnType;
import stroom.dataconnection.shared.DataConnectionDoc;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestDataColumns {

    private static final DataConnectionDoc DOC = DataConnectionDoc.builder().uuid("uuid-1").name("Teams").build();

    @Test
    void columnsMustHaveNames() {
        DataColumns.requireNamed(DOC, List.of(new DataColumn("team", DataColumnType.TEXT)));

        assertThatThrownBy(() -> DataColumns.requireNamed(DOC, List.of(new DataColumn(" ", DataColumnType.TEXT))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("a column with no name");
    }

    @Test
    void namesMustDifferIgnoringCase() {
        DataColumns.requireUniqueNames(DOC, List.of(
                new DataColumn("team", DataColumnType.TEXT),
                new DataColumn("size", DataColumnType.LONG)), "column");

        assertThatThrownBy(() -> DataColumns.requireUniqueNames(DOC, List.of(
                new DataColumn("cn", DataColumnType.TEXT),
                new DataColumn("CN", DataColumnType.TEXT)), "attribute"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Data Connection 'Teams' has more than one attribute named 'CN'.");
    }
}
