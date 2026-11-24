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

import stroom.ai.api.ElasticKeyStreams.Transform;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.instructions.InstructionRules;
import stroom.ai.impl.tools.timeline.TimelineFields.Found;
import stroom.docref.DocRef;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/// Whose events a tool reads in one index: a person, by the person field (an id processing decorates each event
/// with, the same for all of their accounts), or, where the index has no person field, one or more logon ids by
/// the user field (`alex.chen`, `adm-achen`, `alex.chen@corp.example`: each account kept visible). Both fields are
/// found by [TimelineFields] (the call's or skill's, the AGENTS doc's `## Fields`, convention). The result of a tool
/// says which it was ([By#label]), the accounts covered and the indexes where nothing matched; for those, close
/// variants of the logon ids found in the index ([#variants]: the domain added or removed, another case) are
/// offered, never merged without the user's confirmation.
public final class PersonScope {

    /// What a result by logon id says of the accounts it doesn't name.
    public static final String OTHER_ACCOUNTS = "By logon id: other accounts of the person (an admin or service "
                                                + "account) aren't included unless named.";
    /// What makes two logon ids close variants of each other: the domain and the case set aside.
    private static final List<Transform> VARIANT = List.of(Transform.STRIP_DOMAIN, Transform.LOWERCASE);

    private PersonScope() {
        // Static only.
    }

    /// Chooses whose events to read in one index.
    ///
    /// @param fields       Finds the index's person and user fields.
    /// @param session      The session, for the AGENTS docs' audit.
    /// @param index        The index.
    /// @param person       The person id, or null.
    /// @param logonIds     The logon ids, empty for none; the fallback where the index has no person field.
    /// @param personFields The call's (or skill's) person fields, the first the index has used; empty for none.
    /// @param userFields   The call's (or skill's) user fields, likewise.
    /// @param has          True for a field the index has (and can filter on).
    /// @return The scope: by person id, by logon id, or neither with why.
    public static Scope resolve(final TimelineFields fields,
                                final ToolSession session,
                                final DocRef index,
                                final String person,
                                final List<String> logonIds,
                                final List<String> personFields,
                                final List<String> userFields,
                                final Predicate<String> has) {
        final String personId = NullSafe.trim(person);
        final List<String> logons = cleaned(logonIds);
        if (!personId.isEmpty()) {
            final Optional<Found> personField = fields.find(session, index, InstructionRules.PERSON_FIELD,
                    NullSafe.list(personFields), has);
            if (personField.isPresent()) {
                return new Scope(By.PERSON, personField.get(), List.of(personId), null);
            }
            if (logons.isEmpty()) {
                return new Scope(null, null, List.of(), "no field holding the person id was found (a ## Fields "
                                                        + "'person:' line in the AGENTS doc names it), and no "
                                                        + "logon ids were given to fall back on");
            }
        }
        if (logons.isEmpty()) {
            return new Scope(null, null, List.of(), "give a person id or one or more logon ids");
        }
        final Optional<Found> userField = fields.find(session, index, InstructionRules.USER_FIELD,
                NullSafe.list(userFields), has);
        return userField
                .map(found -> new Scope(By.LOGON, found, logons, null))
                .orElseGet(() -> new Scope(null, null, List.of(), "no field holding the user was found"));
    }

    /// Chooses whose events, done to them, to read in one index: those whose target field (the person or account
    /// an action was done to: access granted to them, their password reset, a failed logon against their account)
    /// holds the person id, by the target person field, else the logon ids, by the target user field, each found as
    /// the person and user fields are ([TimelineFields]: the call's or skill's, the AGENTS doc's `## Fields`
    /// `target person:` or `target user:`, the convention's ECS `user.target.id` or `user.target.name`). The person's
    /// own acts on themselves (their own logon, a password they changed) are left out by the actor's scope, so what
    /// is read was done by someone else, or by no account at all (a failed logon).
    ///
    /// @param fields             Finds the index's target fields.
    /// @param session            The session, for the AGENTS docs' audit.
    /// @param index              The index.
    /// @param person             The person id, or null.
    /// @param logonIds           The logon ids, empty for none.
    /// @param targetPersonFields The call's (or skill's) target person fields; empty for none.
    /// @param targetUserFields   The call's (or skill's) target user fields; empty for none.
    /// @param actor              Whose acts the index's events are, as [#resolve] chose (its field and values are
    ///                           left out of what was done to them); unusable for none.
    /// @param has                True for a field the index has (and can filter on).
    /// @return The target scope: by person id, by logon id, or neither with why.
    public static Target target(final TimelineFields fields,
                                final ToolSession session,
                                final DocRef index,
                                final String person,
                                final List<String> logonIds,
                                final List<String> targetPersonFields,
                                final List<String> targetUserFields,
                                final Scope actor,
                                final Predicate<String> has) {
        final String personId = NullSafe.trim(person);
        final List<String> logons = cleaned(logonIds);
        final String actorField = actor != null && actor.usable()
                ? actor.field().field()
                : null;
        final List<String> actorValues = actorField == null
                ? List.of()
                : actor.values();
        if (!personId.isEmpty()) {
            final Optional<Found> targetPerson = fields.find(session, index, InstructionRules.TARGET_PERSON_FIELD,
                    NullSafe.list(targetPersonFields), has);
            if (targetPerson.isPresent()) {
                return new Target(By.PERSON, targetPerson.get(), List.of(personId), actorField, actorValues, null);
            }
        }
        if (logons.isEmpty()) {
            return new Target(null, null, List.of(), null, List.of(), personId.isEmpty()
                    ? "give a person id or one or more logon ids"
                    : "no field holding the person an event was done to was found (a ## Fields 'target person:' "
                      + "line in the AGENTS doc names it), and no logon ids were given to fall back on");
        }
        final Optional<Found> targetUser = fields.find(session, index, InstructionRules.TARGET_USER_FIELD,
                NullSafe.list(targetUserFields), has);
        return targetUser
                .map(found -> new Target(By.LOGON, found, logons, actorField, actorValues, null))
                .orElseGet(() -> new Target(null, null, List.of(), null, List.of(), "no field holding the account "
                                                                                     + "an event was done to was "
                                                                                     + "found (a ## Fields 'target "
                                                                                     + "user:' line in the AGENTS "
                                                                                     + "doc names it)"));
    }

