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

package stroom.ai.impl.tools.instructions;

import stroom.ai.impl.tools.instructions.AgentInstructions.InstructionDoc;
import stroom.ai.impl.tools.instructions.InstructionChecks.Facts;
import stroom.ai.impl.tools.instructions.InstructionChecks.IndexFacts;
import stroom.ai.impl.tools.instructions.InstructionChecks.PlanField;
import stroom.ai.impl.tools.instructions.InstructionRules.FieldRule;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

/// A draft of the `## Fields` lines of a folder's AGENTS doc, from facts (asked for by the user: the tools don't
/// guess, so people are helped to write the lines): for each key, the candidates of each index where the doc applies,
/// from what the author said (the agent passes it on, from their words, the chat or `instruction_evidence`), the
/// AGENTS docs in force there, the index plans kept with the pipelines feeding the index and the field conventions.
/// Only what the author alone knows is asked: an index with candidates that differ, and a key asked for that has none.
/// The answers make the lines, merged into the folder's doc ([#merged]).
public final class InstructionDraft {

    /// The answer to a question with candidates that leaves the key out for that index.
    public static final String LEAVE_OUT = "Leave it out";

    // The keys whose fields the index plans and field conventions find, in the order their lines are drafted.
    private static final List<String> FOUND_KEYS = List.of(InstructionRules.USER_FIELD,
            InstructionRules.PERSON_FIELD, InstructionRules.TARGET_USER_FIELD, InstructionRules.TARGET_PERSON_FIELD,
            InstructionRules.HOST_FIELD, InstructionRules.ACTION_FIELD, InstructionRules.EVENT_TYPE_FIELD);

    private InstructionDraft() {
        // Static only.
    }

    /// The candidates for each key, and what is to be asked.
    ///
    /// @param folder    The folder whose doc is drafted, e.g. `/Endpoint`.
    /// @param stated    For each key, the fields the author named, in their order.
    /// @param requested Keys the doc is to have a line for, whatever is found.
    /// @param facts     The AGENTS docs (with the folder's own, as saved) and the indexes.
    /// @return The plan.
    public static Plan plan(final String folder,
                            final Map<String, List<String>> stated,
                            final Collection<String> requested,
                            final Facts facts) {
        final String at = AgentInstructions.normalise(folder);
        final List<IndexFacts> indexes = facts.indexes().stream()
                .filter(index -> AgentInstructions.isWithin(AgentInstructions.normalise(index.folder()), at))
                .toList();
        final Set<String> keys = new LinkedHashSet<>(stated.keySet());
        keys.addAll(requested);
        keys.addAll(FOUND_KEYS);
        final List<KeyPlan> plans = new ArrayList<>();
        final List<Question> questions = new ArrayList<>();
        for (final String key : keys) {
            final List<String> given = NullSafe.list(stated.get(key));
            final Map<String, Candidate> chosen = new LinkedHashMap<>();
            boolean anyFound = false;
            boolean allInForce = true;
            for (final IndexFacts index : indexes) {
                final List<Candidate> candidates = candidates(key, given, index, facts.docs());
                anyFound |= !candidates.isEmpty();
                if (candidates.size() == 1) {
                    chosen.put(index.name(), candidates.getFirst());
                    allInForce &= candidates.getFirst().inForce();
                } else if (candidates.size() > 1) {
                    allInForce = false;
                    questions.add(new Question("q" + (questions.size() + 1), key, index.name(), candidates));
                }
            }
            if (!anyFound && given.isEmpty() && requested.contains(key)) {
                questions.add(new Question("q" + (questions.size() + 1), key, null, List.of()));
            }
            // A key nobody named whose fields every index finds by a doc in force already needs no line.
            if (given.isEmpty() && !requested.contains(key) && (!anyFound || allInForce)
                && questions.stream().noneMatch(question -> question.key().equals(key))) {
                continue;
            }
            plans.add(new KeyPlan(key, given, chosen));
        }
        return new Plan(at, indexes, plans, questions);
    }

