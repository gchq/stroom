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

import stroom.ai.impl.tools.EqlQueryWriter.Filter;
import stroom.ai.impl.tools.EqlQueryWriter.Step;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.docref.DocRef;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.TimeRange;
import stroom.util.shared.NullSafe;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/// Writes the StroomQL of a `sequence_query` call that runs in Stroom rather than in Elasticsearch: two steps,
/// joined by their `by` fields, as one windowed join (see `stroom-query/JOINS_DESIGN.md`, "Windowed event-to-event
/// joins"), and the grouped count of the first step that is made before it runs.
///
/// ```
/// from "Events" as a
/// join "Events" as e on a.UserId = e.UserId within 5m after a.EventTime
/// where a.Action = "Logon" and e.Action = "Export"
/// limit 1000
/// select a.UserId as "UserId", a.EventTime as "Step 1 time", ..., e.EventTime as "Step 2 time", ...
/// ```
///
/// The same text is the query the user can save: it is valid StroomQL, checked against Stroom's own parser by its
/// tests. Conditions it can't write exactly (case-insensitive matching, a string's length, is null, a regex) are
/// refused by [#unwritable], never approximated.
final class SequenceStroomQl {

    /// The first step's alias in the join.
    static final String FIRST = "a";
    /// The second step's alias in the join.
    static final String SECOND = "e";
    /// The name of the first step's time column.
    static final String TIME = "time";
    /// The count query's column of first-step events.
    static final String ANCHORS = "Anchors";
    /// The count query's column of distinct join values among them.
    static final String KEYS = "Keys";
    // The count query groups every event into one group of this name.
    private static final String ALL = "Sequence";
    private static final Pattern PLAIN_NAME = Pattern.compile("[A-Za-z_@][A-Za-z0-9_@.]*");
    // A time as StroomQL takes it unquoted: an ISO 8601 time, or a date expression such as day() - 7d.
    private static final Pattern TIME_VALUE = Pattern.compile("[A-Za-z0-9:.+\\-() ]+");

    private SequenceStroomQl() {
        // Static only.
    }

    /// The windowed join for two steps.
    ///
    /// @param first     The first step's side.
    /// @param second    The second step's side.
    /// @param window    How long after a first-step event the second may happen.
    /// @param timeRange The time range, or null for none.
    /// @param limit     The most pairs.
    /// @return The query.
    static String join(final Side first,
                       final Side second,
                       final Duration window,
                       final TimeRange timeRange,
                       final int limit) {
        final StringBuilder sb = new StringBuilder("from ").append(source(first.doc())).append(" as ").append(FIRST)
                .append("\njoin ").append(source(second.doc())).append(" as ").append(SECOND).append(" on ");
        final List<String> keys = new ArrayList<>();
        final List<String> firstKeys = first.step().joinFields();
        final List<String> secondKeys = second.step().joinFields();
        for (int i = 0; i < firstKeys.size(); i++) {
            keys.add(field(FIRST, firstKeys.get(i)) + " = " + field(SECOND, secondKeys.get(i)));
        }
        sb.append(String.join(" and ", keys)).append(" within ").append(EqlQueryWriter.maxspanText(window))
                .append(" after ").append(field(FIRST, first.timeField()));
        final List<String> terms = new ArrayList<>();
        terms.addAll(conditions(FIRST, first.step()));
        terms.addAll(conditions(SECOND, second.step()));
        terms.addAll(timeTerms(FIRST, first.timeField(), timeRange));
        terms.addAll(timeTerms(SECOND, second.timeField(), timeRange));
        if (!terms.isEmpty()) {
            sb.append("\nwhere ").append(String.join("\n  and ", terms));
        }
        // StroomQL takes limit before select.
        sb.append("\nlimit ").append(limit)
                .append("\nselect ").append(String.join(", ", selected(first, second)));
        return sb.toString();
    }

