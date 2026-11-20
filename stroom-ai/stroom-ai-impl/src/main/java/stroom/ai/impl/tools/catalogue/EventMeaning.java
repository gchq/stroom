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

import stroom.ai.impl.tools.draft.SourceNotes;
import stroom.ai.impl.tools.validation.EventSchema;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/// The schema checks of what an event type means (its event detail, Action, outcome and what it acts on), shared by
/// the event type catalogue and record_source_notes' events, so the two check alike: the event detail an action
/// element ([SourceNotes#detailProblem]); an outcome and an Action only where the element has them, the Action one the
/// schema allows, as it spells it ([SourceNotes#allowedAction]); what it acts on an object under the element, written
/// out to its leaf ([SourceNotes#affectedPath]).
public final class EventMeaning {

    private EventMeaning() {
        // Static only.
    }

    /// The outcome and Action checked against the action element: the parts it has no element for, and an Action the
    /// schema's enumeration doesn't hold. Seen in chat 163: Alert given an outcome and an Action, which the user
    /// confirmed, and the draft then failed on; in the agent eval (cases 60 and 65), "Add member to role" confirmed as
    /// an Authorise Action, which takes only Request or Modify.
    ///
    /// @param schema  The event-logging schema.
    /// @param label   What the problems name it by, e.g. `event logon` or `event type Update-Password`.
    /// @param detail  Its event detail, e.g. `Authenticate`.
    /// @param success Its outcome, or null for none.
    /// @param action  Its Action, or null or empty for none.
    /// @return The Action as the schema spells it (null when none is given or it can't be one), and the problems.
    public static Parts parts(final EventSchema schema,
                              final String label,
                              final String detail,
                              final Object success,
                              final String action) {
        final String element = "EventDetail/" + detail;
        final List<String> problems = new ArrayList<>();
        if (success != null && !schema.hasPath(element + "/Outcome")) {
            problems.add(label + ": " + element + " has no Outcome, so leave success out");
        }
        final boolean hasAction = action != null && !action.isEmpty();
        if (hasAction && !schema.hasPath(element + "/Action")) {
            problems.add(label + ": " + element + " has no Action, so leave action out" + ownElement(schema, detail,
                    action));
            return new Parts(null, problems);
        }
        if (!hasAction) {
            return new Parts(null, problems);
        }
        final Optional<String> allowed = SourceNotes.allowedAction(schema, detail, action);
        if (allowed.isEmpty()) {
            problems.add(label + ": " + element + "/Action takes only " + String.join(", ",
                    SourceNotes.allowedActions(schema, detail)) + ", not '" + action + "': give the one nearest "
                         + "the kind (its own name stays in type_id and description)");
            return new Parts(null, problems);
        }
        return new Parts(allowed.get(), problems);
    }

    // An Action that names an action element of its own (Move, Delete, Copy), said to be one. Seen in eval case 56
    // (2026-11-20): Update with the action Move, told only to leave action out, kept as Update.
    private static String ownElement(final EventSchema schema, final String detail, final String action) {
        final String stripped = action.strip();
        if (stripped.isEmpty()) {
            return "";
        }
        final String element = Character.toUpperCase(stripped.charAt(0)) + stripped.substring(1);
        if (element.equals(detail) || element.contains("/") || SourceNotes.detailProblem(schema, element) != null) {
            return "";
        }
        return "; " + element + " is an action element of its own: event_detail " + element + ", if that is what "
               + "the records are";
    }

    /// What it acts on (`affected_as`) checked against its action element, written out to the leaf the affected
    /// field's value goes in (`User` becomes `User/Id`).
    ///
    /// @param schema     The event-logging schema.
    /// @param label      What the problem names it by.
    /// @param detail     Its event detail.
    /// @param affectedAs Its affected_as.
    /// @return The path below the action element, or why it can't be one.
    public static Affected affected(final EventSchema schema,
                                    final String label,
                                    final String detail,
                                    final String affectedAs) {
        try {
            return new Affected(SourceNotes.affectedPath(schema, detail, affectedAs), null);
        } catch (final IllegalArgumentException e) {
            return new Affected(null, label + ": " + e.getMessage());
        }
    }


    // --------------------------------------------------------------------------------


    /// The outcome and Action checked.
    ///
    /// @param action   The Action as the schema spells it, or null.
    /// @param problems Why the parts can't be written, none when they can.
    public record Parts(String action, List<String> problems) {

    }


    // --------------------------------------------------------------------------------


    /// What it acts on, checked.
    ///
    /// @param path    The path below the action element, or null.
    /// @param problem Why it can't be one, or null.
    public record Affected(String path, String problem) {

    }
}
