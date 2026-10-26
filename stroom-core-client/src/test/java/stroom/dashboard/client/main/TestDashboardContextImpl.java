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

package stroom.dashboard.client.main;

import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestDashboardContextImpl {

    @Test
    void aSelectedValueIsPutInAsSelectedWithoutQuotes() {
        // Seen in testing: clicking a node named "Skyler White" filtered the search by 'Skyler White', quotes
        // included, which no value holds, so the dashboard showed nothing.
        final Map<String, String> selection = Map.of("component.vis-1.selection.node", "Skyler White");

        assertThat(DashboardContextImpl.selectionTermValue("${component.vis-1.selection.node}", selection))
                .isEqualTo("Skyler White");
        assertThat(DashboardContextImpl.selectionTermValue("${component.vis-1.selection.node}*", selection))
                .isEqualTo("Skyler White*");
    }

    @Test
    void aSelectionThatIsntHeldLeavesNothing() {
        // So the term drops out (see ExpressionUtil.isValidTerm) while nothing is selected.
        assertThat(DashboardContextImpl.selectionTermValue("${component.vis-1.selection.node}", Map.of()))
                .isEmpty();
    }

    // The selection filters Stroom AI builds (written by TestProposeDashboardChangeTool, from what the user asks
    // for), applied to what each chart's click selects, as a dashboard applies them: each case keeps the rows the
    // user means. Seen in testing: a filter on "the person as sender or as recipient" filtered nothing for a
    // clicked node, though each side passed its own tests.
    @Test
    @SuppressWarnings("unchecked")
    void stroomAisSelectionFiltersKeepTheRowsTheUserMeans() throws IOException {
        final Path file = Path.of("..", "stroom-ai", "stroom-ai-impl", "src", "test", "resources", "stroom", "ai",
                "impl", "golden", "selection-filters", "scenarios.json");
        final Map<String, Object> fixture = JsonUtil.readValue(Files.readString(file), Map.class);
        final String chartId = (String) fixture.get("chartId");
        final List<Map<String, String>> rows = (List<Map<String, String>>) fixture.get("rows");
        final List<String> checked = new ArrayList<>();
        for (final Map<String, Object> scenario : (List<Map<String, Object>>) fixture.get("scenarios")) {
            final ExpressionOperator expression = JsonUtil.readValue(
                    JsonUtil.writeValueAsString(scenario.get("expression")), ExpressionOperator.class);
            for (final Map<String, Object> filterCase : (List<Map<String, Object>>) scenario.get("cases")) {
                final ExpressionOperator filter = DashboardContextImpl.withSelections(expression, chartId,
                        (List<Map<String, String>>) filterCase.get("selections")).orElse(null);
                final List<Integer> kept = new ArrayList<>();
                for (int i = 0; i < rows.size(); i++) {
                    if (filter == null || matches(filter, rows.get(i))) {
                        kept.add(i);
                    }
                }
                assertThat(kept)
                        .as(scenario.get("name") + ": " + filterCase.get("name") + " (filter " + filter + ")")
                        .isEqualTo(filterCase.get("keeps"));
                checked.add(scenario.get("name") + ": " + filterCase.get("name"));
            }
        }
        assertThat(checked).hasSizeGreaterThan(10);
    }

    // Whether a row matches a filter, as a table's rows are filtered: AND, OR and NOT; a term equal (ignoring case)
    // or on or after, or on or before, a time or number. Enough for the filters Stroom AI builds.
    private static boolean matches(final ExpressionItem item, final Map<String, String> row) {
        if (item instanceof final ExpressionOperator operator) {
            final List<ExpressionItem> children = operator.getChildren() == null
                    ? List.of()
                    : operator.getChildren();
            return switch (operator.op()) {
                case AND -> children.stream().allMatch(child -> matches(child, row));
                case OR -> children.isEmpty() || children.stream().anyMatch(child -> matches(child, row));
                case NOT -> children.stream().noneMatch(child -> matches(child, row));
            };
        }
        final ExpressionTerm term = (ExpressionTerm) item;
        final String value = row.get(term.getField());
        if (value == null) {
            return false;
        }
        return switch (term.getCondition()) {
            case EQUALS -> value.toLowerCase(Locale.ROOT).equals(term.getValue().toLowerCase(Locale.ROOT));
            case GREATER_THAN_OR_EQUAL_TO -> compare(value, term.getValue()) >= 0;
            case LESS_THAN_OR_EQUAL_TO -> compare(value, term.getValue()) <= 0;
            default -> throw new IllegalArgumentException("Not a condition Stroom AI's filters use: " + term);
        };
    }

    private static int compare(final String a, final String b) {
        try {
            return Instant.parse(a).compareTo(Instant.parse(b));
        } catch (final DateTimeParseException e) {
            return Double.compare(Double.parseDouble(a), Double.parseDouble(b));
        }
    }
}