    /// The grouped count of the first step, made before the join runs: one row, with how many events the first
    /// step matches ([#ANCHORS]) and how many distinct join values they have ([#KEYS]).
    ///
    /// @param first     The first step's side.
    /// @param timeRange The time range, or null for none.
    /// @return The query.
    static String anchorCount(final Side first, final TimeRange timeRange) {
        final StringBuilder sb = new StringBuilder("from ").append(source(first.doc()));
        final List<String> terms = new ArrayList<>(conditions(null, first.step()));
        terms.addAll(timeTerms(null, first.timeField(), timeRange));
        if (!terms.isEmpty()) {
            sb.append("\nwhere ").append(String.join("\n  and ", terms));
        }
        final List<String> keys = first.step().joinFields().stream().map(name -> field(null, name)).toList();
        final String key = keys.size() == 1
                ? keys.getFirst()
                : "concat(" + String.join(", '|', ", keys) + ")";
        return sb.append("\neval ").append(ALL).append(" = 'all'")
                .append("\ngroup by ").append(ALL)
                // The group's column is selected, or nothing is grouped (seen in Stroom: a row of 1 per event).
                .append("\nselect ").append(ALL).append(", count() as ").append(ANCHORS).append(", countUnique(").append(key).append(") as ")
                .append(KEYS)
                .toString();
    }

    /// The names of the join's columns, in the order it selects them: the first step's join fields, then for each
    /// step its time, its StreamId and EventId (where its data source has them) and its fields to show.
    ///
    /// @param first  The first step's side.
    /// @param second The second step's side.
    /// @return The columns.
    static List<String> columnNames(final Side first, final Side second) {
        final List<String> names = new ArrayList<>(first.step().joinFields());
        names.addAll(sideColumns(1, first));
        names.addAll(sideColumns(2, second));
        return names;
    }

    /// @param stepNumber The step's number, from 1.
    /// @param column     A column of the step: [#TIME], StreamId, EventId or a field to show.
    /// @return The join's name for it, e.g. `Step 1 time`.
    static String stepColumn(final int stepNumber, final String column) {
        return "Step " + stepNumber + " " + column;
    }

    /// What a step asks that its StroomQL can't say exactly, so the call is refused rather than run as something
    /// else.
    ///
    /// @param what The step, e.g. `Step 1`.
    /// @param step The step.
    /// @param doc  Its data source.
    /// @return Why it can't run in Stroom, or null if it can.
    static String unwritable(final String what, final Step step, final DocRef doc) {
        final List<Filter> all = new ArrayList<>(step.filters());
        all.addAll(step.anyOf());
        for (final Filter filter : all) {
            final String on = what + "'s condition on '" + filter.field() + "'";
            if (filter.caseInsensitive()) {
                return on + " has caseInsensitive=true, which " + DataSourceAccess.describe(doc) + " can't be "
                       + "searched with: whether case matters is set by the field itself (a Lucene index's analyser). "
                       + "Leave caseInsensitive out.";
            }
            if (filter.length()) {
                return on + " compares the string's length (length=true), which a search of "
                       + DataSourceAccess.describe(doc) + " can't do. Leave it out.";
            }
            if (filter.condition() == Condition.IS_NULL || filter.condition() == Condition.IS_NOT_NULL) {
                return on + " is " + filter.condition() + ", which StroomQL has no condition for. Leave it out.";
            }
            if (filter.condition() == Condition.MATCHES_REGEX) {
                return on + " is a regex (MATCHES_REGEX), which a StroomQL where can't take. Use EQUALS with * "
                       + "wildcards, or CONTAINS, STARTS_WITH or ENDS_WITH.";
            }
        }
        return null;
    }

    /// A field as a query names it: `a.User`, or `${a.User name}` when its name needs braces.
    ///
    /// @param alias The alias of its step, or null in a query of one data source.
    /// @param name  The field's name.
    /// @return It, as StroomQL names it.
    static String field(final String alias, final String name) {
        final String full = alias == null
                ? name
                : alias + "." + name;
        return PLAIN_NAME.matcher(name).matches()
                ? full
                : "${" + full + "}";
    }

    // --------------------------------------------------------------------------------

    private static List<String> conditions(final String alias, final Step step) {
        final List<String> terms = new ArrayList<>();
        step.filters().forEach(filter -> terms.add(condition(alias, filter)));
        if (!step.anyOf().isEmpty()) {
            terms.add("(" + String.join(" or ", step.anyOf().stream()
                    .map(filter -> condition(alias, filter))
                    .toList()) + ")");
        }
        return terms;
    }

