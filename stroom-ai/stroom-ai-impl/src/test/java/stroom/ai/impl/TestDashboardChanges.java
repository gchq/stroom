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

import stroom.ai.impl.DashboardChanges.Change;
import stroom.ai.impl.DashboardChanges.Checked;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestDashboardChanges {

    private static final List<String> COLUMNS = List.of("EventTime", "UserId", "EventType");

    @Test
    void eachOperationIsReadAndDescribed() {
        final List<Change> changes = DashboardChanges.parse(List.of(
                Map.of("op", "group", "column", "EventType", "level", 1),
                Map.of("op", "UNGROUP", "column", "UserId"),
                Map.of("op", "add_column", "name", "Count", "expression", "count()", "format", "number",
                        "position", 2),
                Map.of("op", "edit_column", "column", "UserId", "name", "User", "expression", "upperCase(${UserId})"),
                Map.of("op", "hide_column", "column", "EventTime"),
                Map.of("op", "show_column", "column", "EventTime"),
                Map.of("op", "sort", "column", "Count", "direction", "descending", "priority", 1),
                Map.of("op", "sort", "column", "EventTime", "direction", "none"),
                Map.of("op", "filter", "column", "EventType", "filter", "Logon"),
                Map.of("op", "filter", "column", "EventType", "filter", "")));

        assertThat(DashboardChanges.describe(changes)).containsExactly(
                "Group by 'EventType' (level 1)",
                "Stop grouping by 'UserId'",
                "Add column 'Count' with expression count(), formatted as Number, at position 2",
                "Change column 'UserId': rename it 'User', set its expression to upperCase(${UserId})",
                "Hide column 'EventTime'",
                "Show column 'EventTime'",
                "Sort by 'Count', descending (priority 1)",
                "Stop sorting by 'EventTime'",
                "Filter column 'EventType' by 'Logon'",
                "Clear the filter on column 'EventType'");
        // Only the fields each has are given to the browser.
        assertThat(changes.get(1).toMap()).containsOnlyKeys("op", "column");
    }

    @Test
    void badChangesAreRefusedSayingWhy() {
        assertThatThrownBy(() -> DashboardChanges.parse(null)).hasMessageContaining("Give changes");
        assertThatThrownBy(() -> DashboardChanges.parse(List.of())).hasMessageContaining("Give changes");
        assertThatThrownBy(() -> DashboardChanges.parse(List.of("group")))
                .hasMessageContaining("isn't an object");
        assertThatThrownBy(() -> DashboardChanges.parse(List.of(Map.of("op", "drop_table"))))
                .hasMessageContaining("op must be one of");
        assertThatThrownBy(() -> DashboardChanges.parse(List.of(Map.of("op", "group", "column", "UserId"))))
                .hasMessageContaining("the level");
        assertThatThrownBy(() -> DashboardChanges.parse(List.of(Map.of("op", "group", "column", "UserId",
                "level", 0)))).hasMessageContaining("whole number from 1");
        assertThatThrownBy(() -> DashboardChanges.parse(List.of(Map.of("op", "hide_column"))))
                .hasMessageContaining("needs the column");
        assertThatThrownBy(() -> DashboardChanges.parse(List.of(Map.of("op", "add_column", "name", "X"))))
                .hasMessageContaining("a name and an expression");
        assertThatThrownBy(() -> DashboardChanges.parse(List.of(Map.of("op", "edit_column", "column", "X"))))
                .hasMessageContaining("a new name, expression or format");
        assertThatThrownBy(() -> DashboardChanges.parse(List.of(Map.of("op", "sort", "column", "X",
                "direction", "up")))).hasMessageContaining("direction");
        assertThatThrownBy(() -> DashboardChanges.parse(List.of(Map.of("op", "filter", "column", "X"))))
                .hasMessageContaining("filter text");
        assertThatThrownBy(() -> DashboardChanges.parse(List.of(Map.of("op", "add_column", "name", "X",
                "expression", "count()", "format", "Fancy")))).hasMessageContaining("format must be one of");
        assertThatThrownBy(() -> DashboardChanges.parse(List.of(Map.of("op", "add_column", "name", "X\u0000",
                "expression", "count()")))).hasMessageContaining("control characters");
        assertThatThrownBy(() -> DashboardChanges.parse(java.util.Collections.nCopies(
                DashboardChanges.MAX_CHANGES + 1, Map.of("op", "hide_column", "column", "X"))))
                .hasMessageContaining("at most");
    }

    @Test
    void columnsAreCheckedFollowingTheChangesBeforeThem() {
        final Checked checked = DashboardChanges.check(DashboardChanges.parse(List.of(
                // Matched ignoring case, and given its name as the table has it.
                Map.of("op", "group", "column", "eventtype", "level", 1),
                Map.of("op", "add_column", "name", "Count", "expression", "count()"),
                Map.of("op", "sort", "column", "Count", "direction", "descending"),
                Map.of("op", "edit_column", "column", "UserId", "name", "User"),
                Map.of("op", "filter", "column", "User", "filter", "admin"))), COLUMNS);

        assertThat(checked.problems()).isEmpty();
        assertThat(checked.changes().getFirst().column()).isEqualTo("EventType");
    }

    @Test
    void unknownOrClashingColumnsAreProblems() {
        final Checked checked = DashboardChanges.check(DashboardChanges.parse(List.of(
                Map.of("op", "hide_column", "column", "Nope"),
                Map.of("op", "add_column", "name", "UserId", "expression", "count()"),
                Map.of("op", "edit_column", "column", "EventTime", "name", "EventType"))), COLUMNS);

        assertThat(checked.problems()).containsExactly(
                "Change 1 (hide_column): the table has no column 'Nope'. Its columns are: EventTime, UserId, "
                + "EventType.",
                "Change 2 (add_column): the table already has a column 'UserId'. Give another name, or use "
                + "edit_column.",
                "Change 3 (edit_column): the table already has a column 'EventType'.");
    }
}
