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

import stroom.ai.api.ElasticEql;
import stroom.ai.api.ElasticKeyStreams;
import stroom.ai.api.ElasticKeyStreams.Refusal;
import stroom.ai.api.ElasticKeyStreams.Transform;
import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.AiWorkspaceConfig;
import stroom.ai.impl.tools.ActivityCharacterisation.Activity;
import stroom.ai.impl.tools.ActivityCharacterisation.Characterised;
import stroom.ai.impl.tools.ActivityCharacterisation.Placement;
import stroom.ai.impl.tools.EqlQueryWriter.Filter;
import stroom.ai.impl.tools.TimelineReader.Read;
import stroom.ai.impl.tools.TimelineReader.RoleRead;
import stroom.ai.impl.tools.TimelineReader.Source;
import stroom.ai.impl.tools.TimelineSessions.Action;
import stroom.ai.impl.tools.TimelineSessions.Daily;
import stroom.ai.impl.tools.TimelineSessions.Event;
import stroom.ai.impl.tools.TimelineSessions.Flag;
import stroom.ai.impl.tools.TimelineSessions.Options;
import stroom.ai.impl.tools.TimelineSessions.Role;
import stroom.ai.impl.tools.TimelineSessions.Session;
import stroom.ai.impl.tools.TimelineSessions.Timeline;
import stroom.ai.impl.tools.TimelineSessions.Window;
import stroom.ai.impl.tools.ToolSchema.ArraySchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.TimeRange;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
import stroom.query.common.v2.DateExpressionParser;
import stroom.query.impl.QueryService;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.security.shared.DocumentPermission;
import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PermissionException;
import stroom.util.time.StroomDuration;

import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/// `activity_timeline`: one user's (or entity's) activity over a period from several sources, worked out in code
/// ([TimelineSessions]), never by the model: sessions of active use of each application on each host, the actions
/// taken in each, each day's active time by application, the window lifetimes, the actions in no session, the raw
/// events by their stream and event ids, and the data-quality flags (missing events, never smoothed over). Each
/// source names the fields holding the user, host, application, window title, window, object and text, and its
/// roles as conditions (as `sequence_query`'s steps take them): what a focus, an open, a close, an idle start or
/// end, and each kind of action look like in that source. The events are read with the streaming join's machinery
/// ([TimelineReader]: Elastic Indexes, a terms condition for the one user), and the sessions are written as a
/// workspace table `gantt_chart` draws as it is.
///
/// With `characterise`, the activities the sessions make up, what the user was working on
/// ([ActivityCharacterisation]): anchors from the sessions' graph (Leiden, as the chart analytics' clusters), loose
/// items classified by the model in side calls ([ModelSideCalls]: the chat's model; over `/mcp` the client's, by
/// sampling, else skipped, saying so), so the chat's model sees only the outcome. The analyst's corrections are kept
/// with the investigation (the chat's workspace, beside the results), so a re-run keeps them.
class ActivityTimelineTool extends AbstractWorkspaceTool {

    static final String NAME = "activity_timeline";
    static final String ENTITY_ARG = "entity";
    static final String SOURCES_ARG = "sources";
    static final String TIME_FROM_ARG = "timeFrom";
    static final String TIME_TO_ARG = "timeTo";
    static final String IDLE_CAP_ARG = "idleCap";
    static final String CHARACTERISE_ARG = "characterise";
    static final String CORRECTIONS_ARG = "corrections";
    static final String RUN_ANYWAY_ARG = "runAnyway";
    static final String INDEX_KEY = "index";
    static final String NAME_KEY = "name";
    static final String USER_KEY = "user";
    static final String HOST_KEY = "host";
    static final String PROCESS_KEY = "process";
    static final String TITLE_KEY = "title";
    static final String WINDOW_KEY = "window";
    static final String OBJECT_KEY = "object";
    static final String TEXT_KEY = "text";
    static final String TRANSFORM_KEY = "transform";
    static final String ROLES_KEY = "roles";
    static final String ACTIONS_KEY = "actions";
    static final String KIND_KEY = "kind";
    static final String WHERE_KEY = SequenceQueryTool.WHERE_KEY;
    static final String ANY_OF_KEY = SequenceQueryTool.ANY_OF_KEY;
    /// The period when the call gives none.
    static final String DEFAULT_FROM = "now()-7d";
    static final String DEFAULT_TO = "now()";
    /// The folder each user's timeline is written to, beside its corrections.
    static final String FOLDER = "/outputs/timelines/";
    static final String SESSIONS_FILE = "sessions.csv";
    static final String ACTIONS_FILE = "actions.csv";
    static final String EVENTS_FILE = "events.csv";
    static final String DAILY_FILE = "daily.csv";
    static final String WINDOWS_FILE = "windows.csv";
    static final String QUALITY_FILE = "quality.csv";
    static final String ACTIVITIES_FILE = "activities.csv";
    static final String ITEMS_FILE = "items.csv";
    static final String CORRECTIONS_FILE = "corrections.json";
    static final String CACHE_FILE = "characterisation-cache.json";
    /// The sessions and activities shown in the result; the rest are in the tables.
    static final int SHOWN = 15;
    /// The form's key for classifying past the budget.
    static final String BEYOND_FORM = "classify";
    static final String CLASSIFY_ALL = "Classify them all";
    static final String CLASSIFY_BUDGET = "Classify up to the budget";
    /// What every result says of the events.
    static final String CAVEAT = "The events come straight from Elasticsearch, as sequence_query's do: they may "
                                 + "include events of streams the user can't see or that have been deleted.";
    /// What every result says the sessions are.
    static final String DERIVED = "Sessions are worked out in code from the roles given: an interval on an "
                                  + "application starts at a focus on it and ends at the next focus, an idle start, "
                                  + "or the idle cap (marked uncertain). Missing events are flagged, never "
                                  + "smoothed over.";
    private static final List<String> ROLE_NAMES = List.of(Role.FOCUS.argName(), Role.OPEN.argName(),
            Role.CLOSE.argName(), Role.IDLE_START.argName(), Role.IDLE_END.argName());

    private final Provider<ElasticKeyStreams> keyStreamsProvider;
    private final DataSourceAccess dataSourceAccess;
    private final Provider<QueryService> queryServiceProvider;
    private final AiAuditLog auditLog;

    /// @param workspace            The chats' workspaces.
    /// @param keyStreamsProvider   Reads each source's events for the entity, in time order.
    /// @param dataSourceAccess     Finds the sources' indexes the user can use.
    /// @param queryServiceProvider Gives each index's fields and their types.
    /// @param auditLog             Audits what each index was searched for.
    @Inject
    ActivityTimelineTool(final ChatWorkspace workspace,
                         final Provider<ElasticKeyStreams> keyStreamsProvider,
                         final DataSourceAccess dataSourceAccess,
                         final Provider<QueryService> queryServiceProvider,
                         final AiAuditLog auditLog) {
        super(workspace);
        this.keyStreamsProvider = keyStreamsProvider;
        this.dataSourceAccess = dataSourceAccess;
        this.queryServiceProvider = queryServiceProvider;
        this.auditLog = auditLog;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .checksDocuments(DocumentPermission.USE)
                .note("Use on every source's Elastic Index is checked, as for any search, again by Stroom's "
                      + "Elasticsearch module as each is read. No permission of its own: the tool policies decide "
                      + "who may build a timeline.")
                .build();
    }

