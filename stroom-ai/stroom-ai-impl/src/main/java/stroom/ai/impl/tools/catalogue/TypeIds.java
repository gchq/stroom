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

package stroom.ai.impl.tools.catalogue;

import stroom.ai.impl.tools.generation.Condition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/// An event type's TypeId: Verb-Object (`Authenticate-Logon`, `Update-Password`, `Process-SOAPRequest`), and the
/// default suggested when a proposal gives none. A drop rule (an event type with `drop: true`, whose records are
/// left untranslated) writes no event, so its `type_id` is a plain short label ([#dropProblem]): only its name and
/// the reason its records are dropped.
public final class TypeIds {

    /// A TypeId: a verb, then one or more objects, each a capital letter then letters and digits, joined by `-`.
    public static final Pattern VERB_OBJECT = Pattern.compile(
            "^[A-Z][A-Za-z0-9]*-[A-Z][A-Za-z0-9]*(?:-[A-Z][A-Za-z0-9]*)*$");
    /// The longest a TypeId may be.
    public static final int MAX_LENGTH = 100;
    /// The verb of a default TypeId for an event type with no event detail (EventDetail/Unknown's).
    public static final String UNKNOWN_VERB = "Unknown";
    /// The object of a default TypeId when nothing else gives one.
    public static final String DEFAULT_OBJECT = "Event";
    /// The longest a drop rule's label may be.
    public static final int MAX_DROP_LENGTH = 60;
    /// A drop rule's label when nothing else gives one.
    public static final String DEFAULT_DROP_LABEL = "Dropped records";
    /// The labels the uncatalogued records go by (the catch-all's), which no drop rule may take (found in review,
    /// 2026-11-27: a drop rule labelled Unknown had its count overwritten by the uncatalogued records'), compared
    /// ignoring case.
    public static final List<String> RESERVED_DROP_LABELS = List.of("Unknown", "Uncatalogued records");
    /// A drop rule's label: a letter or digit, then letters, digits, spaces and `_ . , ( ) / ' -`, no run of `-` (it
    /// is written in XSLT comments) and no `-` last.
    public static final Pattern DROP_LABEL = Pattern.compile("^[A-Za-z0-9](?:[A-Za-z0-9 _.,()/']|-(?!-))*(?<![- ])$");

    private static final Pattern NOT_ALPHANUMERIC = Pattern.compile("[^A-Za-z0-9]");
    // What splits a value into its segments: a code's dots, slashes, backslashes and colons.
    private static final Pattern SEGMENTS = Pattern.compile("[./\\\\:]");
    private static final Pattern WORDS = Pattern.compile("\\s+");
    private static final Pattern OBJECT = Pattern.compile("^[A-Z][A-Za-z0-9]*$");
    // What a drop rule's default label may not hold, and the words of a description it is made from.
    private static final Pattern NOT_LABEL = Pattern.compile("[^A-Za-z0-9 _.,()/'-]+");
    private static final Pattern DASHES = Pattern.compile("-{2,}");
    private static final Pattern SENTENCE_END = Pattern.compile("[.;:](?:\\s|$)");
    private static final Pattern LABEL_ENDS = Pattern.compile("^[^A-Za-z0-9]+|[- ]+$");
    private static final int LABEL_WORDS = 6;

    private TypeIds() {
        // Static only.
    }

    /// @param typeId A TypeId.
    /// @return Why it isn't one (not Verb-Object, or too long), or null when it is.
    public static String problem(final String typeId) {
        if (typeId == null || typeId.isBlank()) {
            return "type_id is needed: Verb-Object, e.g. Update-Password";
        }
        if (typeId.length() > MAX_LENGTH) {
            return "type_id '" + typeId.substring(0, 40) + "...' is " + typeId.length() + " characters long, at most "
                   + MAX_LENGTH;
        }
        if (!VERB_OBJECT.matcher(typeId).matches()) {
            return "type_id '" + typeId + "' isn't Verb-Object: a verb and what it acts on, each starting with a "
                   + "capital letter, letters and digits only, joined by '-' (Update-Password, Process-SOAPRequest)";
        }
        return null;
    }

    /// @param label A drop rule's label (its `type_id`).
    /// @return Why it can't be one (none, too long, or holding what a short label doesn't), or null when it can.
    public static String dropProblem(final String label) {
        if (label == null || label.isBlank()) {
            return "type_id is needed: a drop rule's short label, e.g. Operational logging";
        }
        if (label.length() > MAX_DROP_LENGTH) {
            return "type_id '" + label.substring(0, 40) + "...' is " + label.length() + " characters long: a drop "
                   + "rule's label is at most " + MAX_DROP_LENGTH;
        }
        if (!DROP_LABEL.matcher(label).matches()) {
            return "type_id '" + label + "' isn't a drop rule's short label: letters, digits and spaces (and "
                   + "_ . , ( ) / ' -), starting with a letter or digit, e.g. Operational logging";
        }
        if (reservedDropLabel(label)) {
            return "type_id '" + label + "' is what the uncatalogued records go by: give the drop rule a label of "
                   + "its own, e.g. Operational logging";
        }
        return null;
    }

    // True if a label is one the uncatalogued records go by.
    private static boolean reservedDropLabel(final String label) {
        return RESERVED_DROP_LABELS.stream().anyMatch(reserved -> reserved.equalsIgnoreCase(label.strip()));
    }