    // An index's candidates for a key: the author's (the first the index has, alone), else the doc in force's, the
    // plan's and the convention's, each a field the index has, once.
    private static List<Candidate> candidates(final String key,
                                              final List<String> given,
                                              final IndexFacts index,
                                              final List<InstructionDoc> docs) {
        for (final String field : given) {
            if (index.fields().contains(field)) {
                return List.of(new Candidate(field, "given", false));
            }
        }
        final Map<String, Candidate> out = new LinkedHashMap<>();
        final List<InstructionDoc> applying = docs.stream().filter(doc -> doc.appliesTo(index.folder())).toList();
        final FieldRule rule = InstructionRules.fields(applying).get(key);
        if (rule != null) {
            rule.fields().stream().filter(index.fields()::contains).findFirst().ifPresent(field ->
                    out.put(field, new Candidate(field, "the AGENTS doc in " + rule.from(), true)));
        }
        final PlanField planned = index.planned().get(key);
        if (planned != null && index.fields().contains(planned.field())) {
            out.merge(planned.field(), new Candidate(planned.field(), "the index plan of '" + planned.pipeline()
                                                                      + "'", false), Candidate::and);
        }
        final String convention = index.conventions().get(key);
        if (convention != null && index.fields().contains(convention)) {
            out.merge(convention, new Candidate(convention, "a field convention", false), Candidate::and);
        }
        return List.copyOf(out.values());
    }

    /// The lines the plan and the answers make, merged into the folder's doc.
    ///
    /// @param plan    The plan.
    /// @param answers The answers to its questions, by id (a field, [#LEAVE_OUT], or blank).
    /// @param text    The folder's doc as saved, or empty for a new one.
    /// @return The draft.
    public static Draft draft(final Plan plan, final Map<String, Object> answers, final String text) {
        final Map<String, String> lines = new LinkedHashMap<>();
        final List<String> notes = new ArrayList<>();
        for (final KeyPlan keyPlan : plan.keys()) {
            final Map<String, String> chosen = new LinkedHashMap<>();
            keyPlan.chosen().forEach((index, candidate) -> chosen.put(index, candidate.field()));
            final List<String> named = new ArrayList<>(keyPlan.given());
            for (final Question question : plan.questions()) {
                if (!question.key().equals(keyPlan.key())) {
                    continue;
                }
                final String answer = NullSafe.trim(String.valueOf(NullSafe.map(answers)
                        .getOrDefault(question.id(), "")));
                if (answer.isEmpty() || LEAVE_OUT.equals(answer) || "null".equals(answer)) {
                    continue;
                }
                if (question.index() == null) {
                    named.addAll(InstructionRules.fieldNames(answer));
                } else {
                    chosen.put(question.index(), answer);
                }
            }
            final List<String> names = ordered(named, chosen, plan.indexes());
            if (names.isEmpty()) {
                continue;
            }
            lines.put(keyPlan.key(), "- " + keyPlan.key() + ": " + String.join(", ", names));
            final List<String> unmet = unmet(names, chosen, plan.indexes());
            if (!unmet.isEmpty()) {
                notes.add("For " + keyPlan.key() + ", one line can't give " + String.join(", ", unmet)
                          + ": the first field an index has is used, so give that index's folder a doc of its own.");
            }
        }
        if (lines.isEmpty()) {
            return new Draft(NullSafe.string(text), lines, notes);
        }
        final String base = NullSafe.isBlankString(text)
                ? "# Standing instructions\n"
                : text;
        return new Draft(InstructionChecks.withFieldLines(base, lines), lines, notes);
    }

    // The fields of a line: the author's first, then each index's choice, each before any other field the index has
    // (the first an index has is used).
    private static List<String> ordered(final List<String> named,
                                        final Map<String, String> chosen,
                                        final List<IndexFacts> indexes) {
        final List<String> names = new ArrayList<>(new LinkedHashSet<>(named));
        chosen.values().stream().filter(field -> !names.contains(field)).distinct().forEach(names::add);
        // Move a choice ahead of another field its index has, as often as there are fields (no more can help).
        for (int pass = 0; pass < names.size(); pass++) {
            boolean moved = false;
            for (final IndexFacts index : indexes) {
                final String field = chosen.get(index.name());
                if (field == null) {
                    continue;
                }
                final int at = names.indexOf(field);
                for (int i = 0; i < at; i++) {
                    if (index.fields().contains(names.get(i))) {
                        names.remove(at);
                        names.add(i, field);
                        moved = true;
                        break;
                    }
                }
            }
            if (!moved) {
                break;
            }
        }
        return names;
    }

