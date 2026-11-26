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

package stroom.ai.impl.tools.timeline;

import stroom.ai.impl.tools.instructions.InstructionRules;
import stroom.ai.impl.tools.query.FieldMatches;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// The fields a flat index likely holds a timeline's things in, where nothing names them (no call's field, AGENTS doc's
/// `## Fields`, index plan, index documentation or convention): found by the thing's own words, as describe_data_source
/// splits a field's name ([FieldMatches#words]), never by a list of vendors' names, and never used unasked (decided by
/// the user 2026-11-27; seen in eval cases 113 to 115 on Haiku and Sonnet: `UserName`, `Host`, `Object` and `EventCode`
/// unread, the first call refused, and the event codes unclassified). The first call's result names them for the
/// calling model to give; a later call in the same chat that still leaves the user's or the event type's field unmapped
/// puts the likely fields to the user in a form, and their answer is used for the chat's later calls and offered as `##
/// Fields` lines.
final class TimelineFieldHints {

    /// The things hinted at, in the order they are said.
    static final List<String> THINGS = List.of(InstructionRules.USER_FIELD, InstructionRules.EVENT_TYPE_FIELD,
            InstructionRules.HOST_FIELD, InstructionRules.OBJECT_FIELD, InstructionRules.APPLICATION_FIELD,
            InstructionRules.TITLE_FIELD, InstructionRules.TEXT_FIELD, InstructionRules.SESSION_FIELD);
    /// The things a later call in a chat asks the user about, still unmapped.
    static final Set<String> ASKED = Set.of(InstructionRules.USER_FIELD, InstructionRules.EVENT_TYPE_FIELD);
    /// The file the chat's hints and answers are kept in.
    static final String FILE = "/outputs/timelines/field-hints.json";
    /// The most likely fields named for a thing.
    static final int MOST = 3;

    private static final String HINTED = "hinted";
    private static final String ANSWERED = "answered";

    private TimelineFieldHints() {
        // Static only.
    }

    /// @param mapped The things found for an index, by thing ([InstructionRules#USER_FIELD] and so on).
    /// @param names  The index's field names.
    /// @param skip   Fields never named: the time field, StreamId, EventId.
    /// @return For each thing not mapped, the fields whose names have its words (at most [#MOST]), in [#THINGS]
    /// order; a thing no field's name matches is left out.
    static Map<String, List<String>> unmapped(final Map<String, ?> mapped,
                                              final Collection<String> names,
                                              final Set<String> skip) {
        final Set<String> taken = new LinkedHashSet<>(skip);
        mapped.values().forEach(found -> {
            if (found instanceof final TimelineFields.Found field) {
                taken.add(field.field());
            }
        });
        final List<String> free = names.stream().filter(name -> !taken.contains(name)).toList();
        final Map<String, List<String>> out = new LinkedHashMap<>();
        for (final String thing : THINGS) {
            if (mapped.containsKey(thing)) {
                continue;
            }
            final List<String> likely = free.stream()
                    .filter(name -> phrases(thing).stream().anyMatch(phrase -> named(phrase, name)))
                    .limit(MOST)
                    .toList();
            if (!likely.isEmpty()) {
                out.put(thing, likely);
            }
        }
        return out;
    }

    /// @param hints The likely fields of the things not mapped, by thing.
    /// @param used  The fields the timeline reads after all (a probed element or code field, the command line).
    /// @return The hints without those fields; a thing left with none is left out.
    static Map<String, List<String>> without(final Map<String, List<String>> hints, final Collection<String> used) {
        final Map<String, List<String>> out = new LinkedHashMap<>();
        hints.forEach((thing, fields) -> {
            final List<String> left = fields.stream().filter(field -> !used.contains(field)).toList();
            if (!left.isEmpty()) {
                out.put(thing, left);
            }
        });
        return out;
    }

    /// @param hints The likely fields of the things not mapped, by thing.
    /// @return The source's note, e.g. "not mapped: event type (maybe EventCode), host (maybe Host); give them in
    /// the call's sources ({index, eventTypeField, host}) or an AGENTS doc's ## Fields lines"; null for none.
    static String said(final Map<String, List<String>> hints) {
        if (hints.isEmpty()) {
            return null;
        }
        final List<String> things = new ArrayList<>();
        final List<String> keys = new ArrayList<>();
        hints.forEach((thing, fields) -> {
            things.add(thing + " (maybe " + String.join(" or ", fields) + ")");
            NullSafe.consume(callKey(thing), keys::add);
        });
        return "not mapped: " + String.join(", ", things) + "; give them in the call's sources ({index, "
               + String.join(", ", keys) + "}) or an AGENTS doc's ## Fields lines";
    }

