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
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.draft.DraftMapping;
import stroom.ai.impl.tools.draft.SourceNotes;
import stroom.ai.impl.tools.sample.ToolJson;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// Which value key of each old/new property-change list holds a property's value before the change (decided by the
/// user 2026-11-23): not settled by the items' key order alone (Microsoft 365 writes `NewValue` before `OldValue`,
/// Graph `oldValue` before `newValue`). In a build, with forms, the draft asks the user of each list it found by
/// order, showing its two keys and an item holding both values, the order's guess first; the answer is kept in the
/// build's record ([BuildRecords#rememberDraftChoice], as the time source is), so a redraft doesn't ask again, and
/// `catalogue_event_types` checks the build's placements by it. Without forms, or a build, the items' key order
/// stands, with the draft's note telling the agent to check it and swap `old_key` and `new_key`. Keys given in a
/// placement or a mapping entry win over both.
final class ChangeSides {

    /// The question asked of a list, its start (its list, keys and example follow).
    static final String QUESTION = "Which key of an old/new list holds a property's value before the change?";
    /// The build record's draft choice holding the answers: each list's name to its key holding the old value.
    static final String CHOICE = "change_sides";

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ChangeSides.class);
    private static final String LIST = "list";
    private static final String OLD_KEY = "old_key";
    private static final String NEW_KEY = "new_key";
    private static final String BY = "by";

    private ChangeSides() {
        // Static only.
    }

    /// @param records The build records, or null.
    /// @param build   The build, or null.
    /// @return The answers kept for the build, by the list: the key holding the value before; none without a build
    /// (or a record that can't be read).
    static Map<String, String> kept(final BuildRecords records, final String build) {
        final Map<String, String> out = new LinkedHashMap<>();
        if (records == null || NullSafe.isBlankString(build)) {
            return out;
        }
        try {
            records.draftChoice(build, CHOICE).ifPresent(choice -> JsonValues.mapOrEmpty(choice).forEach(
                    (list, key) -> {
                        if (key instanceof final String before && !before.isBlank()) {
                            out.put(list, before);
                        }
                    }));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Couldn't read build {}'s {}: {}", build, CHOICE, e.getMessage()));
        }
        return out;
    }

    /// @param sourceNotes The source notes the draft reads, or null.
    /// @param sides       The answers, by the list.
    /// @return The notes with the answers ([SourceNotes#CHANGE_SIDES]) for the draft to follow; as given where
    /// there are none.
    static Map<String, Object> withSides(final Map<String, Object> sourceNotes, final Map<String, String> sides) {
        if (sides.isEmpty()) {
            return sourceNotes;
        }
        final Map<String, Object> out = sourceNotes == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(sourceNotes);
        out.put(SourceNotes.CHANGE_SIDES, new LinkedHashMap<>(sides));
        return out;
    }

    /// Asks the user, in a build with forms, which key holds the value before the change of each old/new list the
    /// draft took by its items' key order, the order's guess first; each answer kept for the build.
    ///
    /// @param call    The call.
    /// @param context Its context.
    /// @param consent Asks the user.
    /// @param action  The tool asking.
    /// @param records The build records.
    /// @param build   The build, or null (nothing asked).
    /// @param result  The draft's result, its [DraftMapping#CHANGE_LISTS].
    /// @return The answers given now, by the list: the key holding the value before (none without a build, forms,
    /// or a list taken by order).
    static Map<String, String> ask(final ToolCall call,
                                   final ToolCallContext context,
                                   final Consent consent,
                                   final String action,
                                   final BuildRecords records,
                                   final String build,
                                   final Map<String, Object> result) {
        final Map<String, String> answered = new LinkedHashMap<>();
        if (NullSafe.isBlankString(build) || !context.userQuestions().canAsk()) {
            return answered;
        }
        for (final Map<String, Object> list : JsonValues.maps(result.get(DraftMapping.CHANGE_LISTS))) {
            if (!"order".equals(list.get(BY))) {
                continue;
            }
            final String name = ToolText.str(list.get(LIST));
            final String first = ToolText.str(list.get(OLD_KEY));
            final String second = ToolText.str(list.get(NEW_KEY));
            final Map<String, String> answers = new LinkedHashMap<>();
            answers.put(first + " holds the value before, " + second + " the value after", first);
            answers.put(second + " holds the value before, " + first + " the value after", second);
            final Optional<String> chosen = consent.choose(call, context, action, question(list),
                    List.copyOf(answers.keySet()), null).map(answers::get);
            chosen.ifPresent(before -> answered.put(name, before));
        }
        if (!answered.isEmpty()) {
            final Map<String, String> all = kept(records, build);
            all.putAll(answered);
            try {
                records.rememberDraftChoice(build, CHOICE, new LinkedHashMap<>(all));
            } catch (final RuntimeException e) {
                LOGGER.debug(() -> LogUtil.message("Couldn't keep {} in build {}'s record: {}", CHOICE, build,
                        e.getMessage()), e);
            }
        }
        return answered;
    }

    /// @param list A list the draft found ([DraftMapping#CHANGE_LISTS]).
    /// @return The question asked of it: the list, its two keys and an item holding both values.
    static String question(final Map<String, Object> list) {
        final Object example = list.get("example");
        return QUESTION + " " + list.get(LIST) + " (each item's " + list.get("name_key") + " names the property): "
               + list.get(OLD_KEY) + " or " + list.get(NEW_KEY) + (example == null
                ? ""
                : "? E.g. " + ToolJson.dumps(example)) + ".";
    }

    /// Says, in the draft's notes, the lists whose old key the user's answer set (now or earlier for the build).
    ///
    /// @param result  The draft's result.
    /// @param earlier The answers kept for the build before this draft, by the list.
    static void noted(final Map<String, Object> result, final Map<String, String> earlier) {
        final List<String> said = new ArrayList<>();
        for (final Map<String, Object> list : JsonValues.maps(result.get(DraftMapping.CHANGE_LISTS))) {
            if ("user".equals(list.get(BY))) {
                final String name = ToolText.str(list.get(LIST));
                said.add(ToolText.repr(name) + " takes its old values from " + list.get(OLD_KEY) + " and its new "
                         + "from " + list.get(NEW_KEY) + ", as the user said" + (earlier.containsKey(name)
                        ? " earlier for this build"
                        : ""));
            }
        }
        if (!said.isEmpty()) {
            final List<Object> notes = new ArrayList<>(NullSafe.list((List<?>) result.get("notes")));
            notes.add(String.join("; ", said) + " (old_key and new_key given in a placement or entry win).");
            result.put("notes", notes);
        }
    }
}