    /// The values an index holds that are close variants of the logon ids given (the domain added or removed, or
    /// another case) but not the same: offered to the user where nothing matched as given, never merged unasked.
    ///
    /// @param logonIds The logon ids given.
    /// @param held     Values of the index's user field, e.g. from a sample read with the domain and case set aside.
    /// @return The variants, in the order held, each once.
    public static List<String> variants(final Collection<String> logonIds, final Collection<String> held) {
        final Set<String> given = new LinkedHashSet<>(cleaned(logonIds));
        final Set<String> keys = new LinkedHashSet<>();
        given.forEach(logon -> keys.add(variantKey(logon)));
        final Set<String> out = new LinkedHashSet<>();
        for (final String value : NullSafe.collection(held)) {
            if (value != null && !value.isBlank() && !given.contains(value.strip())
                && keys.contains(variantKey(value))) {
                out.add(value.strip());
            }
        }
        return List.copyOf(out);
    }

    /// @return What sets close variants aside: the domain stripped, then lower case.
    public static List<Transform> variantTransforms() {
        return VARIANT;
    }

    private static String variantKey(final String value) {
        return Transform.applyAll(VARIANT, value.strip());
    }

    private static List<String> cleaned(final Collection<String> values) {
        final Set<String> out = new LinkedHashSet<>();
        for (final String value : NullSafe.collection(values)) {
            if (value != null && !value.isBlank()) {
                out.add(value.strip());
            }
        }
        return new ArrayList<>(out);
    }


    // --------------------------------------------------------------------------------


    /// How an index's events are matched to whom they are of.
    public enum By {
        /// By the person field.
        PERSON("by person id"),
        /// By the user field, for each logon id given.
        LOGON("by logon id");

        private final String label;

        By(final String label) {
            this.label = label;
        }

        /// @return How the result says it, e.g. "by person id".
        public String label() {
            return label;
        }
    }


    // --------------------------------------------------------------------------------


    /// Whose events to read in one index.
    ///
    /// @param by       By person id or logon id; null if neither can be read (see `leftOut`).
    /// @param field    The field filtered on and where it came from; null with `by`.
    /// @param values   The values filtered on: the person id, or each logon id.
    /// @param leftOut  Why the index can't be read for them, or null.
    public record Scope(By by, Found field, List<String> values, String leftOut) {

        /// Copies the list.
        public Scope {
            values = NullSafe.list(values).stream().toList();
        }

        /// @return True if the index can be read.
        public boolean usable() {
            return by != null;
        }
    }


    // --------------------------------------------------------------------------------


    /// Whose events, done to them, to read in one index.
    ///
    /// @param by          By the target person id or the target logon id; null if neither can be read (see
    ///                    `leftOut`).
    /// @param field       The target field filtered on and where it came from; null with `by`.
    /// @param values      The values it is filtered on: the person id, or each logon id.
    /// @param actorField  The field holding who acted, whose `actorValues` are left out (the person's own acts on
    ///                    themselves); null for none.
    /// @param actorValues The actor's values left out.
    /// @param leftOut     Why the index can't be read for them, or null.
    public record Target(By by,
                         Found field,
                         List<String> values,
                         String actorField,
                         List<String> actorValues,
                         String leftOut) {

        /// Copies the lists.
        public Target {
            values = NullSafe.list(values).stream().toList();
            actorValues = NullSafe.list(actorValues).stream().toList();
        }

        /// @return True if the index can be read.
        public boolean usable() {
            return by != null;
        }

        /// @return How the result says it, e.g. "done to them, by target person id (user.target.id)".
        public String describe() {
            return by == null
                    ? leftOut
                    : "done to them, by target " + by.label().substring("by ".length()) + " (" + field.field()
                      + " from " + field.from() + (actorField == null
                    ? ")"
                    : "; their own acts by " + actorField + " left out)");
        }
    }
}
