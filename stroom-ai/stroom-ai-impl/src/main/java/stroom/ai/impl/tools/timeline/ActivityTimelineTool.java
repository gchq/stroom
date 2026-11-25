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

import stroom.ai.api.ElasticEql;
import stroom.ai.api.ElasticEsql;
import stroom.ai.api.ElasticKeyStreams;
import stroom.ai.api.ElasticKeyStreams.Refusal;
import stroom.ai.api.ElasticKeyStreams.Transform;
import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.AiWorkspaceConfig;
import stroom.ai.impl.tools.AbstractWorkspaceTool;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolDescription;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema;
import stroom.ai.impl.tools.ToolSchema.ArraySchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.ToolTables;
import stroom.ai.impl.tools.UserQuestions;
import stroom.ai.impl.tools.catalogue.TimelineRole;
import stroom.ai.impl.tools.catalogue.TimelineRoles;
import stroom.ai.impl.tools.compare.CompareActivityTool;
import stroom.ai.impl.tools.documents.DocumentAccess;
import stroom.ai.impl.tools.instructions.AgentInstructions;
import stroom.ai.impl.tools.instructions.InstructionRules;
import stroom.ai.impl.tools.instructions.InstructionRules.TypeRoleRule;
import stroom.ai.impl.tools.query.DataSourceAccess;
import stroom.ai.impl.tools.query.DataSourceDocumentation;
import stroom.ai.impl.tools.query.EsqlQueryTool;
import stroom.ai.impl.tools.query.StroomQlDatePoints;
import stroom.ai.impl.tools.sequence.EqlQueryWriter.Filter;
import stroom.ai.impl.tools.sequence.EqlSequenceResults;
import stroom.ai.impl.tools.sequence.JoinBudget;
import stroom.ai.impl.tools.sequence.JoinKeys;
import stroom.ai.impl.tools.sequence.SequenceQueryTool;
import stroom.ai.impl.tools.sequence.SequenceSteps;
import stroom.ai.impl.tools.sequence.StreamFilters;
import stroom.ai.impl.tools.skills.SkillCatalogue;
import stroom.ai.impl.tools.skills.SkillCatalogue.Listed;
import stroom.ai.impl.tools.skills.SkillDoc;
import stroom.ai.impl.tools.streams.IndexingTrace;
import stroom.ai.impl.tools.streams.StreamProvenance;
import stroom.ai.impl.tools.streams.StreamProvenance.PipelineFacts;
import stroom.ai.impl.tools.streams.StreamProvenance.Provenance;
import stroom.ai.impl.tools.time.ToolTimeZone;
import stroom.ai.impl.tools.time.UserTimeZones;
import stroom.ai.impl.tools.timeline.ActivityCharacterisation.Characterised;
import stroom.ai.impl.tools.timeline.EventChains.Entry;
import stroom.ai.impl.tools.timeline.EventCodes.CodeRole;
import stroom.ai.impl.tools.timeline.EventXmlClassifier.Classified;
import stroom.ai.impl.tools.timeline.KeySpans.KeyRule;
import stroom.ai.impl.tools.timeline.PairInference.Candidate;
import stroom.ai.impl.tools.timeline.PairInference.Inferred;
import stroom.ai.impl.tools.timeline.PairInference.Typed;
import stroom.ai.impl.tools.timeline.PairRule.Origin;
import stroom.ai.impl.tools.timeline.PersonScope.Scope;
import stroom.ai.impl.tools.timeline.TimelineClassification.Fields;
import stroom.ai.impl.tools.timeline.TimelineClassification.RoleOverride;
import stroom.ai.impl.tools.timeline.TimelineFields.Found;
import stroom.ai.impl.tools.timeline.TimelineLearning.CreateIn;
import stroom.ai.impl.tools.timeline.TimelineLearning.Learned;
import stroom.ai.impl.tools.timeline.TimelineLearning.NamedCandidate;
import stroom.ai.impl.tools.timeline.TimelineLearning.SkillInUse;
import stroom.ai.impl.tools.timeline.TimelineReader.Read;
import stroom.ai.impl.tools.timeline.TimelineReader.RoleRead;
import stroom.ai.impl.tools.timeline.TimelineReader.Source;
import stroom.ai.impl.tools.timeline.TimelineReader.Varied;
import stroom.ai.impl.tools.timeline.StreamFields.Indexed;
import stroom.ai.impl.tools.timeline.TimelineReading.XmlPaths;
import stroom.ai.impl.tools.timeline.TimelineRun.EventRange;
import stroom.ai.impl.tools.timeline.TimelineRun.Period;
import stroom.ai.impl.tools.timeline.TimelineRun.SourceNote;
import stroom.ai.impl.tools.timeline.TimelineRun.View;
import stroom.ai.impl.tools.timeline.TimelineRun.XmlRead;
import stroom.ai.impl.tools.timeline.TimelineSessions.Event;
import stroom.ai.impl.tools.timeline.TimelineSessions.Options;
import stroom.ai.impl.tools.timeline.TimelineSessions.Role;
import stroom.ai.impl.tools.timeline.TimelineSessions.Timeline;
import stroom.ai.impl.tools.timeline.TimelineSpans.Built;
import stroom.ai.impl.tools.timeline.TimelineTables.Files;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.documentation.shared.DocumentationDoc;
import stroom.explorer.shared.ExplorerConstants;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.TimeRange;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
import stroom.query.common.v2.DateExpressionParser;
import stroom.query.impl.QueryService;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.security.shared.DocumentPermission;
import stroom.task.api.TaskTerminatedException;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PermissionException;
import stroom.util.time.StroomDuration;

import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.function.Function;