    /// @param thing A thing ([InstructionRules#USER_FIELD] and so on).
    /// @return The call's source key giving its field, e.g. `eventTypeField`; null where the call has none.
    static String callKey(final String thing) {
        return switch (thing) {
            case InstructionRules.USER_FIELD -> "user";
            case InstructionRules.EVENT_TYPE_FIELD -> "eventTypeField";
            case InstructionRules.HOST_FIELD -> "host";
            case InstructionRules.OBJECT_FIELD -> "object";
            case InstructionRules.APPLICATION_FIELD -> "process";
            case InstructionRules.TITLE_FIELD -> "title";
            case InstructionRules.TEXT_FIELD -> "text";
            default -> null;
        };
    }

    /// @param workspace The chat's workspace, or null.
    /// @param index     The index's uuid.
    /// @return The things hinted at for the index earlier in the chat.
    static Set<String> hinted(final SessionWorkspace workspace, final String index) {
        return new LinkedHashSet<>(strings(entry(read(workspace), index).get(HINTED)));
    }

    /// @param workspace The chat's workspace, or null.
    /// @param index     The index's uuid.
    /// @return The fields the user gave for the index in the chat, by thing.
    static Map<String, String> answered(final SessionWorkspace workspace, final String index) {
        final Map<String, String> out = new LinkedHashMap<>();
        if (entry(read(workspace), index).get(ANSWERED) instanceof final Map<?, ?> map) {
            map.forEach((thing, field) -> {
                if (thing != null && field != null) {
                    out.put(String.valueOf(thing), String.valueOf(field));
                }
            });
        }
        return out;
    }

    /// Keeps, for the chat, the things hinted at for an index and the fields the user gave.
    ///
    /// @param workspace The chat's workspace, or null (nothing kept).
    /// @param index     The index's uuid.
    /// @param hinted    The things hinted at now.
    /// @param answered  The fields the user gave now, by thing.
    static void keep(final SessionWorkspace workspace,
                     final String index,
                     final Collection<String> hinted,
                     final Map<String, String> answered) {
        if (workspace == null || (hinted.isEmpty() && answered.isEmpty())) {
            return;
        }
        final Map<String, Object> all = read(workspace);
        final Map<String, Object> entry = new LinkedHashMap<>(entry(all, index));
        final Set<String> things = new LinkedHashSet<>(strings(entry.get(HINTED)));
        things.addAll(hinted);
        entry.put(HINTED, List.copyOf(things));
        final Map<String, String> fields = new LinkedHashMap<>(answered(workspace, index));
        fields.putAll(answered);
        entry.put(ANSWERED, fields);
        all.put(index, entry);
        try {
            workspace.write(WorkspacePath.parse(FILE), JsonUtil.writeValueAsString(all, true), null);
        } catch (final RuntimeException e) {
            // Not kept: a later call hints again rather than asking.
        }
    }

    // A thing's own words, as the tools name it: the event type is the codes it is told by.
    private static List<String> phrases(final String thing) {
        return switch (thing) {
            case InstructionRules.APPLICATION_FIELD -> List.of("process", "application");
            case InstructionRules.EVENT_TYPE_FIELD -> List.of("event type", "event code");
            default -> List.of(thing);
        };
    }

    // True if a field's name has every word of the phrase and at most one more (UserName for user, EventCode for
    // event code; not process.command_line for process).
    private static boolean named(final String phrase, final String name) {
        final List<String> wanted = FieldMatches.words(phrase);
        final List<String> has = FieldMatches.words(name);
        return has.containsAll(wanted) && has.size() <= wanted.size() + 1;
    }

    private static Map<String, Object> read(final SessionWorkspace workspace) {
        final WorkspacePath path = WorkspacePath.parse(FILE);
        if (workspace == null || workspace.find(path).isEmpty()) {
            return new LinkedHashMap<>();
        }
        try {
            final Object json = JsonUtil.readValue(String.join("\n", workspace.readLines(path, 0,
                    Integer.MAX_VALUE)), Map.class);
            final Map<String, Object> out = new LinkedHashMap<>();
            if (json instanceof final Map<?, ?> map) {
                map.forEach((key, value) -> out.put(String.valueOf(key), value));
            }
            return out;
        } catch (final RuntimeException e) {
            return new LinkedHashMap<>();
        }
    }

    private static Map<String, Object> entry(final Map<String, Object> all, final String index) {
        final Map<String, Object> out = new LinkedHashMap<>();
        if (all.get(index) instanceof final Map<?, ?> map) {
            map.forEach((key, value) -> out.put(String.valueOf(key), value));
        }
        return out;
    }

    private static List<String> strings(final Object value) {
        final List<String> out = new ArrayList<>();
        if (value instanceof final List<?> list) {
            list.forEach(item -> NullSafe.consume(item, one -> out.add(String.valueOf(one))));
        }
        return out;
    }
}