    // The indexes whose choice the line wouldn't give, as "index (field)".
    private static List<String> unmet(final List<String> names,
                                      final Map<String, String> chosen,
                                      final List<IndexFacts> indexes) {
        final List<String> out = new ArrayList<>();
        for (final IndexFacts index : indexes) {
            final String field = chosen.get(index.name());
            final String used = names.stream().filter(index.fields()::contains).findFirst().orElse(null);
            if (field != null && used != null && !field.equals(used)) {
                out.add(index.name() + " (" + field + ")");
            }
        }
        return out;
    }

    /// @param lines `key: fields` lines as the author gave them, e.g. `user: actor.login`.
    /// @return The fields of each, by the key the tools know it by.
    /// @throws IllegalArgumentException If a line names no key the tools know, or no field.
    public static Map<String, List<String>> stated(final List<String> lines) {
        final Map<String, List<String>> out = new LinkedHashMap<>();
        for (final String line : NullSafe.list(lines)) {
            final Matcher bullet = InstructionRules.BULLET.matcher("- " + NullSafe.string(line).strip()
                    .replaceFirst("^[-*]\\s*", ""));
            final String key = bullet.matches()
                    ? InstructionRules.FIELDS.get(InstructionRules.normaliseKey(bullet.group(1)))
                    : null;
            if (key == null) {
                throw new IllegalArgumentException("'" + line + "' isn't 'key: field': the keys are "
                                                   + String.join(", ", new LinkedHashSet<>(
                        InstructionRules.FIELDS.values())) + ".");
            }
            out.put(key, InstructionRules.fieldNames(bullet.group(2)));
        }
        return out;
    }

    /// @param key A key as the author named it, e.g. `person id`.
    /// @return The key the tools know it by, e.g. `person`, or null if none.
    public static String key(final String key) {
        return InstructionRules.FIELDS.get(InstructionRules.normaliseKey(key));
    }


    // --------------------------------------------------------------------------------


    /// A field that could hold a key's value in an index.
    ///
    /// @param field   The field.
    /// @param from    Where it comes from, e.g. "the index plan of 'Endpoint indexing'".
    /// @param inForce True if an AGENTS doc in force there gives it already.
    public record Candidate(String field, String from, boolean inForce) {

        // The same field from two places.
        Candidate and(final Candidate other) {
            return new Candidate(field, from + " and " + other.from(), inForce || other.inForce());
        }
    }


    // --------------------------------------------------------------------------------


    /// Something only the author knows.
    ///
    /// @param id         Its id in the form.
    /// @param key        The key, e.g. `user`.
    /// @param index      The index whose candidates differ, or null for a key asked for with none found.
    /// @param candidates The index's candidates; empty for none.
    public record Question(String id, String key, String index, List<Candidate> candidates) {

        /// @return The question, as the user reads it.
        public String text() {
            return index == null
                    ? "Which field holds the " + key + "? (as the indexes name it; blank to leave it out)"
                    : "Index " + index + ": which field holds the " + key + "?";
        }

        /// @return Its candidates and where each comes from, as the user reads them, e.g. "user.name (the index
        /// plan of 'Endpoint indexing')".
        public String found() {
            return String.join("; ", candidates.stream().map(candidate -> candidate.field() + " ("
                                                                          + candidate.from() + ")").toList());
        }
    }


    // --------------------------------------------------------------------------------


    /// A key's candidates.
    ///
    /// @param key    The key.
    /// @param given  The fields the author named for it.
    /// @param chosen Each index's one candidate, by index name.
    public record KeyPlan(String key, List<String> given, Map<String, Candidate> chosen) {

    }


    // --------------------------------------------------------------------------------


    /// What is drafted and asked.
    ///
    /// @param folder    The folder.
    /// @param indexes   The indexes where the doc applies.
    /// @param keys      Each key to give a line.
    /// @param questions What only the author knows.
    public record Plan(String folder, List<IndexFacts> indexes, List<KeyPlan> keys, List<Question> questions) {

    }


    // --------------------------------------------------------------------------------


    /// A drafted doc.
    ///
    /// @param text  The doc's text, with the lines.
    /// @param lines The `## Fields` lines drafted, by key.
    /// @param notes What the lines can't do.
    public record Draft(String text, Map<String, String> lines, List<String> notes) {

    }
}