/// `activity_timeline`: one user's (or entity's) activity over a period from any sources, worked out in code
/// ([TimelineSessions], [TimelineSpans]), never by the model. Two levels: the detail spans (focus sessions of an
/// application on a host where a source records window focus; pairs, from a start to its end, such as a process's
/// start and end; and activity periods, the actions in neither, close in time) and the activities rolled up from
/// them, what the user was working on ([ActivityCharacterisation]); three views of them (activities, detail, both),
/// each a table `gantt_chart` draws as it is.
///
/// - **Sources**: given by the call (or a skill); else asked in a chat's form ([TimelineSourceChoice]); over MCP,
///   every Elastic Index the user can use. A source with roles is read by them, as before (the override, e.g. for a
///   source of window focus); one without has its events classified: from their Events XML, read by StreamId and
///   EventId ([TimelineEventXml], [EventXmlClassifier]; for an event code nothing settles, a few of its events'
///   XML, the code classified once and applied to all its events), else the index's element field, else the
///   source's own event codes classified once ([EventCodes]) ([TimelineClassification]). The user and the time come
///   from the index.
/// - **The user field** of each index ([TimelineFields]): the call's (or skill's), the AGENTS doc's `## Fields`, a
///   field convention's, the index's documentation, else asked.
/// - **Pairs** ([PairRule]): the skill's Event chains, the call's, the schema's, and those inferred from the user's
///   own events ([PairInference]); borderline ones listed, not used.
/// - **Learning** ([TimelineLearning]): what was inferred, or corrected by the call, is offered for the timeline
///   skill's Event chains, where the user may change it.
///
/// The events are read with the streaming join's machinery ([TimelineReader]: Elastic Indexes, a terms condition
/// for the one user). With `characterise` (or the activities view), the activities: anchors from the spans' graph,
/// loose items classified by the model in side calls ([stroom.ai.impl.tools.ModelSideCalls]), so the chat's model
/// sees only the outcome. The analyst's corrections are kept with the investigation, so a re-run keeps them.
public class ActivityTimelineTool extends AbstractWorkspaceTool {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ActivityTimelineTool.class);

    static final String NAME = "activity_timeline";
    static final String ENTITY_ARG = "entity";
    static final String SOURCES_ARG = "sources";
    static final String TIME_FROM_ARG = "timeFrom";
    static final String TIME_TO_ARG = "timeTo";
    static final String IDLE_CAP_ARG = "idleCap";
    static final String CHARACTERISE_ARG = "characterise";
    static final String CORRECTIONS_ARG = "corrections";
    static final String RUN_ANYWAY_ARG = "runAnyway";
    static final String USER_FIELD_ARG = "userField";
    static final String PERSON_ARG = "person";
    static final String LOGON_IDS_ARG = "logonIds";
    static final String LOGON_FORMS_ARG = PersonScope.LOGON_FORMS_ARG;
    /// The transforms logonForms may name.
    static final List<String> LOGON_FORMS = PersonScope.LOGON_FORMS;
    /// What a source's note says where its user field was matched through a transform.
    static final String SCRIPTED = PersonScope.SCRIPTED;
    static final String PERSON_FIELD_ARG = "personField";
    static final String VIEW_ARG = "view";
    static final String LANES_ARG = "lanes";
    static final String SKILL_ARG = "skill";
    static final String INCLUDE_TARGETS_ARG = "includeTargets";
    static final String TARGET_PERSON_FIELD_ARG = "targetPersonField";
    static final String TARGET_USER_FIELD_ARG = "targetUserField";
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
    static final String PAIRS_KEY = "pairs";
    static final String TYPE_ROLES_KEY = "typeRoles";
    static final String SPAN_BY_KEY = "spanBy";
    static final String ELEMENT_FIELD_KEY = "elementField";
    static final String ACTION_FIELD_KEY = "actionField";
    static final String EVENT_TYPE_FIELD_KEY = "eventTypeField";
    static final String START_KEY = "start";
    static final String END_KEY = "end";
    static final String BY_KEY = "by";
    static final String LABEL_KEY = "label";
    static final String WHERE_KEY = SequenceQueryTool.WHERE_KEY;
    static final String ANY_OF_KEY = SequenceQueryTool.ANY_OF_KEY;
    /// The sources argument for every Elastic Index the user can use.
    static final String ALL_SOURCES = "all";
    /// The sources argument to ask the user which (as none does).
    static final String ASK_SOURCES = "ask";
    /// The period when the call gives none.
    static final String DEFAULT_FROM = "now()-7d";
    static final String DEFAULT_TO = "now()";
    /// The folder each user's timeline is written to, beside its corrections.
    static final String FOLDER = "/outputs/timelines/";
    static final String SESSIONS_FILE = "sessions.csv";
    static final String SPANS_FILE = "spans.csv";
    static final String ACTIONS_FILE = "actions.csv";
    static final String EVENTS_FILE = "events.csv";
    static final String DAILY_FILE = "daily.csv";
    static final String WINDOWS_FILE = "windows.csv";
    static final String QUALITY_FILE = "quality.csv";
    static final String ACTIVITIES_FILE = "activities.csv";
    static final String ITEMS_FILE = "items.csv";
    static final String OVERVIEW_FILE = "overview.csv";
    static final String CORRECTIONS_FILE = "corrections.json";
    static final String CACHE_FILE = "characterisation-cache.json";
    /// The form's key for classifying past the budget.
    static final String BEYOND_FORM = "classify";
    static final String CLASSIFY_ALL = "Classify them all";
    static final String CLASSIFY_BUDGET = "Classify up to the budget";
    /// The form's key for the user fields no way found.
    static final String USER_FIELDS_FORM = "timeline-user-fields";
    static final String LEAVE_OUT = "Leave it out";
    /// The form's key for confirming an index's event codes' classification.
    static final String CODES_FORM = "timeline-event-codes";
    static final String USE_CODES = "Use it";
    static final String DONT_USE_CODES = "Don't use it";
    /// The events of an automatic source probed first: whether the user has any there, and which of its fields
    /// holds the EventDetail element.
    static final int PROBE_EVENTS = 200;
    /// The most fields fetched by the probe.
    static final int PROBE_FIELDS = 100;
    /// The share of an index field's values in the probe that are EventDetail elements, for it to be taken as the
    /// element field.
    static final double ELEMENT_SHARE = 0.9;
    /// The most event codes listed in the confirming form.
    static final int CODES_SHOWN = 15;
    /// The most sources read when every supported index is taken with no form to ask: past it, the calling model
    /// chooses.
    static final int MAX_AUTOMATIC_SOURCES = 10;
    /// The paths read from the Events XML where an index doesn't hold an event's type.
    /// The events of one unsettled event code whose Events XML is read to classify the code (decided with the user
    /// 2026-11-25): the code is classified once from them and applied to all its events.
    static final int XML_EVENTS_PER_CODE = 3;
    static final List<String> TYPE_PATHS = List.of("EventDetail/TypeId", "EventDetail/* (the element)",
            "EventDetail/*/Action");
    /// The paths read from the Events XML where an index doesn't hold an event's content.
    static final List<String> CONTENT_PATHS = List.of("EventDetail/*/*/Subject", "EventDetail/*/*/URL",
            "EventDetail/*/*/Path", "EventDetail/*/*/Title", "EventDetail/Process/Command");
    /// What every result says of the events.
    static final String CAVEAT = "The events come straight from Elasticsearch, as sequence_query's do: they may "
                                 + "include events of streams the user can't see or that have been deleted.";
    /// What every result says the spans are.
    static final String DERIVED = "Spans are worked out in code: a focus session on an application starts at a "
                                  + "focus on it and ends at the next focus, an idle start, or the idle cap (marked "
                                  + "uncertain); a pair's span runs from a start to its end; an activity period "
                                  + "holds actions in neither, close in time. Missing events are flagged, never "
                                  + "smoothed over.";
    private static final List<String> ROLE_NAMES = List.of(Role.FOCUS.argName(), Role.OPEN.argName(),
            Role.CLOSE.argName(), Role.IDLE_START.argName(), Role.IDLE_END.argName());
    private static final Set<String> KEYWORD_TYPES = Set.of("keyword", "constant_keyword", "wildcard");

    private final Provider<ElasticKeyStreams> keyStreamsProvider;
    private final Provider<ElasticEsql> elasticEsqlProvider;
    private final DataSourceAccess dataSourceAccess;
    private final Provider<QueryService> queryServiceProvider;
    private final AiAuditLog auditLog;
    private final TimelineEventXml eventXml;
    private final TimelineFields timelineFields;
    private final TimelineSourceChoice sourceChoice;
    private final Provider<SkillCatalogue> skillCatalogueProvider;
    private final DocumentAccess documentAccess;
    private final AgentInstructions agentInstructions;
    private final Provider<DataSourceDocumentation> dataSourceDocumentationProvider;
    private final Provider<StreamProvenance> streamProvenanceProvider;
    private final Provider<IndexingTrace> indexingTraceProvider;

    /// @param workspace              The chats' workspaces.
    /// @param keyStreamsProvider     Reads each source's events for the entity, in time order.
    /// @param dataSourceAccess       Finds the sources' indexes the user can use.
    /// @param queryServiceProvider   Gives each index's fields and their types.
    /// @param auditLog               Audits what each index was searched for.
    /// @param eventXml               Reads the events' Events XML.
    /// @param timelineFields         Finds each index's user field (and host, element, Action and code fields).
    /// @param sourceChoice           Asks which sources to read, where neither the call nor a skill says.
    /// @param skillCatalogueProvider Finds the skill in use, for its Event chains.
    /// @param documentAccess         Checks the user may change the skill's doc, or create one.
    /// @param agentInstructions      Gives the folder an index is in.
    /// @param elasticEsqlProvider    Finds each source's first and last events where the period has none.
    /// @param dataSourceDocumentationProvider Describes the field the lanes are by, for the charts' lane axis.
    /// @param streamProvenanceProvider Gives the pipeline that produced each stream read, and its translation's
    ///                               catalogue.
    /// @param indexingTraceProvider  Traces each Events stream to the feeding pipeline that indexed it, where the
    ///                               feeding pipelines' plans differ.
    @Inject
    ActivityTimelineTool(final ChatWorkspace workspace,
                         final Provider<ElasticKeyStreams> keyStreamsProvider,
                         final DataSourceAccess dataSourceAccess,
                         final Provider<QueryService> queryServiceProvider,
                         final AiAuditLog auditLog,
                         final TimelineEventXml eventXml,
                         final TimelineFields timelineFields,
                         final TimelineSourceChoice sourceChoice,
                         final Provider<SkillCatalogue> skillCatalogueProvider,
                         final DocumentAccess documentAccess,
                         final AgentInstructions agentInstructions,
                         final Provider<ElasticEsql> elasticEsqlProvider,
                         final Provider<DataSourceDocumentation> dataSourceDocumentationProvider,
                         final Provider<StreamProvenance> streamProvenanceProvider,
                         final Provider<IndexingTrace> indexingTraceProvider) {
        super(workspace);
        this.indexingTraceProvider = indexingTraceProvider;
        this.streamProvenanceProvider = streamProvenanceProvider;
        this.dataSourceDocumentationProvider = dataSourceDocumentationProvider;
        this.keyStreamsProvider = keyStreamsProvider;
        this.elasticEsqlProvider = elasticEsqlProvider;
        this.dataSourceAccess = dataSourceAccess;
        this.queryServiceProvider = queryServiceProvider;
        this.auditLog = auditLog;
        this.eventXml = eventXml;
        this.timelineFields = timelineFields;
        this.sourceChoice = sourceChoice;
        this.skillCatalogueProvider = skillCatalogueProvider;
        this.documentAccess = documentAccess;
        this.agentInstructions = agentInstructions;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .checksDocuments(DocumentPermission.USE)
                .note("Use on every source's Elastic Index is checked, as for any search, again by Stroom's "
                      + "Elasticsearch module as each is read. The events' Events XML is read as the data viewer "
                      + "reads it (View Data, and the stream store's check on each stream's feed); without View "
                      + "Data the index's fields classify them. Where an index's feeding pipelines' plans differ, "
                      + "each Events stream is traced to the one that indexed it by its processing tasks or the "
                      + "processor filters, read as the processing user only to make that link: only feeding "
                      + "pipelines whose plans the user can view are candidates, and no filter, feed or other "
                      + "pipeline is named. No permission of its own: the tool policies decide "
                      + "who may build a timeline. Recording what was learned in a skill is offered only to a user "
                      + "with Edit on its skills doc (or Create in a folder, for a new one).")
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
                .addEnumProperty("condition", SequenceSteps.conditionNames(), "The condition (default "
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
                .description("Optional: the source's roles, which override classifying its events (e.g. for "
                             + "window focus, which the schema has no element for); each given only where the "
                             + "source holds such events.")
                .addProperty(Role.FOCUS.argName(), conditions)
                .addProperty(Role.OPEN.argName(), conditions)
                .addProperty(Role.CLOSE.argName(), conditions)
                .addProperty(Role.IDLE_START.argName(), conditions)
                .addProperty(Role.IDLE_END.argName(), conditions)
                .addProperty(ACTIONS_KEY, ArraySchema.builder().items(action)
                        .description("The kinds of action it holds.").build())
                .build();
        final ObjectSchema pair = ObjectSchema.builder()
                .description("Two event types bounding spans: an end closes the latest open start of the same key.")
                .addStringProperty(START_KEY, "The start's type: a TypeId, or an element and Action, e.g. "
                                              + "Process/Execute.")
                .addStringProperty(END_KEY, "The end's type.")
                .addStringProperty(BY_KEY, "Optional: the key path under the element both hold, e.g. ProcessId "
                                           + "(default the host alone).")
                .addStringProperty(LABEL_KEY, "Optional: the key path whose value labels the span.")
                .required(START_KEY, END_KEY)
                .build();
        final ObjectSchema source = ObjectSchema.builder()
                .description("One source: an Elastic Index, and optionally the fields that hold each thing and its "
                             + "roles.")
                .addStringProperty(INDEX_KEY, "The Elastic Index doc's uuid or name.")
                .addStringProperty(NAME_KEY, "A short name for it in the result (default the index's).")
                .addStringProperty(USER_KEY, "The field holding the user (default: found, see userField).")
                .addProperty(TRANSFORM_KEY, ArraySchema.builder()
                        .items(new ToolSchema.EnumSchema(null, SequenceQueryTool.TRANSFORMS))
                        .description("Optional: what is done to this source's user field's values and the entity "
                                     + "before they are compared (strip_domain, lowercase), only where the user says "
                                     + "it writes logons with a domain or as an email: slower, a script runs over "
                                     + "every event in the period; a person id or exact logonIds is better.")
                        .build())
                .addStringProperty(HOST_KEY, "The field holding the host (optional).")
                .addStringProperty(PROCESS_KEY, "The field holding the application (process).")
                .addStringProperty(TITLE_KEY, "The field holding the window title.")
                .addStringProperty(WINDOW_KEY, "The field holding a window's id, pairing its open and close "
                                               + "(default the application).")
                .addStringProperty(OBJECT_KEY, "The field holding what an event touched: a file, a URL, a "
                                               + "subject.")
                .addStringProperty(TEXT_KEY, "The field holding free text (a message, an AI chat prompt), for "
                                             + "characterise.")
                .addStringProperty(ELEMENT_FIELD_KEY, "Optional: the field holding the EventDetail element, for "
                                                      + "events whose Events XML is gone (default: found).")
                .addStringProperty(ACTION_FIELD_KEY, "Optional: the field holding the element's Action.")
                .addStringProperty(EVENT_TYPE_FIELD_KEY, "Optional: the field holding the source's own event code.")
                .addProperty(ROLES_KEY, roles)
                .addProperty(PAIRS_KEY, ArraySchema.builder().items(pair)
                        .description("Optional: the source's own pairs (e.g. one the user confirmed).").build())
                .addProperty(SPAN_BY_KEY, stringArraySchema("Optional: keys (an index field, or a key path such as "
                                                            + "Data[SessionId]) whose values bound spans, from the "
                                                            + "first to the last event sharing one, e.g. "
                                                            + "destination.domain (session ids are found)."))
                .addProperty(TYPE_ROLES_KEY, ObjectSchema.builder()
                        .description("Optional: the user's corrections, an event type's role: {\"<type>\": "
                                     + "\"active|idle|focus|open|close|action|ignore\"}.")
                        .build())
                .required(INDEX_KEY)
                .build();
        return ToolDefinition.builder()
                .name(NAME)
                .description(ToolDescription.builder()
                        .general("Builds one user's (or entity's) activity timeline for a period from any Elastic "
                                 + "Index sources, in code. Two levels: detail spans (focus sessions where a source "
                                 + "records window focus; pairs from a start to its end, e.g. a process's start "
                                 + "and end, the schema's, a skill's, or inferred from the user's own events; "
                                 + "spans bounded by a key, e.g. a session id; "
                                 + "activity periods, the actions in neither close in time, by host or source) and "
                                 + "activities rolled up from them (what the user was working on). Each source is "
                                 + "read once, each event counted once (by its raw stream and record, so a "
                                 + "reprocessed event or one in two indexes isn't doubled); its fields (user, "
                                 + "host, type, object...) mapped once per index: "
                                 + "userField and the call's, the AGENTS doc's ## Fields, the feeding pipelines' "
                                 + "index plans (each event its own pipeline's where they differ), the index's "
                                 + "documentation, a convention, else asked (where plans differ and nothing names "
                                 + "the field: each event's stream traced to its indexing pipeline, else its Events "
                                 + "XML at the thing's path, else not read; said per source). Roles by event type: "
                                 + "typeRoles or the "
                                 + "skill's, the catalogue of the stream's translation, the AGENTS doc's ## Event "
                                 + "types, the schema's element and Action, else the codes classified (offered as "
                                 + "## Event types lines). Events XML only for what the index doesn't map (a few "
                                 + "events per unsettled code, applied to all its events), named to add to its "
                                 + "plan; the user and time always the index's. Past the events cap "
                                 + "a day-by-day overview comes instead, to pick days. Omit sources to ask the user "
                                 + "which (over MCP: the AGENTS doc's ## Sources, else the indexes holding the "
                                 + "user's events, at most 10). Data-quality flags for missing events. "
                                 + "includeTargets adds what was done to the person, apart from their activity.")
                        .chatOnly(" Its tables draw as timelines with gantt_chart (dashboards group) as they are; "
                                  + "the result gives the arguments for the view.")
                        .build())
                .parameters(ObjectSchema.builder()
                        .addStringProperty(ENTITY_ARG, "The user (or entity), as the sources hold it, e.g. "
                                                       + "jsmith. Matched exactly: other written forms "
                                                       + "(CORP\\jsmith, jsmith@corp) only by logonIds or "
                                                       + "logonForms.")
                        .addStringProperty(PERSON_ARG, "Optional: a person id, read in each index by its person "
                                                       + "field (personField, AGENTS ## Fields 'person:', ECS "
                                                       + "user.id); an index without one falls back to entity and "
                                                       + "logonIds. The result says 'by person id' or 'by logon "
                                                       + "id' per source.")
                        .addProperty(LOGON_IDS_ARG, stringArraySchema("Optional: more logon ids of the person (forms "
                                                                      + "differ by source: alex.chen, CORP\\achen, "
                                                                      + "adm-achen), read together, each account "
                                                                      + "counted; close variants are offered, never "
                                                                      + "merged."))
                        .addProperty(LOGON_FORMS_ARG, ArraySchema.builder()
                                .items(new ToolSchema.EnumSchema(null, LOGON_FORMS))
                                .description("Optional: only when the user says logons are written with a domain "
                                             + "(CORP\\jsmith) or as an email in some sources: strip_domain "
                                             + "(both forms), lowercase; applied to the user field of every source "
                                             + "read by logon id. Slower: a script runs over every event in the "
                                             + "period. A person id or exact logonIds is better.")
                                .build())
                        .addStringProperty(PERSON_FIELD_ARG, "Optional: the field holding the person id (a "
                                                             + "skill's), before the other ways.")
                        .addProperty(SOURCES_ARG, ArraySchema.builder().items(ToolSchema.AnyOfSchema.of(null,
                                        new ToolSchema.StringSchema(ToolDescription.of("An Elastic Index's name "
                                                                                       + "or uuid.")), source))
                                .description("Optional: the sources (index names alone will do), \"all\", or "
                                             + "\"ask\" (as omitted: the user is asked which).").build())
                        .addStringProperty(USER_FIELD_ARG, "Optional: the field holding the user in every source "
                                                           + "that has it (a skill's), before the other ways.")
                        .addStringProperty(TIME_FROM_ARG, "The period's start: a date, ISO 8601 or a date "
                                                          + "expression (default " + DEFAULT_FROM + "). A date "
                                                          + "alone is a whole day in timeZone: timeFrom "
                                                          + "2026-11-09 and timeTo 2026-11-13 are those five days.")
                        .addStringProperty(TIME_TO_ARG, "The period's end (default " + DEFAULT_TO + "); a date "
                                                        + "alone is included.")
                        .addStringProperty(IDLE_CAP_ARG, "How long with no event on a host ends an interval, "
                                                         + "e.g. 30m (default the setting, 30 minutes).")
                        .addEnumProperty(VIEW_ARG, List.of(View.ACTIVITIES.argName(), View.DETAIL.argName(),
                                View.BOTH.argName()), "Which level to draw: activities (what the user was working "
                                                      + "on; turns characterise on), detail (the spans), or both "
                                                      + "(default detail).")
                        .addEnumProperty(LANES_ARG, List.of("day", "activity", "host", "source"), "Optional: "
                                                                                                  + "the lanes: "
                                                                                                  + "activities "
                                                                                                  + "by day "
                                                                                                  + "(default) or "
                                                                                                  + "activity; "
                                                                                                  + "detail by "
                                                                                                  + "host "
                                                                                                  + "(default) or "
                                                                                                  + "source.")
                        .addBooleanProperty(INCLUDE_TARGETS_ARG, "True to also list what was done to the person "
                                                                 + "(their target fields: a group granted, a "
                                                                 + "password reset, MFA changed, failed logons "
                                                                 + "against them), in a lane of its own per source "
                                                                 + "and never in their activity (default false).")
                        .addStringProperty(TARGET_PERSON_FIELD_ARG, "Optional: the field holding the person an "
                                                                    + "event was done to (AGENTS ## Fields 'target "
                                                                    + "person:', ECS user.target.id).")
                        .addStringProperty(TARGET_USER_FIELD_ARG, "Optional: the field holding the account an "
                                                                  + "event was done to (AGENTS 'target user:', ECS "
                                                                  + "user.target.name).")
                        .addStringProperty(SKILL_ARG, "Optional: the name of the skill being run, whose Event "
                                                      + "chains are applied and to which what is learned is "
                                                      + "offered.")
                        .addBooleanProperty(CHARACTERISE_ARG, "True to find what the user was working on: the "
                                                              + "activities the spans make up.")
                        .addProperty(CORRECTIONS_ARG, ArraySchema.builder().items(ObjectSchema.builder()
                                        .description("{move: <span or item id>, to: <activity id, not_work or "
                                                     + "unclear>}, {merge: [<activity id>, ...]} (into the "
                                                     + "first), or {rename: <activity id>, label: <name>}.")
                                        .build())
                                .description("Optional: the analyst's corrections to the activities, kept with "
                                             + "this investigation so a re-run keeps them.")
                                .build())
                        .addBooleanProperty(RUN_ANYWAY_ARG, "True to classify loose items past the budget, as "
                                                            + "the user agrees.")
                        .addStringProperty(UserTimeZones.ARG, UserTimeZones.DESCRIPTION)
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
        final Who who = who(request);
        final String entity = who.label();
        final ToolTimeZone zone = timeZone(request, context);
        final long now = System.currentTimeMillis();
        final Period period = period(request, zone, now);
        final long capMs = idleCap(request, config);
        final View view = view(request);
        final String lanes = lanes(request);
        final Instant deadline = Instant.now().plus(timeLimit(context));
        final SkillInUse skill = skillInUse(request, context);
        final Plan plan = plan(request, context, who, period, deadline, skill);
        final List<Source> sources = plan.sources();
        // A long period first counted: past the events cap, a day at a time by aggregation, for days to be picked.
        final ToolResult overview = overview(context, workspace, config, plan, period, zone, deadline, entity);
        if (overview != null) {
            return overview;
        }
        final JoinBudget budget = new JoinBudget(config.getTimelineMaxEvents(), Long.MAX_VALUE, deadline,
                context.stopped());
        context.progress().report("Reading " + entity + "'s events from " + sources.size() + " source(s)");
        final Read scanned;
        try {
            scanned = TimelineReader.read(keyStreamsProvider.get(), sources, period.timeRange(), budget);
        } catch (final PermissionException e) {
            throw new WorkspaceException("You can't use one of the sources' Elastic Indexes: " + e.getMessage());
        }
        audit(context, sources, scanned, entity, period);
        final TimelineTargets.Asked targets = booleanArg(request, INCLUDE_TARGETS_ARG, false)
                ? targets(request, context, who, plan, period, deadline, config)
                : null;

        // Where each event came from, by its stream (one batched lookup of the distinct streams read): its own
        // pipeline's fields where the pipelines feeding an index differ, and its translation's catalogue.
        final Provenance provenance = provenance(context, sources, scanned);
        // Each event once: the same event in two sources, or a reprocessed event's old and new documents, by its raw
        // parent stream and record.
        final EventDedupe.Deduped deduped = EventDedupe.dedupe(scanned.events(), sources,
                streamId -> parentOf(provenance, streamId));
        // Where the feeding pipelines' plans differ: each event's own indexing pipeline, else its XML at the thing's
        // path, else not read.
        final BiFunction<Integer, Long, Indexed> indexedBy = indexedBy(context, sources, deduped.events(),
                provenance);
        final StreamFields.ToRead fieldReads = StreamFields.toRead(deduped.events(), sources, indexedBy,
                Math.max(0, config.getTimelineMaxXmlEvents()));
        final Map<String, String> fieldXml = fieldReads.ids().isEmpty()
                ? Map.of()
                : readFieldXml(context, config, fieldReads);
        final StreamFields.Resolved resolved = StreamFields.resolve(deduped.events(), sources, scanned.extras(),
                indexedBy, fieldXml);
        final Read read = scanned.with(resolved.events(), resolved.extras());
        final TypeRoleLookup roles = new TypeRoleLookup(plan.overrides(), catalogue(provenance),
                agentsRoles(context, sources));
        final boolean characterising = booleanArg(request, CHARACTERISE_ARG, false) || view != View.DETAIL;
        final XmlOutcome xml = readXml(context, config, sources, read, plan.fields(), roles, characterising,
                fieldXml);
        final Map<Integer, Map<String, CodeRole>> classifiedNow = new LinkedHashMap<>();
        final Map<Integer, Map<String, CodeRole>> codes = codes(request, context, sources, read, plan.fields(),
                xml.xml(), roles, classifiedNow);
        final TimelineClassification.Result classification = TimelineClassification.classify(read.events(),
                sources, read.extras(), plan.fields(), xml.xml(), codes, roles);
        final TimelineReading reading = new TimelineReading(read.scans(), resolved.read(), xml.paths(),
                offers(sources, classifiedNow), provenance == null
                ? 0
                : provenance.streams().size(), deduped.duplicates(), deduped.notDeduplicated());

        final Set<Integer> withOpen = new LinkedHashSet<>();
        for (int i = 0; i < sources.size(); i++) {
            if (sources.get(i).opens()) {
                withOpen.add(i);
            }
        }
        final List<String> names = sources.stream().map(Source::name).toList();
        final Options options = new Options(capMs, period.toMs(), now, zone.zone(), names, withOpen);
        final Timeline timeline = TimelineSessions.build(classification.events(), options);
        final Map<String, Event> byId = new LinkedHashMap<>();
        classification.events().forEach(event -> byId.put(event.id(), event));

        final List<PairRule> pairs = new ArrayList<>(plan.pairs());
        pairs.addAll(PairRule.SCHEMA);
        final List<Typed> typed = typed(classification, byId, options);
        final Inferred inferred = PairInference.infer(typed, pairs);
        for (final Candidate candidate : inferred.clear()) {
            pairs.add(new PairRule(candidate.source(), candidate.start(), candidate.end(), candidate.by(), null,
                    Origin.INFERRED, candidate.share()));
        }
        final List<KeyRule> keys = new ArrayList<>(plan.keys());
        for (final KeyRule found : KeySpans.find(typed, pairs, new KeySpans.Idle(classification.events().stream()
                .map(Event::time).toList(), capMs))) {
            if (keys.stream().noneMatch(key -> key.source() == found.source() && key.path().equals(found.path()))) {
                keys.add(found);
            }
        }
        final Built built = TimelineSpans.build(timeline, classification.events(), classification.byId(), pairs,
                keys, options);

        final String folder = FOLDER + safe(entity) + "/";
        final Characterised characterised = characterising
                ? characterise(request, context, workspace, config, built, byId, names, folder, zone)
                : null;
        final Learned learned = TimelineLearning.learn(learnedEntries(request, sources, inferred, plan),
                inferred.borderline().stream()
                        .map(candidate -> new NamedCandidate(sources.get(candidate.source()).doc().getName(),
                                candidate))
                        .toList(), skill, skill == null || skill.listed() == null
                        ? createIn(context, sources)
                        : null, workspace, folder);
        final List<EventRange> ranges = read.events().isEmpty()
                ? sources.stream().map(source -> range(context, source.name(), source.doc(), source.userField(),
                source.values(), deadline)).toList()
                : List.of();
        final String hostLanes = "source".equals(lanes)
                ? null
                : TimelineLaneTitles.hosts(sources, (source, field) -> fieldDescription(context, source.doc(),
                        field));
        final TimelineRun run = new TimelineRun(entity, period, capMs, sources, plan.notes(), plan.chosen(), read,
                classification, xml.summary(), timeline, built, byId, inferred.borderline(), characterised, view,
                lanes, learned, zone, ranges, targets, hostLanes, reading);
        final Files files = TimelineTables.write(workspace, folder, run);
        // So its tables can be written elsewhere later, as they are (propose_timeline_dashboard).
        TimelineSnapshot.of(run, now).write(workspace, folder);
        final Map<String, Object> structured = TimelineResult.structured(run, files);
        return context.session().isChat()
                ? ToolResult.text(TimelineResult.markdown(run, files)).withStructuredContent(structured)
                : ToolResult.text(JsonUtil.writeValueAsString(structured, false)).withStructuredContent(structured);
    }

    // ------------------------------------------------------------------------------------------------
    // A long period

    // Each source's events of the user in the period, counted by one aggregate each before any is read; where they
    // are more than a timeline reads, the period a day at a time (by source, and by type where the index holds it)
    // as the result, with the days to pick; null to read the events.
    private ToolResult overview(final ToolCallContext context,
                                final SessionWorkspace workspace,
                                final AiWorkspaceConfig config,
                                final Plan plan,
                                final Period period,
                                final ToolTimeZone zone,
                                final Instant deadline,
                                final String entity) {
        final List<Source> sources = plan.sources();
        final ElasticEsql esql = elasticEsqlProvider.get();
        if (esql == null) {
            return null;
        }
        context.progress().report("Counting " + entity + "'s events in " + sources.size() + " source(s)");
        final List<TimelineOverview.Counted> counts = new ArrayList<>();
        final List<String> timeFields = new ArrayList<>();
        long known = 0;
        for (final Source source : sources) {
            final String timeField = timeField(source.doc());
            timeFields.add(timeField);
            final TimelineOverview.Counted counted = TimelineOverview.count(esql, source, timeField,
                    period.timeRange(), TimelineOverview.atLeastASecond(deadline), context.stopped());
            counts.add(counted);
            known += Math.max(0, counted.events());
        }
        final int cap = config.getTimelineMaxEvents();
        if (known <= cap) {
            return null;
        }
        context.progress().report("Aggregating " + entity + "'s " + known + " events a day at a time");
        final List<TimelineOverview.SourceDays> days = new ArrayList<>();
        for (int i = 0; i < sources.size(); i++) {
            final Fields fields = plan.fields().get(i);
            final String typeField = fields == null
                    ? null
                    : fields.eventType() != null
                            ? fields.eventType()
                            : fields.element();
            days.add(TimelineOverview.days(esql, sources.get(i), timeFields.get(i), typeField, period.timeRange(),
                    zone.zone().getId(), TimelineOverview.atLeastASecond(deadline), context.stopped()));
        }
        final String folder = FOLDER + safe(entity) + "/";
        final List<List<String>> rows = new ArrayList<>();
        for (final TimelineOverview.SourceDays source : days) {
            source.byDay().forEach((day, events) -> {
                final Map<String, Long> types = source.byType().getOrDefault(day, Map.of());
                if (types.isEmpty()) {
                    rows.add(List.of(day, source.source(), "", String.valueOf(events)));
                } else {
                    types.forEach((type, count) -> rows.add(List.of(day, source.source(), type,
                            String.valueOf(count))));
                }
            });
        }
        rows.sort(Comparator.comparing((List<String> row) -> row.getFirst()).thenComparing(row -> row.get(1)));
        final WorkspacePath file = WorkspacePath.parse(folder + OVERVIEW_FILE);
        ToolTables.write(workspace, file, List.of("day", "source", "type", "events"), rows, null);
        final Map<String, Object> structured = TimelineResult.overview(entity, period, cap, known, counts, days,
                file.toString(), context.session().isChat());
        return context.session().isChat()
                ? ToolResult.text(TimelineResult.overviewMarkdown(structured)).withStructuredContent(structured)
                : ToolResult.text(JsonUtil.writeValueAsString(structured, false)).withStructuredContent(structured);
    }

    // An index's time field, or null where it can't be told.
    private String timeField(final DocRef doc) {
        try {
            return keyStreamsProvider.get().target(List.of(doc)).docs().getFirst().timeField();
        } catch (final RuntimeException e) {
            return null;
        }
    }

    // A field's description, as describe_data_source gives it: from the index's documentation (a field table's
    // Description column), else the index plan kept with a pipeline feeding it; null if neither has one, or they
    // can't be read.
    private String fieldDescription(final ToolCallContext context, final DocRef index, final String field) {
        try {
            final DataSourceDocumentation documentation = dataSourceDocumentationProvider.get();
            final Map<String, String> row = DataSourceDocumentation.fieldRows(documentation.find(context.session(),
                    index, "Documentation read by the " + NAME + " tool")).get(field);
            final String documented = NullSafe.map(row).entrySet().stream()
                    .filter(entry -> entry.getKey().strip().toLowerCase(Locale.ROOT).startsWith("description"))
                    .map(Map.Entry::getValue)
                    .filter(NullSafe::isNonBlankString)
                    .findFirst()
                    .orElse(null);
            if (documented != null) {
                return documented;
            }
            return documentation.planned(documentation.feedingPipelines(index), field)
                    .map(planned -> planned.field().description())
                    .filter(NullSafe::isNonBlankString)
                    .orElse(null);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Unable to describe field {} of {}", field, index), e);
            return null;
        }
    }

    // ------------------------------------------------------------------------------------------------
    // What was done to the person

    // What was done to the person in each source (and in those left out for holding none of their own events): one
    // more read each, by its target fields, the person's own acts on themselves left out. Kept apart from every
    // computation of their activity.
    private TimelineTargets.Asked targets(final ToolCall request,
                                          final ToolCallContext context,
                                          final Who who,
                                          final Plan plan,
                                          final Period period,
                                          final Instant deadline,
                                          final AiWorkspaceConfig config) {
        final List<String> targetPersonFields = stringListArg(request, TARGET_PERSON_FIELD_ARG);
        final List<String> targetUserFields = stringListArg(request, TARGET_USER_FIELD_ARG);
        final List<TimelineTargets.TargetSource> read = new ArrayList<>();
        final Map<String, String> leftOut = new LinkedHashMap<>();
        final List<Source> sources = plan.sources();
        for (int i = 0; i < sources.size() + plan.noneOfTheirs().size(); i++) {
            final boolean kept = i < sources.size();
            final String name = kept
                    ? sources.get(i).name()
                    : plan.noneOfTheirs().get(i - sources.size()).name();
            final DocRef doc = kept
                    ? sources.get(i).doc()
                    : plan.noneOfTheirs().get(i - sources.size()).doc();
            final Scope actor = kept
                    ? new Scope(sources.get(i).by(), new Found(sources.get(i).userField(),
                    TimelineFields.Origin.CALL, null), sources.get(i).values(), null)
                    : actorScope(plan.noneOfTheirs().get(i - sources.size()));
            final Map<String, ElasticEql.Field> mapping = target(doc, name).fields();
            final PersonScope.Target target = PersonScope.target(timelineFields, context.session(), doc,
                    who.person(), who.logons(), targetPersonFields, targetUserFields, actor,
                    field -> userKey(field, mapping) != null);
            if (!target.usable()) {
                leftOut.put(name, target.leftOut());
                continue;
            }
            final String key = userKey(target.field().field(), mapping);
            final PersonScope.Target keyed = new PersonScope.Target(target.by(), new Found(key,
                    target.field().origin(), target.field().detail()), target.values(), target.actorField(),
                    target.actorValues(), null);
            final Fields fields = kept
                    ? plan.fields().get(i)
                    : null;
            final Map<String, FieldType> none = Map.of();
            read.add(new TimelineTargets.TargetSource(i, name, doc, keyed, JoinKeys.kind(mapping.get(key)
                    .nativeType()), fields != null && fields.action() != null
                    ? fields.action()
                    : found(context, doc, InstructionRules.ACTION_FIELD, null, mapping, none),
                    fields != null && fields.element() != null
                            ? fields.element()
                            : found(context, doc, InstructionRules.ELEMENT_FIELD, null, mapping, none),
                    fields != null && fields.eventType() != null
                            ? fields.eventType()
                            : found(context, doc, InstructionRules.EVENT_TYPE_FIELD, null, mapping, none),
                    found(context, doc, InstructionRules.USER_FIELD, null, mapping, none),
                    found(context, doc, InstructionRules.PERSON_FIELD, null, mapping, none),
                    kept && sources.get(i).hostField() != null
                            ? sources.get(i).hostField()
                            : found(context, doc, InstructionRules.HOST_FIELD, null, mapping, none),
                    kept
                            ? sources.get(i).objectField()
                            : null,
                    mapping.containsKey(EqlSequenceResults.STREAM_ID)
                    && mapping.containsKey(EqlSequenceResults.EVENT_ID)));
        }
        context.progress().report("Reading what was done to " + who.label() + " in " + read.size() + " source(s)");
        final TimelineTargets.Read done;
        try {
            done = TimelineTargets.read(keyStreamsProvider.get(), read, period.timeRange(), new JoinBudget(
                    config.getTimelineMaxEvents(), Long.MAX_VALUE, deadline, context.stopped()));
        } catch (final PermissionException e) {
            throw new WorkspaceException("You can't use one of the sources' Elastic Indexes: " + e.getMessage());
        }
        for (int i = 0; i < read.size(); i++) {
            final TimelineTargets.TargetSource source = read.get(i);
            auditLog.searched(context.session(), source.doc(), "what was done to " + who.label() + " (" + NAME
                                                               + ")\ntarget field: " + source.target().field()
                                                                       .field() + " in " + source.target().values()
                                                               + "\nleft out: " + TimelineTargets.notTheirOwn(
                    source.target()) + "\ntime: " + period.timeRange().getFrom() + " to "
                                                               + period.timeRange().getTo(), i < done.counts().size()
                    ? done.counts().get(i)
                    : 0L, "What was done to a person, by the " + NAME + " tool");
        }
        return new TimelineTargets.Asked(read, done, leftOut);
    }

    // The scope an automatic source would have been read by, as the actor's left out of what was done to them.
    private static Scope actorScope(final Automatic automatic) {
        return automatic.user() == null
                ? new Scope(null, null, List.of(), automatic.leftOut())
                : new Scope(automatic.by(), automatic.user(), automatic.values(), null);
    }

    // ------------------------------------------------------------------------------------------------
    // The sources

    // The sources, each checked against its index: given by the call (roles or not), or chosen; each index's user
    // field found; an automatic source probed for the user's events (leaving it out, listed, if it has none where
    // every source was taken) and for an element field.
    private Plan plan(final ToolCall request,
                      final ToolCallContext context,
                      final Who who,
                      final Period period,
                      final Instant deadline,
                      final SkillInUse skill) {
        final Object given = argsMap(request).get(SOURCES_ARG);
        final List<Map<?, ?>> specs = new ArrayList<>();
        final String chosen;
        boolean all = false;
        final boolean ask = given == null || (given instanceof final String text && (text.isBlank()
                                                                                    || ASK_SOURCES.equalsIgnoreCase(
                text.strip())));
        final TimelineSourceChoice.Chosen ruled = ask && !(context.session().isChat()
                                                           && context.userQuestions().canAsk())
                ? agentsSources(context)
                : null;
        if (ruled != null) {
            ruled.indexes().forEach(doc -> specs.add(Map.of(INDEX_KEY, doc.getUuid())));
            chosen = ruled.how();
        } else if (ask || (given instanceof final String text && ALL_SOURCES.equalsIgnoreCase(text.strip()))) {
            final TimelineSourceChoice.Chosen choice = ask
                    ? sourceChoice.choose(request, context, NAME)
                    : new TimelineSourceChoice.Chosen(sourceChoice.supported(), true, "all supported data sources, "
                                                                                     + "as the call asked");
            if (choice.indexes().isEmpty()) {
                throw new WorkspaceException("You can't use any Elastic Index, so there is nothing to build a "
                                             + "timeline from.");
            }
            choice.indexes().forEach(doc -> specs.add(Map.of(INDEX_KEY, doc.getUuid())));
            chosen = choice.how();
            all = choice.all();
        } else {
            final List<?> list = given instanceof final List<?> items
                    ? items
                    : given instanceof final String text
                            ? text.strip().startsWith("[")
                                    ? parsedList(text)
                                    : List.of(text.split(","))
                            : List.of();
            if (list.isEmpty()) {
                throw new WorkspaceException("Give " + SOURCES_ARG + ": at least one source (an index's name will "
                                             + "do), \"all\", or none to ask the user.");
            }
            for (int i = 0; i < list.size(); i++) {
                final Object item = list.get(i);
                if (item instanceof final Map<?, ?> map) {
                    specs.add(map);
                } else if (item instanceof final String name && !name.isBlank()) {
                    specs.add(Map.of(INDEX_KEY, name.strip()));
                } else {
                    throw new WorkspaceException("Source " + (i + 1) + " isn't an index's name or an object {index, "
                                                 + "...}.");
                }
            }
            chosen = skill != null && skill.listed() != null
                    ? "the call's, from the skill '" + skill.listed().skill().name() + "'"
                    : "the call's";
        }
        final List<String> userFields = stringListArg(request, USER_FIELD_ARG);
        final List<String> personFields = stringListArg(request, PERSON_FIELD_ARG);
        final List<Transform> logonForms = logonForms(request);
        final String entity = who.label();
        final List<Source> sources = new ArrayList<>();
        final List<SourceNote> notes = new ArrayList<>();
        final List<Map<?, ?>> automaticSpecs = new ArrayList<>();
        final List<Prepared> prepared = new ArrayList<>();
        final List<Automatic> noneOfTheirs = new ArrayList<>();
        final Map<Integer, String> sessions = new LinkedHashMap<>();
        for (int i = 0; i < specs.size(); i++) {
            final Map<?, ?> spec = specs.get(i);
            final String what = "Source " + (i + 1);
            if (spec.get(ROLES_KEY) != null) {
                prepared.add(new Prepared(spec, source(spec, what, who), null));
            } else {
                automaticSpecs.add(spec);
                prepared.add(prepare(context, spec, what, userFields, personFields, who, logonForms));
            }
        }
        askUserFields(request, context, prepared);
        final Map<Integer, Fields> fields = new LinkedHashMap<>();
        final List<Map<?, ?>> kept = new ArrayList<>();
        for (final Prepared one : prepared) {
            if (one.source() != null) {
                sources.add(one.source());
                kept.add(one.spec());
                notes.add(new SourceNote(one.source().name(), one.source().doc().getName(), one.source().userField(),
                        "the call (roles given)", null, null, one.source().by(), null, List.of()));
                continue;
            }
            final Automatic automatic = one.automatic();
            if (automatic.user() == null) {
                notes.add(new SourceNote(automatic.name(), automatic.doc().getName(), null, null, null,
                        who.person() == null
                                ? "no field holding the user was found (give sources [{index, user}], or a ## Fields "
                                  + "line in the AGENTS doc where it is)"
                                : automatic.leftOut(), null, null, List.of()));
                continue;
            }
            final Probed probed = probe(context, automatic, period, deadline);
            if (probed == null) {
                final String none = who.single()
                        ? "no events of " + entity + " in the period"
                        : "no events " + automatic.by().label() + " (" + String.join(", ", automatic.values())
                          + ") in the period";
                // Every supported index read: most hold none of the user's events, so no hint for each.
                final String hint = all
                        ? null
                        : formsHint(automatic);
                if (all || specs.size() > 1) {
                    notes.add(new SourceNote(automatic.name(), automatic.doc().getName(), automatic.user().field(),
                            automatic.user().from(), null, hint == null
                                    ? none
                                    : none + ". " + hint, automatic.by(), null, List.of()));
                    noneOfTheirs.add(automatic);
                    continue;
                }
                sources.add(automatic.source(automatic.transforms(), spanFields(one.spec(), automatic.mapping())));
                kept.add(one.spec());
                notes.add(new SourceNote(automatic.name(), automatic.doc().getName(), automatic.user().field(),
                        automatic.user().from(), transformNote(automatic.transforms()), null, automatic.by(), hint,
                        automatic.suggestions()));
                fields.put(sources.size() - 1, automatic.fields(null));
                continue;
            }
            final Fields found = automatic.fields(probed.element());
            final List<String> extra = new ArrayList<>();
            for (final String field : new String[]{found.element(), found.action(), found.eventType()}) {
                if (field != null && !extra.contains(field)) {
                    extra.add(field);
                }
            }
            spanFields(one.spec(), automatic.mapping()).stream().filter(field -> !extra.contains(field))
                    .forEach(extra::add);
            sources.add(automatic.source(automatic.transforms(), extra));
            kept.add(one.spec());
            fields.put(sources.size() - 1, found);
            NullSafe.consume(fieldOf(automatic.mapped(), InstructionRules.SESSION_FIELD), field ->
                    sessions.put(sources.size() - 1, field));
            notes.add(new SourceNote(automatic.name(), automatic.doc().getName(), automatic.user().field(),
                    automatic.user().from(), transformNote(automatic.transforms()), null, automatic.by(), null,
                    automatic.suggestions()));
        }
        // Every supported index taken with no form to ask: only those holding the user's events are read, and past
        // about ten the calling model chooses (decided with the user 2026-11-25).
        if (all && !context.session().isChat() && sources.size() > MAX_AUTOMATIC_SOURCES) {
            final StringBuilder sb = new StringBuilder().append(sources.size()).append(" Elastic Indexes hold ")
                    .append(entity).append("'s events in the period, more than the ").append(MAX_AUTOMATIC_SOURCES)
                    .append(" a timeline reads unasked. Call again with sources naming those to read (an AGENTS doc "
                            + "at the top of the tree may name a default: ## Sources, - timeline: <index>, ...):");
            for (int i = 0; i < sources.size(); i++) {
                final TimelineOverview.Counted counted = TimelineOverview.count(elasticEsqlProvider.get(),
                        sources.get(i), timeField(sources.get(i).doc()), period.timeRange(),
                        TimelineOverview.atLeastASecond(deadline), context.stopped());
                sb.append("\n- ").append(sources.get(i).doc().getName()).append(" (").append(sources.get(i).doc()
                        .getUuid()).append("): ").append(counted.events() < 0
                        ? "events not counted"
                        : counted.events() + " event(s)").append(", user field ").append(sources.get(i).userField());
            }
            throw new WorkspaceException(sb.toString());
        }
        if (sources.isEmpty()) {
            final StringBuilder sb = new StringBuilder("No source holds ").append(entity)
                    .append("'s events in the period with a field holding the user:");
            notes.forEach(note -> sb.append("\n- ").append(note.index()).append(": ").append(note.leftOut()));
            final List<EventRange> ranges = prepared.stream()
                    .map(Prepared::automatic)
                    .filter(automatic -> automatic != null && automatic.user() != null)
                    .map(automatic -> range(context, automatic.name(), automatic.doc(), automatic.user().field(),
                            automatic.values(), deadline))
                    .toList();
            sb.append(TimelineResult.rangesText(ranges));
            throw new WorkspaceException(sb.toString());
        }
        final List<KeyRule> keys = new ArrayList<>(keysOf(kept));
        // A session's key mapped for an index (an AGENTS doc's `session:`, an index plan's EventSource/SessionId)
        // bounds its spans, as the call's spanBy would.
        sessions.forEach((i, field) -> {
            if (keys.stream().noneMatch(key -> key.source() == i && key.path().equals(field))) {
                keys.add(new KeyRule(i, field, true));
            }
        });
        return new Plan(sources, notes, chosen, fields, pairsOf(kept, sources, skill), overridesOf(kept, sources,
                skill), keys, noneOfTheirs);
    }

    // An automatic source's index, its fields, and its user field found (or not yet).
    private Prepared prepare(final ToolCallContext context,
                             final Map<?, ?> spec,
                             final String what,
                             final List<String> userFields,
                             final List<String> personFields,
                             final Who who,
                             final List<Transform> logonForms) {
        final String index = text(spec.get(INDEX_KEY));
        if (index == null) {
            throw new WorkspaceException(what + " has no " + INDEX_KEY + ": give its Elastic Index's uuid or name.");
        }
        final DocRef doc = dataSource(index);
        if (!ElasticIndexDoc.TYPE.equals(doc.getType())) {
            throw new WorkspaceException(what + ": " + DataSourceAccess.describe(doc) + " isn't an Elastic Index: "
                                         + "a timeline reads Elastic Indexes only.");
        }
        final String name = NullSafe.nonBlankStringElse(text(spec.get(NAME_KEY)), doc.getName());
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
        final List<String> givenUser = new ArrayList<>();
        NullSafe.consume(text(spec.get(USER_KEY)), givenUser::add);
        givenUser.addAll(userFields);
        // By the person field where a person is given and the index has one, else by the logon ids.
        final Scope scope = PersonScope.resolve(timelineFields, context.session(), doc, who.person(), who.logons(),
                personFields, givenUser, field -> userKey(field, mapping) != null);
        final Found user = scope.usable()
                ? scope.field()
                : null;
        // The ## Fields lines that would make the fields found by a guess the site's.
        final List<String> suggestions = new ArrayList<>();
        if (user != null) {
            NullSafe.consume(user.suggestion(), suggestions::add);
        }
        // Every thing the timeline reads, mapped once for the index: the call's, else found ([TimelineFields]),
        // each with the field every feeding pipeline's plan gives it where they differ.
        final Map<String, Found> mapped = new LinkedHashMap<>();
        checked(spec.get(PROCESS_KEY), mapping, types, what, doc);
        checked(spec.get(TITLE_KEY), mapping, types, what, doc);
        checked(spec.get(OBJECT_KEY), mapping, types, what, doc);
        checked(spec.get(TEXT_KEY), mapping, types, what, doc);
        final Map<String, Object> givenBy = new LinkedHashMap<>();
        givenBy.put(InstructionRules.HOST_FIELD, spec.get(HOST_KEY));
        givenBy.put(InstructionRules.ELEMENT_FIELD, spec.get(ELEMENT_FIELD_KEY));
        givenBy.put(InstructionRules.ACTION_FIELD, spec.get(ACTION_FIELD_KEY));
        givenBy.put(InstructionRules.EVENT_TYPE_FIELD, spec.get(EVENT_TYPE_FIELD_KEY));
        givenBy.put(InstructionRules.APPLICATION_FIELD, spec.get(PROCESS_KEY));
        givenBy.put(InstructionRules.TITLE_FIELD, spec.get(TITLE_KEY));
        givenBy.put(InstructionRules.OBJECT_FIELD, spec.get(OBJECT_KEY));
        givenBy.put(InstructionRules.TEXT_FIELD, spec.get(TEXT_KEY));
        givenBy.put(InstructionRules.SESSION_FIELD, null);
        givenBy.put(TimelineFields.PROCESS_ID, null);
        givenBy.forEach((thing, given) -> NullSafe.consume(mapped(context, doc, thing, given, mapping, types,
                suggestions), found -> mapped.put(thing, found)));
        if (user != null) {
            mapped.put(InstructionRules.USER_FIELD, user);
        }
        final Automatic automatic = new Automatic(name, doc, mapping, types, user == null
                ? null
                : new Found(userKey(user.field(), mapping), user.origin(), user.detail(), user.suggestion(),
                        user.byPipeline()),
                fieldOf(mapped, InstructionRules.HOST_FIELD), fieldOf(mapped, InstructionRules.ELEMENT_FIELD),
                fieldOf(mapped, InstructionRules.ACTION_FIELD), fieldOf(mapped, InstructionRules.EVENT_TYPE_FIELD),
                fieldOf(mapped, InstructionRules.OBJECT_FIELD),
                new Described(fieldOf(mapped, InstructionRules.APPLICATION_FIELD), fieldOf(mapped,
                        InstructionRules.TITLE_FIELD), checked(spec.get(WINDOW_KEY), mapping, types, what, doc),
                        fieldOf(mapped, InstructionRules.TEXT_FIELD)), userTransforms(spec, what, scope,
                logonForms), scope.usable()
                        ? scope.by()
                        : PersonScope.By.LOGON, scope.usable()
                        ? scope.values()
                        : who.logons(), scope.leftOut(), List.copyOf(suggestions), mapped);
        return new Prepared(spec, null, automatic);
    }

    // One thing's field found by TimelineFields, the call's first; one found by a guess adds its ## Fields line to
    // the suggestions.
    private Found mapped(final ToolCallContext context,
                         final DocRef doc,
                         final String what,
                         final Object given,
                         final Map<String, ElasticEql.Field> mapping,
                         final Map<String, FieldType> types,
                         final List<String> suggestions) {
        final List<String> list = new ArrayList<>();
        NullSafe.consume(text(given), list::add);
        final Optional<Found> found = timelineFields.find(context.session(), doc, what, list,
                field -> has(field, mapping, types));
        found.map(Found::suggestion).ifPresent(suggestions::add);
        return found.orElse(null);
    }

    private static String fieldOf(final Map<String, Found> mapped, final String thing) {
        return NullSafe.get(mapped.get(thing), Found::field);
    }

    // The field holding one thing, found by TimelineFields (for what was done to the person, whose fields aren't
    // suggested for the AGENTS doc).
    private String found(final ToolCallContext context,
                         final DocRef doc,
                         final String what,
                         final Object given,
                         final Map<String, ElasticEql.Field> mapping,
                         final Map<String, FieldType> types) {
        return found(context, doc, what, given, mapping, types, new ArrayList<>());
    }

    // The field holding one thing, found by TimelineFields; one found by a guess adds its ## Fields line to the
    // suggestions.
    private String found(final ToolCallContext context,
                         final DocRef doc,
                         final String what,
                         final Object given,
                         final Map<String, ElasticEql.Field> mapping,
                         final Map<String, FieldType> types,
                         final List<String> suggestions) {
        final List<String> list = new ArrayList<>();
        NullSafe.consume(text(given), list::add);
        final Optional<Found> found = timelineFields.find(context.session(), doc, what, list,
                field -> has(field, mapping, types));
        found.map(Found::suggestion).ifPresent(suggestions::add);
        return found.map(Found::field).orElse(null);
    }

    private static String checked(final Object given,
                                  final Map<String, ElasticEql.Field> mapping,
                                  final Map<String, FieldType> types,
                                  final String what,
                                  final DocRef doc) {
        final String field = text(given);
        if (field != null) {
            checkField(field, mapping, types, what, doc);
        }
        return field;
    }

    private static boolean has(final String field,
                               final Map<String, ElasticEql.Field> mapping,
                               final Map<String, FieldType> types) {
        return mapping.isEmpty()
                ? types.containsKey(field)
                : mapping.containsKey(field) || types.containsKey(field);
    }

    // The field as a key the user can be matched on (a text field's keyword subfield), or null if it can't be one.
    private static String userKey(final String field, final Map<String, ElasticEql.Field> mapping) {
        final ElasticEql.Field mapped = mapping.get(field);
        if (mapped == null) {
            return null;
        }
        if (Set.of("text", "match_only_text").contains(mapped.nativeType())) {
            return mapping.values().stream()
                    .filter(other -> field.equals(other.multiFieldParent()) && "keyword".equals(other.nativeType()))
                    .map(ElasticEql.Field::name)
                    .findFirst()
                    .orElse(null);
        }
        return JoinKeys.kind(mapped.nativeType()) == null
                ? null
                : field;
    }

    // Asks, in one form, for the user field of each automatic source none was found for; over MCP they are left out.
    private void askUserFields(final ToolCall request, final ToolCallContext context, final List<Prepared> prepared) {
        final List<Integer> missing = new ArrayList<>();
        for (int i = 0; i < prepared.size(); i++) {
            if (prepared.get(i).automatic() != null && prepared.get(i).automatic().user() == null
                && !prepared.get(i).automatic().values().isEmpty()) {
                missing.add(i);
            }
        }
        if (missing.isEmpty() || !context.session().isChat() || !context.userQuestions().canAsk()) {
            return;
        }
        final Map<String, Object> properties = new LinkedHashMap<>();
        final Map<String, Integer> byKey = new LinkedHashMap<>();
        for (final int i : missing) {
            final Automatic automatic = prepared.get(i).automatic();
            final List<String> choices = new ArrayList<>(automatic.mapping().values().stream()
                    .filter(field -> KEYWORD_TYPES.contains(field.nativeType()))
                    .map(ElasticEql.Field::name)
                    .sorted()
                    .limit(PROBE_FIELDS)
                    .toList());
            choices.add(LEAVE_OUT);
            final String key = "index_" + (i + 1);
            byKey.put(key, i);
            properties.put(key, Map.of("type", "string", "title", automatic.doc().getName() + ": the field holding "
                                                                  + "the user", "enum", choices, "default",
                    LEAVE_OUT));
        }
        final ElicitResult answer = context.userQuestions().ask(NAME, UserQuestions.formCallId(request.id(),
                USER_FIELDS_FORM), "No field holding the user was found in these indexes (from the call, an AGENTS "
                                   + "doc's ## Fields, a field convention or the index's documentation). Which is "
                                   + "it?", Map.of("type", "object", "properties", properties));
        if (answer.action() != ElicitResult.Action.ACCEPT) {
            return;
        }
        final Map<String, Object> content = NullSafe.map(answer.content());
        byKey.forEach((key, i) -> {
            final String field = content.get(key) == null
                    ? null
                    : String.valueOf(content.get(key));
            final Automatic automatic = prepared.get(i).automatic();
            if (field != null && !LEAVE_OUT.equals(field) && userKey(field, automatic.mapping()) != null) {
                prepared.set(i, new Prepared(prepared.get(i).spec(), null, automatic.withUser(new Found(
                        userKey(field, automatic.mapping()), TimelineFields.Origin.ASKED, null))));
            }
        });
    }

    // The first of the user's events in an automatic source, with its keyword fields: whether there are any (the
    // user field matched exactly, or with the transforms asked for), and which field holds the EventDetail element
    // where none was named. Null if the user has none there. No other written form is ever tried unasked: a
    // transform is a script over every event in the period, and another domain's account may be someone else's.
    private Probed probe(final ToolCallContext context,
                         final Automatic automatic,
                         final Period period,
                         final Instant deadline) {
        final String userField = automatic.user().field();
        final List<String> fields = new ArrayList<>(automatic.mapping().values().stream()
                .filter(field -> KEYWORD_TYPES.contains(field.nativeType()))
                .map(ElasticEql.Field::name)
                .filter(name -> !name.equals(userField))
                .sorted()
                .limit(PROBE_FIELDS)
                .toList());
        final Source source = automatic.source(automatic.transforms(), List.of());
        final List<Map<String, String>> rows;
        try {
            rows = TimelineReader.sample(keyStreamsProvider.get(), source, period.timeRange(), fields,
                    PROBE_EVENTS, deadline, context.stopped());
        } catch (final PermissionException e) {
            throw new WorkspaceException("You can't use Elastic Index '" + automatic.doc().getName() + "'.");
        }
        if (rows.isEmpty()) {
            return null;
        }
        return new Probed(automatic.element() != null
                ? automatic.element()
                : elementField(rows, fields));
    }

    // Where a source read by logon id, exactly, has none of them: the hint that logonForms would match other written
    // forms, at a cost (no search is made for them). Null where that wouldn't help: read by person id, with
    // transforms already, or a user field that isn't text.
    private static String formsHint(final Automatic automatic) {
        if (automatic.by() != PersonScope.By.LOGON || !automatic.transforms().isEmpty()
            || JoinKeys.kind(automatic.mapping().get(automatic.user().field()).nativeType()) != JoinKeys.Kind.TEXT) {
            return null;
        }
        return "If logons are written with a domain or as an email in '" + automatic.doc().getName() + "', and the "
               + "user says so, give " + LOGON_FORMS_ARG + " [strip_domain] (slower: a script runs over every event "
               + "in the period), or the exact forms in " + LOGON_IDS_ARG;
    }

    // What a source's note says of the transforms its user field was matched with, or null for none.
    private static String transformNote(final List<Transform> transforms) {
        return transforms.isEmpty()
                ? null
                : "matched with " + String.join(", ", transforms.stream()
                        .map(transform -> transform.name().toLowerCase(Locale.ROOT))
                        .toList()) + ": " + SCRIPTED;
    }

    // The transforms of an automatic source's user field: the source's own, else, read by logon id, the call's
    // logonForms; none by default.
    private static List<Transform> userTransforms(final Map<?, ?> spec,
                                                  final String what,
                                                  final Scope scope,
                                                  final List<Transform> logonForms) {
        final List<Transform> own = transforms(spec.get(TRANSFORM_KEY), what);
        if (!own.isEmpty()) {
            return own;
        }
        final boolean byLogon = !scope.usable() || scope.by() == PersonScope.By.LOGON;
        return byLogon
                ? logonForms
                : List.of();
    }

    // The call's logonForms: strip_domain and lowercase only.
    private static List<Transform> logonForms(final ToolCall request) {
        return PersonScope.logonForms(argsMap(request).get(LOGON_FORMS_ARG));
    }

    // A source's first and last events of the values filtered on, at any time (one aggregate), for a period with
    // none of them, so the agent needn't hunt for the range; nothing known where the source can't be asked.
    private EventRange range(final ToolCallContext context,
                             final String name,
                             final DocRef doc,
                             final String field,
                             final List<String> values,
                             final Instant deadline) {
        final String timeField;
        try {
            timeField = keyStreamsProvider.get().target(List.of(doc)).docs().getFirst().timeField();
        } catch (final RuntimeException e) {
            return new EventRange(name, null, null, -1, NullSafe.nonBlankStringElse(e.getMessage(), "its index "
                                                                                                   + "can't be read"));
        }
        if (timeField == null || values.isEmpty()) {
            return new EventRange(name, null, null, -1, "it has no time field");
        }
        final String time = "`" + timeField.replace("`", "``") + "`";
        final ExpressionOperator filter = ExpressionOperator.builder()
                .addTerm(ExpressionTerm.builder().field(field).condition(Condition.IN).value(String.join(",", values))
                        .build())
                .build();
        try {
            final Duration left = Duration.between(Instant.now(), deadline);
            final ElasticEsql.Result result = elasticEsqlProvider.get().query(new ElasticEsql.Request(doc,
                    "| STATS first_seen = MIN(" + time + "), last_seen = MAX(" + time + "), events = COUNT(*)",
                    filter, null, 1,
                    left.isNegative()
                            ? Duration.ofSeconds(1)
                            : left, false, context.stopped()));
            if (result.rows().isEmpty()) {
                return new EventRange(name, null, null, 0, null);
            }
            final List<Object> row = result.rows().getFirst();
            final long events = row.size() > 2 && row.get(2) instanceof final Number number
                    ? number.longValue()
                    : 0;
            return new EventRange(name, events == 0
                    ? null
                    : text(row.get(0)), events == 0
                    ? null
                    : text(row.get(1)), events, null);
        } catch (final ElasticEsql.Refusal refusal) {
            if (refusal.reason() == ElasticEsql.Reason.STOPPED) {
                throw new TaskTerminatedException();
            }
            return new EventRange(name, null, null, -1, NullSafe.nonBlankStringElse(refusal.getMessage(),
                    refusal.reason().name()));
        } catch (final RuntimeException e) {
            return new EventRange(name, null, null, -1, NullSafe.nonBlankStringElse(e.getMessage(),
                    e.getClass().getSimpleName()));
        }
    }

    // The field whose values are nearly all EventDetail elements, if one is.
    private static String elementField(final List<Map<String, String>> rows, final List<String> fields) {
        for (final String field : fields) {
            int values = 0;
            int elements = 0;
            for (final Map<String, String> row : rows) {
                final String value = row.get(field);
                if (value != null) {
                    values++;
                    if (EventXmlClassifier.isElement(value)) {
                        elements++;
                    }
                }
            }
            if (values >= 3 && elements >= ELEMENT_SHARE * values) {
                return field;
            }
        }
        return null;
    }

    // The index fields a source's spanBy names, fetched so they are keys of its events.
    private static List<String> spanFields(final Map<?, ?> spec, final Map<String, ElasticEql.Field> mapping) {
        return spanBy(spec).stream().filter(mapping::containsKey).toList();
    }

    private static List<String> spanBy(final Map<?, ?> spec) {
        final Object given = spec.get(SPAN_BY_KEY);
        final List<?> items = given instanceof final List<?> list
                ? list
                : given == null
                        ? List.of()
                        : List.of(String.valueOf(given).split(","));
        final List<String> out = new ArrayList<>();
        for (final Object item : items) {
            final String key = text(item);
            if (key != null && !out.contains(key)) {
                out.add(key);
            }
        }
        return out;
    }

    // The keys each source's call gives spans to be bounded by.
    private static List<KeyRule> keysOf(final List<Map<?, ?>> specs) {
        final List<KeyRule> keys = new ArrayList<>();
        for (int i = 0; i < specs.size(); i++) {
            for (final String key : spanBy(specs.get(i))) {
                keys.add(new KeyRule(i, key, true));
            }
        }
        return keys;
    }

    // Each source's pairs: the skill's Event chains first, then the call's.
    private static List<PairRule> pairsOf(final List<Map<?, ?>> specs,
                                          final List<Source> sources,
                                          final SkillInUse skill) {
        final List<PairRule> pairs = new ArrayList<>();
        if (skill != null) {
            for (final Entry entry : skill.chains().entries()) {
                if (entry.isPair()) {
                    for (int i = 0; i < sources.size(); i++) {
                        if (entry.appliesTo(sources.get(i).name()) || entry.appliesTo(sources.get(i).doc()
                                .getName())) {
                            pairs.add(new PairRule(i, entry.start(), entry.end(), entry.by(), entry.label(),
                                    Origin.SKILL, 1));
                        }
                    }
                }
            }
        }
        for (int i = 0; i < specs.size(); i++) {
            if (specs.get(i).get(PAIRS_KEY) instanceof final List<?> list) {
                for (final Object item : list) {
                    if (!(item instanceof final Map<?, ?> pair) || text(pair.get(START_KEY)) == null
                        || text(pair.get(END_KEY)) == null) {
                        throw new WorkspaceException("Source " + (i + 1) + "'s " + PAIRS_KEY + " is a list of "
                                                     + "{start, end, by, label}.");
                    }
                    pairs.add(new PairRule(i, text(pair.get(START_KEY)), text(pair.get(END_KEY)),
                            text(pair.get(BY_KEY)), text(pair.get(LABEL_KEY)), Origin.CALL, 1));
                }
            }
        }
        return pairs;
    }

    // Each source's types given a role: the skill's Event chains, then the call's typeRoles (the user's own).
    private static Map<Integer, Map<String, RoleOverride>> overridesOf(final List<Map<?, ?>> specs,
                                                                       final List<Source> sources,
                                                                       final SkillInUse skill) {
        final Map<Integer, Map<String, RoleOverride>> out = new LinkedHashMap<>();
        if (skill != null) {
            for (final Entry entry : skill.chains().entries()) {
                if (!entry.isPair()) {
                    for (int i = 0; i < sources.size(); i++) {
                        if (entry.appliesTo(sources.get(i).name()) || entry.appliesTo(sources.get(i).doc()
                                .getName())) {
                            out.computeIfAbsent(i, k -> new LinkedHashMap<>()).put(entry.type(), new RoleOverride(
                                    EventCodes.role(entry.role()), "skill"));
                        }
                    }
                }
            }
        }
        for (int i = 0; i < specs.size(); i++) {
            for (final Map.Entry<String, String> typeRole : typeRoles(specs.get(i), i).entrySet()) {
                out.computeIfAbsent(i, k -> new LinkedHashMap<>()).put(typeRole.getKey(), new RoleOverride(
                        EventCodes.role(typeRole.getValue()), "call"));
            }
        }
        return out;
    }

    private static Map<String, String> typeRoles(final Map<?, ?> spec, final int i) {
        final Object given = spec.get(TYPE_ROLES_KEY);
        final Map<String, String> out = new LinkedHashMap<>();
        if (given == null) {
            return out;
        }
        if (!(given instanceof final Map<?, ?> map)) {
            throw new WorkspaceException("Source " + (i + 1) + "'s " + TYPE_ROLES_KEY + " is {\"<type>\": \"<role>\"}, "
                                         + "each role one of " + String.join(", ", EventChains.ROLES) + ".");
        }
        map.forEach((type, role) -> {
            final String name = String.valueOf(role).strip().toLowerCase(Locale.ROOT);
            if (!EventChains.ROLES.contains(name)) {
                throw new WorkspaceException("Source " + (i + 1) + ": '" + role + "' isn't a role: give one of "
                                             + String.join(", ", EventChains.ROLES) + ".");
            }
            out.put(String.valueOf(type).strip(), name);
        });
        return out;
    }

    // ------------------------------------------------------------------------------------------------
    // Classifying

    // The automatic sources' events' Events XML, read by StreamId and EventId, only for what the index doesn't map
    // (decided with the user 2026-11-25): an event whose role its index doesn't settle (no element for the schema's
    // table, and no event code the call, a skill, its catalogue or an AGENTS doc gives a role), in time order up to
    // the cap, so the schema's table reads its element and Action from the XML before its code is classified; and,
    // characterising, an event of a typed source with no content mapped (its object, title or text), spread over the
    // period up to characterisation's budget. Each path read is named with its index, to add to the index plan.
    private XmlOutcome readXml(final ToolCallContext context,
                               final AiWorkspaceConfig config,
                               final List<Source> sources,
                               final Read read,
                               final Map<Integer, Fields> fields,
                               final TypeRoleLookup roles,
                               final boolean characterising,
                               final Map<String, String> alreadyRead) {
        final List<Event> forType = new ArrayList<>();
        final Map<Integer, List<Event>> forContent = new LinkedHashMap<>();
        for (final Event event : read.events()) {
            final Source source = sources.get(event.source());
            if (!source.automatic() || number(event.streamId()) == null || number(event.eventId()) == null) {
                continue;
            }
            final Fields found = fields.getOrDefault(event.source(), Fields.NONE);
            final Map<String, String> extra = read.extras().getOrDefault(event.id(), Map.of());
            if (!typedByIndex(found, extra, roles, event)) {
                forType.add(event);
            } else if (characterising && event.object() == null && event.title() == null && event.text() == null) {
                forContent.computeIfAbsent(event.source(), k -> new ArrayList<>()).add(event);
            }
        }
        if (forType.isEmpty() && forContent.isEmpty()) {
            return new XmlOutcome(Map.of(), XmlRead.NONE, List.of());
        }
        forType.sort(Comparator.comparingLong(Event::time));
        final List<Event> toRead = sampledByCode(forType, read, fields);
        final int cap = Math.max(0, config.getTimelineMaxXmlEvents());
        final Map<Long, List<Long>> ids = new TreeMap<>();
        final Set<String> seen = new LinkedHashSet<>();
        final Map<Integer, Integer> typeRead = new LinkedHashMap<>();
        for (final Event event : toRead) {
            if (seen.size() >= cap) {
                break;
            }
            if (seen.add(event.streamId() + ":" + event.eventId())) {
                ids.computeIfAbsent(number(event.streamId()), k -> new ArrayList<>()).add(number(event.eventId()));
                typeRead.merge(event.source(), 1, Integer::sum);
            }
        }
        final int pastCap = (int) toRead.stream()
                .map(event -> event.streamId() + ":" + event.eventId())
                .distinct()
                .count() - seen.size();
        final int budget = Math.max(0, config.getCharacteriseMaxItems());
        final Map<Integer, Integer> contentRead = new LinkedHashMap<>();
        for (final Map.Entry<Integer, List<Event>> source : forContent.entrySet()) {
            final List<Event> events = source.getValue().stream().sorted(Comparator.comparingLong(Event::time))
                    .toList();
            // Spread over the period, so the budget doesn't go on its first hours.
            final double step = events.size() <= budget
                    ? 1
                    : (double) events.size() / Math.max(1, budget);
            for (double at = 0; at < events.size() && contentRead.getOrDefault(source.getKey(), 0) < budget;
                 at += step) {
                final Event event = events.get((int) at);
                if (seen.add(event.streamId() + ":" + event.eventId())) {
                    ids.computeIfAbsent(number(event.streamId()), k -> new ArrayList<>())
                            .add(number(event.eventId()));
                    contentRead.merge(source.getKey(), 1, Integer::sum);
                }
            }
        }
        final List<XmlPaths> paths = new ArrayList<>();
        typeRead.forEach((i, count) -> paths.add(new XmlPaths(sources.get(i).name(), sources.get(i).doc().getName(),
                TYPE_PATHS, count)));
        contentRead.forEach((i, count) -> paths.add(new XmlPaths(sources.get(i).name(),
                sources.get(i).doc().getName(), CONTENT_PATHS, count)));
        // The events whose XML was read for their fields aren't read again.
        final Map<String, String> reused = new LinkedHashMap<>();
        final Map<Long, List<Long>> unread = new TreeMap<>();
        ids.forEach((stream, events) -> events.forEach(id -> {
            final String text = NullSafe.map(alreadyRead).get(stream + ":" + id);
            if (text != null) {
                reused.put(stream + ":" + id, text);
            } else {
                unread.computeIfAbsent(stream, k -> new ArrayList<>()).add(id);
            }
        }));
        context.progress().report("Reading the Events XML of " + seen.size() + " event(s)");
        final TimelineEventXml.Read xml = unread.isEmpty()
                ? new TimelineEventXml.Read(Map.of(), 0, 0, 0, true)
                : eventXml.read(context.session(), unread, config.getMaxEventBytes(), context.stopped(),
                        count -> context.progress().report("Read the Events XML of " + (reused.size() + count)
                                                           + " of " + seen.size() + " event(s)"));
        final Map<String, String> all = new LinkedHashMap<>(reused);
        all.putAll(xml.xml());
        return new XmlOutcome(all, new XmlRead(seen.size() + pastCap, all.size(), xml.missing(),
                xml.unreadable(), pastCap, xml.notAllowed(), cap), paths);
    }

    // The events whose XML is read for their type, in the order read: of each unsettled event code, a few events
    // spread over the period (the first round of every code first, so the cap reaches every code), as the code is
    // classified once from them and applied to all its events; between the rounds, every event with no code, each
    // read for itself.
    private static List<Event> sampledByCode(final List<Event> forType,
                                             final Read read,
                                             final Map<Integer, Fields> fields) {
        final Map<Integer, Map<String, List<Event>>> byCode = new LinkedHashMap<>();
        final List<Event> uncoded = new ArrayList<>();
        for (final Event event : forType) {
            final Fields found = fields.getOrDefault(event.source(), Fields.NONE);
            final String code = found.eventType() == null
                    ? null
                    : read.extras().getOrDefault(event.id(), Map.of()).get(found.eventType());
            if (NullSafe.isBlankString(code)) {
                uncoded.add(event);
            } else {
                byCode.computeIfAbsent(event.source(), k -> new LinkedHashMap<>())
                        .computeIfAbsent(code, k -> new ArrayList<>()).add(event);
            }
        }
        final List<List<Event>> samples = new ArrayList<>();
        byCode.values().forEach(codes -> codes.values().forEach(events -> samples.add(spread(events,
                XML_EVENTS_PER_CODE))));
        final List<Event> out = new ArrayList<>();
        samples.forEach(sample -> out.add(sample.getFirst()));
        out.addAll(uncoded);
        for (int round = 1; round < XML_EVENTS_PER_CODE; round++) {
            for (final List<Event> sample : samples) {
                if (sample.size() > round) {
                    out.add(sample.get(round));
                }
            }
        }
        return out;
    }

    // At most the given number of the events (in time order), spread from the first to the last.
    private static List<Event> spread(final List<Event> events, final int most) {
        if (events.size() <= most) {
            return events;
        }
        final List<Event> out = new ArrayList<>(most);
        for (int i = 0; i < most; i++) {
            out.add(events.get((int) Math.round((double) i * (events.size() - 1) / Math.max(1, most - 1))));
        }
        return out;
    }

    // True if the index's fields settle the event's role: its element (and Action) for the schema's table, or an
    // event code something gives a role.
    private static boolean typedByIndex(final Fields fields,
                                        final Map<String, String> extra,
                                        final TypeRoleLookup roles,
                                        final Event event) {
        if (fields.element() != null && EventXmlClassifier.isElement(extra.get(fields.element()))) {
            return true;
        }
        final String code = fields.eventType() == null
                ? null
                : extra.get(fields.eventType());
        return NullSafe.isNonBlankString(code) && roles.settles(event.source(), event.streamId(), code);
    }

    // Where each event read came from: the streams' meta (their parents, to count each event once) and producing
    // pipelines, one batched lookup; null where no source has StreamIds, or they can't be looked up.
    private Provenance provenance(final ToolCallContext context, final List<Source> sources, final Read read) {
        final Set<Long> streams = new LinkedHashSet<>();
        for (final Event event : read.events()) {
            if (sources.get(event.source()).hasStreamIds()) {
                NullSafe.consume(number(event.streamId()), streams::add);
            }
        }
        if (streams.isEmpty()) {
            return null;
        }
        context.progress().report("Looking up where " + streams.size() + " stream(s) came from");
        try {
            return streamProvenanceProvider.get().of(streams);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("The streams' provenance wasn't looked up: {}", e.getMessage()), e);
            return null;
        }
    }

    // For the sources whose feeding pipelines' plans differ: the pipeline that indexed each Events stream read into
    // each one's index: its producing pipeline where that is one of the index's feeding pipelines, else the one its
    // tasks or the processor filters trace it to (once per stream), among the feeding pipelines whose plans the user
    // can view.
    private BiFunction<Integer, Long, Indexed> indexedBy(final ToolCallContext context,
                                                       final List<Source> sources,
                                                       final List<Event> events,
                                                       final Provenance provenance) {
        final Map<Integer, Map<Long, Indexed>> bySource = new LinkedHashMap<>();
        for (int i = 0; i < sources.size(); i++) {
            final Source source = sources.get(i);
            if (source.varied().isEmpty()) {
                continue;
            }
            final List<DocRef> feeding = feedingPipelines(source.doc());
            final Set<String> feedingUuids = new LinkedHashSet<>();
            feeding.forEach(pipeline -> feedingUuids.add(pipeline.getUuid()));
            final Map<Long, Indexed> indexed = new LinkedHashMap<>();
            final Set<Long> untraced = new LinkedHashSet<>();
            for (final Event event : events) {
                final Long streamId = number(event.streamId());
                if (event.source() != i || streamId == null || indexed.containsKey(streamId)) {
                    continue;
                }
                final DocRef producing = pipelineOf(provenance, streamId);
                if (producing != null && feedingUuids.contains(producing.getUuid())) {
                    indexed.put(streamId, new Indexed(producing, StreamFields.BY_STREAM));
                } else {
                    untraced.add(streamId);
                }
            }
            if (!untraced.isEmpty() && !feeding.isEmpty()) {
                context.progress().report("Tracing " + untraced.size() + " stream(s) to the pipelines indexing "
                                          + source.doc().getName());
                final IndexingTrace.Traced traced = indexingTraceProvider.get().trace(untraced, feeding);
                traced.byTasks().forEach((stream, pipeline) -> indexed.put(stream, new Indexed(pipeline,
                        StreamFields.BY_TASKS)));
                traced.byFilters().forEach((stream, pipeline) -> indexed.put(stream, new Indexed(pipeline,
                        StreamFields.BY_FILTERS)));
            }
            bySource.put(i, indexed);
        }
        return (source, streamId) -> streamId == null
                ? null
                : bySource.getOrDefault(source, Map.of()).get(streamId);
    }

    // The pipelines feeding an index whose plans the user can view.
    private List<DocRef> feedingPipelines(final DocRef index) {
        try {
            final DataSourceDocumentation.FeedingPlans plans = dataSourceDocumentationProvider.get()
                    .feedingPlans(index);
            return NullSafe.list(NullSafe.get(plans, DataSourceDocumentation.FeedingPlans::pipelines)).stream()
                    .filter(pipeline -> plans.plans().containsKey(pipeline) && documentAccess.canView(pipeline))
                    .toList();
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("The pipelines feeding {} weren't read: {}", index, e.getMessage()),
                    e);
            return List.of();
        }
    }

    // The Events XML of the events whose varied things no known pipeline gives, for their values at the things'
    // paths.
    private Map<String, String> readFieldXml(final ToolCallContext context,
                                             final AiWorkspaceConfig config,
                                             final StreamFields.ToRead toRead) {
        context.progress().report("Reading the Events XML of " + toRead.size() + " event(s) for their fields");
        return eventXml.read(context.session(), toRead.ids(), config.getMaxEventBytes(), context.stopped(),
                count -> context.progress().report("Read the Events XML of " + count + " of " + toRead.size()
                                                   + " event(s) for their fields")).xml();
    }

    // The pipeline that produced a stream, named where the user can read it; null where it isn't known.
    private static DocRef pipelineOf(final Provenance provenance, final Long streamId) {
        if (provenance == null || streamId == null) {
            return null;
        }
        return provenance.pipelineOf(streamId).map(PipelineFacts::pipeline).orElse(null);
    }

    // A thing's event-logging paths, for reading it from an event's Events XML: its convention's, else those an
    // index plan may map it from; none for a thing with no path.
    private static List<String> schemaPaths(final String thing) {
        final String convention = TimelineFields.CONVENTION_PATHS.get(thing);
        return convention != null
                ? List.of(convention)
                : TimelineFields.PLAN_PATHS.getOrDefault(thing, List.of());
    }

    // The raw stream a stream was translated from; null where it isn't known.
    private static Long parentOf(final Provenance provenance, final Long streamId) {
        if (provenance == null || streamId == null) {
            return null;
        }
        return NullSafe.get(provenance.streams().get(streamId), StreamProvenance.StreamSource::parentId);
    }

    // The catalogue's roles of the translation that produced each stream, read once per pipeline.
    private Function<String, Map<String, TimelineRoles.Role>> catalogue(final Provenance provenance) {
        if (provenance == null) {
            return streamId -> Map.of();
        }
        final Map<String, Map<String, TimelineRoles.Role>> byPipeline = new LinkedHashMap<>();
        final StreamProvenance streamProvenance = streamProvenanceProvider.get();
        return streamId -> {
            final Long id = number(streamId);
            final PipelineFacts facts = id == null
                    ? null
                    : provenance.pipelineOf(id).orElse(null);
            if (facts == null) {
                return Map.of();
            }
            return byPipeline.computeIfAbsent(facts.pipeline().getUuid(), uuid -> {
                try {
                    return NullSafe.map(streamProvenance.timelineRoles(facts));
                } catch (final RuntimeException e) {
                    LOGGER.debug(() -> LogUtil.message("No catalogue roles of {}: {}", uuid, e.getMessage()), e);
                    return Map.of();
                }
            });
        };
    }

    // Each automatic source's types given a role by the AGENTS docs where its index is.
    private Map<Integer, Map<String, TypeRoleRule>> agentsRoles(final ToolCallContext context,
                                                                final List<Source> sources) {
        final Map<Integer, Map<String, TypeRoleRule>> out = new LinkedHashMap<>();
        for (int i = 0; i < sources.size(); i++) {
            if (sources.get(i).automatic()) {
                final Map<String, TypeRoleRule> ruled = NullSafe.map(agentInstructions.eventTypesRuled(
                        context.session(), sources.get(i).doc()));
                if (!ruled.isEmpty()) {
                    out.put(i, ruled);
                }
            }
        }
        return out;
    }

    // The `## Event types` lines to offer for the codes classified this run (by the model), per index, so a later
    // run takes them from the AGENTS doc.
    private static Map<String, String> offers(final List<Source> sources,
                                              final Map<Integer, Map<String, CodeRole>> classifiedNow) {
        final Map<String, String> out = new LinkedHashMap<>();
        classifiedNow.forEach((i, codes) -> {
            final Map<String, TimelineRole> roles = new LinkedHashMap<>();
            codes.forEach((code, role) -> TimelineRole.of(role.role()).ifPresent(found -> roles.put(code, found)));
            if (!roles.isEmpty()) {
                out.put(sources.get(i).doc().getName(), InstructionRules.eventTypesSection(roles));
            }
        });
        return out;
    }

    // The event codes of each automatic source whose events neither XML nor an element field classify: kept from an
    // earlier run, else asked of the model once and confirmed by the user (a chat's form; over MCP used unconfirmed
    // and not kept). Where nothing gives any of a source's codes a role (no model to ask, as over a client that
    // doesn't sample, nor a skill's or the call's typeRoles), the call is refused with the codes, for the calling
    // model to give their roles: a timeline of codes nothing classified has no focus, idle or window, so its
    // sessions would be wrong (eval case 77 with no roles: 11 sessions for the plan's 63).
    private Map<Integer, Map<String, CodeRole>> codes(final ToolCall request,
                                                      final ToolCallContext context,
                                                      final List<Source> sources,
                                                      final Read read,
                                                      final Map<Integer, Fields> fields,
                                                      final Map<String, String> xml,
                                                      final TypeRoleLookup roles,
                                                      final Map<Integer, Map<String, CodeRole>> classifiedNow) {
        final Map<Integer, Map<String, CodeRole>> out = new LinkedHashMap<>();
        final Map<Integer, Map<String, Integer>> counts = new LinkedHashMap<>();
        final Set<Integer> settled = new LinkedHashSet<>();
        final Map<Integer, Map<String, Classified>> fromXml = TimelineClassification.codesFromXml(read.events(),
                read.extras(), fields, xml);
        for (final Event event : read.events()) {
            final Fields found = fields.get(event.source());
            if (found == null || found.eventType() == null || xml.containsKey(event.streamId() + ":"
                                                                               + event.eventId())) {
                continue;
            }
            final Map<String, String> extra = read.extras().getOrDefault(event.id(), Map.of());
            if (found.element() != null && EventXmlClassifier.isElement(extra.get(found.element()))) {
                continue;
            }
            final String code = extra.get(found.eventType());
            // A code the call, the skill, its stream's catalogue or an AGENTS doc gives a role needs no other, nor
            // does one the Events XML of a few of its events classified.
            if (code != null && fromXml.getOrDefault(event.source(), Map.of()).containsKey(code)) {
                settled.add(event.source());
            } else if (code != null && roles.settles(event.source(), event.streamId(), code)) {
                settled.add(event.source());
            } else if (code != null) {
                counts.computeIfAbsent(event.source(), s -> new LinkedHashMap<>()).merge(code, 1, Integer::sum);
            }
        }
        final SessionWorkspace keptIn = context.userWorkspace().orElse(sessionWorkspace(context));
        for (final Map.Entry<Integer, Map<String, Integer>> source : counts.entrySet()) {
            final DocRef doc = sources.get(source.getKey()).doc();
            final Map<String, CodeRole> kept = new LinkedHashMap<>(EventCodes.kept(keptIn, doc.getUuid()));
            final Map<String, Integer> unknown = new LinkedHashMap<>();
            source.getValue().entrySet().stream()
                    .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                    .filter(entry -> !kept.containsKey(entry.getKey()))
                    .forEach(entry -> unknown.put(entry.getKey(), entry.getValue()));
            if (!unknown.isEmpty()) {
                EventCodes.ask(context.sideCalls(), doc.getName(), unknown).ifPresent(asked -> {
                    final Boolean confirmed = confirmCodes(request, context, doc, asked);
                    if (confirmed == null) {
                        // No form: used, but not kept, as the user hasn't confirmed it.
                        kept.putAll(asked);
                        classifiedNow.put(source.getKey(), asked);
                    } else if (confirmed) {
                        asked.forEach((code, role) -> kept.put(code, new CodeRole(role.role(), role.kind(), true)));
                        EventCodes.keep(keptIn, doc.getUuid(), kept);
                        classifiedNow.put(source.getKey(), asked);
                    }
                });
            }
            final boolean anyRole = settled.contains(source.getKey())
                                    || source.getValue().keySet().stream().anyMatch(kept::containsKey);
            if (!anyRole) {
                throw new WorkspaceException(EventCodes.unclassified(sources.get(source.getKey()).name(),
                        fields.get(source.getKey()).eventType(), source.getValue(), context.sideCalls()));
            }
            out.put(source.getKey(), kept);
        }
        return out;
    }

    // True if the user confirms the codes' classification, false if not, null if they can't be asked.
    private Boolean confirmCodes(final ToolCall request,
                                 final ToolCallContext context,
                                 final DocRef doc,
                                 final Map<String, CodeRole> asked) {
        if (!context.session().isChat() || !context.userQuestions().canAsk()) {
            return null;
        }
        final Map<String, Object> field = Map.of("type", "string", "title", "Use this classification?", "enum",
                List.of(USE_CODES, DONT_USE_CODES), "default", USE_CODES);
        final List<String> lines = EventCodes.lines(asked, CODES_SHOWN);
        final ElicitResult answer = context.userQuestions().ask(NAME, UserQuestions.formCallId(request.id(),
                CODES_FORM + "-" + doc.getUuid().substring(0, Math.min(8, doc.getUuid().length()))), "The events of "
                + doc.getName() + " have no Events XML to go by, so the model classified their event codes "
                + "(kept for this index once you confirm):\n" + String.join("\n", lines) + (asked.size() > lines.size()
                ? "\nand " + (asked.size() - lines.size()) + " more"
                : ""), Map.of("type", "object", "properties", Map.of("use", field), "required", List.of("use")));
        return answer.action() == ElicitResult.Action.ACCEPT
               && USE_CODES.equals(String.valueOf(NullSafe.map(answer.content()).get("use")));
    }

    // The automatic sources' classified events as pairs (and keys bounding spans) are inferred by: never a focus,
    // which the focus sessions use, so a focus and its loss aren't inferred as a pair that takes the sessions'
    // events (eval case 77 with no roles). The user going idle or coming back stays: a vendor's connect and
    // disconnect are a session's pair.
    private static List<Typed> typed(final TimelineClassification.Result classification,
                                     final Map<String, Event> byId,
                                     final Options options) {
        final List<Typed> out = new ArrayList<>();
        classification.byId().forEach((id, c) -> {
            final Event event = byId.get(id);
            if (event != null && c.type() != null && event.role() != Role.FOCUS) {
                out.add(new Typed(id, event.source(), event.time(), TimelineSpans.lane(event, options), c.type(),
                        c.schemaType(), c.keys()));
            }
        });
        return out;
    }

    private static Long number(final String value) {
        try {
            return value == null
                    ? null
                    : Long.parseLong(value.strip());
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------------------------------------
    // The skill

    // The skill being run, its Event chains, and whether the user may change its doc.
    private SkillInUse skillInUse(final ToolCall request, final ToolCallContext context) {
        final String name = stringArg(request, SKILL_ARG);
        if (name == null) {
            return null;
        }
        final SkillCatalogue catalogue = skillCatalogueProvider.get();
        final List<DocRef> folders = context.session().chatId().isPresent()
                ? catalogue.contextFolders(context.session().chatId().getAsInt())
                : List.of();
        final Listed listed = catalogue.readable(folders).stream()
                .filter(skill -> skill.skill().name().equalsIgnoreCase(name)
                                 || SkillDoc.slug(skill.skill().name()).equals(SkillDoc.slug(name)))
                .min(Comparator.comparingInt(skill -> skill.near() < 0
                        ? Integer.MAX_VALUE
                        : skill.near()))
                .orElse(null);
        if (listed == null) {
            return new SkillInUse(name, null, EventChains.parse(""), false);
        }
        return new SkillInUse(name, listed, EventChains.parse(listed.skill().sections().get(EventChains.SECTION)),
                documentAccess.canEdit(listed.doc()));
    }

    // A folder the user may create a skills doc in: the chat's context folders', else a source index's folder.
    private CreateIn createIn(final ToolCallContext context, final List<Source> sources) {
        final List<DocRef> folders = new ArrayList<>();
        if (context.session().chatId().isPresent()) {
            folders.addAll(skillCatalogueProvider.get().contextFolders(context.session().chatId().getAsInt()));
        }
        for (final Source source : sources) {
            documentAccess.parent(source.doc()).ifPresent(folders::add);
        }
        for (final DocRef folder : folders) {
            if (documentAccess.canCreate(folder, DocumentationDoc.TYPE)) {
                return new CreateIn(folder, ExplorerConstants.SYSTEM_DOC_REF.equals(folder)
                        ? "/"
                        : agentInstructions.folderPath(folder));
            }
        }
        return null;
    }

    // What this run learned that a skill could hold: the pairs inferred and used, and the call's own pairs and type
    // roles (the user's corrections).
    private static List<Entry> learnedEntries(final ToolCall request,
                                              final List<Source> sources,
                                              final Inferred inferred,
                                              final Plan plan) {
        final List<Entry> entries = new ArrayList<>();
        for (final Candidate candidate : inferred.clear()) {
            entries.add(Entry.pair(sources.get(candidate.source()).doc().getName(), candidate.start(),
                    candidate.end(), candidate.by(), null));
        }
        for (final PairRule rule : plan.pairs()) {
            if (rule.origin() == Origin.CALL) {
                entries.add(Entry.pair(sources.get(rule.source()).doc().getName(), rule.start(), rule.end(),
                        rule.by(), rule.label()));
            }
        }
        plan.overrides().forEach((source, types) -> types.forEach((type, override) -> {
            if ("call".equals(override.from())) {
                entries.add(Entry.role(sources.get(source).doc().getName(), type, roleName(override.role())));
            }
        }));
        return entries;
    }

    private static String roleName(final Role role) {
        if (role == null) {
            return "ignore";
        }
        return switch (role) {
            case IDLE_END -> "active";
            case IDLE_START -> "idle";
            case FOCUS -> "focus";
            case OPEN -> "open";
            case CLOSE -> "close";
            default -> "action";
        };
    }

    // ------------------------------------------------------------------------------------------------
    // The call

    // Whose timeline: a person id, and the logon ids (entity, then logonIds), each once.
    private Who who(final ToolCall request) {
        final String person = stringArg(request, PERSON_ARG);
        final List<String> logons = new ArrayList<>();
        NullSafe.consume(stringArg(request, ENTITY_ARG), logons::add);
        for (final String logon : stringListArg(request, LOGON_IDS_ARG)) {
            if (logon != null && !logon.isBlank() && !logons.contains(logon.strip())) {
                logons.add(logon.strip());
            }
        }
        if (NullSafe.isBlankString(person) && logons.isEmpty()) {
            throw new WorkspaceException("Give " + ENTITY_ARG + " (the user whose timeline to build), " + PERSON_ARG
                                         + " (a person id) or " + LOGON_IDS_ARG + ".");
        }
        final String entity = stringArg(request, ENTITY_ARG);
        return new Who(NullSafe.isBlankString(person)
                ? null
                : person.strip(), logons, NullSafe.isBlankString(entity)
                ? null
                : entity.strip());
    }

    private static View view(final ToolCall request) {
        final Object given = argsMap(request).get(VIEW_ARG);
        if (given == null || String.valueOf(given).isBlank()) {
            return View.DETAIL;
        }
        for (final View view : View.values()) {
            if (view.argName().equalsIgnoreCase(String.valueOf(given).strip())) {
                return view;
            }
        }
        throw new WorkspaceException(VIEW_ARG + " is one of activities, detail or both.");
    }

    private static String lanes(final ToolCall request) {
        final Object given = argsMap(request).get(LANES_ARG);
        if (given == null || String.valueOf(given).isBlank()) {
            return null;
        }
        final String lanes = String.valueOf(given).strip().toLowerCase(Locale.ROOT);
        if (!List.of("day", "activity", "host", "source").contains(lanes)) {
            throw new WorkspaceException(LANES_ARG + " is one of day or activity (the activities view), host or "
                                         + "source (the detail view).");
        }
        return lanes;
    }

    // ------------------------------------------------------------------------------------------------
    // Helpers

    // The period, its date expressions and times without an offset in the call's zone, as instants.
    private Period period(final ToolCall request, final ToolTimeZone zone, final long now) {
        final String givenFrom = NullSafe.nonBlankStringElse(stringArg(request, TIME_FROM_ARG), DEFAULT_FROM);
        final String givenTo = NullSafe.nonBlankStringElse(stringArg(request, TIME_TO_ARG), DEFAULT_TO);
        final long from = bound(givenFrom, zone, now, false, TIME_FROM_ARG);
        final long to = bound(givenTo, zone, now, true, TIME_TO_ARG);
        if (to <= from) {
            throw new WorkspaceException(TIME_TO_ARG + " must be after " + TIME_FROM_ARG + ".");
        }
        return new Period(from, to, new TimeRange(null, Instant.ofEpochMilli(from).toString(),
                Instant.ofEpochMilli(to).toString()));
    }

    // A bound of the period: a date alone a whole day in the zone (an end date included), as compare_activity reads
    // it; else a time, one without an offset in the zone, or a date expression.
    private static long bound(final String value,
                              final ToolTimeZone zone,
                              final long now,
                              final boolean end,
                              final String arg) {
        return CompareActivityTool.dateBound(value, zone, end)
                .map(Instant::toEpochMilli)
                .orElseGet(() -> instant(StroomQlDatePoints.timeInZone(value, zone, now), now, arg));
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
        throw new WorkspaceException(arg + " '" + value + "' isn't a time: give a date (yyyy-MM-dd), ISO 8601 "
                                     + "(e.g. 2026-11-10T00:00:00Z) or a date expression (e.g. now()-7d).");
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

    private static List<?> parsedList(final String text) {
        try {
            return JsonUtil.readValue(text, List.class);
        } catch (final RuntimeException e) {
            throw new WorkspaceException(SOURCES_ARG + " is a list of sources, each {index, user, roles, ...}.");
        }
    }

    private Source source(final Map<?, ?> map, final String what, final Who who) {
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
        if (who.logons().isEmpty()) {
            throw new WorkspaceException(what + " gives its roles, so it is read by its " + USER_KEY + " field: give "
                                         + ENTITY_ARG + " or " + LOGON_IDS_ARG + ".");
        }
        return new Source(name, doc, user, kind, transforms, fields.get(HOST_KEY), fields.get(PROCESS_KEY),
                fields.get(TITLE_KEY), fields.get(WINDOW_KEY), fields.get(OBJECT_KEY), fields.get(TEXT_KEY), ids,
                roles, false, List.of(), who.logons(), PersonScope.By.LOGON);
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
        for (final Filter filter : SequenceSteps.filters(conditions.get(WHERE_KEY), what, WHERE_KEY)) {
            checkField(filter.field(), mapping, types, what, doc);
            items.add(StreamFilters.item(filter, types, what));
        }
        final List<Filter> anyOf = SequenceSteps.filters(conditions.get(ANY_OF_KEY), what, ANY_OF_KEY);
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

    // The default sources an AGENTS doc at the top of the tree names (## Sources, - timeline: ...), each an Elastic
    // Index the user can use; null where none is named, or none named can be used.
    private TimelineSourceChoice.Chosen agentsSources(final ToolCallContext context) {
        final InstructionRules.SourcesRule rule = agentInstructions.sourcesRuled(context.session(),
                InstructionRules.TIMELINE_SOURCES).orElse(null);
        if (rule == null) {
            return null;
        }
        final List<DocRef> found = new ArrayList<>();
        final List<String> missing = new ArrayList<>();
        for (final String name : rule.sources()) {
            final List<DocRef> docs = dataSourceAccess.find(name).stream()
                    .filter(doc -> ElasticIndexDoc.TYPE.equals(doc.getType()))
                    .toList();
            if (docs.size() == 1) {
                if (!found.contains(docs.getFirst())) {
                    found.add(docs.getFirst());
                }
            } else {
                missing.add(name);
            }
        }
        if (found.isEmpty()) {
            return null;
        }
        return new TimelineSourceChoice.Chosen(found, false, "the AGENTS doc's ## Sources (" + rule.from() + ")"
                                                              + (missing.isEmpty()
                ? ""
                : "; not one Elastic Index you can use: " + String.join(", ", missing)));
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
                                       final Built built,
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
        final Timeline timeline = built.combined();
        context.progress().report("Characterising " + timeline.sessions().size() + " session(s)");
        final boolean runAnyway = booleanArg(request, RUN_ANYWAY_ARG, false);
        final ActivityCharacterisation.Settings settings = new ActivityCharacterisation.Settings(
                Math.max(1, config.getCharacteriseExcerptChars()), Math.max(1, config.getCharacteriseBatchItems()),
                Math.max(0, config.getCharacteriseMaxItems()), runAnyway, Math.max(1, config.getJoinWorkers()),
                zone.zone(), corrections, readCache(workspace, cachePath),
                count -> askBeyond(request, context, count, config.getCharacteriseMaxItems()));
        final Characterised characterised = ActivityCharacterisation.characterise(timeline, byId, names,
                built.spans().stream().filter(TimelineSpans.Span::isItem).toList(), settings, context.sideCalls());
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

    /// @param entity A user.
    /// @return A folder name for them.
    static String safe(final String entity) {
        final String safe = entity.strip().replaceAll("[^A-Za-z0-9_.-]", "_");
        return safe.length() > 60
                ? safe.substring(0, 60)
                : safe;
    }


    // --------------------------------------------------------------------------------


    /// The sources planned: each to read, each index's note, how they were chosen, the automatic sources'
    /// classifying fields, and the pairs and type roles given (by a skill or the call).
    ///
    /// @param sources   The sources.
    /// @param notes     Each index considered.
    /// @param chosen    How they were chosen.
    /// @param fields    The automatic sources' classifying fields, by place.
    /// @param pairs     The pairs given, in the order they are applied.
    /// @param overrides The types given a role, by source.
    /// @param keys      The keys the call gives spans to be bounded by, by source.
    /// @param noneOfTheirs The automatic sources left out for holding none of the person's events in the period
    ///                  (things may still have been done to them there).
    private record Plan(List<Source> sources,
                        List<SourceNote> notes,
                        String chosen,
                        Map<Integer, Fields> fields,
                        List<PairRule> pairs,
                        Map<Integer, Map<String, RoleOverride>> overrides,
                        List<KeyRule> keys,
                        List<Automatic> noneOfTheirs) {

    }


    // --------------------------------------------------------------------------------


    // A source as given: read by its roles (source), or classified (automatic).
    private record Prepared(Map<?, ?> spec, Source source, Automatic automatic) {

    }


    // --------------------------------------------------------------------------------


    // An automatic source's index and the fields found in it.
    private record Automatic(String name,
                             DocRef doc,
                             Map<String, ElasticEql.Field> mapping,
                             Map<String, FieldType> types,
                             Found user,
                             String host,
                             String element,
                             String action,
                             String eventType,
                             String object,
                             Described described,
                             List<Transform> transforms,
                             PersonScope.By by,
                             List<String> values,
                             String leftOut,
                             List<String> suggestions,
                             Map<String, Found> mapped) {

        Automatic withUser(final Found found) {
            final Map<String, Found> withIt = new LinkedHashMap<>(mapped);
            withIt.put(InstructionRules.USER_FIELD, found);
            return new Automatic(name, doc, mapping, types, found, host, element, action, eventType, object,
                    described, transforms, by, values, leftOut, suggestions, withIt);
        }

        // The application, title, window and text are the call's or those mapped for the index ([TimelineFields]):
        // never guessed from a code or an element field, which say what an event was, not these (eval case 77 with
        // no roles: focus sessions with no application).
        Source source(final List<Transform> sourceTransforms, final List<String> extraFields) {
            final boolean ids = mapping.containsKey(EqlSequenceResults.STREAM_ID)
                                && mapping.containsKey(EqlSequenceResults.EVENT_ID);
            final List<String> fetched = new ArrayList<>(extraFields);
            final Map<String, Varied> varied = varied();
            for (final Varied one : varied.values()) {
                one.fields().stream().filter(field -> !fetched.contains(field)).forEach(fetched::add);
            }
            for (final String thing : List.of(InstructionRules.SESSION_FIELD, TimelineFields.PROCESS_ID)) {
                NullSafe.consume(fieldOf(mapped, thing), field -> {
                    if (!fetched.contains(field)) {
                        fetched.add(field);
                    }
                });
            }
            return new Source(name, doc, user.field(), JoinKeys.kind(mapping.get(user.field()).nativeType()),
                    sourceTransforms, host, described.process(), described.title(), described.window(), object,
                    described.text(), ids, List.of(new RoleRead(Role.ACTION, null, null)), true, fetched, values,
                    by, otherUserFields(), varied);
        }

        // The other fields the feeding pipelines' plans put the user in, as keys of the same kind.
        private List<String> otherUserFields() {
            final List<String> out = new ArrayList<>();
            if (user == null || !user.varies()) {
                return out;
            }
            final JoinKeys.Kind kind = JoinKeys.kind(mapping.get(user.field()).nativeType());
            for (final String field : user.fields()) {
                final String key = userKey(field, mapping);
                if (key != null && !key.equals(user.field()) && !out.contains(key)
                    && JoinKeys.kind(mapping.get(key).nativeType()) == kind) {
                    out.add(key);
                }
            }
            return out;
        }

        // The things the feeding pipelines' plans put in different fields (the user aside, read by each of its
        // fields), each with its field per pipeline.
        private Map<String, Varied> varied() {
            final Map<String, Varied> out = new LinkedHashMap<>();
            mapped.forEach((thing, found) -> {
                if (!InstructionRules.USER_FIELD.equals(thing) && found.varies()) {
                    final Map<String, String> byPipeline = new LinkedHashMap<>();
                    found.byPipeline().forEach((field, pipelines) -> pipelines.forEach(pipeline ->
                            byPipeline.putIfAbsent(pipeline, field)));
                    out.put(thing, new Varied(found.field(), byPipeline, schemaPaths(thing)));
                }
            });
            return out;
        }

        Fields fields(final String detectedElement) {
            return new Fields(element != null
                    ? element
                    : detectedElement, action, eventType, fieldOf(mapped, TimelineFields.PROCESS_ID));
        }
    }


    // --------------------------------------------------------------------------------


    // The fields the call names for an automatic source's events' application, window title, window and text, each
    // null if not named.
    private record Described(String process, String title, String window, String text) {

    }


    // --------------------------------------------------------------------------------


    // What a probe found: the field holding the EventDetail element, or null.
    private record Probed(String element) {

    }


    // --------------------------------------------------------------------------------


    // Whose timeline: a person id (or null), the logon ids, and the entity given (or null).
    private record Who(String person, List<String> logons, String entity) {

        // How the result names them: the person id, else the entity given (its other written forms in logonIds
        // are its own accounts, each counted), else the logon ids.
        String label() {
            if (person != null) {
                return person;
            }
            return entity != null
                    ? entity
                    : String.join(", ", logons);
        }

        // True for one user given alone, as before persons: tried again without its domain and in lower case.
        boolean single() {
            return person == null && logons.size() == 1;
        }
    }


    // --------------------------------------------------------------------------------


    // The Events XML read, and its summary.
    private record XmlOutcome(Map<String, String> xml, XmlRead summary, List<XmlPaths> paths) {

    }
}