    /// Offered to a user who can use an Elastic Index, the only data sources it reads (as the streaming join).
    ///
    /// @return True if the user can use at least one Elastic Index.
    @Override
    public boolean isUsable() {
        return dataSourceAccess.canUseAny(ElasticIndexDoc.TYPE);
    }

    @Override
    public boolean usesRunSlot() {
        // It runs searches.
        return true;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.SEARCH;
    }

    // The definition is full, as it is offered only once the model turns on its group (timelines, see
    // BuiltInToolGroups).
    @Override
    public ToolDefinition definition() {
        final ObjectSchema condition = ObjectSchema.builder()
                .description("One condition, as a sequence's steps take it.")
                .addStringProperty("field", "The index's field.")
                .addEnumProperty("condition", SequenceQueryTool.conditionNames(), "The condition (default "
                                                                                    + "EQUALS).")
                .addStringProperty("value", "BETWEEN: 'from,to'; IN and NOT_IN: values separated by commas; "
                                            + "EQUALS takes * wildcards.")
                .addBooleanProperty(SequenceQueryTool.CASE_INSENSITIVE_KEY, "True to match text whatever its "
                                                                            + "case.")
                .required("field")
                .build();
        final ObjectSchema conditions = ObjectSchema.builder()
                .description("What the role's events look like in this source: conditions all of which hold "
                             + "(where), and of which at least one holds (any_of).")
                .addProperty(WHERE_KEY, ArraySchema.builder().items(condition).description("All hold.").build())
                .addProperty(ANY_OF_KEY, ArraySchema.builder().items(condition).description("One holds.").build())
                .build();
        final ObjectSchema action = ObjectSchema.builder()
                .description("A kind of action and what its events look like.")
                .addStringProperty(KIND_KEY, "The action's label, e.g. 'clipboard copy', 'file copy', 'web "
                                             + "access'.")
                .addProperty(WHERE_KEY, ArraySchema.builder().items(condition).description("All hold.").build())
                .addProperty(ANY_OF_KEY, ArraySchema.builder().items(condition).description("One holds.").build())
                .required(KIND_KEY)
                .build();
        final ObjectSchema roles = ObjectSchema.builder()
                .description("The source's roles: each optional, given only where the source holds such events.")
                .addProperty(Role.FOCUS.argName(), conditions)
                .addProperty(Role.OPEN.argName(), conditions)
                .addProperty(Role.CLOSE.argName(), conditions)
                .addProperty(Role.IDLE_START.argName(), conditions)
                .addProperty(Role.IDLE_END.argName(), conditions)
                .addProperty(ACTIONS_KEY, ArraySchema.builder().items(action)
                        .description("The kinds of action it holds.").build())
                .build();
        final ObjectSchema source = ObjectSchema.builder()
                .description("One source: an Elastic Index, the fields that hold each thing, and its roles.")
                .addStringProperty(INDEX_KEY, "The Elastic Index doc's uuid or name.")
                .addStringProperty(NAME_KEY, "A short name for it in the result (default the index's).")
                .addStringProperty(USER_KEY, "The field holding the user (or entity).")
                .addProperty(TRANSFORM_KEY, ArraySchema.builder()
                        .items(new ToolSchema.EnumSchema(null, SequenceQueryTool.TRANSFORMS))
                        .description("Optional: what is done to the user field's values and the entity before they "
                                     + "are compared, where sources write users differently (strip_domain, "
                                     + "lowercase).")
                        .build())
                .addStringProperty(HOST_KEY, "The field holding the host (optional: an action with no host goes "
                                             + "in the session active at its time).")
                .addStringProperty(PROCESS_KEY, "The field holding the application (process).")
                .addStringProperty(TITLE_KEY, "The field holding the window title.")
                .addStringProperty(WINDOW_KEY, "The field holding a window's id, pairing its open and close "
                                               + "(default the application).")
                .addStringProperty(OBJECT_KEY, "The field holding what an event touched: a file, a URL, a "
                                               + "subject.")
                .addStringProperty(TEXT_KEY, "The field holding free text (a message, an AI chat prompt), for "
                                             + "characterise.")
                .addProperty(ROLES_KEY, roles)
                .required(INDEX_KEY, USER_KEY, ROLES_KEY)
                .build();
        return ToolDefinition.builder()
                .name(NAME)
                .description(ToolDescription.builder()
                        .general("Builds one user's (or entity's) activity timeline for a period from several "
                                 + "sources, in code: sessions of active use of each application on each host "
                                 + "(from a focus to the next focus, an idle start, or the idle cap), the actions "
                                 + "in each, active time per day by application, and data-quality flags for "
                                 + "missing events (unpaired opens and closes, idle starts with no end, a source "
                                 + "falling silent while another shows the user active, clocks that disagree). "
                                 + "Each source gives its fields and its roles as conditions ({field, condition, "
                                 + "value}, as a sequence's steps take them): what a focus, open, close, idle start "
                                 + "(lock, sleep, logoff), "
                                 + "idle end (unlock, wake, logon) and each kind of action look like there; a "
                                 + "skill, or the source's documentation, may hold them. Elastic Indexes only. "
                                 + "With characterise=true, also what the user was working on: activities "
                                 + "tying sessions together by the documents, pages and terms they share, loose "
                                 + "items (messages, prompts) classified by a model outside this conversation.")
                        .chatOnly(" The sessions table draws as a timeline with gantt_chart (dashboards group) "
                                  + "as it is.")
                        .build())
                .parameters(ObjectSchema.builder()
                        .addStringProperty(ENTITY_ARG, "The user (or entity), as the sources hold it, e.g. "
                                                       + "jsmith.")
                        .addProperty(SOURCES_ARG, ArraySchema.builder().items(source)
                                .description("The sources, at least one.").build())
                        .addStringProperty(TIME_FROM_ARG, "The period's start: ISO 8601 or a date expression "
                                                          + "(default " + DEFAULT_FROM + ").")
                        .addStringProperty(TIME_TO_ARG, "The period's end (default " + DEFAULT_TO + ").")
                        .addStringProperty(IDLE_CAP_ARG, "How long with no event on a host ends an interval, "
                                                         + "e.g. 30m (default the setting, 30 minutes).")
                        .addBooleanProperty(CHARACTERISE_ARG, "True to find what the user was working on: the "
                                                              + "activities the sessions make up.")
                        .addProperty(CORRECTIONS_ARG, ArraySchema.builder().items(ObjectSchema.builder()
                                        .description("{move: <session or item id>, to: <activity id, not_work or "
                                                     + "unclear>}, {merge: [<activity id>, ...]} (into the "
                                                     + "first), or {rename: <activity id>, label: <name>}.")
                                        .build())
                                .description("Optional: the analyst's corrections to the activities, kept with "
                                             + "this investigation so a re-run keeps them.")
                                .build())
                        .addBooleanProperty(RUN_ANYWAY_ARG, "True to classify loose items past the budget, as "
                                                            + "the user agrees.")
                        .addStringProperty(UserTimeZones.ARG, UserTimeZones.DESCRIPTION)
                        .required(ENTITY_ARG, SOURCES_ARG)
                        .build())
                .build();
    }

