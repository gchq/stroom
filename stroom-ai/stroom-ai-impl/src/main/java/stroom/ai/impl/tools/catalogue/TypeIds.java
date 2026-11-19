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
import java.util.List;
import java.util.regex.Pattern;

/// An event type's TypeId: Verb-Object (`Authenticate-Logon`, `Update-Password`, `Process-SOAPRequest`), and the
/// default suggested when a proposal gives none.
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

    private static final Pattern NOT_ALPHANUMERIC = Pattern.compile("[^A-Za-z0-9]");
    // What splits a value into its segments: a code's dots, slashes, backslashes and colons.
    private static final Pattern SEGMENTS = Pattern.compile("[./\\\\:]");
    private static final Pattern WORDS = Pattern.compile("\\s+");
    private static final Pattern OBJECT = Pattern.compile("^[A-Z][A-Za-z0-9]*$");

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
