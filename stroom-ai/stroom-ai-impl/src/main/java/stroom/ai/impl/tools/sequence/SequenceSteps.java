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

package stroom.ai.impl.tools.sequence;

import stroom.ai.api.ElasticKeyStreams.Transform;
import stroom.ai.impl.tools.AbstractBuiltInTool;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.query.IndexEvents;
import stroom.ai.impl.tools.sequence.EqlQueryWriter.Filter;
import stroom.ai.impl.tools.sequence.SequenceQueryTool.GivenStep;
import stroom.ai.impl.tools.timeline.ActivityTimelineTool;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/// The steps of a `sequence_query` call ([SequenceQueryTool]) as the model gives them, read from its arguments
/// and checked as given, before each is checked against its doc's fields: their conditions (the `where` form,
/// and an `any_of` group), join fields, `missing`, `runs`, fields to show, `keysOnly` and join-field transforms.
public final class SequenceSteps {

    private SequenceSteps() {
        // Static methods only.
    }


    /// @param request The model's call.
    /// @param notes   Where notes on how the steps were read are added.
    /// @return The steps, two or more, as given.
    /// @throws WorkspaceException if they are too few, or one can't be read.
    static List<GivenStep> givenSteps(final ToolCall request, final List<String> notes) {
        final Object value = AbstractBuiltInTool.argsMap(request).get(SequenceQueryTool.STEPS_ARG);
        final List<?> items = value instanceof final List<?> list
                ? list
                : parsedList(value);
        if (items.size() < 2) {
            throw new WorkspaceException("Give steps: 2 or more, in order, each with its index, where conditions and "
                                         + "join fields (by).");
        }
        final List<GivenStep> steps = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (!(items.get(i) instanceof final Map<?, ?> map)) {
                throw new WorkspaceException("Step " + (i + 1) + " isn't an object with index, where and by.");
            }
            steps.add(givenStep(map, "Step " + (i + 1), notes));
        }
        if (steps.stream().allMatch(GivenStep::missing)) {
            throw new WorkspaceException("Every step is missing=true: at least one step must be an event that "
                                         + "happens.");
        }
        return steps;
    }

    /// @param request The model's call.
    /// @param notes   Where notes on how the step was read are added.
    /// @return The until step, or null if the call gives none.
    /// @throws WorkspaceException if it can't be read, or takes more than index, where and by.
    static GivenStep givenUntil(final ToolCall request, final List<String> notes) {
        final Object value = AbstractBuiltInTool.argsMap(request).get(SequenceQueryTool.UNTIL_ARG);
        if (value == null || (value instanceof final String text && text.isBlank())) {
            return null;
        }
        final Object parsed = value instanceof final String text
                ? parsedObject(text)
                : value;
        if (!(parsed instanceof final Map<?, ?> map)) {
            throw new WorkspaceException("until is one step: an object with index, where and by.");
        }
        final GivenStep until = givenStep(map, "The until step", notes);
        if (until.missing() || until.runs() > 1 || !until.fields().isEmpty() || until.keysOnly() || until.target()) {
            throw new WorkspaceException("The until step takes index, where and by only.");
        }
        return until;
    }

    private static GivenStep givenStep(final Map<?, ?> map, final String what, final List<String> notes) {
        final String index = map.get(SequenceQueryTool.INDEX_KEY) == null
                ? ""
                : String.valueOf(map.get(SequenceQueryTool.INDEX_KEY)).strip();
        if (index.isEmpty()) {
            throw new WorkspaceException(what + " has no index: give the Elastic Index doc's uuid or name.");
        }
        final List<Filter> filters = filters(map.get(SequenceQueryTool.WHERE_KEY), what, SequenceQueryTool.WHERE_KEY);
        final List<Filter> anyOf = filters(map.get(SequenceQueryTool.ANY_OF_KEY), what, SequenceQueryTool.ANY_OF_KEY);
        if (anyOf.size() == 1) {
            throw new WorkspaceException(what + "'s " + SequenceQueryTool.ANY_OF_KEY + " has one condition: give 2 or "
                                         + "more (at least one must hold), or put the one in where.");
        }
        final boolean missing = truthy(map.get(SequenceQueryTool.MISSING_KEY));
        final int runs = runs(map.get(SequenceQueryTool.RUNS_KEY), what);
        if (missing && runs > 1) {
            throw new WorkspaceException(what + " has missing=true and runs: an event that must not happen can't "
                                         + "repeat. Give one or the other.");
        }
        final List<String> fields = names(map.get(SequenceQueryTool.FIELDS_KEY), what, SequenceQueryTool.FIELDS_KEY,
                notes);
        if (fields.size() > SequenceQueryTool.MAX_STEP_FIELDS) {
            throw new WorkspaceException(what + " asks for " + fields.size() + " fields: show at most "
                                         + SequenceQueryTool.MAX_STEP_FIELDS + ".");
        }
        if (missing && !fields.isEmpty()) {
            throw new WorkspaceException(what + " is missing=true, so it has no events to show fields of.");
        }
        final boolean keysOnly = truthy(map.get(SequenceQueryTool.KEYS_ONLY_KEY));
        if (keysOnly && (runs > 1 || !fields.isEmpty())) {
            throw new WorkspaceException(what + " is keysOnly=true: it says which entities count, so it has no runs "
                                         + "or fields to show.");
        }
        return new GivenStep(index, filters, anyOf, names(map.get(SequenceQueryTool.BY_KEY), what,
                SequenceQueryTool.BY_KEY, notes), missing, runs, fields, keysOnly,
                transforms(map.get(SequenceQueryTool.TRANSFORM_KEY), what), target(map.get(SequenceQueryTool
                .ROLE_KEY), what));
    }

    // True for a step of acts done to the person (role target), false for their own (actor, the default).
    private static boolean target(final Object given, final String what) {
        if (given == null || String.valueOf(given).isBlank()) {
            return false;
        }
        final String role = String.valueOf(given).strip().toLowerCase(Locale.ROOT);
        if (SequenceQueryTool.TARGET_ROLE.equals(role)) {
            return true;
        } else if (SequenceQueryTool.ACTOR_ROLE.equals(role)) {
            return false;
        }
        throw new WorkspaceException(what + "'s " + SequenceQueryTool.ROLE_KEY + " is '" + given + "': give "
                                     + SequenceQueryTool.ACTOR_ROLE + " (the person's own acts, the default) or "
                                     + SequenceQueryTool.TARGET_ROLE + " (acts done to them).");
    }

    // The join-field transforms a step names, in order.
    private static List<Transform> transforms(final Object given, final String what) {
        final List<?> names = given == null
                ? List.of()
                : given instanceof final List<?> list
                        ? list
                        : List.of(String.valueOf(given).split(","));
        final List<Transform> transforms = new ArrayList<>();
        for (final Object name : names) {
            final String text = NullSafe.trim(name == null
                    ? null
                    : String.valueOf(name)).toLowerCase(Locale.ROOT);
            if (text.isEmpty()) {
                continue;
            }
            if (!SequenceQueryTool.TRANSFORMS.contains(text)) {
                throw new WorkspaceException(what + ": transform '" + name + "' isn't one of "
                                             + String.join(", ", SequenceQueryTool.TRANSFORMS) + ".");
            }
            transforms.add(Transform.valueOf(text.toUpperCase(Locale.ROOT)));
        }
        return transforms;
    }

    /// A list of conditions in the where form: where's, or an any_of group's (one level: no group inside). The
    /// activity timeline's roles take them too ([ActivityTimelineTool]).
    ///
    /// @param given The conditions as the call gives them: a list of `{field, condition, value}`.
    /// @param what  What they are of, for a refusal (e.g. `Step 2`).
    /// @param key   The argument they were given in, for a refusal (e.g. `where`).
    /// @return The conditions.
    /// @throws WorkspaceException if they aren't such a list, or name a condition that isn't one.
    public static List<Filter> filters(final Object given, final String what, final String key) {
        final List<?> conditions = given == null
                ? List.of()
                : given instanceof final List<?> list
                        ? list
                        : given instanceof final Map<?, ?> one
                                ? List.of(one)
                                : parsedList(given);
        final List<Filter> filters = new ArrayList<>();
        for (final Object item : conditions) {
            if (item instanceof final Map<?, ?> term && term.containsKey(SequenceQueryTool.ANY_OF_KEY)) {
                throw new WorkspaceException(what + "'s " + key + " has a condition with its own "
                                             + SequenceQueryTool.ANY_OF_KEY + ": give one "
                                             + SequenceQueryTool.ANY_OF_KEY + " per step, of plain conditions.");
            }
            if (!(item instanceof final Map<?, ?> term) || term.get("field") == null
                || String.valueOf(term.get("field")).isBlank()) {
                throw new WorkspaceException(what + "'s " + key + " is a list of {field, condition, value}, each "
                                             + "with a field.");
            }
            final String condition = conditionName(term.get("condition"), what);
            filters.add(new Filter(String.valueOf(term.get("field")).strip(), SequenceQueryTool.NOT_IN.equals(condition)
                    ? Condition.IN
                    : Condition.valueOf(condition),
                    term.get("value") == null
                            ? ""
                            : String.valueOf(term.get("value")),
                    truthy(term.get(SequenceQueryTool.CASE_INSENSITIVE_KEY)),
                    SequenceQueryTool.NOT_IN.equals(condition),
                    truthy(term.get(SequenceQueryTool.LENGTH_KEY))));
        }
        return filters;
    }

    /// @return The conditions a step takes: search_events's, and NOT_IN.
    public static List<String> conditionNames() {
        final List<String> names = new ArrayList<>(IndexEvents.CONDITIONS.stream()
                .filter(EqlQueryWriter::takes)
                .map(Condition::name)
                .toList());
        names.add(SequenceQueryTool.NOT_IN);
        return names;
    }

    // The condition's name, checked: one of conditionNames().
    private static String conditionName(final Object given, final String what) {
        if (given == null || String.valueOf(given).isBlank()) {
            return Condition.EQUALS.name();
        }
        final String name = String.valueOf(given).strip().toUpperCase(Locale.ROOT);
        if (conditionNames().contains(name)) {
            return name;
        }
        throw new WorkspaceException(what + ": condition '" + given + "' isn't one of "
                                     + String.join(", ", conditionNames()) + ".");
    }

    private static int runs(final Object given, final String what) {
        if (given == null || String.valueOf(given).isBlank()) {
            return 1;
        }
        final int runs;
        try {
            final double number = Double.parseDouble(String.valueOf(given).strip());
            runs = number == Math.rint(number)
                    ? (int) number
                    : -1;
        } catch (final NumberFormatException e) {
            throw new WorkspaceException(what + ": runs is a whole number, from 1 to " + EqlQueryWriter.MAX_RUNS
                                         + ".");
        }
        if (runs < 1 || runs > EqlQueryWriter.MAX_RUNS) {
            throw new WorkspaceException(what + ": runs is a whole number, from 1 to " + EqlQueryWriter.MAX_RUNS
                                         + ".");
        }
        return runs;
    }

    private static boolean truthy(final Object value) {
        return value instanceof final Boolean flag
                ? flag
                : value != null && "true".equalsIgnoreCase(String.valueOf(value).strip());
    }

    // Field names: a list of strings, or comma-separated text. A list of one-name lists (seen: a small model sent
    // by as [["username"], ["host"]]) is read as the names, said in notes; any other shape is refused.
    private static List<String> names(final Object value,
                                      final String what,
                                      final String key,
                                      final List<String> notes) {
        final List<String> items = new ArrayList<>();
        if (value instanceof final List<?> list) {
            for (final Object item : list) {
                if (item instanceof final String name) {
                    items.add(name.strip());
                } else if (item instanceof final List<?> one && one.size() == 1
                           && one.getFirst() instanceof final String name) {
                    items.add(name.strip());
                    notes.add("Read the one-name lists in " + key + " as the names: give field names as strings, e.g. "
                              + "[\"user\", \"host\"].");
                } else if (item != null) {
                    throw new WorkspaceException(what + "'s " + key + ": give field names as strings, e.g. "
                                                 + "[\"user\", \"host\"].");
                }
            }
        } else if (value != null) {
            for (final String item : String.valueOf(value).split(",")) {
                items.add(item.strip());
            }
        }
        return items.stream().filter(item -> !item.isEmpty()).toList();
    }

    private static List<?> parsedList(final Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return List.of();
        }
        final Object parsed = parsedObject(String.valueOf(value));
        if (parsed instanceof final List<?> list) {
            return list;
        }
        throw new WorkspaceException("Give steps as a list of objects, each with index, where and by.");
    }

    private static Object parsedObject(final String text) {
        try {
            return JsonUtil.readValue(text, Object.class);
        } catch (final RuntimeException e) {
            throw new WorkspaceException("The steps can't be read: give them as JSON objects.");
        }
    }
}
