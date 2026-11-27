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
import stroom.ai.impl.tools.query.DataSourceDocumentation.FeedingPlans;
import stroom.ai.impl.tools.query.DataSourceDocumentation.Planned;
import stroom.docref.DocRef;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Which field of an index holds the user (or the host, the element, the Action, the event code) for an activity
/// timeline, found in order, each index on its own (a site standard most but not all indexes follow falls back per
/// index): the call's (or the skill's) field; else the `## Fields` section of the AGENTS docs that apply where the
/// index is ([InstructionRules#fields]); else the field whose source is the thing's event-logging path in the index
/// plan kept with the indexing XSLT of the pipelines feeding the index, as the build tools keep it
/// ([DataSourceDocumentation#feedingPlans], only plans of pipelines the user can view, read from their effective
/// properties: every feeding pipeline's, where they agree one field, where they differ each pipeline's field
/// ([Found#byPipeline])); else the field the index's documentation gives the path in its From column (its Field
/// mapping table, [DataSourceDocumentation#fieldRows]); else the field conventions'
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
/// Delete and Update too). What has no convention (the application, the object, the window title, a session's key, a
/// process's id) is found by the paths of [#PLAN_PATHS] in the plans and the From column alike.
public class TimelineFields {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TimelineFields.class);

    /// A process's id, as the schema's Process element holds it: read from the index where a plan maps it, so the
    /// schema's pair (a process from its start to its end) is matched by it without the Events XML. Not a `## Fields`
    /// key.
    static final String PROCESS_ID = "process id";
    /// The event-logging paths of what is found, as the conventions map them.
    static final Map<String, String> CONVENTION_PATHS = InstructionRules.CONVENTION_PATHS;
    /// What else a timeline reads of an event, with no field convention: the event-logging paths an index plan (or
    /// the From column of an index's documentation) may map, the most telling first. Only the schema's own names.
    static final Map<String, List<String>> PLAN_PATHS = Map.of(
            InstructionRules.APPLICATION_FIELD, List.of("EventDetail/Process/Command"),
            InstructionRules.OBJECT_FIELD, List.of("EventDetail/*/*/Subject", "EventDetail/*/*/URL",
                    "EventDetail/*/*/Path", "EventDetail/*/URL", "EventDetail/*/Path"),
            InstructionRules.TITLE_FIELD, List.of("EventDetail/*/*/Title"),
            InstructionRules.SESSION_FIELD, List.of("EventSource/SessionId"),
            PROCESS_ID, List.of("EventDetail/Process/ProcessId"));
    private static final Pattern CONVENTION_LINE = Pattern.compile(
            "^\\s+([A-Za-z*/]+):\\s*\\{\\s*name:\\s*([^,}\\s]+)", Pattern.MULTILINE);
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_@.\\-]+");
    private static final Pattern USER_WORD = Pattern.compile("\\buser", Pattern.CASE_INSENSITIVE);
    // How long an index's plans and documentation are kept for one call's lookups, and for how many indexes.
    private static final long MEMO_MS = 30_000;
    private static final int MEMO_SIZE = 200;

    private final AgentInstructions agentInstructions;
    private final DataSourceAccess dataSourceAccess;
    private final Optional<AgentKnowledge> knowledge;
    private final Provider<DataSourceDocumentation> documentationProvider;
    private final Map<String, IndexReads> memo = new ConcurrentHashMap<>();

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
        final List<String> paths = path != null
                ? List.of(path)
                : PLAN_PATHS.getOrDefault(what, List.of());
        if (!paths.isEmpty()) {
            final IndexReads reads = reads(session, index);
            for (final String one : paths) {
                final Optional<Found> planned = planned(reads, one, has);
                if (planned.isPresent()) {
                    return planned;
                }
            }
            for (final String one : paths) {
                final Optional<Found> documentedFrom = documentedFrom(reads, one, has);
                if (documentedFrom.isPresent()) {
                    return documentedFrom;
                }
            }
        }
        if (path != null) {
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

    // The field whose source in the feeding pipelines' index plans is the path, of those the index has: every
    // pipeline's plan is read; where they agree, one field; where they differ, each pipeline's field, the first
    // pipeline's first. Empty where none is, or the plans can't be read.
    private static Optional<Found> planned(final IndexReads reads, final String path, final Predicate<String> has) {
        final List<Planned> usable = reads.plans().plannedFrom(path).stream()
                .filter(planned -> has.test(planned.field().name()))
                .toList();
        if (usable.isEmpty()) {
            return Optional.empty();
        }
        final Map<String, List<String>> byField = new LinkedHashMap<>();
        FeedingPlans.byField(usable).forEach((field, pipelines) -> byField.put(field, pipelines.stream()
                .map(DocRef::getName)
                .toList()));
        final Planned first = usable.getFirst();
        return Optional.of(new Found(first.field().name(), Origin.PLAN, first.pipeline().getName(), null,
                byField.size() > 1
                        ? byField
                        : Map.of()));
    }

    // The field the index's documentation (its Field mapping table) says comes from the path, in its From column,
    // of those the index has.
    private static Optional<Found> documentedFrom(final IndexReads reads,
                                                  final String path,
                                                  final Predicate<String> has) {
        for (final Map.Entry<String, Map<String, String>> row : reads.rows().entrySet()) {
            final String from = row.getValue().entrySet().stream()
                    .filter(cell -> cell.getKey().strip().toLowerCase(Locale.ROOT).startsWith("from"))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .map(cell -> cell.replace("`", "").strip())
                    .orElse("");
            if (DataSourceDocumentation.sourceIs(from, path) && has.test(row.getKey())) {
                return Optional.of(new Found(row.getKey(), Origin.INDEX_DOCUMENTATION, null));
            }
        }
        return Optional.empty();
    }

    // The index's plans and documentation rows, read once for the lookups of one call (the user, the host, the
    // Action...), kept a short while for the session; read afresh after that.
    private IndexReads reads(final ToolSession session, final DocRef index) {
        final String key = session + "\n" + index.getUuid();
        final long now = System.currentTimeMillis();
        final IndexReads known = memo.get(key);
        if (known != null && now - known.readAt() < MEMO_MS) {
            return known;
        }
        if (memo.size() > MEMO_SIZE) {
            memo.clear();
        }
        final IndexReads read = new IndexReads(session, index, documentationProvider, now);
        memo.put(key, read);
        return read;
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
    ///         doc in /Indexes (with propose_instructions)."
    static String suggestion(final String what, final Found found, final String folder) {
        return "To make " + found.field() + " the " + what + " field here, add `- " + what + ": " + found.field()
               + "` under `## Fields` in an AGENTS doc in " + NullSafe.nonBlankStringElse(folder,
                "the index's folder") + " (with propose_instructions).";
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
        /// The From column of the index's documentation (its Field mapping table).
        INDEX_DOCUMENTATION("the From column of the index's documentation"),
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
    /// @param field      The field: the first the index has.
    /// @param origin     Where it came from.
    /// @param detail     The AGENTS doc's folder, the pipeline whose index plan it is in, or the convention's name;
    ///                   or null.
    /// @param suggestion For a field found by a guess (a convention or the documentation), the `## Fields` line to
    ///                   add to an AGENTS doc to make it the site's ([TimelineFields#suggestion]), for the
    ///                   agent to offer; else null.
    /// @param byPipeline Where the index plans of the pipelines feeding the index differ: each field, with the
    ///                   pipelines (by name) whose plan maps the thing to it, the first [#field]; else empty.
    public record Found(String field, Origin origin, String detail, String suggestion,
                        Map<String, List<String>> byPipeline) {

        /// Copies the pipelines' fields, in order.
        public Found {
            byPipeline = byPipeline == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(byPipeline));
        }

        /// A field found with no suggestion.
        ///
        /// @param field  The field.
        /// @param origin Where it came from.
        /// @param detail The AGENTS doc's folder, the pipeline or the convention's name; or null.
        public Found(final String field, final Origin origin, final String detail) {
            this(field, origin, detail, null, Map.of());
        }

        /// A field found, the same in every plan.
        ///
        /// @param field      The field.
        /// @param origin     Where it came from.
        /// @param detail     The AGENTS doc's folder, the pipeline or the convention's name; or null.
        /// @param suggestion The `## Fields` line to suggest, or null.
        public Found(final String field, final Origin origin, final String detail, final String suggestion) {
            this(field, origin, detail, suggestion, Map.of());
        }

        /// @return Every field it may be in: where the feeding pipelines' plans differ, each pipeline's (for now a
        /// reader may take any of them; which pipeline wrote an event is its stream's); else the one field.
        public List<String> fields() {
            return byPipeline.isEmpty()
                    ? List.of(field)
                    : List.copyOf(byPipeline.keySet());
        }

        /// @return True if the feeding pipelines' plans map it to different fields.
        public boolean varies() {
            return byPipeline.size() > 1;
        }

        /// @return Where it came from, as the result says it, e.g. "the index plan of 'Mail indexing'", or where
        /// the plans differ "the index plans, which differ by pipeline: user.name ('A indexing'), UserId ('B
        /// indexing')".
        public String from() {
            if (varies()) {
                final List<String> each = new ArrayList<>();
                byPipeline.forEach((name, pipelines) -> each.add(name + " (" + String.join(", ", pipelines.stream()
                        .map(pipeline -> "'" + pipeline + "'")
                        .toList()) + ")"));
                return "the index plans, which differ by pipeline: " + String.join(", ", each);
            }
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
            return new Found(field, origin, detail, suggestion, byPipeline);
        }
    }


    // --------------------------------------------------------------------------------


    // An index's plans and documentation rows, each read when first asked for.
    private static final class IndexReads {

        private final ToolSession session;
        private final DocRef index;
        private final Provider<DataSourceDocumentation> documentationProvider;
        private final long readAt;
        private FeedingPlans plans;
        private Map<String, Map<String, String>> rows;

        private IndexReads(final ToolSession session,
                           final DocRef index,
                           final Provider<DataSourceDocumentation> documentationProvider,
                           final long readAt) {
            this.session = session;
            this.index = index;
            this.documentationProvider = documentationProvider;
            this.readAt = readAt;
        }

        private long readAt() {
            return readAt;
        }

        // The index plans of the pipelines feeding the index; none where they can't be read.
        private synchronized FeedingPlans plans() {
            if (plans == null) {
                try {
                    plans = Objects.requireNonNullElse(documentationProvider.get().feedingPlans(index),
                            FeedingPlans.none());
                } catch (final RuntimeException e) {
                    LOGGER.debug(() -> "Unable to read the index plans feeding " + index, e);
                    plans = FeedingPlans.none();
                }
            }
            return plans;
        }

        // The field rows of the index's documentation; none where it can't be read.
        private synchronized Map<String, Map<String, String>> rows() {
            if (rows == null) {
                try {
                    rows = DataSourceDocumentation.fieldRows(NullSafe.list(documentationProvider.get().find(session,
                            index, "Documentation read to find the fields of an index")));
                } catch (final RuntimeException e) {
                    LOGGER.debug(() -> "Unable to read the documentation of " + index, e);
                    rows = Map.of();
                }
            }
            return rows;
        }
    }
}