    @Override
    protected Duration timeLimit(final ToolCallContext context) {
        return limits(context).getQueryTimeLimit().getDuration();
    }

    @Override
    protected ToolResult run(final ToolCall request, final ToolCallContext context) {
        final SessionWorkspace workspace = sessionWorkspace(context);
        final AiWorkspaceConfig config = limits(context);
        final String entity = stringArg(request, ENTITY_ARG);
        if (entity == null) {
            throw new WorkspaceException("Give " + ENTITY_ARG + ": the user (or entity) whose timeline to build.");
        }
        final ToolTimeZone zone = timeZone(request, context);
        final long now = System.currentTimeMillis();
        final Period period = period(request, zone, now);
        final long capMs = idleCap(request, config);
        final List<Source> sources = sources(request);
        final Instant deadline = Instant.now().plus(timeLimit(context));
        final JoinBudget budget = new JoinBudget(config.getTimelineMaxEvents(), Long.MAX_VALUE, deadline,
                context.stopped());
        context.progress().report("Reading " + entity + "'s events from " + sources.size() + " source(s)");
        final Read read;
        try {
            read = TimelineReader.read(keyStreamsProvider.get(), sources, entity, period.timeRange(), budget);
        } catch (final PermissionException e) {
            throw new WorkspaceException("You can't use one of the sources' Elastic Indexes: " + e.getMessage());
        }
        audit(context, sources, read, entity, period);

        final Set<Integer> withOpen = new LinkedHashSet<>();
        for (int i = 0; i < sources.size(); i++) {
            if (sources.get(i).opens()) {
                withOpen.add(i);
            }
        }
        final List<String> names = sources.stream().map(Source::name).toList();
        final Timeline timeline = TimelineSessions.build(read.events(), new Options(capMs, period.toMs(), now,
                zone.zone(), names, withOpen));
        final Map<String, Event> byId = new LinkedHashMap<>();
        read.events().forEach(event -> byId.put(event.id(), event));

        final String folder = FOLDER + safe(entity) + "/";
        final Characterised characterised = booleanArg(request, CHARACTERISE_ARG, false)
                ? characterise(request, context, workspace, config, timeline, byId, names, folder, zone)
                : null;
        final Files files = write(workspace, folder, timeline, byId, names, characterised, sources);
        final Map<String, Object> structured = structured(entity, period, capMs, sources, read, timeline,
                characterised, files, zone);
        return context.session().isChat()
                ? ToolResult.text(markdown(entity, period, capMs, sources, read, timeline, characterised, files,
                        zone)).withStructuredContent(structured)
                : ToolResult.text(JsonUtil.writeValueAsString(structured, false)).withStructuredContent(structured);
    }

    // ------------------------------------------------------------------------------------------------
    // The call

    // The period, its date expressions and times without an offset in the call's zone, as instants.
    private Period period(final ToolCall request, final ToolTimeZone zone, final long now) {
        final String givenFrom = NullSafe.nonBlankStringElse(stringArg(request, TIME_FROM_ARG), DEFAULT_FROM);
        final String givenTo = NullSafe.nonBlankStringElse(stringArg(request, TIME_TO_ARG), DEFAULT_TO);
        final long from = instant(StroomQlDatePoints.timeInZone(givenFrom, zone, now), now, TIME_FROM_ARG);
        final long to = instant(StroomQlDatePoints.timeInZone(givenTo, zone, now), now, TIME_TO_ARG);
        if (to <= from) {
            throw new WorkspaceException(TIME_TO_ARG + " must be after " + TIME_FROM_ARG + ".");
        }
        return new Period(from, to, new TimeRange(null, Instant.ofEpochMilli(from).toString(),
                Instant.ofEpochMilli(to).toString()));
    }

    private static long instant(final String value, final long now, final String arg) {
        try {
            final Optional<ZonedDateTime> parsed = DateExpressionParser.parse(value.strip(), now);
            if (parsed.isPresent()) {
                return parsed.get().toInstant().toEpochMilli();
            }
        } catch (final RuntimeException e) {
            // Said below.
        }
        throw new WorkspaceException(arg + " '" + value + "' isn't a time: give ISO 8601 (e.g. 2026-11-10T00:00:00Z) "
                                     + "or a date expression (e.g. now()-7d).");
    }

    private long idleCap(final ToolCall request, final AiWorkspaceConfig config) {
        final String given = stringArg(request, IDLE_CAP_ARG);
        final Duration cap;
        if (given == null) {
            cap = NullSafe.getOrElse(config.getTimelineIdleCap(), StroomDuration::getDuration,
                    Duration.ofMinutes(30));
        } else {
            try {
                cap = StroomDuration.parse(given).getDuration();
            } catch (final RuntimeException e) {
                throw new WorkspaceException(IDLE_CAP_ARG + " '" + given + "' isn't a duration: give e.g. 30m or 1h.");
            }
        }
        if (cap.compareTo(Duration.ofMinutes(1)) < 0 || cap.compareTo(Duration.ofHours(24)) > 0) {
            throw new WorkspaceException(IDLE_CAP_ARG + " must be from 1 minute to 24 hours.");
        }
        return cap.toMillis();
    }

