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

import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.instructions.AgentInstructions;
import stroom.ai.impl.tools.instructions.AgentKnowledge;
import stroom.ai.impl.tools.instructions.InstructionRules;
import stroom.ai.impl.tools.instructions.InstructionRules.FieldRule;
import stroom.ai.impl.tools.query.DataSourceAccess;
import stroom.docref.DocRef;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Which field of an index holds the user (or the host, the element, the Action, the event code) for an activity
/// timeline, found in order, each index on its own (a site standard most but not all indexes follow falls back per
/// index): the call's (or the skill's) field; else the `## Fields` section of the AGENTS docs that apply where the
/// index is ([InstructionRules#fields]); else the field conventions' (the ECS profile's `user.name`, the flat
/// profile's `UserId`: their `EventSource/User/Id`); else, for the user, a field the index's documentation names on
/// a line about the user. The first the index has is used, and where it came from is said. Asking the user is the
/// tool's, when none is found. The person ([InstructionRules#PERSON_FIELD], an id decorated on each event, the same
/// for all of a person's accounts) is found the same way, by the convention's `EventSource/User/UserDetails/Id`
/// (ECS `user.id`); [PersonScope] chooses between it and the logon ids for each index. So are the fields of the
/// person (or account) an event was done to ([InstructionRules#TARGET_PERSON_FIELD], `TARGET_USER_FIELD`): a User
/// object of the action, the convention's `EventDetail/*/User/UserDetails/Id` and `EventDetail/*/User/Id` (ECS
/// `user.target.id`, `user.target.name`).
public class TimelineFields {

