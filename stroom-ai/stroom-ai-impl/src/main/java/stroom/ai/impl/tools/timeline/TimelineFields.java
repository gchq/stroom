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
import stroom.ai.impl.tools.query.DataSourceDocumentation;
import stroom.ai.impl.tools.query.DataSourceDocumentation.Planned;
import stroom.docref.DocRef;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

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
/// index is ([InstructionRules#fields]); else the field whose source is the thing's event-logging path in the index
/// plan kept with the indexing XSLT of a pipeline feeding the index, as the build tools keep it
/// ([DataSourceDocumentation#plannedFrom], only plans of pipelines the user can view); else the field conventions'
/// (the ECS profile's `user.name`, the flat profile's `UserId`: their `EventSource/User/Id`); else, for the user, a
/// field the index's documentation names on a line about the user. The first the index has is used, and where it
/// came from is said; one found by a convention or the documentation, a guess, comes with the `## Fields` line to
/// add to an AGENTS doc where the index is, to make it the site's ([Found#suggestion]). Asking the user is the
/// tool's, when none is found. The person ([InstructionRules#PERSON_FIELD], an id decorated on each event, the same
/// for all of a person's accounts) is found the same way, by the convention's `EventSource/User/UserDetails/Id`
/// (ECS `user.id`); [PersonScope] chooses between it and the logon ids for each index. So are the fields of the
/// person (or account) an event was done to ([InstructionRules#TARGET_PERSON_FIELD], `TARGET_USER_FIELD`): a User
/// object of the action, by the convention's `EventDetail/Authorise/User/UserDetails/Id` and
/// `EventDetail/Authorise/User/Id` (ECS `user.target.id`, `user.target.name`, as it maps the User objects of Create,
/// Delete and Update too).
public class TimelineFields {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TimelineFields.class);

    /// The event-logging paths of what is found, as the conventions map them.
    static final Map<String, String> CONVENTION_PATHS = InstructionRules.CONVENTION_PATHS;
    private static final Pattern CONVENTION_LINE = Pattern.compile(
            "^\\s+([A-Za-z*/]+):\\s*\\{\\s*name:\\s*([^,}\\s]+)", Pattern.MULTILINE);
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_@.\\-]+");
    private static final Pattern USER_WORD = Pattern.compile("\\buser", Pattern.CASE_INSENSITIVE);

    private final AgentInstructions agentInstructions;
    private final DataSourceAccess dataSourceAccess;
    private final Optional<AgentKnowledge> knowledge;
    private final Provider<DataSourceDocumentation> documentationProvider;

    /// @param agentInstructions     The AGENTS docs.
    /// @param dataSourceAccess      Gives an index's documentation.
    /// @param knowledge             The field conventions, where the module that bundles them is present.
    /// @param documentationProvider Gives the index plans kept with the pipelines feeding an index.
    @Inject
    public TimelineFields(final AgentInstructions agentInstructions,
                          final DataSourceAccess dataSourceAccess,
                          final Optional<AgentKnowledge> knowledge,
                          final Provider<DataSourceDocumentation> documentationProvider) {
        this.agentInstructions = agentInstructions;
        this.dataSourceAccess = dataSourceAccess;
        this.knowledge = knowledge;
        this.documentationProvider = documentationProvider;
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
            final Optional<Planned> planned = planned(index, path, has);
            if (planned.isPresent()) {
                return Optional.of(new Found(planned.get().field().name(), Origin.PLAN,
                        planned.get().pipeline().getName()));
            }
            for (final Map.Entry<String, String> convention : conventions(path).entrySet()) {
                if (has.test(convention.getValue())) {
                    return Optional.of(guessed(index, what, new Found(convention.getValue(), Origin.CONVENTION,
                            convention.getKey())));
                }
            }
        }
        if (InstructionRules.USER_FIELD.equals(what)) {
            return documented(index, has).map(found -> guessed(index, what, found));
        }
        return Optional.empty();
    }

    // The first field the index has whose source in a feeding pipeline's index plan is the path; empty where none
    // is, or the plans can't be read.
    private Optional<Planned> planned(final DocRef index, final String path, final Predicate<String> has) {
        try {
            final DataSourceDocumentation documentation = documentationProvider.get();
            return documentation.plannedFrom(documentation.feedingPipelines(index), path).stream()
                    .filter(planned -> has.test(planned.field().name()))
                    .findFirst();
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to read the index plans feeding " + index, e);
            return Optional.empty();
        }
    }

    // A field found by a convention or the documentation, with the ## Fields line that would make it the site's.
    private Found guessed(final DocRef index, final String what, final Found found) {
        return found.withSuggestion(suggestion(what, found, folderOf(index)));
    }

    // The index's folder, or null where it can't be told.
    private String folderOf(final DocRef index) {
        try {
            return agentInstructions.folderOf(index);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to find the folder of " + index, e);
            return null;
        }
    }

    /// The `## Fields` line to add to an AGENTS doc where an index is, so a field found by a guess (a convention or
    /// the index's documentation) is the site's, for every tool and index there.
    ///
    /// @param what   What the field holds: [InstructionRules#USER_FIELD] and so on.
    /// @param found  The field found.
    /// @param folder The index's folder, e.g. `/Indexes`, or null if not known.
    /// @return E.g. "To make user.name the user field here, add `- user: user.name` under `## Fields` in an AGENTS
    ///         doc in /Indexes."
    static String suggestion(final String what, final Found found, final String folder) {
        return "To make " + found.field() + " the " + what + " field here, add `- " + what + ": " + found.field()
               + "` under `## Fields` in an AGENTS doc in " + NullSafe.nonBlankStringElse(folder,
                "the index's folder") + ".";
    }

    /// @param path An event-logging path, e.g. `EventSource/User/Id`.
    /// @return Each convention profile's field for it, by the profile's name.
    public Map<String, String> conventions(final String path) {
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
        /// The index plan kept with the indexing XSLT of a pipeline feeding the index.
        PLAN("the index plan"),
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
    /// @param field      The field.
    /// @param origin     Where it came from.
    /// @param detail     The AGENTS doc's folder, the pipeline whose index plan it is in, or the convention's name;
    ///                   or null.
    /// @param suggestion For a field found by a guess (a convention or the documentation), the `## Fields` line to
    ///                   add to an AGENTS doc to make it the site's ([TimelineFields#suggestion]), for the
    ///                   agent to offer; else null.
    public record Found(String field, Origin origin, String detail, String suggestion) {

        /// A field found with no suggestion.
        ///
        /// @param field  The field.
        /// @param origin Where it came from.
        /// @param detail The AGENTS doc's folder, the pipeline or the convention's name; or null.
        public Found(final String field, final Origin origin, final String detail) {
            this(field, origin, detail, null);
        }

        /// @return Where it came from, as the result says it, e.g. "the index plan of 'Mail indexing'".
        public String from() {
            if (detail == null) {
                return origin.description();
            }
            return origin == Origin.PLAN
                    ? origin.description() + " of '" + detail + "'"
                    : origin.description() + " (" + detail + ")";
        }

        /// @param suggestion The `## Fields` line to suggest, or null.
        /// @return It with that suggestion.
        public Found withSuggestion(final String suggestion) {
            return new Found(field, origin, detail, suggestion);
        }
    }
}
