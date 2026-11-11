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

import stroom.ai.shared.AiTabSnapshotPart;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.util.shared.NullSafe;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/// Reads a dashboard's description ([DashboardPresenter#getComparableDescription()]) by component, so changes the AI
/// chat applied are applied, and undone, on top of whatever else the user has changed since, as long as what they
/// replace hasn't changed; and puts a chart's id in the filter its clicks drive, once the chart is added, and the
/// keys of its selection a converted chart renames.
public final class DashboardDescriptions {

    // What starts a component's heading, e.g. "## Table 'Events' (id table-1)".
    private static final String HEADING = "## ";
    // What comes before the component's id at the end of its heading.
    private static final String ID = " (id ";

    private DashboardDescriptions() {
        // Static methods only.
    }

    /// @param description A dashboard's description.
    /// @return Each component's part of it (its heading and the lines below it, up to the next heading, without
    /// blank ones), by its component id.
    public static Map<String, String> componentParts(final String description) {
        final Map<String, String> parts = new HashMap<>();
        String id = null;
        StringBuilder part = null;
        for (final String line : NullSafe.string(description).split("\n")) {
            if (line.startsWith(HEADING) && line.endsWith(")") && line.lastIndexOf(ID) > 0) {
                if (id != null) {
                    parts.put(id, part.toString());
                }
                id = line.substring(line.lastIndexOf(ID) + ID.length(), line.length() - 1);
                part = new StringBuilder();
            }
            // Blank lines only separate components, so a component's part is the same whatever follows it.
            if (part != null && !line.isBlank()) {
                part.append(line).append('\n');
            }
        }
        if (id != null) {
            parts.put(id, part.toString());
        }
        return parts;
    }

    /// @param before       A dashboard's description at one time.
    /// @param after        Its description at another.
    /// @param componentIds Some of its components' ids.
    /// @return True if each of the components is described the same in both (or is in neither).
    public static boolean sameComponents(final String before,
                                         final String after,
                                         final Collection<String> componentIds) {
        final Map<String, String> partsBefore = componentParts(before);
        final Map<String, String> partsAfter = componentParts(after);
        return NullSafe.collection(componentIds).stream()
                .allMatch(id -> Objects.equals(partsBefore.get(id), partsAfter.get(id)));
    }

    /// @param expression A selection filter's expression, its terms naming a chart's selection, e.g.
    ///                   `${component.new-chart.selection.x}`.
    /// @param from       The chart's id the filter was planned with.
    /// @param to         The chart's id on the dashboard.
    /// @return The expression, its terms naming the chart by its id on the dashboard.
    public static ExpressionOperator withChartId(final ExpressionOperator expression,
                                                 final String from,
                                                 final String to) {
        if (expression == null || from.equals(to)) {
            return expression;
        }
        final String planned = "component." + from + ".selection.";
        final String actual = "component." + to + ".selection.";
        return withValues(expression, value -> value.replace(planned, actual));
    }

    /// @param expression A selection filter's expression, its terms naming a chart's selection, e.g.
    ///                   `${component.vis-1.selection.x}`.
    /// @param chartId    The chart's id.
    /// @param renamed    Keys of the chart's selection renamed (e.g. a line chart's x as a bar chart's category,
    ///                   once it is converted to one), by old key.
    /// @return The expression, each of its terms naming a key renamed naming the new one; the same expression if
    /// none names one.
    public static ExpressionOperator withSelections(final ExpressionOperator expression,
                                                    final String chartId,
                                                    final Map<String, String> renamed) {
        if (expression == null || NullSafe.isEmptyMap(renamed)) {
            return expression;
        }
        final String prefix = "${component." + chartId + ".selection.";
        final ExpressionOperator changed = withValues(expression, value -> renameKeys(value, prefix, renamed));
        return changed.equals(expression)
                ? expression
                : changed;
    }

    /// @param text Keys renamed, as `old=new` pairs separated by commas, e.g. `x=category,series=name`.
    /// @return The new key for each old one.
    public static Map<String, String> renamedKeys(final String text) {
        final Map<String, String> renamed = new HashMap<>();
        for (final String pair : NullSafe.string(text).split(",")) {
            final int equals = pair.indexOf('=');
            if (equals > 0 && equals < pair.length() - 1) {
                renamed.put(pair.substring(0, equals).trim(), pair.substring(equals + 1).trim());
            }
        }
        return renamed;
    }

    // The expression with each term's value changed.
    private static ExpressionOperator withValues(final ExpressionOperator expression,
                                                 final Function<String, String> change) {
        final ExpressionOperator.Builder builder = ExpressionOperator.builder()
                .op(expression.op())
                .enabled(expression.getEnabled());
        for (final ExpressionItem child : NullSafe.list(expression.getChildren())) {
            if (child instanceof final ExpressionOperator operator) {
                builder.addOperator(withValues(operator, change));
            } else if (child instanceof final ExpressionTerm term) {
                builder.addTerm(term.copy().value(change.apply(NullSafe.string(term.getValue()))).build());
            }
        }
        return builder.build();
    }

    // The value with each key of the selection (after the prefix, up to its closing brace) renamed. Not with a
    // regular expression, which the browser's code can't use.
    private static String renameKeys(final String value, final String prefix, final Map<String, String> renamed) {
        final StringBuilder sb = new StringBuilder();
        int from = 0;
        int start = value.indexOf(prefix);
        while (start >= 0) {
            final int keyStart = start + prefix.length();
            final int end = value.indexOf('}', keyStart);
            if (end < 0) {
                break;
            }
            final String key = value.substring(keyStart, end);
            sb.append(value, from, keyStart).append(renamed.containsKey(key)
                    ? renamed.get(key)
                    : key);
            from = end;
            start = value.indexOf(prefix, end);
        }
        return sb.append(value.substring(from)).toString();
    }

    /// @param proposedHash The hash ([AiTabSnapshotPart#hashLines(String)]) of a query's expression as the
    ///                     dashboard's description gave it when a change to it was proposed.
    /// @param expression   The query's expression now, or null.
    /// @return True if the expression is as it was.
    public static boolean sameExpression(final String proposedHash, final ExpressionOperator expression) {
        // As the description gives it.
        final String described = String.valueOf(NullSafe.get(expression, ExpressionOperator::toMultiLineString));
        return AiTabSnapshotPart.hashLines(described).equalsIgnoreCase(proposedHash);
    }
}