    /// The event-logging paths of what is found, as the conventions map them.
    static final Map<String, String> CONVENTION_PATHS = Map.of(
            InstructionRules.USER_FIELD, "EventSource/User/Id",
            InstructionRules.PERSON_FIELD, "EventSource/User/UserDetails/Id",
            InstructionRules.TARGET_USER_FIELD, "EventDetail/*/User/Id",
            InstructionRules.TARGET_PERSON_FIELD, "EventDetail/*/User/UserDetails/Id",
            InstructionRules.HOST_FIELD, "EventSource/Device/HostName",
            InstructionRules.ACTION_FIELD, "EventDetail/*/Action",
            InstructionRules.EVENT_TYPE_FIELD, "EventDetail/TypeId");
    private static final Pattern CONVENTION_LINE = Pattern.compile(
            "^\\s+([A-Za-z*/]+):\\s*\\{\\s*name:\\s*([^,}\\s]+)", Pattern.MULTILINE);
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_@.\\-]+");
    private static final Pattern USER_WORD = Pattern.compile("\\buser", Pattern.CASE_INSENSITIVE);

    private final AgentInstructions agentInstructions;
    private final DataSourceAccess dataSourceAccess;
    private final Optional<AgentKnowledge> knowledge;

    /// @param agentInstructions The AGENTS docs.
    /// @param dataSourceAccess  Gives an index's documentation.
    /// @param knowledge         The field conventions, where the module that bundles them is present.
    @Inject
    public TimelineFields(final AgentInstructions agentInstructions,
                          final DataSourceAccess dataSourceAccess,
                          final Optional<AgentKnowledge> knowledge) {
        this.agentInstructions = agentInstructions;
        this.dataSourceAccess = dataSourceAccess;
        this.knowledge = knowledge;
    }

    /// Finds the field holding one thing in an index.
    ///
    /// @param session The session, for the AGENTS docs' audit.
    /// @param index   The index.
    /// @param what    What it holds: [InstructionRules#USER_FIELD], `HOST_FIELD` and so on.
    /// @param given   The call's (or skill's) fields, the first the index has used; empty for none.
    /// @param has     True for a field the index has.
    /// @return The field and where it came from, or empty if none is found.
    public Optional<Found> find(final ToolSession session,
                         final DocRef index,
                         final String what,
                         final List<String> given,
                         final Predicate<String> has) {
        for (final String field : NullSafe.list(given)) {
            if (field != null && has.test(field)) {
                return Optional.of(new Found(field, Origin.CALL, null));
            }
        }
        final FieldRule rule = agentInstructions.fieldsRuled(session, index).get(what);
        if (rule != null) {
            for (final String field : rule.fields()) {
                if (has.test(field)) {
                    return Optional.of(new Found(field, Origin.AGENTS, rule.from()));
                }
            }
        }
        final String path = CONVENTION_PATHS.get(what);
        if (path != null) {
            for (final Map.Entry<String, String> convention : conventions(path).entrySet()) {
                if (has.test(convention.getValue())) {
                    return Optional.of(new Found(convention.getValue(), Origin.CONVENTION, convention.getKey()));
                }
            }
        }
        if (InstructionRules.USER_FIELD.equals(what)) {
            return documented(index, has);
        }
        return Optional.empty();
    }

    /// @param path An event-logging path, e.g. `EventSource/User/Id`.
    /// @return Each convention profile's field for it, by the profile's name.
    Map<String, String> conventions(final String path) {
        final Map<String, String> out = new LinkedHashMap<>();
        if (knowledge.isEmpty()) {
            return out;
        }
        for (final String name : knowledge.get().conventionNames()) {
            knowledge.get().convention(name).flatMap(text -> conventionField(text, path))
                    .ifPresent(field -> out.put(name, field));
        }
        return out;
    }

    /// @param text A convention profile's YAML.
    /// @param path An event-logging path.
    /// @return The field its `field_map` maps the path to, if it maps it.
    static Optional<String> conventionField(final String text, final String path) {
        final Matcher matcher = CONVENTION_LINE.matcher(NullSafe.string(text));
        while (matcher.find()) {
            if (matcher.group(1).equals(path)) {
                return Optional.of(matcher.group(2).strip());
            }
        }
        return Optional.empty();
    }

    // A field the index's documentation names on a line that speaks of the user.
    private Optional<Found> documented(final DocRef index, final Predicate<String> has) {
        final String documentation;
        try {
            documentation = dataSourceAccess.documentation(index).orElse("");
        } catch (final RuntimeException e) {
            return Optional.empty();
        }
        for (final String line : documentation.split("\\R")) {
            if (!USER_WORD.matcher(line).find()) {
                continue;
            }
            final List<String> tokens = new ArrayList<>();
            final Matcher token = TOKEN.matcher(line.replace("`", " "));
            while (token.find()) {
                tokens.add(token.group());
            }
            for (final String candidate : tokens) {
                if (has.test(candidate)) {
                    return Optional.of(new Found(candidate, Origin.DOCUMENTATION, null));
                }
            }
        }
        return Optional.empty();
    }


    // --------------------------------------------------------------------------------


    /// Where a field came from, in the order they are tried.
    public enum Origin {
        /// The call's, or the skill's.
        CALL("the call (or skill)"),
        /// An AGENTS doc's `## Fields`.
        AGENTS("the AGENTS doc's ## Fields"),
        /// A field convention.
        CONVENTION("the field convention"),
        /// The index's documentation.
        DOCUMENTATION("the index's documentation"),
        /// The user, asked.
        ASKED("asked");

        private final String description;

        Origin(final String description) {
            this.description = description;
        }

        /// @return Its name in the result.
        public String description() {
            return description;
        }
    }


    // --------------------------------------------------------------------------------


    /// A field found.
    ///
    /// @param field  The field.
    /// @param origin Where it came from.
    /// @param detail The AGENTS doc's folder or the convention's name, or null.
    public record Found(String field, Origin origin, String detail) {

        /// @return Where it came from, as the result says it.
        public String from() {
            return detail == null
                    ? origin.description()
                    : origin.description() + " (" + detail + ")";
        }
    }
}
