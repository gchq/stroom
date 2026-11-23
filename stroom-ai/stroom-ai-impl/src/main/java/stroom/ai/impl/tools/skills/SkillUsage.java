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

package stroom.ai.impl.tools.skills;

import stroom.ai.impl.ToolCallRecord;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiMessageType;
import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/// How skills were used in a user's chats, read from the chats themselves (as `instruction_evidence` reads them):
/// which skills each question was offered (those of the list given to the model that matched it, recorded as a
/// [AiMessageType#SKILLS_OFFERED] message), which were chosen (read whole with `find_skills` by name, in the same
/// question), and which were asked for by name (a `/skill:` command, or the name in the question) when they weren't
/// offered. `check_skill` reports a skill often offered and never chosen (its description misleads) and one asked
/// for by name and not offered (its "When to use" misses how people ask).
public final class SkillUsage {

    /// The key of the offered skills in a [AiMessageType#SKILLS_OFFERED] message.
    public static final String OFFERED_KEY = "offered";
    /// The most questions kept as examples of a skill asked for by name and not offered.
    static final int MAX_EXAMPLES = 3;
    private static final int MAX_EXAMPLE_CHARS = 160;
    // A name shorter than this is too likely to appear in a question by chance to count as asked for by name.
    private static final int MIN_NAME_CHARS = 6;

    private SkillUsage() {
        // Static only.
    }

    /// @param offered The names of the skills a question was offered.
    /// @return The [AiMessageType#SKILLS_OFFERED] message recording them.
    public static String offeredMessage(final List<String> offered) {
        return JsonUtil.writeValueAsString(Map.of(OFFERED_KEY, List.copyOf(offered)), false);
    }

    /// @param chats  Each chat's messages, in order.
    /// @param skills The names of the skills to count.
    /// @return How each was used, by its name in lower case.
    static Map<String, Counts> collect(final Collection<List<AiChatMessage>> chats, final Collection<String> skills) {
        final Map<String, Integer> offered = new HashMap<>();
        final Map<String, Integer> chosen = new HashMap<>();
        final Map<String, List<String>> askedNotOffered = new HashMap<>();
        for (final List<AiChatMessage> messages : chats) {
            final Map<Integer, Set<String>> offeredByTurn = new HashMap<>();
            final Map<Integer, Set<String>> chosenByTurn = new HashMap<>();
            final Map<Integer, String> questionByTurn = new LinkedHashMap<>();
            for (final AiChatMessage message : NullSafe.list(messages)) {
                final int turn = message.getTurnId();
                switch (message.getMessageType()) {
                    case SKILLS_OFFERED -> offeredByTurn.computeIfAbsent(turn, ignored -> new HashSet<>())
                            .addAll(offeredIn(message.getMessage()));
                    case TOOL_CALL -> chosenIn(message.getMessage())
                            .ifPresent(name -> chosenByTurn.computeIfAbsent(turn, ignored -> new HashSet<>())
                                    .add(name));
                    case USER_MESSAGE -> questionByTurn.putIfAbsent(turn, NullSafe.string(message.getMessage()));
                    default -> {
                        // Nothing else says how skills were used.
                    }
                }
            }
            for (final String skill : skills) {
                final String key = skill.toLowerCase(Locale.ROOT);
                final String slug = SkillDoc.slug(skill);
                offeredByTurn.values().stream().filter(names -> names.contains(key))
                        .forEach(ignored -> offered.merge(key, 1, Integer::sum));
                chosenByTurn.values().stream().filter(names -> names.contains(key) || names.contains(slug))
                        .forEach(ignored -> chosen.merge(key, 1, Integer::sum));
                questionByTurn.forEach((turn, question) -> {
                    if (askedByName(question, skill) && !NullSafe.set(offeredByTurn.get(turn)).contains(key)) {
                        final List<String> examples = askedNotOffered.computeIfAbsent(key,
                                ignored -> new ArrayList<>());
                        if (examples.size() < MAX_EXAMPLES) {
                            examples.add(cut(question.strip()));
                        }
                    }
                });
            }
        }
        final Map<String, Counts> counts = new LinkedHashMap<>();
        for (final String skill : skills) {
            final String key = skill.toLowerCase(Locale.ROOT);
            counts.put(key, new Counts(offered.getOrDefault(key, 0), chosen.getOrDefault(key, 0),
                    List.copyOf(askedNotOffered.getOrDefault(key, List.of()))));
        }
        return counts;
    }

    /// @param question A question, as the user sent it.
    /// @param skill    A skill's name.
    /// @return True if the question asks for the skill by name: its `/skill:` command, or its whole name.
    static boolean askedByName(final String question, final String skill) {
        final Optional<SkillInvocations.Invocation> invocation = SkillInvocations.parse(question);
        if (invocation.isPresent()) {
            return invocation.get().name().equals(SkillDoc.slug(skill));
        }
        return skill.length() >= MIN_NAME_CHARS
               && NullSafe.string(question).toLowerCase(Locale.ROOT).contains(skill.toLowerCase(Locale.ROOT));
    }

    private static List<String> offeredIn(final String json) {
        try {
            final Object names = JsonUtil.readValue(json, Map.class).get(OFFERED_KEY);
            if (names instanceof final List<?> list) {
                return list.stream().map(name -> String.valueOf(name).toLowerCase(Locale.ROOT)).toList();
            }
        } catch (final RuntimeException e) {
            // Not a record that can be read: nothing offered.
        }
        return List.of();
    }

    // The skill a find_skills call read by name, in lower case.
    private static Optional<String> chosenIn(final String json) {
        try {
            final ToolCallRecord call = JsonUtil.readValue(json, ToolCallRecord.class);
            if (!FindSkillsTool.NAME.equals(call.tool()) || call.arguments() == null) {
                return Optional.empty();
            }
            final Object name = JsonUtil.readValue(call.arguments(), Map.class).get(FindSkillsTool.NAME_ARG);
            return name instanceof final String text && !text.isBlank()
                    ? Optional.of(text.strip().toLowerCase(Locale.ROOT))
                    : Optional.empty();
        } catch (final RuntimeException e) {
            return Optional.empty();
        }
    }

    private static String cut(final String text) {
        return text.length() <= MAX_EXAMPLE_CHARS
                ? text
                : text.substring(0, MAX_EXAMPLE_CHARS - 3) + "...";
    }


    // --------------------------------------------------------------------------------


    /// How one skill was used.
    ///
    /// @param offered         How many questions it was offered for.
    /// @param chosen          How many questions chose it.
    /// @param askedNotOffered Questions that asked for it by name when it wasn't offered.
    public record Counts(int offered, int chosen, List<String> askedNotOffered) {

    }
}
