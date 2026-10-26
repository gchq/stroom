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
import stroom.docref.DocRef;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

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
    void aSelectionFilterIsReadAndDescribed() {
        final Change change = DashboardChanges.parse(List.of(Map.of("op", "add_selection_filter",
                "target", "Events", "chart", "Codes", "mode", "table", "match", "any",
                "terms", List.of(Map.of("field", "Sender", "selection", "node"),
                        Map.of("field", "Recipient", "selection", "node"))))).getFirst();

        assertThat(change.target()).isEqualTo("Events");
        assertThat(change.name()).isEqualTo("Codes");
        assertThat(change.value()).isEqualTo(DashboardChanges.TABLE_FILTER);
        assertThat(change.settings()).containsExactly(Map.entry("Sender", "node"), Map.entry("Recipient", "node"));
        assertThat(DashboardChanges.describe(List.of(change))).containsExactly(
                "Filter the rows of 'Events' by what is selected in chart 'Codes': Sender = its node or Recipient "
                + "= its node");
    }

    @Test
    void eachOperationReadsBackFromTheFormTheBrowserIsGiven() {
        // A proposal's action is checked again from its changes as the browser is given them, so each must read
        // back as it was. Seen in testing: a selection filter didn't, so its Apply button was never shown.
        final List<Change> changes = new ArrayList<>(DashboardChanges.parse(List.of(
                Map.of("op", "group", "column", "EventType", "level", 1),
                Map.of("op", "ungroup", "column", "UserId"),
                Map.of("op", "add_column", "name", "Count", "expression", "count()", "position", 2),
                Map.of("op", "edit_column", "column", "UserId", "name", "User", "expression", "upperCase(${UserId})"),
                Map.of("op", "hide_column", "column", "EventTime"),
                Map.of("op", "sort", "column", "Count", "direction", "descending", "priority", 1),
                Map.of("op", "filter", "column", "EventType", "filter", "Logon"),
                Map.of("op", "rename", "target", "Events", "target_id", "table-1", "name", "Logons"),
                Map.of("op", "remove_selection_filter", "target", "Events", "target_id", "table-1",
                        "filter", "ab12c"))));
        changes.add(DashboardChanges.parse(List.of(Map.of("op", "add_selection_filter",
                        "target", "Query", "chart", "Network", "mode", "query", "match", "any",
                        "terms", List.of(Map.of("field", "user.full_name", "selection", "node"),
                                Map.of("field", "user.target.name", "selection", "node")))))
                .getFirst()
                .toBuilder()
                .targetId("query-1")
                .ref("vis-1")
                .query(ExpressionOperator.builder()
                        .op(Op.OR)
                        .addTerm(ExpressionTerm.builder().field("user.full_name").condition(Condition.EQUALS)
                                .value("${component.vis-1.selection.node}").build())
                        .addTerm(ExpressionTerm.builder().field("user.target.name").condition(Condition.EQUALS)
                                .value("${component.vis-1.selection.node}").build())
                        .build())
                .build());

        assertThat(DashboardChanges.parse(changes.stream().map(Change::toMap).toList()))
                .containsExactlyElementsOf(changes);
    }

    @Test
    void aSelectionFilterNeedsItsTargetChartModeAndTerms() {
        final Map<String, Object> filter = Map.of("op", "add_selection_filter", "target", "Events", "chart", "Codes",
                "mode", "query", "terms", List.of(Map.of("field", "event.code", "selection", "category")));
        assertThat(DashboardChanges.parse(List.of(filter)).getFirst().filter()).isEqualTo("all");

        assertThatThrownBy(() -> DashboardChanges.parse(List.of(without(filter, "mode"))))
                .hasMessageContaining("needs mode: query")
                .hasMessageContaining("Ask the user which if they haven't said.");
        assertThatThrownBy(() -> DashboardChanges.parse(List.of(without(filter, "chart"))))
                .hasMessageContaining("needs the target");
        assertThatThrownBy(() -> DashboardChanges.parse(List.of(with(filter, "terms", List.of()))))
                .hasMessageContaining("needs terms");
        assertThatThrownBy(() -> DashboardChanges.parse(List.of(with(filter, "terms",
                List.of(Map.of("field", "a", "selection", "x"), Map.of("field", "a", "selection", "x"))))))
                .hasMessageContaining("'a' = x is in two terms");
        // A field may be compared with more than one part of the selection, e.g. a node and a link's source.
        final Change both = DashboardChanges.parse(List.of(with(filter, "terms",
                List.of(Map.of("field", "a", "selection", "node"), Map.of("field", "a", "selection", "source")))))
                .getFirst();
        assertThat(both.settings()).containsExactly(Map.entry("a", "node,source"));
        assertThat(DashboardChanges.describe(List.of(both))).singleElement().asString()
                .endsWith("a = its node and a = its source");
        assertThat(DashboardChanges.parse(List.of(both.toMap()))).containsExactly(both);
        assertThatThrownBy(() -> DashboardChanges.parse(List.of(with(filter, "match", "most"))))
                .hasMessageContaining("match must be all or any");
    }

    private static Map<String, Object> without(final Map<String, Object> map, final String key) {
        final Map<String, Object> copy = new LinkedHashMap<>(map);
        copy.remove(key);
        return copy;
    }

    private static Map<String, Object> with(final Map<String, Object> map, final String key, final Object value) {
        final Map<String, Object> copy = new LinkedHashMap<>(map);
        copy.put(key, value);
        return copy;
    }

    @Test
    void aQueryExpressionIsReadBackAsItWasWrittenAndDescribedInWords() {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .op(Op.OR)
                .addTerm(ExpressionTerm.builder().field("Text").condition(Condition.EQUALS).value("*Afghan*")
                        .build())
                .addOperator(ExpressionOperator.builder()
                        .op(Op.NOT)
                        .addTerm(ExpressionTerm.builder().field("User").condition(Condition.IN_DICTIONARY)
                                .docRef(new DocRef("Dictionary", "dict-uuid", "Bots")).build())
                        .build())
                .addTerm(ExpressionTerm.builder().field("Lang").condition(Condition.EQUALS).value("en")
                        .enabled(false).build())
                .build();
        final Change built = DashboardChanges.parse(List.of(Map.of("op", "set_expression",
                "where", "Text = \"*Afghan*\"\n  and not User in dictionary \"Bots\""))).getFirst()
                .withQuery(expression);

        // As stored with the button and read back when it is offered.
        final Change readBack = DashboardChanges.parse(List.of(built.toMap())).getFirst();

        assertThat(readBack.query()).isEqualTo(expression);
        // Line breaks in the conditions are kept as spaces.
        assertThat(readBack.where()).isEqualTo("Text = \"*Afghan*\" and not User in dictionary \"Bots\"");
        assertThat(DashboardChanges.check(List.of(readBack), COLUMNS).problems()).isEmpty();
        assertThat(readBack.describe()).startsWith("Set the query to find: Text is '*Afghan*' or not (");
    }

    @Test
    void tablesAddedAsTabsAreSetUpByTheChangesAfterThem() {
        // A tab per event category, each with its own columns and a filter on the category.
        final List<Change> changes = DashboardChanges.parse(List.of(
                Map.of("op", "add_table", "name", "Logons"),
                Map.of("op", "add_column", "table", "Logons", "name", "User", "expression", "${UserId}"),
                Map.of("op", "add_column", "table", "logons", "name", "EventType", "expression", "${EventType}"),
                Map.of("op", "filter", "table", "Logons", "column", "eventtype", "filter", "Logon"),
                Map.of("op", "add_table", "name", "Everything", "copy", true),
                Map.of("op", "move_column", "table", "Everything", "column", "EventType", "position", 1),
                Map.of("op", "move_column", "column", "UserId", "position", 3)));

        final Checked checked = DashboardChanges.check(changes, COLUMNS, "Events", List.of("Events", "Errors"));

        assertThat(checked.problems()).isEmpty();
        assertThat(DashboardChanges.describe(checked.changes())).containsExactly(
                "Add table 'Logons' as a tab beside this one, fed by the same query, with no columns yet",
                "In table 'Logons': Add column 'User' with expression ${UserId}, at the end",
                "In table 'logons': Add column 'EventType' with expression ${EventType}, at the end",
                // Its column, as the new table has it.
                "In table 'Logons': Filter column 'EventType' by 'Logon'",
                "Add table 'Everything' as a tab beside this one, fed by the same query, starting with this "
                + "table's columns",
                "In table 'Everything': Move column 'EventType' to position 1",
                "Move column 'UserId' to position 3");
        assertThat(checked.changes().get(4).toMap()).containsEntry("copy", true);
    }

    @Test
    void addedTablesMustHaveNewNamesAndChangesOnlyTheirOwnTables() {
        final List<Change> changes = DashboardChanges.parse(List.of(
                Map.of("op", "add_table", "name", "errors"),
                Map.of("op", "hide_column", "table", "Errors", "column", "UserId"),
                Map.of("op", "add_table", "name", "Empty"),
                Map.of("op", "hide_column", "table", "Empty", "column", "UserId"),
                Map.of("op", "move_column", "column", "Nope", "position", 1)));

        assertThat(DashboardChanges.check(changes, COLUMNS, "Events", List.of("Events", "Errors")).problems())
                .containsExactly(
                        "Change 1 (add_table): the dashboard already has a table 'errors'. Give another name.",
                        // Checked as the table it would add, which has no columns.
                        "Change 2 (hide_column): table 'Errors' has no column 'UserId'. Its columns are: none yet.",
                        "Change 4 (hide_column): table 'Empty' has no column 'UserId'. Its columns are: none yet.",
                        "Change 5 (move_column): the table has no column 'Nope'. Its columns are: EventTime, UserId, "
                        + "EventType.");
        assertThatThrownBy(() -> DashboardChanges.parse(List.of(Map.of("op", "add_table"))))
                .hasMessageContaining("add_table needs the new table's name");
        assertThatThrownBy(() -> DashboardChanges.parse(List.of(Map.of("op", "move_column", "column", "UserId"))))
                .hasMessageContaining("move_column needs the position");
        // An existing table other than the proposal's can't be changed from it.
        assertThat(DashboardChanges.check(DashboardChanges.parse(List.of(Map.of("op", "hide_column",
                "table", "Other", "column", "UserId"))), COLUMNS, "Events", List.of("Events", "Other"))
                .problems())
                .singleElement().asString().contains("there is no table 'Other' to change");
    }

    @Test
    void componentsAreRenamedAndTheirSettingsChangedAsResolved() {
        final List<Change> changes = DashboardChanges.parse(List.of(
                Map.of("op", "rename", "name", "Tweets"),
                Map.of("op", "rename", "target", "Query", "name", "Search"),
                Map.of("op", "set_setting", "target", "Query", "setting", "queryOnOpen", "value", true),
                Map.of("op", "set_setting", "setting", "pageSize", "value", 50),
                Map.of("op", "set_setting", "target", "Preview", "setting", "pipeline", "value", "p-uuid")));
        final List<Change> resolved = List.of(
                changes.get(0).withResolved("table-1", null, null, null),
                changes.get(1).withResolved("query-1", null, null, null),
                changes.get(2).withResolved("query-1", "true", null, null),
                changes.get(3).withResolved("table-1", "50", null, null),
                changes.get(4).withResolved("text-1", "p-uuid", null, new DocRef("Pipeline", "p-uuid", "Display")));

        // Not columns, so they aren't checked against them.
        assertThat(DashboardChanges.check(resolved, COLUMNS).problems()).isEmpty();
        assertThat(DashboardChanges.describe(resolved)).containsExactly(
                "Rename this table to 'Tweets'",
                "Rename 'Query' to 'Search'",
                "Set run the query when the dashboard opens (queryOnOpen) of 'Query' to on",
                "Set rows per page (pageSize) of this table to 50",
                "Set the pipeline it shows the data through (pipeline) of 'Preview' to Display");
        // As stored with the button and read back when it is offered.
        final Change readBack = DashboardChanges.parse(List.of(resolved.get(4).toMap())).getFirst();
        assertThat(readBack).isEqualTo(resolved.get(4));

        assertThatThrownBy(() -> DashboardChanges.parse(List.of(Map.of("op", "set_setting", "setting", "colour",
                "value", "red"))))
                .hasMessageContaining("set_setting needs a setting, one of pageSize");
        assertThatThrownBy(() -> DashboardChanges.parse(List.of(Map.of("op", "set_setting",
                "setting", "pageSize"))))
                .hasMessageContaining("set_setting needs the value");
        assertThatThrownBy(() -> DashboardChanges.parse(List.of(Map.of("op", "rename"))))
                .hasMessageContaining("rename needs the new name");
    }

    @Test
    void formattingRulesAreBuiltFromConditionsOnColumnsAndReadBackAsStored() {
        final List<Change> changes = DashboardChanges.parse(List.of(
                Map.of("op", "add_format_rule", "match", "any", "style", "red", "bold", true,
                        "conditions", List.of(
                                Map.of("column", "eventtype", "condition", "=", "value", "Logon Failure"),
                                Map.of("column", "UserId", "condition", "starts with", "value", "admin"))),
                Map.of("op", "add_format_rule", "position", 1, "hide", true,
                        "conditions", List.of(Map.of("column", "EventTime", "condition", "is null"))),
                Map.of("op", "remove_format_rule", "position", 3)));

        final Checked checked = DashboardChanges.check(changes, COLUMNS);

        assertThat(checked.problems()).isEmpty();
        assertThat(DashboardChanges.describe(checked.changes())).containsExactly(
                // Columns named as the table has them.
                "Add a formatting rule: rows where EventType is 'Logon Failure' or UserId starts with 'admin' get "
                + "a Red background, bold",
                "Add a formatting rule at position 1: rows where EventTime is empty are hidden",
                "Remove formatting rule 3");
        final Change readBack = DashboardChanges.parse(List.of(checked.changes().getFirst().toMap())).getFirst();
        assertThat(readBack).isEqualTo(checked.changes().getFirst());
        assertThat(checked.changes().getFirst().toMap()).containsEntry("style", "Red")
                .containsEntry("format_type", "background")
                .containsEntry("bold", true);
    }

    @Test
    void formattingRulesThatDontFitAreRefused() {
        assertThat(DashboardChanges.check(DashboardChanges.parse(List.of(Map.of("op", "add_format_rule",
                "style", "Red", "conditions", List.of(Map.of("column", "Nope", "condition", "=", "value", "x"))))),
                COLUMNS).problems())
                .singleElement().asString().contains("the table has no column 'Nope'");
        assertThatThrownBy(() -> DashboardChanges.parse(List.of(Map.of("op", "add_format_rule", "style", "Red"))))
                .hasMessageContaining("add_format_rule needs conditions");
        assertThatThrownBy(() -> DashboardChanges.parse(List.of(Map.of("op", "add_format_rule",
                "conditions", List.of(Map.of("column", "UserId", "condition", "=", "value", "x"))))))
                .hasMessageContaining("needs something to do");
        assertThatThrownBy(() -> DashboardChanges.parse(List.of(Map.of("op", "add_format_rule", "style", "Mauve",
                "conditions", List.of(Map.of("column", "UserId", "condition", "=", "value", "x"))))))
                .hasMessageContaining("style must be one of Red");
        assertThatThrownBy(() -> DashboardChanges.parse(List.of(Map.of("op", "add_format_rule", "style", "Red",
                "conditions", List.of(Map.of("column", "UserId", "condition", "like", "value", "x"))))))
                .hasMessageContaining("needs a column and a condition, one of =");
        assertThatThrownBy(() -> DashboardChanges.parse(List.of(Map.of("op", "add_format_rule", "style", "Red",
                "conditions", List.of(Map.of("column", "UserId", "condition", "="))))))
                .hasMessageContaining("needs a value");
        assertThatThrownBy(() -> DashboardChanges.parse(List.of(Map.of("op", "remove_format_rule"))))
                .hasMessageContaining("remove_format_rule needs the rule's number");
    }

    @Test
    void aTextWidgetFollowsTheTableOrOneAddedBeforeIt() {
        final List<Change> changes = DashboardChanges.parse(List.of(
                Map.of("op", "add_text", "name", "Preview"),
                Map.of("op", "add_table", "name", "Logons"),
                Map.of("op", "add_text", "name", "Logon preview", "table", "Logons"),
                Map.of("op", "add_text", "name", "Lost", "table", "Nope")));

        final Checked checked = DashboardChanges.check(changes, COLUMNS, "Events", List.of("Events"));

        assertThat(DashboardChanges.describe(checked.changes()).getFirst())
                .isEqualTo("Add a Text widget 'Preview' below this table, showing the event of its selected row");
        assertThat(DashboardChanges.describe(checked.changes()).get(2))
                .isEqualTo("Add a Text widget 'Logon preview' below table 'Logons', showing the event of its "
                           + "selected row");
        assertThat(checked.problems()).singleElement().asString()
                .contains("Change 4 (add_text): there is no table 'Nope' for it to follow");
        assertThatThrownBy(() -> DashboardChanges.parse(List.of(Map.of("op", "add_text"))))
                .hasMessageContaining("add_text needs the new Text widget's name");
    }

    @Test
    void anExpressionThatCantBeReadIsRefused() {
        final Map<String, Object> unknownOp = Map.of("op", "XOR", "children", List.of());
        final Map<String, Object> unknownCondition = Map.of("op", "AND",
                "children", List.of(Map.of("field", "x", "condition", "LIKE")));

        assertThatThrownBy(() -> DashboardChanges.parse(List.of(Map.of("op", "set_expression", "query", unknownOp))))
                .hasMessageContaining("the query's expression can't be read");
        assertThatThrownBy(() -> DashboardChanges.parse(List.of(Map.of("op", "set_expression",
                "query", unknownCondition))))
                .hasMessageContaining("the query's expression can't be read");
        assertThatThrownBy(() -> DashboardChanges.parse(List.of(Map.of("op", "set_expression"))))
                .hasMessageContaining("set_expression needs where");
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

    @Test
    void manyColumnsCanBeAddedInOneProposal() {
        // Splitting a large change over two proposals doesn't work: the second is proposed against the dashboard
        // as it was before the first was applied, so it must fit in one.
        final List<Map<String, Object>> adds = IntStream.rangeClosed(1, 30)
                .mapToObj(i -> Map.<String, Object>of("op", "add_column", "name", "Field" + i,
                        "expression", "${Field" + i + "}"))
                .toList();

        final Checked checked = DashboardChanges.check(DashboardChanges.parse(adds), COLUMNS);

        assertThat(checked.problems()).isEmpty();
        assertThat(checked.changes()).hasSize(30);
    }

    @Test
    void manyColumnsCanBeHiddenInOneProposal() {
        final List<String> columns = IntStream.rangeClosed(1, 30).mapToObj(i -> "Field" + i).toList();
        final List<Map<String, Object>> hides = columns.stream()
                .map(column -> Map.<String, Object>of("op", "hide_column", "column", column))
                .toList();

        final Checked checked = DashboardChanges.check(DashboardChanges.parse(hides), columns);

        assertThat(checked.problems()).isEmpty();
        assertThat(checked.changes()).hasSize(30);
    }

    @Test
    void asManyTablesAsAllowedCanEachBeSetUp() {
        // The tool's description suggests a tab per category: add_table, its columns and a filter. MAX_TABLES
        // tables, each with one column and a filter, should fit in one proposal.
        final List<Map<String, Object>> changes = new ArrayList<>();
        for (int i = 1; i <= DashboardChanges.MAX_TABLES; i++) {
            changes.add(Map.of("op", "add_table", "name", "Category " + i));
            changes.add(Map.of("op", "add_column", "table", "Category " + i, "name", "EventType",
                    "expression", "${EventType}"));
            changes.add(Map.of("op", "filter", "table", "Category " + i, "column", "EventType",
                    "filter", "Type" + i));
        }

        final Checked checked = DashboardChanges.check(DashboardChanges.parse(changes), COLUMNS, "Table",
                List.of("Table"));

        assertThat(checked.problems()).isEmpty();
        assertThat(checked.changes()).hasSize(DashboardChanges.MAX_TABLES * 3);
    }

    @Test
    void aVisualisationIsAddedBelowOrBesideATableAndReadBackAsStored() {
        final List<Change> changes = DashboardChanges.parse(List.of(
                Map.of("op", "add_vis", "name", "Users", "visualisation", "vis-uuid",
                        "settings", ordered("category", "UserId", "value", "", "limit", 10)),
                Map.of("op", "add_table", "name", "Logons"),
                Map.of("op", "add_vis", "name", "Logon chart", "table", "Logons", "visualisation", "vis-uuid",
                        "placement", "TAB")));

        final Checked checked = DashboardChanges.check(changes, COLUMNS, "Events", List.of("Events"));

        assertThat(checked.problems()).isEmpty();
        final Change named = checked.changes().getFirst().toBuilder()
                .doc(new DocRef("Visualisation", "vis-uuid", "Bar chart"))
                .build();
        // An empty value is the control's default, which a new component has anyway; numbers are text.
        assertThat(named.settings()).containsExactly(Map.entry("category", "UserId"), Map.entry("limit", "10"));
        assertThat(named.describe())
                .isEqualTo("Add a visualisation 'Users' (Bar chart) of this table as a tab with the dashboard's "
                           + "charts (at the bottom of the dashboard if it has none), with category 'UserId', "
                           + "limit '10'");
        assertThat(checked.changes().get(2).describe())
                .isEqualTo("Add a visualisation 'Logon chart' (vis-uuid) of table 'Logons' as a tab beside the "
                           + "table");
        assertThat(checked.columnsOf(null)).isEqualTo(COLUMNS);
        assertThat(checked.columnsOf("events")).isEqualTo(COLUMNS);
        assertThat(checked.columnsOf("Logons")).isEmpty();
        assertThat(checked.columnsOf("Nope")).isNull();

        // As stored with the button and read back when it is offered.
        final Map<String, Object> stored = named.toMap();
        assertThat(stored)
                .containsEntry("doc", Map.of("type", "Visualisation", "uuid", "vis-uuid", "name", "Bar chart"))
                .containsEntry("settings", Map.of("category", "UserId", "limit", "10"))
                .doesNotContainKey("placement");
        assertThat(checked.changes().get(2).toMap()).containsEntry("placement", "tab");
        assertThat(DashboardChanges.parse(List.of(stored)).getFirst()).isEqualTo(named);
    }

    @Test
    void addingAVisualisationNeedsANameAVisualisationAndATable() {
        assertThatThrownBy(() -> DashboardChanges.parse(List.of(Map.of("op", "add_vis", "visualisation", "v"))))
                .hasMessageContaining("add_vis needs the new Visualisation component's name");
        assertThatThrownBy(() -> DashboardChanges.parse(List.of(Map.of("op", "add_vis", "name", "Chart"))))
                .hasMessageContaining("add_vis needs visualisation");
        assertThatThrownBy(() -> DashboardChanges.parse(List.of(Map.of("op", "add_vis", "name", "Chart",
                "visualisation", "v", "placement", "above"))))
                .hasMessageContaining("placement must be one of charts, bottom, below, tab");
        assertThatThrownBy(() -> DashboardChanges.parse(List.of(Map.of("op", "add_vis", "name", "Chart",
                "visualisation", "v", "settings", "category=UserId"))))
                .hasMessageContaining("settings must be an object");
        assertThatThrownBy(() -> DashboardChanges.parse(List.of(Map.of("op", "add_vis", "name", "Chart",
                "visualisation", "v", "settings", Map.of("category", List.of("UserId"))))))
                .hasMessageContaining("the value of setting 'category' must be text");
        assertThatThrownBy(() -> DashboardChanges.parse(List.of(Map.of("op", "add_vis", "name", "Chart",
                "doc", Map.of("type", "Pipeline", "uuid", "p")))))
                .hasMessageContaining("can only show a Visualisation");

        assertThat(DashboardChanges.check(DashboardChanges.parse(List.of(Map.of("op", "add_vis", "name", "Chart",
                "visualisation", "v", "table", "Nope"))), COLUMNS, "Events", List.of("Events")).problems())
                .containsExactly("Change 1 (add_vis): there is no table 'Nope' for it to be linked to. It is "
                                 + "linked to table 'Events', or a table added before it with add_table.");
    }

    @Test
    void aVisualisationsSettingsAreChangedAndDescribed() {
        final List<Change> changes = DashboardChanges.parse(List.of(
                Map.of("op", "set_vis_settings", "target", "Users", "settings", ordered("category", "EventType",
                        "value", "")),
                Map.of("op", "set_vis_settings", "target", "Users", "visualisation", "pie-uuid")));

        assertThat(DashboardChanges.check(changes, COLUMNS).problems()).isEmpty();
        assertThat(DashboardChanges.describe(changes)).containsExactly(
                "Change visualisation 'Users': set category 'EventType', value back to its default",
                "Change visualisation 'Users': show pie-uuid");
        // An empty value is kept, to remove the control's value.
        final Change readBack = DashboardChanges.parse(List.of(changes.getFirst().toBuilder()
                .targetId("vis-1")
                .build()
                .toMap())).getFirst();
        assertThat(readBack.settings()).containsEntry("value", "");
        assertThat(readBack.targetId()).isEqualTo("vis-1");

        assertThatThrownBy(() -> DashboardChanges.parse(List.of(Map.of("op", "set_vis_settings",
                "settings", Map.of("a", "b")))))
                .hasMessageContaining("set_vis_settings needs the target");
        assertThatThrownBy(() -> DashboardChanges.parse(List.of(Map.of("op", "set_vis_settings",
                "target", "Users"))))
                .hasMessageContaining("needs settings to change, or a visualisation to switch to");
    }

    @Test
    void aVisualisationsControlsAreReadFromItsSettings() {
        final String settings = """
                {"data": {}, "tabs": [
                  {"name": "Data", "controls": [
                    {"id": "category", "type": "field", "label": "Category"},
                    {"id": "value", "type": "field"}]},
                  {"name": "Style", "controls": [{"id": "colour", "type": "text"}, {"type": "text"}]}]}
                """;

        assertThat(DashboardChanges.visControls(settings)).containsExactly(Map.entry("category", "field"),
                Map.entry("value", "field"), Map.entry("colour", "text"));
        assertThat(DashboardChanges.visControls("not json")).isEmpty();
        assertThat(DashboardChanges.visControls(null)).isEmpty();
        assertThat(DashboardChanges.visControls("{\"tabs\": 3}")).isEmpty();
    }

    @Test
    void aVisualisationsSettingsMustBeItsControlsWithColumnsOfItsTable() {
        final Map<String, String> controls = Map.of("category", "field", "colour", "text");

        assertThat(DashboardChanges.visSettingsProblems(Map.of("category", "UserId", "colour", "red"), controls,
                COLUMNS, "Bar chart", "table 'Events'")).isEmpty();
        // An empty value goes back to the default; unknown columns aren't checked.
        assertThat(DashboardChanges.visSettingsProblems(Map.of("category", ""), controls, COLUMNS, "Bar chart",
                "table 'Events'")).isEmpty();
        assertThat(DashboardChanges.visSettingsProblems(Map.of("category", "Nope"), controls, null, "Bar chart",
                "table 'Events'")).isEmpty();
        assertThat(DashboardChanges.visSettingsProblems(Map.of("category", "userid"), controls, COLUMNS,
                "Bar chart", "table 'Events'")).containsExactly("control 'category' takes a column of table "
                                                               + "'Events', which has no column 'userid' (did you "
                                                               + "mean 'UserId'?). Its columns are: EventTime, "
                                                               + "UserId, EventType.");
        assertThat(DashboardChanges.visSettingsProblems(Map.of("Colour", "red"), Map.of("colour", "text"),
                COLUMNS, "Bar chart", "table 'Events'")).containsExactly("Visualisation 'Bar chart' has no "
                                                                         + "control 'Colour' (did you mean "
                                                                         + "'colour'?). Its controls are: colour "
                                                                         + "(text).");
        assertThat(DashboardChanges.visSettingsProblems(Map.of("x", "y"), Map.of(), COLUMNS, "Plain",
                "table 'Events'")).containsExactly("Visualisation 'Plain' has no control 'x'. Its controls are: "
                                                   + "none.");
    }

    // A map keeping the order its keys are given in, as the model's JSON objects do.
    private static Map<String, Object> ordered(final Object... keysAndValues) {
        final Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put(String.valueOf(keysAndValues[i]), keysAndValues[i + 1]);
        }
        return map;
    }
}