    /// One condition as StroomQL writes it.
    ///
    /// @param alias  The alias of its step, or null.
    /// @param filter The condition.
    /// @return The term.
    static String condition(final String alias, final Filter filter) {
        final String name = field(alias, filter.field());
        final String value = filter.value();
        if (filter.negated()) {
            return "not (" + name + " in (" + list(value) + "))";
        }
        return switch (filter.condition()) {
            case EQUALS -> name + " = " + string(value);
            case NOT_EQUALS -> name + " != " + string(value);
            case CONTAINS -> name + " = " + string("*" + value + "*");
            case STARTS_WITH -> name + " = " + string(value + "*");
            case ENDS_WITH -> name + " = " + string("*" + value);
            case GREATER_THAN -> name + " > " + string(value);
            case GREATER_THAN_OR_EQUAL_TO -> name + " >= " + string(value);
            case LESS_THAN -> name + " < " + string(value);
            case LESS_THAN_OR_EQUAL_TO -> name + " <= " + string(value);
            case BETWEEN -> between(name, value);
            case IN -> name + " in (" + list(value) + ")";
            default -> throw new WorkspaceException("Condition " + filter.condition() + " can't be used in a step "
                                                    + "run in Stroom.");
        };
    }

    private static String between(final String name, final String value) {
        final String[] parts = value.split(",", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new WorkspaceException("BETWEEN on '" + name + "' takes 'from,to', e.g. '10,20'.");
        }
        return name + " between " + string(parts[0].strip()) + " and " + string(parts[1].strip());
    }

    private static String list(final String value) {
        final List<String> values = new ArrayList<>();
        for (final String item : value.split(",")) {
            if (!item.isBlank()) {
                values.add(string(item.strip()));
            }
        }
        return String.join(", ", values);
    }

    private static List<String> timeTerms(final String alias, final String timeField, final TimeRange timeRange) {
        final List<String> terms = new ArrayList<>();
        if (timeRange == null) {
            return terms;
        }
        final String name = field(alias, timeField);
        if (NullSafe.isNonBlankString(timeRange.getFrom())) {
            terms.add(name + " >= " + time(timeRange.getFrom()));
        }
        if (NullSafe.isNonBlankString(timeRange.getTo())) {
            terms.add(name + " <= " + time(timeRange.getTo()));
        }
        return terms;
    }

    // A time as given (ISO 8601, or a date expression such as day() - 7d), unquoted, as StroomQL reads a date
    // expression's tokens; anything else is refused.
    private static String time(final String given) {
        final String time = given.strip();
        if (!TIME_VALUE.matcher(time).matches()) {
            throw new WorkspaceException("The time '" + given + "' can't be read: give an ISO 8601 time (e.g. "
                                         + "2026-01-01T00:00:00.000Z) or a date expression such as day() - 7d.");
        }
        return time;
    }

    private static List<String> selected(final Side first, final Side second) {
        final List<String> selected = new ArrayList<>();
        for (final String key : first.step().joinFields()) {
            selected.add(field(FIRST, key) + " as " + string(key));
        }
        selected.addAll(sideSelected(FIRST, 1, first));
        selected.addAll(sideSelected(SECOND, 2, second));
        return selected;
    }

    private static List<String> sideSelected(final String alias, final int stepNumber, final Side side) {
        final List<String> selected = new ArrayList<>();
        selected.add(field(alias, side.timeField()) + " as " + string(stepColumn(stepNumber, TIME)));
        if (side.hasIds()) {
            selected.add(field(alias, EqlSequenceResults.STREAM_ID) + " as "
                         + string(stepColumn(stepNumber, EqlSequenceResults.STREAM_ID)));
            selected.add(field(alias, EqlSequenceResults.EVENT_ID) + " as "
                         + string(stepColumn(stepNumber, EqlSequenceResults.EVENT_ID)));
        }
        for (final String shown : side.step().fields()) {
            selected.add(field(alias, shown) + " as " + string(stepColumn(stepNumber, shown)));
        }
        return selected;
    }

    private static List<String> sideColumns(final int stepNumber, final Side side) {
        final List<String> names = new ArrayList<>();
        names.add(stepColumn(stepNumber, TIME));
        if (side.hasIds()) {
            names.add(stepColumn(stepNumber, EqlSequenceResults.STREAM_ID));
            names.add(stepColumn(stepNumber, EqlSequenceResults.EVENT_ID));
        }
        side.step().fields().forEach(shown -> names.add(stepColumn(stepNumber, shown)));
        return names;
    }

    private static String source(final DocRef doc) {
        return string(NullSafe.nonBlankStringElse(doc.getName(), doc.getUuid()));
    }

    private static String string(final String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }


    // --------------------------------------------------------------------------------


    /// One step's side of the join.
    ///
    /// @param doc       Its data source.
    /// @param timeField Its data source's time field.
    /// @param step      The step.
    /// @param hasIds    True if its data source has StreamId and EventId fields.
    record Side(DocRef doc, String timeField, Step step, boolean hasIds) {

    }
}