    // The sources, each checked against its index: Elastic, usable, its fields there, its roles as expressions.
    private List<Source> sources(final ToolCall request) {
        final Object given = argsMap(request).get(SOURCES_ARG);
        final List<?> list = given instanceof final List<?> items
                ? items
                : given instanceof final String text && !text.isBlank()
                        ? parsedList(text)
                        : List.of();
        if (list.isEmpty()) {
            throw new WorkspaceException("Give " + SOURCES_ARG + ": at least one source, each {index, user, roles} "
                                         + "and the fields it holds.");
        }
        final List<Source> sources = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            if (!(list.get(i) instanceof final Map<?, ?> map)) {
                throw new WorkspaceException("Source " + (i + 1) + " isn't an object {index, user, roles, ...}.");
            }
            sources.add(source(map, "Source " + (i + 1)));
        }
        return sources;
    }

    private static List<?> parsedList(final String text) {
        try {
            return JsonUtil.readValue(text, List.class);
        } catch (final RuntimeException e) {
            throw new WorkspaceException(SOURCES_ARG + " is a list of sources, each {index, user, roles, ...}.");
        }
    }

    private Source source(final Map<?, ?> map, final String what) {
        final String index = text(map.get(INDEX_KEY));
        if (index == null) {
            throw new WorkspaceException(what + " has no " + INDEX_KEY + ": give its Elastic Index's uuid or name.");
        }
        final DocRef doc = dataSource(index);
        if (!ElasticIndexDoc.TYPE.equals(doc.getType())) {
            throw new WorkspaceException(what + ": " + DataSourceAccess.describe(doc) + " isn't an Elastic Index: "
                                         + "a timeline reads Elastic Indexes only.");
        }
        final String name = NullSafe.nonBlankStringElse(text(map.get(NAME_KEY)), doc.getName());
        final ElasticKeyStreams.Doc target = target(doc, what);
        if (target.timeField() == null) {
            throw new WorkspaceException(what + ": Elastic Index '" + doc.getName() + "' has no time field, so its "
                                         + "events can't be put in time order.");
        }
        final Map<String, ElasticEql.Field> mapping = target.fields();
        final Map<String, FieldType> types = new LinkedHashMap<>();
        for (final QueryField field : EsqlQueryTool.fields(queryServiceProvider.get(), doc)) {
            types.put(field.getFldName(), field.getFldType());
        }
        String user = text(map.get(USER_KEY));
        if (user == null) {
            throw new WorkspaceException(what + " has no " + USER_KEY + ": give the field holding the user.");
        }
        user = keyField(user, mapping, what, doc);
        final JoinKeys.Kind kind = JoinKeys.kind(NullSafe.get(mapping.get(user), ElasticEql.Field::nativeType));
        if (kind == null) {
            throw new WorkspaceException(what + ": '" + user + "' in Elastic Index '" + doc.getName() + "' can't be "
                                         + "matched to one user: give a keyword, number or IP field.");
        }
        final List<Transform> transforms = transforms(map.get(TRANSFORM_KEY), what);
        if (!transforms.isEmpty() && kind != JoinKeys.Kind.TEXT) {
            throw new WorkspaceException(what + ": '" + user + "' isn't text, so it can't be transformed.");
        }
        final Map<String, String> fields = new LinkedHashMap<>();
        for (final String key : List.of(HOST_KEY, PROCESS_KEY, TITLE_KEY, WINDOW_KEY, OBJECT_KEY, TEXT_KEY)) {
            final String field = text(map.get(key));
            if (field != null) {
                checkField(field, mapping, types, what, doc);
                fields.put(key, field);
            }
        }
        final List<RoleRead> roles = roles(map.get(ROLES_KEY), what, mapping, types, doc);
        final boolean ids = mapping.containsKey(EqlSequenceResults.STREAM_ID)
                            && mapping.containsKey(EqlSequenceResults.EVENT_ID);
        return new Source(name, doc, user, kind, transforms, fields.get(HOST_KEY), fields.get(PROCESS_KEY),
                fields.get(TITLE_KEY), fields.get(WINDOW_KEY), fields.get(OBJECT_KEY), fields.get(TEXT_KEY), ids,
                roles);
    }

    // The user field as a key: a text field's keyword subfield in its place.
    private static String keyField(final String field,
                                   final Map<String, ElasticEql.Field> mapping,
                                   final String what,
                                   final DocRef doc) {
        final ElasticEql.Field mapped = mapping.get(field);
        if (mapped == null && !mapping.isEmpty()) {
            throw new WorkspaceException(what + ": Elastic Index '" + doc.getName() + "' has no field '" + field
                                         + "'. describe_data_source gives its fields.");
        }
        if (mapped != null && Set.of("text", "match_only_text").contains(mapped.nativeType())) {
            return mapping.values().stream()
                    .filter(other -> field.equals(other.multiFieldParent())
                                     && "keyword".equals(other.nativeType()))
                    .map(ElasticEql.Field::name)
                    .findFirst()
                    .orElseThrow(() -> new WorkspaceException(what + ": '" + field + "' is a text field with no "
                                                              + "keyword subfield, so it can't be matched to one "
                                                              + "user."));
        }
        return field;
    }

    private static void checkField(final String field,
                                   final Map<String, ElasticEql.Field> mapping,
                                   final Map<String, FieldType> types,
                                   final String what,
                                   final DocRef doc) {
        if (!mapping.isEmpty() && !mapping.containsKey(field) && !types.containsKey(field)) {
            throw new WorkspaceException(what + ": Elastic Index '" + doc.getName() + "' has no field '" + field
                                         + "'. describe_data_source gives its fields.");
        }
    }

    // The roles: each its conditions as a Stroom expression, the actions one each.
    private static List<RoleRead> roles(final Object given,
                                        final String what,
                                        final Map<String, ElasticEql.Field> mapping,
                                        final Map<String, FieldType> types,
                                        final DocRef doc) {
        if (!(given instanceof final Map<?, ?> map) || map.isEmpty()) {
            throw new WorkspaceException(what + " has no " + ROLES_KEY + ": give what its events mean, e.g. "
                                         + "{\"focus\": {\"where\": [...]}, \"actions\": [{\"kind\": \"file copy\", "
                                         + "\"where\": [...]}]}.");
        }
        final List<RoleRead> roles = new ArrayList<>();
        for (final Map.Entry<?, ?> entry : map.entrySet()) {
            final String key = String.valueOf(entry.getKey()).strip().toLowerCase(Locale.ROOT);
            if (ACTIONS_KEY.equals(key)) {
                if (!(entry.getValue() instanceof final List<?> actions)) {
                    throw new WorkspaceException(what + "'s " + ACTIONS_KEY + " is a list of {kind, where, any_of}.");
                }
                for (final Object action : actions) {
                    if (!(action instanceof final Map<?, ?> one) || text(one.get(KIND_KEY)) == null) {
                        throw new WorkspaceException(what + "'s " + ACTIONS_KEY + " is a list of {kind, where, "
                                                     + "any_of}, each with a kind.");
                    }
                    final String kind = text(one.get(KIND_KEY));
                    roles.add(new RoleRead(Role.ACTION, kind, expression(one, what + "'s action '" + kind + "'",
                            mapping, types, doc)));
                }
            } else if (ROLE_NAMES.contains(key)) {
                if (!(entry.getValue() instanceof final Map<?, ?> conditions)) {
                    throw new WorkspaceException(what + "'s role " + key + " is {where, any_of}.");
                }
                final ExpressionOperator expression = expression(conditions, what + "'s role " + key, mapping,
                        types, doc);
                if (expression == null) {
                    throw new WorkspaceException(what + "'s role " + key + " has no conditions: give where or "
                                                 + "any_of, so its events can be told from the others.");
                }
                roles.add(new RoleRead(Role.valueOf(key.toUpperCase(Locale.ROOT)), null, expression));
            } else {
                throw new WorkspaceException(what + ": '" + key + "' isn't a role: give " + String.join(", ",
                        ROLE_NAMES) + " or " + ACTIONS_KEY + ".");
            }
        }
        return roles;
    }

    private static ExpressionOperator expression(final Map<?, ?> conditions,
                                                 final String what,
                                                 final Map<String, ElasticEql.Field> mapping,
                                                 final Map<String, FieldType> types,
                                                 final DocRef doc) {
        final List<ExpressionItem> items = new ArrayList<>();
        for (final Filter filter : SequenceQueryTool.filters(conditions.get(WHERE_KEY), what, WHERE_KEY)) {
            checkField(filter.field(), mapping, types, what, doc);
            items.add(StreamFilters.item(filter, types, what));
        }
        final List<Filter> anyOf = SequenceQueryTool.filters(conditions.get(ANY_OF_KEY), what, ANY_OF_KEY);
        if (!anyOf.isEmpty()) {
            final List<ExpressionItem> any = new ArrayList<>();
            for (final Filter filter : anyOf) {
                checkField(filter.field(), mapping, types, what, doc);
                any.add(StreamFilters.item(filter, types, what));
            }
            items.add(ExpressionOperator.builder().op(Op.OR).children(any).build());
        }
        return items.isEmpty()
                ? null
                : ExpressionOperator.builder().op(Op.AND).children(items).build();
    }

    private static List<Transform> transforms(final Object given, final String what) {
        final List<?> names = given == null
                ? List.of()
                : given instanceof final List<?> list
                        ? list
                        : List.of(String.valueOf(given).split(","));
        final List<Transform> transforms = new ArrayList<>();
        for (final Object name : names) {
            final String text = NullSafe.trim(name == null
                    ? null
                    : String.valueOf(name)).toLowerCase(Locale.ROOT);
            if (text.isEmpty()) {
                continue;
            }
            if (!SequenceQueryTool.TRANSFORMS.contains(text)) {
                throw new WorkspaceException(what + ": transform '" + name + "' isn't one of "
                                             + String.join(", ", SequenceQueryTool.TRANSFORMS) + ".");
            }
            transforms.add(Transform.valueOf(text.toUpperCase(Locale.ROOT)));
        }
        return transforms;
    }

    private ElasticKeyStreams.Doc target(final DocRef doc, final String what) {
        try {
            final ElasticKeyStreams.Target target = keyStreamsProvider.get().target(List.of(doc));
            if (target == null || target.docs().isEmpty()) {
                throw new WorkspaceException(what + ": Elastic Index '" + doc.getName() + "' can't be read: its "
                                             + "cluster gave nothing for it.");
            }
            return target.docs().getFirst();
        } catch (final Refusal refusal) {
            throw new WorkspaceException(what + ": " + refusal.getMessage() + ".");
        } catch (final PermissionException e) {
            throw new WorkspaceException(what + ": you can't use Elastic Index '" + doc.getName() + "'.");
        }
    }

    // The data source a source names, by uuid or name: any the user can use.
    private DocRef dataSource(final String given) {
        final List<DocRef> found = dataSourceAccess.find(given);
        if (found.size() == 1) {
            return found.getFirst();
        } else if (found.size() > 1) {
            final StringBuilder sb = new StringBuilder("Several data sources you can use are called '")
                    .append(given).append("'. Give the uuid of the one you mean:");
            found.forEach(docRef -> sb.append("\n- ").append(DataSourceAccess.describe(docRef)));
            throw new WorkspaceException(sb.toString());
        }
        throw new WorkspaceException("No data source with uuid or name '" + given + "' that you can use. "
                                     + "list_data_sources gives those you can.");
    }

    private static String text(final Object value) {
        if (value == null) {
            return null;
        }
        final String text = String.valueOf(value).strip();
        return text.isEmpty()
                ? null
                : text;
    }

    private void audit(final ToolCallContext context,
                       final List<Source> sources,
                       final Read read,
                       final String entity,
                       final Period period) {
        for (int i = 0; i < sources.size(); i++) {
            final Source source = sources.get(i);
            final StringBuilder sb = new StringBuilder("activity timeline of ").append(entity).append(" (")
                    .append(NAME).append(")\nuser field: ").append(source.userField());
            for (final RoleRead role : source.roles()) {
                sb.append("\n").append(role.tag()).append(": ").append(role.filter());
            }
            sb.append("\ntime: ").append(period.timeRange().getFrom()).append(" to ").append(period.timeRange()
                    .getTo());
            auditLog.searched(context.session(), source.doc(), sb.toString(), i < read.counts().size()
                    ? read.counts().get(i)
                    : 0L, "Activity timeline by the " + NAME + " tool");
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Characterisation

    private Characterised characterise(final ToolCall request,
                                       final ToolCallContext context,
                                       final SessionWorkspace workspace,
                                       final AiWorkspaceConfig config,
                                       final Timeline timeline,
                                       final Map<String, Event> byId,
                                       final List<String> names,
                                       final String folder,
                                       final ToolTimeZone zone) {
        final WorkspacePath correctionsPath = WorkspacePath.parse(folder + CORRECTIONS_FILE);
        final WorkspacePath cachePath = WorkspacePath.parse(folder + CACHE_FILE);
        final List<Map<String, Object>> corrections = new ArrayList<>(readList(workspace, correctionsPath));
        final List<Map<String, Object>> given = givenCorrections(request);
        corrections.addAll(given);
        if (!given.isEmpty()) {
            // Kept with the investigation, so a re-run keeps them.
            workspace.write(correctionsPath, JsonUtil.writeValueAsString(corrections, true), null);
        }
        context.progress().report("Characterising " + timeline.sessions().size() + " session(s)");
        final boolean runAnyway = booleanArg(request, RUN_ANYWAY_ARG, false);
        final ActivityCharacterisation.Settings settings = new ActivityCharacterisation.Settings(
                Math.max(1, config.getCharacteriseExcerptChars()), Math.max(1, config.getCharacteriseBatchItems()),
                Math.max(0, config.getCharacteriseMaxItems()), runAnyway, Math.max(1, config.getJoinWorkers()),
                zone.zone(), corrections, readCache(workspace, cachePath),
                count -> askBeyond(request, context, count, config.getCharacteriseMaxItems()));
        final Characterised characterised = ActivityCharacterisation.characterise(timeline, byId, names, settings,
                context.sideCalls());
        if (!characterised.cache().isEmpty()) {
            workspace.write(cachePath, JsonUtil.writeValueAsString(characterised.cache(), false), null);
        }
        return characterised;
    }

    private boolean askBeyond(final ToolCall request, final ToolCallContext context, final int count,
                              final int budget) {
        if (!context.session().isChat() || !context.userQuestions().canAsk()) {
            return false;
        }
        final long calls = (count + Math.max(1, limits(context).getCharacteriseBatchItems()) - 1L)
                           / Math.max(1, limits(context).getCharacteriseBatchItems());
        final Map<String, Object> field = new LinkedHashMap<>();
        field.put("type", "string");
        field.put("title", "Classify them all?");
        field.put("enum", List.of(CLASSIFY_ALL, CLASSIFY_BUDGET));
        field.put("default", CLASSIFY_BUDGET);
        final Map<String, Object> form = new LinkedHashMap<>();
        form.put("type", "object");
        form.put("properties", Map.of(BEYOND_FORM, field));
        form.put("required", List.of(BEYOND_FORM));
        final ElicitResult answer = context.userQuestions().ask(NAME, UserQuestions.formCallId(request.id(),
                BEYOND_FORM), count + " loose items (messages, prompts) are to be classified by the model, past the "
                              + "budget of " + budget + ": about " + calls + " model call(s). Classify them all?",
                form);
        return answer.action() == ElicitResult.Action.ACCEPT
               && CLASSIFY_ALL.equals(String.valueOf(NullSafe.map(answer.content()).get(BEYOND_FORM)));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> givenCorrections(final ToolCall request) {
        final Object given = argsMap(request).get(CORRECTIONS_ARG);
        final List<Map<String, Object>> corrections = new ArrayList<>();
        if (given instanceof final List<?> list) {
            for (final Object item : list) {
                if (item instanceof final Map<?, ?> map) {
                    corrections.add(new LinkedHashMap<>((Map<String, Object>) map));
                } else {
                    throw new WorkspaceException(CORRECTIONS_ARG + " is a list of objects: {move, to}, {merge} or "
                                                 + "{rename, label}.");
                }
            }
        } else if (given != null) {
            throw new WorkspaceException(CORRECTIONS_ARG + " is a list of objects: {move, to}, {merge} or "
                                         + "{rename, label}.");
        }
        return corrections;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> readList(final SessionWorkspace workspace, final WorkspacePath path) {
        if (workspace.find(path).isEmpty()) {
            return List.of();
        }
        try {
            final String text = String.join("\n", workspace.readLines(path, 0, Integer.MAX_VALUE));
            final List<?> list = JsonUtil.readValue(text, List.class);
            final List<Map<String, Object>> maps = new ArrayList<>();
            for (final Object item : list) {
                if (item instanceof final Map<?, ?> map) {
                    maps.add((Map<String, Object>) map);
                }
            }
            return maps;
        } catch (final RuntimeException e) {
            throw new WorkspaceException(path + " can't be read as the corrections it holds: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> readCache(final SessionWorkspace workspace,
                                                              final WorkspacePath path) {
        if (workspace.find(path).isEmpty()) {
            return Map.of();
        }
        try {
            final String text = String.join("\n", workspace.readLines(path, 0, Integer.MAX_VALUE));
            final Map<?, ?> map = JsonUtil.readValue(text, Map.class);
            final Map<String, Map<String, Object>> cache = new LinkedHashMap<>();
            map.forEach((key, value) -> {
                if (value instanceof final Map<?, ?> answer) {
                    cache.put(String.valueOf(key), (Map<String, Object>) answer);
                }
            });
            return cache;
        } catch (final RuntimeException e) {
            // A cache that can't be read is asked again.
            return Map.of();
        }
    }

    // ------------------------------------------------------------------------------------------------
    // The tables

    private static Files write(final SessionWorkspace workspace,
                               final String folder,
                               final Timeline timeline,
                               final Map<String, Event> byId,
                               final List<String> names,
                               final Characterised characterised,
                               final List<Source> sources) {
        final String sourceDoc = sources.size() == 1
                ? sources.getFirst().doc().getUuid()
                : null;
        final Map<Integer, String> activityOf = new LinkedHashMap<>();
        final Map<String, String> labels = new LinkedHashMap<>();
        if (characterised != null) {
            characterised.activities().forEach(activity -> labels.put(activity.id(), activity.label()));
            for (final Session session : timeline.sessions()) {
                final Placement placement = characterised.sessions().get(session.id());
                if (placement != null) {
                    activityOf.put(session.number(), placement.activity());
                }
            }
        }
        final List<List<String>> sessionRows = new ArrayList<>();
        for (final Session session : timeline.sessions()) {
            final String activity = activityOf.get(session.number());
            sessionRows.add(List.of(session.id(), NullSafe.string(session.host()), NullSafe.string(session.process()),
                    iso(session.start()), iso(session.end()), minutes(session.activeMs()),
                    String.join("; ", session.titles().stream().limit(3).toList()), kinds(session.actionsByKind()),
                    NullSafe.string(session.startMark()), NullSafe.string(session.endMark()), session.endReason(),
                    activity == null
                            ? ""
                            : activity, activity == null
                            ? ""
                            : labels.getOrDefault(activity, activity)));
        }
        final WorkspacePath sessionsPath = WorkspacePath.parse(folder + SESSIONS_FILE);
        ToolTables.write(workspace, sessionsPath, List.of("session", "host", "process", "start", "end",
                "active_minutes", "titles", "actions", "start_mark", "end_mark", "ended_by", "activity",
                "activity_label"), sessionRows, sourceDoc);

        final List<List<String>> actionRows = new ArrayList<>();
        for (final Action action : timeline.actions()) {
            final Event event = action.event();
            actionRows.add(List.of(iso(event.time()), NullSafe.string(event.host()), NullSafe.string(event.process()),
                    NullSafe.string(event.kind()), NullSafe.string(event.object()), action.session() == 0
                            ? ""
                            : "s" + action.session(), action.by() == null
                            ? ""
                            : action.by().toString(), names.get(event.source()), NullSafe.string(event.streamId()),
                    NullSafe.string(event.eventId())));
        }
        final WorkspacePath actionsPath = WorkspacePath.parse(folder + ACTIONS_FILE);
        ToolTables.write(workspace, actionsPath, List.of("time", "host", "process", "kind", "object", "session", "by",
                "source", "StreamId", "EventId"), actionRows, sourceDoc);

        final List<List<String>> eventRows = new ArrayList<>();
        for (final Event event : byId.values().stream()
                .sorted((a, b) -> Long.compare(a.time(), b.time()))
                .toList()) {
            eventRows.add(List.of(iso(event.time()), names.get(event.source()), event.role().argName(),
                    NullSafe.string(event.kind()), NullSafe.string(event.host()), NullSafe.string(event.process()),
                    NullSafe.string(event.title()), NullSafe.string(event.object()),
                    NullSafe.string(event.streamId()), NullSafe.string(event.eventId())));
        }
        final WorkspacePath eventsPath = WorkspacePath.parse(folder + EVENTS_FILE);
        ToolTables.write(workspace, eventsPath, List.of("time", "source", "role", "kind", "host", "process", "title",
                "object", "StreamId", "EventId"), eventRows, sourceDoc);

        final List<List<String>> dailyRows = new ArrayList<>();
        for (final Daily daily : timeline.daily()) {
            dailyRows.add(List.of(daily.day().toString(), daily.host(), NullSafe.string(daily.process()),
                    minutes(daily.activeMs()), String.valueOf(daily.sessions())));
        }
        final WorkspacePath dailyPath = WorkspacePath.parse(folder + DAILY_FILE);
        ToolTables.write(workspace, dailyPath, List.of("day", "host", "process", "active_minutes", "sessions"),
                dailyRows, sourceDoc);

        final List<List<String>> windowRows = new ArrayList<>();
        for (final Window window : timeline.windows()) {
            windowRows.add(List.of(window.host(), NullSafe.string(window.process()), NullSafe.string(window.window()),
                    window.opened() == null
                            ? ""
                            : iso(window.opened()), window.closed() == null
                            ? ""
                            : iso(window.closed())));
        }
        final WorkspacePath windowsPath = WorkspacePath.parse(folder + WINDOWS_FILE);
        ToolTables.write(workspace, windowsPath, List.of("host", "process", "window", "opened", "closed"), windowRows,
                sourceDoc);

        final List<List<String>> qualityRows = new ArrayList<>();
        for (final Flag flag : timeline.flags()) {
            qualityRows.add(List.of(flag.name().key(), String.valueOf(flag.count()), flag.name().description(),
                    String.join(" | ", flag.examples())));
        }
        final WorkspacePath qualityPath = WorkspacePath.parse(folder + QUALITY_FILE);
        ToolTables.write(workspace, qualityPath, List.of("flag", "count", "what", "examples"), qualityRows,
                sourceDoc);

        WorkspacePath activitiesPath = null;
        WorkspacePath itemsPath = null;
        if (characterised != null) {
            final List<List<String>> activityRows = new ArrayList<>();
            for (final Session session : timeline.sessions()) {
                final Placement placement = characterised.sessions().get(session.id());
                final String activity = placement == null
                        ? ""
                        : placement.activity();
                activityRows.add(List.of(activity, activity.isEmpty()
                                ? "No activity"
                                : labels.getOrDefault(activity, activity), session.id(), iso(session.start()),
                        iso(session.end()), NullSafe.string(session.host()), NullSafe.string(session.process()),
                        placement == null
                                ? ""
                                : placement.how()));
            }
            activitiesPath = WorkspacePath.parse(folder + ACTIVITIES_FILE);
            ToolTables.write(workspace, activitiesPath, List.of("activity", "activity_label", "session", "start",
                    "end", "host", "process", "how"), activityRows, sourceDoc);
            final List<List<String>> itemRows = new ArrayList<>();
            for (final ActivityCharacterisation.Item item : characterised.allItems()) {
                final Placement placement = item.id().startsWith("s") && characterised.sessions()
                        .containsKey(item.id())
                        ? characterised.sessions().get(item.id())
                        : characterised.items().get(item.id());
                itemRows.add(List.of(item.id(), iso(item.time()), item.source(), NullSafe.string(item.host()),
                        item.session() == null
                                ? ""
                                : item.session().id(), placement == null
                                ? ""
                                : placement.activity(), placement == null
                                ? "not classified"
                                : NullSafe.string(placement.how()), placement == null
                                ? ""
                                : NullSafe.string(placement.confidence()), placement == null
                                ? ""
                                : NullSafe.string(placement.reason())));
            }
            itemsPath = WorkspacePath.parse(folder + ITEMS_FILE);
            ToolTables.write(workspace, itemsPath, List.of("item", "time", "source", "host", "session", "activity",
                    "how", "confidence", "reason"), itemRows, sourceDoc);
        }
        return new Files(sessionsPath, actionsPath, eventsPath, dailyPath, windowsPath, qualityPath, activitiesPath,
                itemsPath);
    }

    /// @param ms Epoch milliseconds.
    /// @return It as an ISO 8601 instant, in UTC, as gantt_chart reads it.
    static String iso(final long ms) {
        return Instant.ofEpochMilli(ms).toString();
    }

    private static String minutes(final long ms) {
        return String.valueOf(Math.round(ms / 6_000.0) / 10.0);
    }

    private static String kinds(final Map<String, Integer> byKind) {
        final List<String> parts = new ArrayList<>();
        byKind.forEach((kind, count) -> parts.add(kind + ": " + count));
        return String.join("; ", parts);
    }

    /// @param entity A user.
    /// @return A folder name for them.
    static String safe(final String entity) {
        final String safe = entity.strip().replaceAll("[^A-Za-z0-9_.-]", "_");
        return safe.length() > 60
                ? safe.substring(0, 60)
                : safe;
    }

    // ------------------------------------------------------------------------------------------------
    // The result

    private static Map<String, Object> structured(final String entity,
                                                  final Period period,
                                                  final long capMs,
                                                  final List<Source> sources,
                                                  final Read read,
                                                  final Timeline timeline,
                                                  final Characterised characterised,
                                                  final Files files,
                                                  final ToolTimeZone zone) {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("entity", entity);
        map.put("time", Map.of("from", iso(period.fromMs()), "to", iso(period.toMs())));
        map.put("idle_cap_minutes", capMs / 60_000);
        final List<Map<String, Object>> sourceCounts = new ArrayList<>();
        for (int i = 0; i < sources.size(); i++) {
            sourceCounts.add(Map.of("name", sources.get(i).name(), "index", sources.get(i).doc().getName(),
                    "events", i < read.counts().size()
                            ? read.counts().get(i)
                            : 0L));
        }
        map.put("sources", sourceCounts);
        map.put("events_read", read.events().size());
        map.put("ended_early", endedEarly(read));
        map.put("session_count", timeline.sessions().size());
        map.put("hosts", timeline.sessions().stream().map(Session::host).distinct().toList());
        map.put("uncertain_sessions", timeline.sessions().stream().filter(Session::uncertain).count());
        map.put("actions", timeline.actions().size());
        map.put("unattached_actions", timeline.unattached().size());
        // Active time by host and application over the period, the most first: the answer's rows.
        map.put("columns", List.of("host", "process", "sessions", "active_minutes"));
        map.put("rows", byApplication(timeline).stream()
                .map(row -> List.<Object>of(row.host(), row.process(), row.sessions(),
                        Math.round(row.activeMs() / 6_000.0) / 10.0))
                .toList());
        final Map<String, Object> quality = new LinkedHashMap<>();
        for (final Flag flag : timeline.flags()) {
            quality.put(flag.name().key(), Map.of("count", flag.count(), "what", flag.name().description(),
                    "examples", flag.examples()));
        }
        map.put("data_quality", quality);
        final Map<String, Object> written = new LinkedHashMap<>();
        written.put("sessions", files.sessions().toString());
        written.put("actions", files.actions().toString());
        written.put("events", files.events().toString());
        written.put("daily", files.daily().toString());
        written.put("windows", files.windows().toString());
        written.put("data_quality", files.quality().toString());
        if (files.activities() != null) {
            written.put("activities", files.activities().toString());
            written.put("items", files.items().toString());
        }
        map.put("files", written);
        map.put("gantt", Map.of("path", files.sessions().toString(), "start", "start", "end", "end", "label",
                "process", "lane", "host"));
        if (characterised != null) {
            map.put("characterisation", characterisation(characterised, files, timeline.sessions().size()));
        }
        map.put("time_zone", zone.zone().getId());
        map.put("derived", DERIVED);
        map.put("caveat", CAVEAT);
        return map;
    }

    private static Map<String, Object> characterisation(final Characterised characterised,
                                                        final Files files,
                                                        final int sessions) {
        final Map<String, Object> map = new LinkedHashMap<>();
        final List<Map<String, Object>> activities = new ArrayList<>();
        for (final Activity activity : characterised.activities()) {
            final Map<String, Object> one = new LinkedHashMap<>();
            one.put("id", activity.id());
            one.put("label", activity.label());
            one.put("label_is", ActivityCharacterisation.INFERRED);
            one.put("found_by", activity.fromModel()
                    ? "the model (a theme several items share)"
                    : "the sessions' graph (Leiden clusters)");
            one.put("sessions", activity.sessionCount());
            one.put("items", activity.items());
            one.put("active_minutes", Math.round(activity.activeMs() / 6_000.0) / 10.0);
            one.put("applications", activity.applications());
            one.put("artifacts", activity.artifacts());
            one.put("terms", activity.terms());
            activities.add(one);
        }
        map.put("activities", activities);
        map.put("sessions_in_no_activity", sessions - (int) characterised.sessions().values().stream()
                .filter(placement -> placement.activity().startsWith("a"))
                .count());
        final Map<String, Long> placed = new TreeMap<>();
        characterised.items().values().forEach(placement -> placed.merge(placement.activity().startsWith("a")
                ? "in_an_activity"
                : placement.activity(), 1L, Long::sum));
        map.put("items", characterised.allItems().size());
        map.put("items_placed", placed);
        map.put("model_calls", characterised.calls());
        map.put("items_asked", characterised.asked());
        map.put("items_not_classified", characterised.skipped());
        final List<Map<String, Object>> unclear = new ArrayList<>();
        characterised.items().values().stream()
                .filter(placement -> ActivityCharacterisation.UNCLEAR.equals(placement.activity()))
                .limit(ActivityCharacterisation.UNCLEAR_SHOWN)
                .forEach(placement -> unclear.add(Map.of("item", placement.id(), "reason",
                        NullSafe.string(placement.reason()))));
        map.put("unclear", unclear);
        map.put("modularity", Math.round(characterised.modularity() * 100) / 100.0);
        map.put("notes", characterised.notes());
        map.put("gantt", Map.of("path", files.activities().toString(), "start", "start", "end", "end", "label",
                "process", "lane", "activity_label"));
        return map;
    }

    private static String endedEarly(final Read read) {
        if (read.reached() == null) {
            return read.cut()
                    ? "A role of a source had more than " + KeyGroupSource.MAX_KEY_ROWS + " events: the rest were "
                      + "counted, not kept. Narrow the period."
                    : null;
        }
        return switch (read.reached()) {
            case ROWS -> "Reading stopped at the events cap (stroom.ai.workspace.timelineMaxEvents): narrow the "
                         + "period.";
            case TIME -> "Reading stopped at the time limit: narrow the period, or the sources' roles.";
            default -> "Reading stopped early.";
        };
    }

    // Each host's applications, the most active time first.
    private static List<ByApplication> byApplication(final Timeline timeline) {
        final Map<String, long[]> totals = new LinkedHashMap<>();
        for (final Session session : timeline.sessions()) {
            final long[] total = totals.computeIfAbsent(session.host() + "\u0000" + NullSafe.string(
                    session.process()), key -> new long[2]);
            total[0] += session.activeMs();
            total[1]++;
        }
        final List<ByApplication> rows = new ArrayList<>();
        totals.forEach((key, total) -> {
            final String[] parts = key.split("\u0000", -1);
            rows.add(new ByApplication(parts[0], parts[1], (int) total[1], total[0]));
        });
        rows.sort((a, b) -> a.activeMs() == b.activeMs()
                ? (a.host() + a.process()).compareTo(b.host() + b.process())
                : Long.compare(b.activeMs(), a.activeMs()));
        return rows;
    }

    private static String markdown(final String entity,
                                   final Period period,
                                   final long capMs,
                                   final List<Source> sources,
                                   final Read read,
                                   final Timeline timeline,
                                   final Characterised characterised,
                                   final Files files,
                                   final ToolTimeZone zone) {
        final ZoneId zoneId = zone.zone();
        final StringBuilder sb = new StringBuilder("Activity timeline of ").append(entity).append(", ")
                .append(local(period.fromMs(), zoneId)).append(" to ").append(local(period.toMs(), zoneId))
                .append(": ").append(timeline.sessions().size()).append(" session(s) on ")
                .append(timeline.sessions().stream().map(Session::host).distinct().count()).append(" host(s), ")
                .append(timeline.actions().size()).append(" action(s) (").append(timeline.unattached().size())
                .append(" in no session), from ").append(read.events().size()).append(" event(s): ");
        final List<String> counts = new ArrayList<>();
        for (int i = 0; i < sources.size(); i++) {
            counts.add(sources.get(i).name() + " " + (i < read.counts().size()
                    ? read.counts().get(i)
                    : 0));
        }
        sb.append(String.join(", ", counts)).append(". Idle cap ").append(capMs / 60_000).append(" minutes.\n");
        final String early = endedEarly(read);
        if (early != null) {
            sb.append("\n").append(early).append("\n");
        }
        sb.append("\nActive time by host and application:\n\n| host | process | sessions | active minutes |\n"
                  + "| --- | --- | --- | --- |\n");
        byApplication(timeline).stream().limit(SHOWN).forEach(row -> sb.append("| ").append(row.host())
                .append(" | ").append(row.process()).append(" | ").append(row.sessions()).append(" | ")
                .append(Math.round(row.activeMs() / 6_000.0) / 10.0).append(" |\n"));
        sb.append("\nData quality (missing events are flagged, never smoothed over):");
        if (timeline.flags().isEmpty()) {
            sb.append(" nothing flagged.\n");
        } else {
            sb.append('\n');
            for (final Flag flag : timeline.flags()) {
                sb.append("- ").append(flag.name().key()).append(": ").append(flag.count()).append(" ")
                        .append(flag.name().description()).append(", e.g. ")
                        .append(String.join("; ", flag.examples())).append('\n');
            }
        }
        if (characterised != null) {
            sb.append("\nWhat ").append(entity).append(" was working on (labels are inferences from the evidence):\n");
            for (final Activity activity : characterised.activities().stream().limit(SHOWN).toList()) {
                sb.append("- ").append(activity.id()).append(" \"").append(activity.label()).append("\": ")
                        .append(activity.sessionCount()).append(" session(s), ")
                        .append(Math.round(activity.activeMs() / 6_000.0) / 10.0).append(" active minutes, ")
                        .append(activity.items()).append(" item(s); ").append(String.join(", ",
                                activity.applications())).append(activity.artifacts().isEmpty()
                                ? ""
                                : "; artifacts: " + String.join(", ", activity.artifacts()))
                        .append(activity.fromModel()
                                ? " (a theme the model found in several items)"
                                : "").append('\n');
            }
            final long unclear = characterised.items().values().stream()
                    .filter(placement -> ActivityCharacterisation.UNCLEAR.equals(placement.activity()))
                    .count();
            sb.append(characterised.allItems().size()).append(" item(s) (messages, prompts, sessions the graph "
                                                              + "left out): ").append(characterised.asked())
                    .append(" classified by the model in ").append(characterised.calls())
                    .append(" call(s) outside this conversation, ").append(unclear).append(" unclear (")
                    .append(files.items()).append(" has each with its reason).\n");
            characterised.notes().forEach(note -> sb.append("- ").append(note).append('\n'));
            sb.append("Activities by session: ").append(files.activities()).append(" (gantt_chart with lane "
                                                                                  + "activity_label draws a section "
                                                                                  + "per activity).\n");
        }
        sb.append("\nTables: ").append(files.sessions()).append(" (sessions), ").append(files.actions())
                .append(" (actions, with where each was attached), ").append(files.daily()).append(" (per day), ")
                .append(files.windows()).append(" (window lifetimes), ").append(files.events())
                .append(" (the events, with their StreamId and EventId), ").append(files.quality())
                .append(" (data quality).\n");
        sb.append("To draw it in the chat: gantt_chart with path ").append(files.sessions())
                .append(", start start, end end, label process, lane host.\n\n");
        sb.append(zone.stated()).append(" The tables' times are UTC.\n\n").append(DERIVED).append(' ')
                .append(CAVEAT);
        return sb.toString();
    }

    private static String local(final long ms, final ZoneId zone) {
        return Instant.ofEpochMilli(ms).atZone(zone).toLocalDateTime().toString().replace('T', ' ');
    }


    // --------------------------------------------------------------------------------


    /// The period read.
    ///
    /// @param fromMs    Its start.
    /// @param toMs      Its end.
    /// @param timeRange It as a time range of instants.
    record Period(long fromMs, long toMs, TimeRange timeRange) {

    }


    // --------------------------------------------------------------------------------


    /// The tables written.
    ///
    /// @param sessions   The sessions.
    /// @param actions    The actions.
    /// @param events     The events.
    /// @param daily      The daily totals.
    /// @param windows    The window lifetimes.
    /// @param quality    The data-quality flags.
    /// @param activities The sessions by activity, or null.
    /// @param items      The items and where each went, or null.
    record Files(WorkspacePath sessions,
                 WorkspacePath actions,
                 WorkspacePath events,
                 WorkspacePath daily,
                 WorkspacePath windows,
                 WorkspacePath quality,
                 WorkspacePath activities,
                 WorkspacePath items) {

    }


    // --------------------------------------------------------------------------------


    // A host's application over the period.
    private record ByApplication(String host, String process, int sessions, long activeMs) {

    }
}
