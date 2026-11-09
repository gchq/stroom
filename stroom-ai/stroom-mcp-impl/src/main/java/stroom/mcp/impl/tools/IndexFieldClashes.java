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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.indexing.FieldPlans;
import stroom.ai.impl.tools.indexing.PlannedField;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/// A whole draft's Elasticsearch names where one is a value and also the object holding another (`user` and
/// `user.name`), which nested properties can't map. Seen in testing: the tool's own field for an action's path
/// (`netscaler.authenticate.user.id`) clashed with the agent's names; the draft was refused whole, so nothing was
/// kept for `changes=` to amend, and no argument could rename the tool's field: the agent looped for a long time.
///
/// The tool's own fields give way (asked for by the user): renamed, and said so. A clash between fields the agent
/// gave or the convention plans is refused, naming where each came from and what to change.
final class IndexFieldClashes {

    // The most renames in one draft: each removes a clash, so more means a loop.
    private static final int MAX_RENAMES = 100;

    private IndexFieldClashes() {
        // Static only.
    }

    /// Renames each of the tool's own fields that clashes: a value holding others becomes `name.value`; one under a
    /// value of another field's name has the name joined at it (`user.id` under `user` becomes `user_id`).
    ///
    /// @param fields The plan's fields, renamed in place.
    /// @param tools  The names of the fields the tool planned of its own accord, renamed in place.
    /// @param notes  Each rename, said.
    static void renameTools(final List<PlannedField> fields, final Set<String> tools, final List<String> notes) {
        for (int renames = 0; renames < MAX_RENAMES; renames++) {
            final Clash clash = firstClash(names(fields), tools);
            if (clash == null) {
                return;
            }
            final String from;
            final String to;
            if (tools.contains(clash.value())) {
                from = clash.value();
                to = free(fields, from + ".value", from + "_value");
            } else {
                from = clash.inner();
                to = free(fields, clash.value() + "_" + from.substring(clash.value().length() + 1), null);
            }
            if (to == null) {
                return;
            }
            final String other = from.equals(clash.value())
                    ? clash.inner()
                    : clash.value();
            for (int i = 0; i < fields.size(); i++) {
                if (fields.get(i).name().equals(from)) {
                    fields.set(i, fields.get(i).withName(to));
                }
            }
            tools.remove(from);
            tools.add(to);
            notes.add("Renamed the tool's own field '" + from + "' to '" + to + "': '" + other + "' is planned too, "
                      + "and an Elasticsearch name can't be a value and the object holding another. Change it with "
                      + "changes= if the user wants another name.");
        }
    }

    /// Refuses the draft where names still clash, none of them the tool's own.
    ///
    /// @param fields The plan's fields.
    /// @param tools  The names of the fields the tool planned of its own accord.
    /// @param extras The agent's extra fields.
    /// @param notes  The draft's notes so far, said with the refusal.
    /// @throws ToolRefusal if any names clash.
    static void refuseOthers(final List<PlannedField> fields,
                             final Set<String> tools,
                             final List<PlannedField> extras,
                             final List<String> notes) {
        final Set<String> theirs = extras.stream().map(PlannedField::name).collect(Collectors.toSet());
        final List<String> said = new ArrayList<>();
        final List<String> names = names(fields);
        for (final String value : names) {
            final List<String> inner = names.stream().filter(name -> name.startsWith(value + ".")).toList();
            if (!inner.isEmpty()) {
                said.add("'" + value + "' (" + origin(value, theirs, tools) + ") is a value and also the object "
                         + "holding " + inner.stream()
                                 .map(name -> "'" + name + "' (" + origin(name, theirs, tools) + ")")
                                 .collect(Collectors.joining(", ")));
            }
        }
        if (!said.isEmpty()) {
            throw new ToolRefusal(String.join("; ", said) + ": an Elasticsearch name can't be both. Nothing was "
                                  + "kept. Rename or leave out the one from extra_fields (e.g. 'user_name' beside "
                                  + "'user.name', or 'user.name' itself to replace the convention's), then draft "
                                  + "again." + (notes.isEmpty()
                    ? ""
                    : " Also: " + String.join(" ", notes)));
        }
    }

    /// Notes where a path the tool gave a field of its own looks like one a planned field reads (`User/Id` of an
    /// action beside `EventSource/User/Id`): it could be a second source of that field, or keep its own, as the user
    /// says. Nothing is merged: one field of every user and one of each are both wanted at times (the user's).
    ///
    /// @param fields The plan's fields.
    /// @param tools  The names of the fields the tool planned of its own accord.
    /// @param notes  Each such path, said.
    static void alsoFrom(final List<PlannedField> fields, final Set<String> tools, final List<String> notes) {
        for (final PlannedField own : fields) {
            if (!tools.contains(own.name()) || !JsonValues.truthy(own.source())) {
                continue;
            }
            final String tail = tail(own.source());
            if (tail == null) {
                continue;
            }
            for (final PlannedField planned : fields) {
                if (tools.contains(planned.name()) || !JsonValues.truthy(planned.source())
                    || FieldPlans.sourceMatches(planned.source(), own.source())) {
                    continue;
                }
                if (FieldPlans.branches(planned.source()).stream().anyMatch(branch -> tail.equals(tail(branch)))) {
                    notes.add(own.source() + " (planned as '" + own.name() + "') is a " + tail + " like '"
                              + planned.name() + "' reads (" + planned.source() + "): ask the user whether it "
                              + "should also feed '" + planned.name() + "' (a second source: '" + planned.source()
                              + " | " + own.source() + "'), keep a field of its own, or both.");
                    break;
                }
            }
        }
    }

    // The first clash with a tool's field in it, or null.
    private static Clash firstClash(final List<String> names, final Set<String> tools) {
        for (final String value : names) {
            for (final String inner : names) {
                if (inner.startsWith(value + ".") && (tools.contains(value) || tools.contains(inner))) {
                    return new Clash(value, inner);
                }
            }
        }
        return null;
    }

    // The first of the names no field has, or null.
    private static String free(final List<PlannedField> fields, final String first, final String second) {
        final List<String> names = names(fields);
        if (!names.contains(first)) {
            return first;
        }
        return second != null && !names.contains(second)
                ? second
                : null;
    }

    // The names as the index holds them: a field of objects as its items' names, not a value of its own.
    private static List<String> names(final List<PlannedField> fields) {
        return fields.stream()
                .flatMap(field -> field.isObjects()
                        ? field.leaves().stream()
                        : Stream.of(field))
                .map(PlannedField::name)
                .distinct()
                .sorted()
                .toList();
    }

    private static String origin(final String name, final Set<String> theirs, final Set<String> tools) {
        if (theirs.contains(name)) {
            return "from extra_fields";
        }
        return tools.contains(name)
                ? "the tool's own"
                : "the convention's";
    }

    // A path's last two steps (User/Id), or null for a path of fewer.
    private static String tail(final String path) {
        final String[] steps = path.strip().split("/", -1);
        return steps.length < 2
                ? null
                : steps[steps.length - 2] + "/" + steps[steps.length - 1];
    }


    // --------------------------------------------------------------------------------


    // A name that is a value, and one under it.
    private record Clash(String value, String inner) {

    }
}