    /// @param type An event type.
    /// @return Why its TypeId can't be kept: a drop rule's as a label ([#dropProblem]), an existing pipeline's as it
    /// writes it ([#keptProblem]), any other's as Verb-Object ([#problem]); null when it can.
    public static String problemOf(final EventType type) {
        if (type.drop()) {
            return dropProblem(type.typeId());
        }
        return EventType.EXISTING_PIPELINE.equals(type.basis())
                ? keptProblem(type.typeId())
                : problem(type.typeId());
    }

    /// The TypeId suggested for an event type that gives none: a drop rule's default label ([#defaultDropLabel]),
    /// any other's default TypeId ([#defaultTypeId]).
    ///
    /// @param type The event type.
    /// @return Its default TypeId.
    public static String defaultFor(final EventType type) {
        return type.drop()
                ? defaultDropLabel(type)
                : defaultTypeId(type);
    }

    /// The label suggested for a drop rule that gives none: its title, else the first words of its description, each
    /// kept to what a label may hold; else [#DEFAULT_DROP_LABEL]. Never a made-up prefix or Verb-Object: a drop rule
    /// is no event.
    ///
    /// @param type The drop rule.
    /// @return Its default label.
    public static String defaultDropLabel(final EventType type) {
        for (final String candidate : Arrays.asList(type.title(), firstWords(type.description()))) {
            final String label = asLabel(candidate);
            if (label != null && !reservedDropLabel(label)) {
                return label;
            }
        }
        return DEFAULT_DROP_LABEL;
    }

    // The first words of a text, up to its first full stop, or null.
    private static String firstWords(final String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        final String sentence = SENTENCE_END.split(text.strip(), 2)[0];
        final String[] words = WORDS.split(sentence.strip(), -1);
        return String.join(" ", Arrays.asList(words).subList(0, Math.min(LABEL_WORDS, words.length)));
    }

    // A candidate kept to what a drop rule's label may hold, at most its length; null when nothing is left.
    private static String asLabel(final String candidate) {
        if (candidate == null) {
            return null;
        }
        String label = DASHES.matcher(NOT_LABEL.matcher(candidate).replaceAll(" ")).replaceAll("-");
        label = WORDS.matcher(label).replaceAll(" ").strip();
        if (label.length() > MAX_DROP_LENGTH) {
            final int space = label.lastIndexOf(' ', MAX_DROP_LENGTH);
            label = label.substring(0, space > 0
                    ? space
                    : MAX_DROP_LENGTH);
        }
        label = LABEL_ENDS.matcher(label).replaceAll("");
        return label.isEmpty() || !DROP_LABEL.matcher(label).matches()
                ? null
                : label;
    }

    /// @param typeId A TypeId an existing pipeline writes, kept as it is (any form).
    /// @return Why it can't be kept (none, or too long), or null when it can.
    public static String keptProblem(final String typeId) {
        final String problem = problem(typeId);
        return problem != null && typeId != null && !typeId.isBlank() && typeId.length() <= MAX_LENGTH
                ? null
                : problem;
    }

    /// The TypeId suggested for an event type that gives none: its event detail element as the verb, and as the
    /// object what it acts on (`affected_as`), else the last segment of its first `equals` value as it is, else its
    /// title's first word; e.g. `Process-SOAPService`. A candidate that can't be an object (`4624`, all digits) is
    /// passed over; one of letters and other characters loses the others, and its first letter is made a capital
    /// (`log-in` is `Login`). No tidying of names beyond that: the agent's proposal, or the user's amendment, tidies
    /// it.
    ///
    /// @param type The event type.
    /// @return Its default TypeId.
    public static String defaultTypeId(final EventType type) {
        final String verb = word(firstSegment(type.eventDetail()));
        final List<String> objects = new ArrayList<>();
        objects.add(firstSegment(type.affectedAs()));
        objects.add(lastSegment(firstEquals(type)));
        if (type.title() != null) {
            objects.add(WORDS.split(type.title().strip(), -1)[0]);
        }
        final String object = objects.stream()
                .map(TypeIds::word)
                .filter(candidate -> candidate != null)
                .findFirst()
                .orElse(DEFAULT_OBJECT);
        return (verb == null
                ? UNKNOWN_VERB
                : verb) + "-" + object;
    }

    // The first equals value of the event type's first way of being recognised, or null.
    private static String firstEquals(final EventType type) {
        return type.recognisedBy().stream()
                .flatMap(way -> way.when().stream())
                .map(Condition::equalTo)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private static String firstSegment(final String path) {
        if (path == null) {
            return null;
        }
        final String[] parts = path.strip().split("/", -1);
        return parts[0];
    }

    private static String lastSegment(final String value) {
        if (value == null) {
            return null;
        }
        final String[] parts = SEGMENTS.split(value.strip(), -1);
        for (int i = parts.length - 1; i >= 0; i--) {
            if (!parts[i].isBlank()) {
                return parts[i].strip();
            }
        }
        return null;
    }

    // A candidate as one part of a TypeId: its letters and digits, the first made a capital; null when it can't be
    // one (empty, or not starting with a letter).
    private static String word(final String candidate) {
        if (candidate == null) {
            return null;
        }
        final String kept = NOT_ALPHANUMERIC.matcher(candidate).replaceAll("");
        if (kept.isEmpty()) {
            return null;
        }
        final String capital = Character.toUpperCase(kept.charAt(0)) + kept.substring(1);
        return OBJECT.matcher(capital).matches()
                ? capital
                : null;
    }
}
