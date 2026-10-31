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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// What a user's own chats say about how they want things done, as evidence for standing instructions (AGENTS
/// docs): the answers they gave the agent's forms, grouped by the question (the tool and field that asked), and
/// what they wrote themselves. A choice made the same way in at least the given number of chats, and never another
/// way, is repeated; one made different ways is mixed, which is no rule. Nothing here is a rule yet: the agent
/// drafts rules from it, and the user applies them.
final class InstructionEvidence {

    /// The most of the user's own messages given.
    static final int MAX_MESSAGES = 60;
    /// The longest a message is given, in characters.
    static final int MAX_MESSAGE = 400;
    /// The longest a form's question is given, in characters.
    static final int MAX_QUESTION = 200;

    private InstructionEvidence() {
        // Static only.
    }

    /// @param chats    The user's chats in the window, each with what it holds.
    /// @param minChats How many chats must make a choice the same way for it to be repeated.
    /// @return The evidence.
    static Evidence collect(final List<Chat> chats, final int minChats) {
        // By question, then by answer: the chats that gave it.
        final Map<Question, Map<String, Set<Integer>>> byQuestion = new LinkedHashMap<>();
        final Map<Question, String> asked = new LinkedHashMap<>();
        final Map<Integer, Chat> byId = new LinkedHashMap<>();
        final List<Chat> newestFirst = chats.stream()
                .sorted(Comparator.comparingLong(Chat::timeMs).reversed())
                .toList();
        for (final Chat chat : newestFirst) {
            byId.put(chat.id(), chat);
            for (final Answer answer : chat.answers()) {
                final Question question = new Question(answer.tool(), answer.field(), answer.title());
                byQuestion.computeIfAbsent(question, ignored -> new LinkedHashMap<>())
                        .computeIfAbsent(answer.value(), ignored -> new LinkedHashSet<>())
                        .add(chat.id());
                asked.putIfAbsent(question, cut(answer.question(), MAX_QUESTION));
            }
        }
        final List<Repeated> repeated = new ArrayList<>();
        final List<Mixed> mixed = new ArrayList<>();
        byQuestion.forEach((question, answers) -> {
            final Set<Integer> allChats = new LinkedHashSet<>();
            answers.values().forEach(allChats::addAll);
            if (allChats.size() < minChats) {
                return;
            }
            if (answers.size() == 1) {
                final Map.Entry<String, Set<Integer>> only = answers.entrySet().iterator().next();
                repeated.add(new Repeated(question, asked.get(question), only.getKey(), only.getValue().stream()
                        .map(byId::get)
                        .map(ChatRef::of)
                        .toList()));
            } else {
                final Map<String, Integer> counts = new LinkedHashMap<>();
                answers.forEach((value, ids) -> counts.put(value, ids.size()));
                mixed.add(new Mixed(question, asked.get(question), counts));
            }
        });
        repeated.sort(Comparator.comparingInt((Repeated r) -> r.chats().size()).reversed());
        final List<Said> said = new ArrayList<>();
        for (final Chat chat : newestFirst) {
            for (final String text : chat.said()) {
                if (said.size() < MAX_MESSAGES && !text.isBlank()) {
                    said.add(new Said(ChatRef.of(chat), cut(text.strip(), MAX_MESSAGE)));
                }
            }
        }
        return new Evidence(chats.size(), repeated, mixed, said);
    }

    private static String cut(final String text, final int max) {
        return text == null || text.length() <= max
                ? text
                : text.substring(0, max - 3) + "...";
    }


    // --------------------------------------------------------------------------------


    /// One of the user's chats, with what it holds.
    ///
    /// @param id      The chat's id.
    /// @param title   Its title.
    /// @param timeMs  When it was last used.
    /// @param answers The answers the user gave its forms.
    /// @param said    What the user wrote in it, in order.
    record Chat(int id, String title, long timeMs, List<Answer> answers, List<String> said) {

    }

    /// One answered field of a form.
    ///
    /// @param tool     The tool that asked.
    /// @param field    The field's key.
    /// @param title    Its title, if any.
    /// @param question What the form said.
    /// @param value    The answer, as text.
    record Answer(String tool, String field, String title, String question, String value) {

    }

    /// A question, as forms ask it: the tool and field.
    ///
    /// @param tool  The tool that asks.
    /// @param field The field's key.
    /// @param title Its title, if any.
    record Question(String tool, String field, String title) {

    }

    /// A chat, as evidence names it.
    ///
    /// @param id    Its id.
    /// @param title Its title.
    record ChatRef(int id, String title) {

        static ChatRef of(final Chat chat) {
            return new ChatRef(chat.id(), chat.title());
        }
    }

    /// A choice made the same way in enough chats, and never another way.
    ///
    /// @param question The question.
    /// @param asked    What the latest form asking it said.
    /// @param answer   The answer.
    /// @param chats    The chats that gave it, newest first.
    record Repeated(Question question, String asked, String answer, List<ChatRef> chats) {

    }

    /// A choice made different ways: no rule.
    ///
    /// @param question The question.
    /// @param asked    What the latest form asking it said.
    /// @param answers  Each answer, with how many chats gave it.
    record Mixed(Question question, String asked, Map<String, Integer> answers) {

    }

    /// Something the user wrote.
    ///
    /// @param chat The chat.
    /// @param text What they wrote, cut short.
    record Said(ChatRef chat, String text) {

    }

    /// The evidence.
    ///
    /// @param chats    How many chats were read.
    /// @param repeated The repeated choices, most chats first.
    /// @param mixed    The mixed ones.
    /// @param said     What the user wrote, newest first.
    record Evidence(int chats, List<Repeated> repeated, List<Mixed> mixed, List<Said> said) {

    }
}
