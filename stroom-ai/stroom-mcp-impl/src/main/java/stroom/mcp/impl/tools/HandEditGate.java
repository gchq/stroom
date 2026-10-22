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

import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.generation.CodeDiff;
import stroom.ai.impl.tools.generation.HandEdits;
import stroom.ai.impl.tools.validation.XsltVersionHistory;
import stroom.pipeline.shared.XsltDoc;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/// Keeps an edit made by hand in Stroom's editor since the tools saved an XSLT (the Stroom MCP server's
/// `tools/translation.py` `hand_edit_gate`, `_remembered` and `_CARRY`): new code that undoes it is refused, saying
/// what it changed, so it can be carried into the mapping or plan; where the agent's change changes a field the edit
/// changed too, the user decides whether to keep their edit or use the proposed field.
@Singleton
class HandEditGate {

    /// The action the questions are asked for.
    static final String ACTION = "hand_edit";
    /// Keep the user's edit of a field.
    static final String KEEP = "Keep my hand edit";
    /// Use the field the change proposes.
    static final String OVERWRITE = "Use the proposed field";
    /// Overwrite the XSLT with the change, every hand edit dropped.
    static final String OVERWRITE_ALL = "Overwrite the XSLT with the change";
    /// Decide field by field.
    static final String PER_FIELD = "Decide field by field";
    /// How each kind of kept mapping or plan takes a hand edit.
    static final Map<String, String> CARRY = Map.of(
            KeptMapping.TRANSLATION, "carry it into the mapping: rebuild_mapping uuid='{uuid}' stream_ids=<the "
                                     + "sample streams> reads it from the XSLT, proven on the sample (or "
                                     + "build_translation_xslt changes=)",
            "index", "carry it into the index plan: a field the edit added as a field of index_plan (its name, type, "
                     + "and source: the XPath it reads, from the Event), one it removed left out, a source it changed "
                     + "changed",
            "cef", "carry it into the CEF plan (overrides), as the edit made it");
    /// The most answers remembered: the least recently used are forgotten first.
    static final int MAX_REMEMBERED = 10_000;

