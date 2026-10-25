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

package stroom.ai.client;

import stroom.dashboard.client.event.ApplyDashboardChangeEvent;
import stroom.dashboard.client.table.TableChange;
import stroom.docref.DocRef;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.Format;
import stroom.query.api.Sort.SortDirection;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestUserActionsUi {

    @Test
    void anActionsLabelLinksTheDocumentItActsOn() {
        // Asked for by the user: the pipeline in "Enable processor filter 58 on pipeline 'Twitter-Events-V1.1'"
        // opens from the button's card.
        final DocRef pipeline = new DocRef("Pipeline", "a123a852-baaf-4ead-866f-96f756da1dea", "Twitter-Events-V1.1");
        assertThat(UserActionsUi.labelMarkdown("Enable processor filter 58 on pipeline 'Twitter-Events-V1.1'",
                pipeline)).isEqualTo("Enable processor filter 58 on pipeline '[Twitter-Events-V1.1](?action=open-doc"
                                     + "&docType=Pipeline&docUuid=a123a852-baaf-4ead-866f-96f756da1dea)'");
        // A name Markdown would read is shown as it is; without the document, or not named, nothing is linked.
        final DocRef starred = new DocRef("Pipeline", "u", "*ACME_V1*");
        assertThat(UserActionsUi.labelMarkdown("Disable it on pipeline '*ACME_V1*'", starred))
                .isEqualTo("Disable it on pipeline '[\\*ACME\\_V1\\*](?action=open-doc&docType=Pipeline&docUuid=u)'");
        assertThat(UserActionsUi.labelMarkdown("Open <it> & see", null)).isEqualTo("Open \\<it\\> \\& see");
        assertThat(UserActionsUi.labelMarkdown("Open something else", pipeline)).isEqualTo("Open something else");
        assertThat(UserActionsUi.labelMarkdown(null, pipeline)).isEmpty();
    }

    @Test
    void aButtonCanBeUsedUntilItIsUsedOrExpires() {
        assertThat(UserActionsUi.isPending("PENDING", 2_000, 1_000)).isTrue();
        assertThat(UserActionsUi.isPending("PENDING", 1_000, 1_000)).isFalse();
        assertThat(UserActionsUi.isPending("DONE", 2_000, 1_000)).isFalse();
        assertThat(UserActionsUi.isPending(null, 2_000, 1_000)).isFalse();
    }

    @Test
    void whatIsShownInPlaceOfAButton() {
        assertThat(UserActionsUi.outcomeText("DONE", "Enabled processor filter 31."))
                .isEqualTo("Enabled processor filter 31.");
        assertThat(UserActionsUi.outcomeText("PENDING", null)).isEqualTo("This button has expired.");
        assertThat(UserActionsUi.outcomeText("FAILED", " ")).isEqualTo("FAILED");
    }

    @Test
    void buttonTexts() {
        assertThat(UserActionsUi.buttonText(UserActionsUi.ENABLE_PROCESSOR_FILTER)).isEqualTo("Enable");
        assertThat(UserActionsUi.buttonText(UserActionsUi.DISABLE_PROCESSOR_FILTER)).isEqualTo("Disable");
        assertThat(UserActionsUi.buttonText(UserActionsUi.OPEN_DOCUMENT)).isEqualTo("Open");
        assertThat(UserActionsUi.buttonText(UserActionsUi.CREATE_QUERY)).isEqualTo("Create query…");
        assertThat(UserActionsUi.buttonText(UserActionsUi.CREATE_DASHBOARD)).isEqualTo("Create dashboard…");
        assertThat(UserActionsUi.buttonText(UserActionsUi.APPLY_CODE_CHANGE)).isEqualTo("Review and apply…");
        assertThat(UserActionsUi.buttonText("something_else")).isEqualTo("Do it");
    }

    @Test
    void aRevisedQueryIsAppliedToItsQueryAndCanBeUndoneOnceApplied() {
        assertThat(UserActionsUi.buttonText(UserActionsUi.APPLY_QUERY_CHANGE)).isEqualTo("Apply to query");
        assertThat(UserActionsUi.isAppliedCodeChange(UserActionsUi.APPLY_QUERY_CHANGE, "DONE")).isTrue();
        assertThat(UserActionsUi.isAppliedCodeChange(UserActionsUi.APPLY_QUERY_CHANGE, "PENDING")).isFalse();
        assertThat(UserActionsUi.isAppliedCodeChange(UserActionsUi.APPLY_CODE_CHANGE, "DONE")).isTrue();
    }

    @Test
    void aDocumentationChangeIsReviewedAndCanBeUndoneOnceApplied() {
        assertThat(UserActionsUi.buttonText(UserActionsUi.APPLY_DOCUMENTATION_CHANGE)).isEqualTo("Review and apply…");
        assertThat(UserActionsUi.isAppliedCodeChange(UserActionsUi.APPLY_DOCUMENTATION_CHANGE, "DONE")).isTrue();
        assertThat(UserActionsUi.isAppliedCodeChange(UserActionsUi.APPLY_DOCUMENTATION_CHANGE, "PENDING")).isFalse();
        // Shown as a diff, not as code to copy.
        assertThat(UserActionsUi.showsCode(UserActionsUi.APPLY_DOCUMENTATION_CHANGE)).isFalse();
    }

    @Test
    void aProposedQueryAndAnElasticsearchRequestAreShownAsCode() {
        assertThat(UserActionsUi.showsCode(UserActionsUi.CREATE_QUERY)).isTrue();
        // The dashboard's StroomQL.
        assertThat(UserActionsUi.showsCode(UserActionsUi.CREATE_DASHBOARD)).isTrue();
        assertThat(UserActionsUi.codeLanguage(UserActionsUi.ELASTICSEARCH_REQUEST)).isEqualTo("elasticsearch");
        assertThat(UserActionsUi.codeLanguage(UserActionsUi.CREATE_QUERY)).isEqualTo("stroomql");
        assertThat(UserActionsUi.codeLanguage(UserActionsUi.CREATE_DASHBOARD)).isEqualTo("stroomql");
        assertThat(UserActionsUi.showsCode(UserActionsUi.ELASTICSEARCH_REQUEST)).isTrue();
        assertThat(UserActionsUi.showsCode(UserActionsUi.OPEN_DOCUMENT)).isFalse();
    }

    @Test
    void onlyAnAppliedCodeChangeOffersUndoApply() {
        assertThat(UserActionsUi.isAppliedCodeChange(UserActionsUi.APPLY_CODE_CHANGE, "DONE")).isTrue();
        assertThat(UserActionsUi.isAppliedCodeChange(UserActionsUi.APPLY_CODE_CHANGE, "PENDING")).isFalse();
        assertThat(UserActionsUi.isAppliedCodeChange(UserActionsUi.CREATE_QUERY, "DONE")).isFalse();
        assertThat(UserActionsUi.isAppliedDashboardChange(UserActionsUi.APPLY_DASHBOARD_CHANGE, "DONE")).isTrue();
        assertThat(UserActionsUi.isAppliedDashboardChange(UserActionsUi.APPLY_DASHBOARD_CHANGE, "PENDING")).isFalse();
        assertThat(UserActionsUi.isAppliedDashboardChange(UserActionsUi.APPLY_QUERY_CHANGE, "DONE")).isFalse();
        // A proposed change is shown as a diff, not as code to copy.
        assertThat(UserActionsUi.showsCode(UserActionsUi.APPLY_CODE_CHANGE)).isFalse();
        assertThat(UserActionsUi.UNDO_WITHDRAWN).contains("Ctrl+Z");
    }

    @Test
    void theApplyToDashboardButtonSaysWhyItCantBeUsed() {
        assertThat(UserActionsUi.buttonText(UserActionsUi.APPLY_DASHBOARD_CHANGE)).isEqualTo("Apply to dashboard");
        // Editable, open or not.
        assertThat(UserActionsUi.applyDashboardDisabledReason(true, false, false)).isNull();
        assertThat(UserActionsUi.applyDashboardDisabledReason(true, true, true)).isNull();
        // Not editable when proposed, or open and read-only now.
        assertThat(UserActionsUi.applyDashboardDisabledReason(false, false, false))
                .isEqualTo(ApplyDashboardChangeEvent.NOT_EDITABLE);
        assertThat(UserActionsUi.applyDashboardDisabledReason(true, true, false))
                .isEqualTo(ApplyDashboardChangeEvent.NOT_EDITABLE);
    }

    @Test
    void aProposedOperationIsReadForTheDashboard() {
        final Map<String, String> fields = Map.of("op", "add_column", "name", "Count", "expression", "count()",
                "format", "Number", "position", "2");
        final TableChange change = UserActionsUi.tableChange(fields::get, null);

        assertThat(change.getOp()).isEqualTo(TableChange.ADD_COLUMN);
        assertThat(change.getName()).isEqualTo("Count");
        assertThat(change.getExpression()).isEqualTo("count()");
        assertThat(change.getFormat().getType()).isEqualTo(Format.Type.NUMBER);
        assertThat(change.getPosition()).isEqualTo(2);
        assertThat(change.getLevel()).isNull();

        final TableChange sort = UserActionsUi.tableChange(Map.of("op", "sort", "column", "Count",
                "direction", "descending", "priority", "1.0")::get, null);
        assertThat(sort.getSortDirection()).isEqualTo(SortDirection.DESCENDING);
        assertThat(sort.getPriority()).isEqualTo(1);
        assertThat(UserActionsUi.tableChange(Map.of("op", "sort", "direction", "none")::get, null)
                .getSortDirection())
                .isNull();
        assertThat(UserActionsUi.tableChange(Map.of("op", "group", "level", "x")::get, null).getLevel()).isNull();
    }

    @Test
    void aQueryExpressionIsReadAsTheServerWritesIt() {
        // As the server's DashboardChanges.expressionToMap writes it.
        final Map<String, Object> written = Map.of("op", "OR", "children", List.of(
                Map.of("field", "Text", "condition", "EQUALS", "value", "*Afghan*"),
                Map.of("op", "NOT", "children", List.of(Map.of("field", "User", "condition", "IN_DICTIONARY",
                        "doc", Map.of("type", "Dictionary", "uuid", "dict-uuid", "name", "Bots")))),
                Map.of("field", "Lang", "condition", "EQUALS", "value", "en", "enabled", false)));

        final TableChange change = UserActionsUi.tableChange(Map.of("op", "set_expression")::get, written);

        assertThat(change.getOp()).isEqualTo(TableChange.SET_EXPRESSION);
        assertThat(change.getQuery()).isEqualTo(ExpressionOperator.builder()
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
                .build());
    }

    @Test
    void aChangeToAnAddedTableNamesItsTable() {
        final TableChange added = UserActionsUi.tableChange(Map.of("op", "add_table", "name", "Logons",
                "copy", "true")::get, null);
        final TableChange column = UserActionsUi.tableChange(Map.of("op", "move_column", "table", "Logons",
                "column", "User", "position", "2.0")::get, null);

        assertThat(added.getOp()).isEqualTo(TableChange.ADD_TABLE);
        assertThat(added.isCopy()).isTrue();
        assertThat(column.getTable()).isEqualTo("Logons");
        assertThat(column.getPosition()).isEqualTo(2);
        assertThat(UserActionsUi.tableChange(Map.of("op", "add_table", "name", "x")::get, null).isCopy())
                .isFalse();
    }

    @Test
    void aSettingsChangeNamesItsComponentAndAnyDocument() {
        final TableChange change = UserActionsUi.tableChange(Map.of("op", "set_setting", "target", "Preview",
                        "target_id", "text-1", "setting", "pipeline", "value", "p-uuid")::get, null,
                Map.of("type", "Pipeline", "uuid", "p-uuid", "name", "Display"));

        assertThat(change.getTarget()).isEqualTo("Preview");
        assertThat(change.getTargetId()).isEqualTo("text-1");
        assertThat(change.getSetting()).isEqualTo("pipeline");
        assertThat(change.getValue()).isEqualTo("p-uuid");
        assertThat(change.getDoc()).isEqualTo(new DocRef("Pipeline", "p-uuid", "Display"));
        assertThat(UserActionsUi.tableChange(Map.of("op", "rename", "name", "Tweets")::get, null, "not a doc")
                .getDoc()).isNull();
    }

    @Test
    void aVisualisationChangeGivesItsSettingsAsJsonAndItsPlacement() {
        // The settings object, as the chat's JSON gives it as text.
        final TableChange added = UserActionsUi.tableChange(Map.of("op", "add_vis", "name", "Users",
                        "table", "Logons", "settings", "{\"category\":\"UserId\"}", "placement", "tab",
                        "value", "ignored")::get, null,
                Map.of("type", "Visualisation", "uuid", "vis-uuid", "name", "Bar chart"));

        assertThat(added.getOp()).isEqualTo(TableChange.ADD_VIS);
        assertThat(added.getName()).isEqualTo("Users");
        assertThat(added.getTable()).isEqualTo("Logons");
        assertThat(added.getValue()).isEqualTo("{\"category\":\"UserId\"}");
        assertThat(added.getPlacement()).isEqualTo(TableChange.TAB);
        assertThat(added.getDoc()).isEqualTo(new DocRef("Visualisation", "vis-uuid", "Bar chart"));

        final TableChange changed = UserActionsUi.tableChange(Map.of("op", "set_vis_settings", "target", "Users",
                "target_id", "vis-1", "settings", "{\"value\":\"\"}")::get, null, null);

        assertThat(changed.getTargetId()).isEqualTo("vis-1");
        assertThat(changed.getValue()).isEqualTo("{\"value\":\"\"}");
        assertThat(changed.getPlacement()).isNull();
        assertThat(changed.getDoc()).isNull();
    }

    @Test
    void aFormattingRuleIsReadWithWhatItDoes() {
        final Map<String, Object> conditions = Map.of("op", "AND", "children", List.of(
                Map.of("field", "EventType", "condition", "EQUALS", "value", "Logon Failure")));

        final TableChange rule = UserActionsUi.tableChange(Map.of("op", "add_format_rule", "style", "Red",
                "format_type", "text", "bold", "true", "position", "2.0")::get, conditions);

        assertThat(rule.getOp()).isEqualTo(TableChange.ADD_FORMAT_RULE);
        assertThat(rule.getQuery().getChildren()).hasSize(1);
        assertThat(rule.getStyle()).isEqualTo("Red");
        assertThat(rule.getFormatType()).isEqualTo("text");
        assertThat(rule.isBold()).isTrue();
        assertThat(rule.isItalic()).isFalse();
        assertThat(rule.isHide()).isFalse();
        assertThat(rule.getPosition()).isEqualTo(2);
    }

    @Test
    void anExpressionThatCantBeReadIsNull() {
        assertThat(UserActionsUi.expression(Map.of("op", "XOR"))).isNull();
        assertThat(UserActionsUi.expression(Map.of("op", "AND", "children", List.of(Map.of("field", "x",
                "condition", "LIKE"))))).isNull();
        assertThat(UserActionsUi.expression("not a map")).isNull();
        // A term on its own isn't a query's expression.
        assertThat(UserActionsUi.expression(Map.of("field", "x", "condition", "EQUALS"))).isNull();
    }

    @Test
    void createVisualisationButtonSaysWhatItDoes() {
        assertThat(UserActionsUi.buttonText(UserActionsUi.CREATE_VISUALISATION)).isEqualTo("Create visualisation…");
    }

    @Test
    void quotesStroomQlStrings() {
        assertThat(UserActionsUi.quoteStroomQl("Chat user connections")).isEqualTo("\"Chat user connections\"");
        assertThat(UserActionsUi.quoteStroomQl("a \"b\" \\c")).isEqualTo("\"a \\\"b\\\" \\\\c\"");
    }

    @Test
    void addsAShowClauseAtTheEnd() {
        assertThat(UserActionsUi.withShowClause("from Messages\nselect Sender, Recipient\n",
                "show \"G\" (source = \"Sender\")"))
                .isEqualTo("from Messages\nselect Sender, Recipient\nshow \"G\" (source = \"Sender\")\n");
        assertThat(UserActionsUi.withShowClause("from Messages\nselect Sender", "show \"G\" ()"))
                .isEqualTo("from Messages\nselect Sender\nshow \"G\" ()");
    }

    @Test
    void replacesTheShowClauseTheQueryHas() {
        final String query = "from Messages\nselect Sender, count() as Count\n\nshow BarChart(\n  x = Sender,\n"
                             + "  y = Count)\n";

        assertThat(UserActionsUi.withShowClause(query, "show \"Users\" (category = \"Sender\")"))
                .isEqualTo("from Messages\nselect Sender, count() as Count\nshow \"Users\" (category = \"Sender\")\n");
    }

    @Test
    void summarisesARenderReport() {
        assertThat(UserActionsUi.renderReportSummary(Map.of("status", "ok", "nodes", 42.0, "links", 117.0,
                "library", "ECharts", "libraryVersion", "6.1.0", "droppedRows", Map.of(), "warnings", List.of())))
                .isEqualTo("It drew 42 nodes and 117 links (ECharts 6.1.0).");
        assertThat(UserActionsUi.renderReportSummary(Map.of("status", "ok", "points", 30.0, "series", 3.0,
                "droppedRows", Map.of("value not a number", 2.0),
                "warnings", List.of("5 series beyond Max Series were left out", "another"))))
                .isEqualTo("It drew 30 points in 3 series, leaving out 2 rows (value not a number). 5 series beyond "
                           + "Max Series were left out (+1 more)");
        assertThat(UserActionsUi.renderReportSummary(Map.of("status", "error", "error", "Map a column to target")))
                .isEqualTo("It failed to draw: Map a column to target");
        assertThat(UserActionsUi.renderReportSummary(Map.of("status", "empty"))).startsWith("It drew nothing yet");
        assertThat(UserActionsUi.renderReportSummary(Map.of("status", "ok", "source", "dom", "elementCount", 250.0)))
                .isEqualTo("It drew 250 elements.");
        assertThat(UserActionsUi.renderReportSummary(Map.of())).isEqualTo("It sent a report Stroom couldn't read.");
    }
}