    private final KeptCode keptCode;
    private final Consent consent;
    private final Map<String, Map<String, String>> remembered = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(final Map.Entry<String, Map<String, String>> eldest) {
                    return size() > MAX_REMEMBERED;
                }
            });

    /// @param keptCode What the XSLT's kept mapping or plan generates now.
    /// @param consent  Asks the user about fields both changed.
    @Inject
    HandEditGate(final KeptCode keptCode, final Consent consent) {
        this.keptCode = keptCode;
        this.consent = consent;
    }

    /// @param kind A kind of kept mapping or plan, or null.
    /// @param uuid The XSLT's UUID.
    /// @return How to carry a hand edit into it.
    static String carry(final String kind, final String uuid) {
        return CARRY.getOrDefault(NullSafe.string(kind), "write code that keeps it").replace("{uuid}",
                NullSafe.string(uuid));
    }

    /// Whether new code may be saved over the XSLT as it is now.
    ///
    /// @param call    The call, whose id tells its forms apart.
    /// @param context The call's context.
    /// @param doc     The XSLT as it is now.
    /// @param code    The new code.
    /// @param kind    The kind of mapping or plan the new code is from, or null: to tell which of its fields the
    ///                agent's change touches that the edit touched too, which the user decides.
    /// @param payload That mapping or plan, or null.
    /// @param choices The user's answers from a `needs_guidance` reply (`*` or a field: `keep`, `overwrite` or
    ///                `per_field`), or null.
    /// @return Empty when the new code keeps the edit (or there was none), or the user said to overwrite what it
    /// undoes; otherwise the question to return.
    /// @throws ToolRefusal saying what to carry, when the new code undoes the edit.
    Optional<Map<String, Object>> check(final ToolCall call,
                                        final ToolCallContext context,
                                        final XsltDoc doc,
                                        final String code,
                                        final String kind,
                                        final Map<String, Object> payload,
                                        final Map<String, String> choices) {
        final String current = NullSafe.string(doc.getData());
        if (!Optional.of(false).equals(XsltVersionHistory.untouched(doc.getDescription(), current))) {
            // As the tools saved it, or saved before they recorded what (no edit to tell).
            return Optional.empty();
        }
        final Optional<KeptMapping.Kept> kept = KeptMapping.readMapping(doc.getDescription());
        final String base = kept.flatMap(keptCode::keptCode).orElse(null);
        final List<HandEdits.Undone> items = HandEdits.undone(base, current, code);
        if (items.isEmpty()) {
            return Optional.empty();
        }
        final String by = NullSafe.isNonBlankString(doc.getUpdateUser())
                ? " (by " + doc.getUpdateUser() + ")"
                : "";
        final Map<String, HandEdits.Collision> clashes = kept.isPresent() && kept.get().kind().equals(kind)
                ? HandEdits.collisions(kind, kept.get().payload(), payload, items)
                : Map.of();
        final Map<String, String> decided = remembered(context.session(), doc.getUuid(), current);
        NullSafe.map(choices).forEach((label, choice) -> {
            if (clashes.containsKey(label) || "*".equals(label)) {
                decided.put(label, choice);
            }
        });
        // One question first (asked for by the user): overwrite the XSLT with the change, every hand edit dropped,
        // or decide field by field. Answered per field already, it isn't asked.
        String upfront = null;
        if (!clashes.isEmpty() && !decided.containsKey("*")
            && !copied(decided).keySet().containsAll(clashes.keySet())) {
            upfront = upfrontQuestion(doc, by, items, clashes);
            final Optional<String> chosen = consent.choose(call, context, ACTION, upfront,
                    List.of(OVERWRITE_ALL, PER_FIELD), "XSLT '" + doc.getName() + "', edited by hand: overwrite it, "
                                                       + "or decide field by field?");
            if (chosen.isPresent()) {
                decided.put("*", OVERWRITE_ALL.equals(chosen.get())
                        ? "overwrite"
                        : "per_field");
                upfront = null;
            }
        }
        if ("overwrite".equals(decided.get("*"))) {
            return Optional.empty();
        }
        final List<Map<String, Object>> asked = new ArrayList<>();
        for (final Map.Entry<String, HandEdits.Collision> clash : clashes.entrySet()) {
            final String label = clash.getKey();
            if (decided.containsKey(label)) {
                continue;
            }
            final String question = fieldQuestion(doc, by, label, clash.getValue());
            // Unanswered up front (no form): the per-field questions go to the agent with it, for one round in the
            // chat.
            final Optional<String> chosen = upfront != null
                    ? Optional.empty()
                    : consent.choose(call, context, ACTION, question, List.of(KEEP, OVERWRITE),
                            label + ", edited by hand: keep your edit, or use the proposed one?");
            if (chosen.isEmpty()) {
                final Map<String, Object> options = new LinkedHashMap<>();
                options.put("keep", KEEP);
                options.put("overwrite", OVERWRITE);
                final Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("field", label);
                entry.put("question", question);
                entry.put("options", options);
                asked.add(entry);
            } else {
                decided.put(label, KEEP.equals(chosen.get())
                        ? "keep"
                        : "overwrite");
            }
        }
        if (!asked.isEmpty()) {
            return Optional.of(guidance(upfront, asked));
        }
        final List<String> overwritten = copied(decided).entrySet().stream()
                .filter(entry -> "overwrite".equals(entry.getValue()) && clashes.containsKey(entry.getKey()))
                .map(Map.Entry::getKey)
                .toList();
        final List<HandEdits.Undone> left = items.stream()
                .filter(item -> overwritten.stream().noneMatch(label -> clashes.get(label).undone().contains(item)))
                .toList();
        if (left.isEmpty()) {
            return Optional.empty();
        }
        throw new ToolRefusal(refusal(doc, by, kept.orElse(null), base, current, left, decided, clashes));
    }

    private static String upfrontQuestion(final XsltDoc doc,
                                          final String by,
                                          final List<HandEdits.Undone> items,
                                          final Map<String, HandEdits.Collision> clashes) {
        final Set<String> all = new HashSet<>();
        items.forEach(item -> all.add(item.toString()));
        clashes.values().forEach(clash -> clash.undone().forEach(item -> all.remove(item.toString())));
        final int others = all.size();
        return "XSLT '" + doc.getName() + "' was edited by hand in Stroom" + by + ", and the agent's change changes "
               + clashes.size() + " field" + plural(clashes.size()) + " the edit changed too: "
               + String.join(", ", clashes.keySet())
               + (others > 0
                ? " (the edit changed " + others + " other thing" + plural(others) + " as well)"
                : "")
               + ". Overwrite the XSLT with the change, dropping your hand edits, or decide field by field?";
    }

    private static String fieldQuestion(final XsltDoc doc,
                                        final String by,
                                        final String label,
                                        final HandEdits.Collision clash) {
        final List<String> edits = new ArrayList<>();
        for (final HandEdits.Undone item : clash.undone().subList(0, Math.min(3, clash.undone().size()))) {
            edits.add((item.again()
                    ? "took out"
                    : "added") + " " + item.item());
        }
        final String old = clash.had();
        final String proposed = clash.proposes();
        return "XSLT '" + doc.getName() + "', field " + label + ": you edited it by hand in Stroom" + by + ", which "
               + String.join("; ", edits) + ". The agent's change "
               + (NullSafe.isNonEmptyString(proposed)
                ? "proposes " + proposed
                : "removes it")
               + (NullSafe.isNonEmptyString(old) && NullSafe.isNonEmptyString(proposed)
                ? " (the plan had " + old + ")"
                : "")
               + ". Keep your hand edit, or use the proposed field?";
    }

    private static Map<String, Object> guidance(final String upfront, final List<Map<String, Object>> asked) {
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "needs_guidance");
        result.put("saved", null);
        if (upfront != null) {
            final Map<String, Object> options = new LinkedHashMap<>();
            options.put("overwrite", OVERWRITE_ALL);
            options.put("per_field", PER_FIELD);
            final Map<String, Object> question = new LinkedHashMap<>();
            question.put("question", upfront);
            question.put("options", options);
            result.put("hand_edit", question);
        }
        result.put("hand_edit_collisions", asked);
        result.put("hint", "Not saved: the user edited these fields by hand since the server saved the XSLT, and your "
                           + "change changes them too. " + (upfront != null
                ? "Ask the user the hand_edit question first, exactly, offering both options. To overwrite: call "
                  + "again with the same arguments and hand_edit_choices={'*': 'overwrite'}. Field by field: ask "
                  + "each of the hand_edit_collisions questions too, "
                : "Ask the user each question exactly, offering both options, ")
                           + "then call again with the same arguments and hand_edit_choices={field: 'keep' or "
                           + "'overwrite'}. Don't choose for them.");
        return result;
    }

    private static String refusal(final XsltDoc doc,
                                  final String by,
                                  final KeptMapping.Kept kept,
                                  final String base,
                                  final String current,
                                  final List<HandEdits.Undone> left,
                                  final Map<String, String> decided,
                                  final Map<String, HandEdits.Collision> clashes) {
        final String carry = carry(kept == null
                ? null
                : kept.kind(), doc.getUuid());
        final List<String> keptFields = copied(decided).entrySet().stream()
                .filter(entry -> "keep".equals(entry.getValue()) && clashes.containsKey(entry.getKey()))
                .map(Map.Entry::getKey)
                .toList();
        final String shown = kept != null && NullSafe.isNonEmptyString(base)
                ? " What the edit changed (- what its " + (KeptMapping.TRANSLATION.equals(kept.kind())
                ? "mapping"
                : kept.kind() + " plan") + " generates, + the XSLT): " + String.join(" | ",
                CodeDiff.diff(base, current)) + "."
                : "";
        final String one = keptFields.size() == 1
                ? "it"
                : "them";
        final String keeps = keptFields.isEmpty()
                ? ""
                : " The user keeps their edit of " + String.join(", ", keptFields) + ": leave your change to " + one
                  + " out, and make " + one + " as the edit has " + one + ".";
        final List<String> said = left.subList(0, Math.min(8, left.size())).stream()
                .map(HandEdits.Undone::toString)
                .toList();
        return "Not saved: XSLT '" + doc.getName() + "' was edited by hand since the server saved it" + by + ", and "
               + "this code undoes the edit: it " + String.join("; ", said) + "." + shown + keeps + " Tell the user, "
               + "and " + carry + "; then save again. Only if the user says to drop their edit: "
               + "discard_hand_edit=true.";
    }

    // The user's answers for this XSLT as it is now, kept for the session: asked once, however many calls.
    private Map<String, String> remembered(final ToolSession session, final String uuid, final String current) {
        final String key = (session == null
                ? "-"
                : session.kind() + ":" + session.id()) + "\n" + uuid + "\n"
                           + XsltCode.sha256(current).substring(0, 16);
        return remembered.computeIfAbsent(key, ignored -> Collections.synchronizedMap(new LinkedHashMap<>()));
    }

    /// @param decided The user's answers for an XSLT (a synchronised map another call may change at once).
    /// @return A copy, taken under the map's lock, to iterate: iterating it unlocked could fail or miss an answer.
    static Map<String, String> copied(final Map<String, String> decided) {
        synchronized (decided) {
            return new LinkedHashMap<>(decided);
        }
    }

    private static String plural(final int count) {
        return count > 1
                ? "s"
                : "";
    }
}
