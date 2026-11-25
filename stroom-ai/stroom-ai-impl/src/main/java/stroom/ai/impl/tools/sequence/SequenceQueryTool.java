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

package stroom.ai.impl.tools.sequence;

import stroom.ai.api.ElasticEql;
import stroom.ai.api.ElasticEql.Refusal;
import stroom.ai.api.ElasticEql.Result;
import stroom.ai.api.ElasticEql.Target;
import stroom.ai.api.ElasticEsql;
import stroom.ai.api.ElasticKeyStreams;
import stroom.ai.api.ElasticKeyStreams.KeyField;
import stroom.ai.api.ElasticKeyStreams.Transform;
import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.McpElicitations;
import stroom.ai.impl.tools.AbstractWorkspaceTool;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolDescription;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolResource;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema;
import stroom.ai.impl.tools.ToolSchema.ArraySchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.ToolTables;
import stroom.ai.impl.tools.ToolUserAction;
import stroom.ai.impl.tools.UserQuestions;
import stroom.ai.impl.tools.query.AgentSearches;
import stroom.ai.impl.tools.query.DataSourceAccess;
import stroom.ai.impl.tools.query.EsqlQueryTool;
import stroom.ai.impl.tools.query.IndexEvents;
import stroom.ai.impl.tools.query.StroomQlDatePoints;
import stroom.ai.impl.tools.sequence.EqlQueryWriter.Filter;
import stroom.ai.impl.tools.sequence.EqlQueryWriter.Step;
import stroom.ai.impl.tools.sequence.EqlSequenceResults.Entity;
import stroom.ai.impl.tools.sequence.EqlSequenceResults.EventCount;
import stroom.ai.impl.tools.sequence.EqlSequenceResults.Review;
import stroom.ai.impl.tools.sequence.EqlSequenceResults.Row;
import stroom.ai.impl.tools.sequence.KeyStreamSequence.JoinStep;
import stroom.ai.impl.tools.time.ToolTimeZone;
import stroom.ai.impl.tools.time.UserTimeZones;
import stroom.ai.impl.tools.timeline.ActivityTimelineTool;
import stroom.ai.impl.tools.timeline.PersonScope;
import stroom.ai.impl.tools.timeline.PersonScope.Scope;
import stroom.ai.impl.tools.timeline.TimelineFields;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.ai.shared.AiToolCategory;
import stroom.dashboard.shared.ValidateExpressionResult;
import stroom.docref.DocRef;
import stroom.index.shared.LuceneIndexDoc;
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
import stroom.util.shared.NullSafe;
import stroom.util.shared.PermissionException;

import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;

/// `sequence_query` - an ordered hunt across sources at volume. The engine follows the data sources: over one or
/// more Elastic Index docs on one cluster, one EQL sequence in Elasticsearch ([ElasticEql]); over any other data
/// source, a StroomQL windowed join of two steps run in Stroom ([StroomQlSequence]), with what it can't do refused
/// and the result saying which engine ran and why. E.g. a device plugged in and then a file copied to it, a
/// clipboard copy then PowerShell within 2 minutes by the same user on the same host, 20 failed logons then a
/// success. The model gives structured steps, never EQL: each with its doc, conditions (the `search_events` `where`
/// form, plus case-insensitive matching), its own join fields, and whether it must not happen (`missing`) or must
/// happen N times in a row (`runs`); an optional `until` step; and the window (`maxspan`), which the user is asked
/// for in a form when the model doesn't give it (refused over MCP, with the question to ask).
///
/// Before it runs, the tool counts each step's events up to [#COUNT_CAP] (an ES|QL `COUNT` per step, where the
/// cluster takes ES|QL, unless the time range is short): it says when a later step is far more selective than the
/// first, and refuses, before running, a sequence whose first step passes the cap ([#preflight]). The result gives
/// the EQL run, the window used, a summary per entity (join keys), the sequences as a workspace table (each step's
/// time, the gaps, chosen fields and each step's StreamId/EventId), the caveat that the events come straight from
/// Elasticsearch, and a validated StroomQL review query per doc ([EqlSequenceResults#review]).
public class SequenceQueryTool extends AbstractWorkspaceTool {

    static final String NAME = "sequence_query";
    static final String STEPS_ARG = "steps";
    static final String UNTIL_ARG = "until";
    static final String MAXSPAN_ARG = "maxspan";
    static final String TIME_FROM_ARG = "timeFrom";
    static final String TIME_TO_ARG = "timeTo";
    static final String LIMIT_ARG = "limit";
    static final String RUN_ANYWAY_ARG = "runAnyway";
    static final String INDEX_KEY = "index";
    public static final String WHERE_KEY = "where";
    static final String BY_KEY = "by";
    static final String MISSING_KEY = "missing";
    static final String RUNS_KEY = "runs";
    static final String FIELDS_KEY = "fields";
    public static final String CASE_INSENSITIVE_KEY = "caseInsensitive";
    public static final String ANY_OF_KEY = "any_of";
    static final String LENGTH_KEY = "length";
    static final String KEYS_ONLY_KEY = "keysOnly";
    static final String TRANSFORM_KEY = "transform";
    static final String ROLE_KEY = "role";
    static final String ACTOR_ROLE = "actor";
    static final String TARGET_ROLE = "target";
    static final String ENGINE_ARG = "engine";
    static final String PERSON_ARG = "person";
    static final String LOGON_IDS_ARG = "logonIds";
    static final String LOGON_FORMS_ARG = PersonScope.LOGON_FORMS_ARG;
    static final String PERSON_FIELD_ARG = "personField";
    static final String USER_FIELD_ARG = "userField";
    static final String ENGINE_AUTO = "auto";
    static final String ENGINE_EQL = "eql";
    static final String ENGINE_STREAM = "stream";
    /// Why the streaming engine ran when it was asked for.
    static final String STREAM_ASKED = "engine=stream was asked for, so the sequence ran in Stroom as a streaming "
                                       + "join: each step read from Elasticsearch in join-key order and merged.";
    /// Why the streaming engine ran for a step EQL can't take.
    static final String STREAM_NEEDED = "A step is read as keys (keysOnly) or transforms its join fields, which EQL "
                                        + "can't do, so the sequence ran in Stroom as a streaming join: each step "
                                        + "read from Elasticsearch in join-key order and merged.";
    /// Why the streaming engine ran for docs with different time fields.
    static final String STREAM_TIME_FIELDS = "The docs have different time fields, which one EQL sequence can't "
                                             + "order by, so the sequence ran in Stroom as a streaming join, each "
                                             + "doc's events in order of its own time field.";
    /// The negated list condition, which only this tool takes: `not field in (...)`.
    static final String NOT_IN = "NOT_IN";
    /// The sequences returned when the call doesn't say.
    static final int DEFAULT_LIMIT = 1_000;
    /// The most fields of a step's events shown.
    static final int MAX_STEP_FIELDS = 5;
    /// The most events a step's pre-flight count reads: a step past it is "large", and a first step past it is
    /// refused before the sequence runs (unless `runAnyway`), as the sequence reads every one of its events.
    static final long COUNT_CAP = 1_000_000;
    /// A window this short or shorter isn't counted first: the sequence is cheap enough to just run.
    static final Duration SHORT_WINDOW = Duration.ofHours(1);
    /// How many times more events the first step must match than a later one to suggest putting the later first.
    static final long SELECTIVITY_FACTOR = 10;
    /// The fewest events the first step must match to suggest it, as below this any order is quick.
    static final long SELECTIVITY_MIN_EVENTS = 1_000;
    /// The sequences and entities shown; the rest are in the table.
    static final int SHOWN_ROWS = 20;
    /// The form's key for the window, so the call made again with the answer gets it.
    static final String MAXSPAN_FORM = "maxspan";
    static final String MAXSPAN_QUESTION = "Within what time of each other must the steps happen?";
    static final String WIDER_NOTE = "A wider window finds more pairs, mostly harmless, and runs slower.";
    static final String ANOTHER_INTERVAL = "Another interval (type it, e.g. 10m)";
    static final String NARROW = "narrow the window or the first step";
    /// What every result with sequences says of their number: EQL doesn't find every overlapping match.
    static final String AT_LEAST = "Elasticsearch doesn't find every overlapping match, so these are at least the "
                                   + "sequences there are, not an exact total.";
    /// What a result with no sequences across several docs adds: the join values may differ between them.
    static final String NO_MATCH_ACROSS_DOCS = "The join fields must hold the same values in each doc: a user may "
                                               + "be DOMAIN\\name in one and the bare name or an email in another. "
                                               + "Check with esql_query on each doc (STATS COUNT(*) BY <field> | "
                                               + "LIMIT 5), or join on fields that match, e.g. host.";
    static final String CAVEAT = "The sequences come straight from Elasticsearch, as esql_query's counts do: they "
                                 + "may include events of streams the user can't see or that have been deleted, so "
                                 + "a Stroom search may find fewer.";
    /// What a sequence that timed out adds: EQL keeps a partial sequence per distinct join value, so very many of
    /// them make it slow. Measured on 1 million synthetic events on one node (2026-11-20, `TestBenchSequenceJoins`):
    /// 100,000 first-step events by 63,000 distinct users timed out at 119 seconds, where by 100 users they took 6.
    /// Why a sequence of Elastic Index steps runs as EQL.
    static final String EQL_REASON = "Every step's data source is an Elastic Index, so the sequence ran in "
                                     + "Elasticsearch as one EQL sequence.";
    /// What the result says of the same sequence as a StroomQL windowed join.
    static final String STROOMQL_NOTE = "The same sequence as StroomQL, a windowed join that Stroom runs itself "
                                        + "(valid StroomQL, to save as a Query with propose_query; it gives a row "
                                        + "per step 1 and step 2 pair within the window, where EQL gives each "
                                        + "match once):";
    static final String MANY_KEYS_NOTE = "EQL is slowest with very many distinct join values (it keeps a partial "
                                         + "sequence for each): narrow the first step to fewer entities, or use a "
                                         + "shorter time range.";
    /// The most events Elasticsearch fetches by id for one search's sequences (`index.max_result_window`, 10,000 by
    /// default): asking for more sequences than fit is refused ("Too many ids specified, allowed max result window
    /// is [10000]", seen in the benchmark of 2026-11-20 with 10,000 two-step sequences), so the limit is lowered to
    /// what fits ([#mostSequences]).
    static final int MAX_FETCHED_EVENTS = 10_000;
    /// The join-field transforms a step takes, as the model names them.
    public static final List<String> TRANSFORMS = Arrays.stream(Transform.values())
            .map(transform -> transform.name().toLowerCase(Locale.ROOT))
            .toList();
    private static final int[] ESQL_VERSION = {8, 14};
    private static final int[] MISSING_VERSION = {8, 8};
    private static final int[] RUNS_VERSION = {8, 7};
    private static final String FIELDS_HINT = "describe_data_source gives the index's fields";
    private static final Set<String> TEXT_NATIVE_TYPES = Set.of("text", "match_only_text");
    // The types a text field's subfield may have for EQL to search it in its place, the most fitting first.
    private static final List<String> SUBFIELD_TYPES = List.of("keyword", "constant_keyword", "wildcard");

    private final Provider<ElasticEql> elasticEqlProvider;
    private final Provider<ElasticEsql> elasticEsqlProvider;
    private final DataSourceAccess dataSourceAccess;
    private final Provider<QueryService> queryServiceProvider;
    private final AiAuditLog auditLog;
    private final StroomQlSequence stroomQlSequence;
    private final KeyStreamSequence keyStreamSequence;
    private final TimelineFields timelineFields;

    @Inject
    SequenceQueryTool(final ChatWorkspace workspace,
                      final Provider<ElasticEql> elasticEqlProvider,
                      final Provider<ElasticEsql> elasticEsqlProvider,
                      final DataSourceAccess dataSourceAccess,
                      final Provider<QueryService> queryServiceProvider,
                      final AiAuditLog auditLog,
                      final AgentSearches agentSearches,
                      final KeyStreamSequence keyStreamSequence,
                      final TimelineFields timelineFields) {
        super(workspace);
        this.elasticEqlProvider = elasticEqlProvider;
        this.elasticEsqlProvider = elasticEsqlProvider;
        this.dataSourceAccess = dataSourceAccess;
        this.queryServiceProvider = queryServiceProvider;
        this.auditLog = auditLog;
        this.stroomQlSequence = new StroomQlSequence(queryServiceProvider, agentSearches, dataSourceAccess);
        this.keyStreamSequence = keyStreamSequence;
        this.timelineFields = timelineFields;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .checksDocuments(DocumentPermission.USE)
                .note("Use on every data source is checked, as for any search: for Elastic Indexes again by "
                      + "Stroom's Elasticsearch module when the EQL runs, and for other data sources by Stroom's "
                      + "query service when the StroomQL windowed join runs, as for run_stroomql.")
                .build();
    }

    /// Offered in the chat to a user who can use a data source: an Elastic Index (EQL) or any other (a StroomQL
    /// windowed join), the two index types looked at first as listing every data source is slower. No cluster's
    /// version is looked up, so no cluster is called as a chat starts: EQL sequences work from Elasticsearch 7.10,
    /// older than the clusters Stroom's Elasticsearch client works with, and a call on an older one is still refused
    /// as it runs.
    ///
    /// @return True if the user can use at least one data source.
    @Override
    public boolean isUsable() {
        return dataSourceAccess.canUseAny(ElasticIndexDoc.TYPE)
               || dataSourceAccess.canUseAny(LuceneIndexDoc.TYPE)
               || !dataSourceAccess.listUsable().isEmpty();
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

    // The definition is full, as it is offered only once the model turns on its group (sequences, see
    // BuiltInToolGroups). The rules a call can break are also said by the refusal or result that meets them.
    @Override
    public ToolDefinition definition() {
        final ObjectSchema condition = ObjectSchema.builder()
                .description(ToolDescription.builder()
                        .general("One condition, as a person would set it on a dashboard")
                        .chatOnly(" (as search_events takes it)")
                        .general(".")
                        .build())
                .addStringProperty("field", "The doc's field.")
                .addEnumProperty("condition", SequenceSteps.conditionNames(), "The condition (default EQUALS).")
                .addStringProperty("value", "BETWEEN: 'from,to'; IN and NOT_IN: values separated by commas; "
                                            + "EQUALS takes * wildcards, and a CIDR (10.0.0.0/8) on an IP field.")
                .addBooleanProperty(CASE_INSENSITIVE_KEY, "True to match text whatever its case.")
                .addBooleanProperty(LENGTH_KEY, "True to compare the string's length in characters (a whole "
                                                + "number), e.g. GREATER_THAN 1000 for long command lines.")
                .required("field")
                .build();
        final ArraySchema anyOf = ArraySchema.builder().items(condition)
                .description("Optional: 2 or more conditions of which at least one must hold (written as one OR "
                             + "group, beside where's).")
                .build();
        final ObjectSchema step = ObjectSchema.builder()
                .description("One step.")
                .addStringProperty(INDEX_KEY, "The data source's uuid or name: an Elastic Index doc, or any other "
                                              + "(a Lucene index, a view).")
                .addProperty(WHERE_KEY, ArraySchema.builder().items(condition)
                        .description("Conditions its events meet, all of them.").build())
                .addProperty(ANY_OF_KEY, anyOf)
                .addProperty(BY_KEY, stringArraySchema("Its join fields, in the same order and number in every "
                                                       + "step (e.g. [\"user.name\", \"host.name\"] in one, "
                                                       + "[\"SubjectUserName\", \"Computer\"] in another)."))
                .addBooleanProperty(MISSING_KEY, "True if the event must NOT happen here (and then NOT ...).")
                .addIntegerProperty(RUNS_KEY, "How many times in a row it must happen, e.g. 20 failed logons "
                                              + "(default 1, at most " + EqlQueryWriter.MAX_RUNS + ").")
                .addProperty(FIELDS_KEY, stringArraySchema("Up to " + MAX_STEP_FIELDS + " fields of its events to "
                                                           + "show, beside the join fields and ids."))
                .addBooleanProperty(KEYS_ONLY_KEY, "True if the step only says which entities count, at any time "
                                                   + "(e.g. the users of team A in a directory index): read as keys, "
                                                   + "not in time order; with missing=true, entities NOT in it. "
                                                   + "Elastic Indexes only (the stream engine).")
                .addProperty(TRANSFORM_KEY, ArraySchema.builder()
                        .items(new ToolSchema.EnumSchema(null, TRANSFORMS))
                        .description("Optional: what is done to its join fields' values before they are compared, "
                                     + "in order, where sources write them differently: lowercase, strip_domain "
                                     + "(CORP\\jsmith and jsmith@corp are jsmith), file_name (a path's last part). "
                                     + "Elastic Indexes only (the stream engine).")
                        .build())
                .addEnumProperty(ROLE_KEY, List.of(ACTOR_ROLE, TARGET_ROLE), "With person or logonIds: actor (the "
                                                                             + "default) for their own acts; target "
                                                                             + "for acts done to them, by its doc's "
                                                                             + "target field (AGENTS 'target "
                                                                             + "person:', ECS user.target.id), "
                                                                             + "their own left out.")
                .required(INDEX_KEY, BY_KEY)
                .build();
        final ObjectSchema until = ObjectSchema.builder()
                .description("Optional: a step that ends a sequence before it is complete: its doc, conditions and "
                             + "join fields, as a step's (no missing, runs or fields).")
                .addStringProperty(INDEX_KEY, "The Elastic Index doc's uuid or name (Elastic Indexes only).")
                .addProperty(WHERE_KEY, ArraySchema.builder().items(condition)
                        .description("Conditions its events meet, all of them.").build())
                .addProperty(ANY_OF_KEY, anyOf)
                .addProperty(BY_KEY, stringArraySchema("Its join fields, in the same order and number as the "
                                                       + "steps'."))
                .required(INDEX_KEY, BY_KEY)
                .build();
        return ToolDefinition.builder()
                .name(NAME)
                .description(ToolDescription.builder()
                        .general("Use for any 'X, then Y, within N' question ('did anyone X and then Y within an "
                                 + "hour?': the same person, user or host doing both), in one data source or across "
                                 + "several, rather than joining the results of separate queries")
                        .chatOnly(" (esql_query, search_events, run_stroomql)")
                        .general(" by hand. The engine follows the data sources: on Elastic Index docs on one "
                                 + "cluster, one EQL sequence in Elasticsearch, with everything below; on any other "
                                 + "(a Lucene index, a view), a StroomQL windowed join run in Stroom, which takes 2 "
                                 + "steps joined by by (no until, missing or runs; no caseInsensitive, length, "
                                 + "IS_NULL or regex; step 2 needs a condition) and refuses a first step with too "
                                 + "many events; the result says which ran and why. On Elastic Indexes it runs "
                                 + "instead as a streaming join in Stroom (engine=stream; chosen itself where EQL "
                                 + "can't: a first step too big for EQL, different time fields, keysOnly or "
                                 + "transform): each step read in join-key order and merged, no side capped, sizes "
                                 + "estimated first; keysOnly=true for a step that only says which entities count "
                                 + "(e.g. a team's users in a directory), transform for join values sources write "
                                 + "differently (CORP\\jsmith vs jsmith); a join that finds nothing says why. "
                                 + "Finds ordered sequences of events: e.g. a USB "
                                 + "device plugged in then a file copied to it; a clipboard copy then PowerShell "
                                 + "within 2 minutes by the same user on the same host; 20 failed logons then a "
                                 + "success. Give steps (2 or more, in order), each with its doc, its where "
                                 + "conditions (all must hold; NOT_IN for none of a list; length=true to compare a "
                                 + "string's length), any_of (optional, at least one must hold), its own join "
                                 + "fields (by, same number and order in every step: the same entity in each doc's "
                                 + "own names), missing=true for 'and then NOT ...', runs=N for N in a row; until "
                                 + "for a step that ends a sequence. maxspan: how far apart the first and last "
                                 + "events may be, from the user's words ('within 2 minutes' is 2m), at most 24h")
                        .chatOnly("; if they didn't say, leave it out and the user is asked")
                        .general(". Fields are checked against each doc's")
                        .chatOnly(" (describe_data_source gives them)")
                        .general(". Each step is counted first: the result says when a later step is far more "
                                 + "selective, and a first step too big for EQL runs as the streaming join "
                                 + "(with engine=eql it is refused before it runs: narrow the window or the first "
                                 + "step, or runAnyway=true). The result gives the "
                                 + "EQL, a summary per entity, the sequences with each step's StreamId/EventId, and "
                                 + "a validated StroomQL review query per doc, for a saved query")
                        .chatOnly(" (propose_query)")
                        .general(" or a dashboard with the event preview")
                        .chatOnly(" (propose_dashboard)")
                        .general(". Each reads one doc: when the hunt spans several, ask the user which doc to "
                                 + "review")
                        .chatOnly(" (ask_user, the docs as options)")
                        .general(", then offer that doc's; on other data sources the windowed join itself is the "
                                 + "review query. EQL sequences come straight from Elasticsearch, as ES|QL counts "
                                 + "do, and may include streams the user can't see or deleted ones. Docs on "
                                 + "different clusters and nested fields are refused. "
                                 + "A text field is searched as its keyword subfield (the result says so). A literal "
                                 + "? can't be in a pattern (EQL reads it as any one character).")
                        .build())
                .parameters(ObjectSchema.builder()
                        .addProperty(STEPS_ARG, ArraySchema.builder().items(step)
                                .description("The steps, 2 or more, in order.").build())
                        .addProperty(UNTIL_ARG, until)
                        .addStringProperty(MAXSPAN_ARG, ToolDescription.builder()
                                .general("How far apart the first and last events may be, e.g. 30s, 2m, 1h (at "
                                         + "most 24h)")
                                .chatOnly("; leave it out if the user didn't say, and they are asked")
                                .general(".")
                                .build())
                        .addStringProperty(PERSON_ARG, "Optional: one person's sequences only: a person id, each "
                                                       + "step filtered on its doc's person field (personField, "
                                                       + "AGENTS ## Fields 'person:', ECS user.id), else on its "
                                                       + "user field by logonIds; the result says which.")
                        .addProperty(LOGON_IDS_ARG, stringArraySchema("Optional: the person's logon ids (all "
                                                                      + "kept), for docs with no person field, or "
                                                                      + "alone; matched exactly, so give each written "
                                                                      + "form (jsmith, CORP\\jsmith)."))
                        .addProperty(LOGON_FORMS_ARG, ArraySchema.builder()
                                .items(new ToolSchema.EnumSchema(null, PersonScope.LOGON_FORMS))
                                .description("Optional: only when the user (or a skill) says logons are written "
                                             + "with a domain (CORP\\jsmith) or as an email: strip_domain (both "
                                             + "forms), lowercase; applied to each step's user field read by "
                                             + "logonIds, a role=target step's target field too. Slower: a pattern "
                                             + "over every event in the period. Exact by default.")
                                .build())
                        .addStringProperty(PERSON_FIELD_ARG, "Optional: the field holding the person id (a "
                                                             + "skill's).")
                        .addStringProperty(USER_FIELD_ARG, "Optional: the field holding the logon id (a skill's).")
                        .addStringProperty(TIME_FROM_ARG, "Optional: from this time on the docs' time field (ISO "
                                                          + "8601, or Stroom's day()-7d).")
                        .addStringProperty(TIME_TO_ARG, "Optional: up to this time (ISO 8601, or Stroom's now()).")
                        .addStringProperty(UserTimeZones.ARG, UserTimeZones.DESCRIPTION)
                        .addIntegerProperty(LIMIT_ARG, "The most sequences (default " + DEFAULT_LIMIT + ", at most "
                                                       + ElasticEql.MAX_SEQUENCES + "; the stream engine: default "
                                                       + KeyStreamSequence.DEFAULT_LIMIT + ", at most "
                                                       + KeyStreamSequence.MAX_LIMIT + ", counting all).")
                        .addEnumProperty(ENGINE_ARG, List.of(ENGINE_AUTO, ENGINE_EQL, ENGINE_STREAM), "Optional: "
                                + "auto (default: chosen by the steps and their sizes), eql, or stream (a "
                                + "streaming join in Stroom, for sides too big for EQL or more than "
                                + ElasticEql.MAX_SEQUENCES + " sequences).")
                        .addBooleanProperty(RUN_ANYWAY_ARG, "True to run even though the counts say it won't finish "
                                                            + "in the time limit.")
                        .addStringProperty(OUT_ARG, ToolDescription.builder()
                                .chatOnly("A .md file in /outputs/ for the sequences (default "
                                          + "/outputs/eql/eql-<time>.md).")
                                .build())
                        .required(STEPS_ARG)
                        .build())
                .build();
    }

    @Override
    protected Duration timeLimit(final ToolCallContext context) {
        return limits(context).getQueryTimeLimit().getDuration();
    }

    /// The call as a whole may run as long as the streaming join's time limit, which the user may agree to past the
    /// query time limit; EQL, the counts and the StroomQL join keep the query time limit ([#timeLimit]).
    ///
    /// @param request The model's call.
    /// @param context The call's context.
    /// @return The longer of the two.
    @Override
    protected Duration timeLimit(final ToolCall request, final ToolCallContext context) {
        final Duration query = timeLimit(context);
        final Duration join = NullSafe.getOrElse(limits(context).getJoinTimeLimit(), duration -> duration.getDuration(),
                query);
        return join.compareTo(query) > 0
                ? join
                : query;
    }

    @Override
    protected ToolResult run(final ToolCall request, final ToolCallContext context) {
        final List<String> fieldNotes = new ArrayList<>();
        final List<GivenStep> given = SequenceSteps.givenSteps(request, fieldNotes);
        final GivenStep givenUntil = SequenceSteps.givenUntil(request, fieldNotes);
        final String person = stringArg(request, PERSON_ARG);
        final List<String> logonIds = stringListArg(request, LOGON_IDS_ARG);
        final List<Transform> logonForms = PersonScope.logonForms(argsMap(request).get(LOGON_FORMS_ARG));
        if (NullSafe.isBlankString(person) && logonIds.isEmpty()) {
            if (given.stream().anyMatch(GivenStep::target)) {
                throw new WorkspaceException("A step with " + ROLE_KEY + "=" + TARGET_ROLE + " is of acts done to "
                                             + "one person: give " + PERSON_ARG + " or " + LOGON_IDS_ARG + ".");
            }
            return runSteps(request, context, given, givenUntil, fieldNotes);
        }
        // One person's sequences: each step restricted to them in its doc, by person id or by logon id.
        final Map<String, Scope> scopes = new LinkedHashMap<>();
        final Map<String, PersonScope.Target> targets = new LinkedHashMap<>();
        // Each target doc's actor scope: how their own acts there are read, to leave them out.
        final Map<String, PersonScope.By> actorsBy = new LinkedHashMap<>();
        final List<GivenStep> scoped = new ArrayList<>();
        for (final GivenStep step : given) {
            scoped.add(step.target()
                    ? targeted(request, context, step, new Restriction(person, logonIds, logonForms), scopes,
                            targets, actorsBy)
                    : scoped(request, context, step, person, logonIds, logonForms, scopes));
        }
        final GivenStep scopedUntil = givenUntil == null
                ? null
                : scoped(request, context, givenUntil, person, logonIds, logonForms, scopes);
        final ToolResult result = runSteps(request, context, scoped, scopedUntil, fieldNotes);
        return withScopes(result, scopes, targets, actorsBy, logonForms);
    }

    // A step of acts done to the person: an IN condition on its doc's target person field, else its target user
    // field holding any of the logon ids; their own acts on themselves left out (NOT_IN on the field they are read
    // by as the actor, where the doc has one). Keys only, a step can't be: it has no events done to anyone. A
    // logon id is matched in its other written forms only as logonForms ask, on the actor scope's terms (decided by
    // the user, 2026-11-25): the target field and the actor's own acts left out alike.
    private GivenStep targeted(final ToolCall request,
                               final ToolCallContext context,
                               final GivenStep step,
                               final Restriction restriction,
                               final Map<String, Scope> scopes,
                               final Map<String, PersonScope.Target> targets,
                               final Map<String, PersonScope.By> actorsBy) {
        final String person = restriction.person();
        final List<String> logonIds = restriction.logonIds();
        if (step.keysOnly()) {
            throw new WorkspaceException("A keysOnly step says which entities count, so it can't be " + ROLE_KEY
                                         + "=" + TARGET_ROLE + ".");
        }
        final DocRef doc = dataSource(step.index());
        final PersonScope.Target target = targets.computeIfAbsent(doc.getName(), name -> {
            final Set<String> names = new LinkedHashSet<>();
            EsqlQueryTool.fields(queryServiceProvider.get(), doc).forEach(field -> names.add(field.getFldName()));
            final Scope actor = scopes.containsKey(name)
                    ? scopes.get(name)
                    : PersonScope.resolve(timelineFields, context.session(), doc, person, logonIds,
                            stringListArg(request, PERSON_FIELD_ARG), stringListArg(request, USER_FIELD_ARG),
                            names::contains);
            final PersonScope.Target found = PersonScope.target(timelineFields, context.session(), doc, person,
                    logonIds, List.of(), List.of(), actor, names::contains);
            if (!found.usable()) {
                throw new WorkspaceException(DataSourceAccess.describe(doc) + " can't be restricted to acts done to "
                                             + "the person: " + found.leftOut() + ".");
            }
            if (actor != null && actor.usable()) {
                actorsBy.put(name, actor.by());
            }
            return found;
        });
        final List<Transform> forms = restriction.logonForms();
        final List<Filter> filters = new ArrayList<>(NullSafe.list(step.filters()));
        filters.add(personFilter(target.field().field(), target.values(), target.by() == PersonScope.By.LOGON
                ? forms
                : List.of()));
        if (target.actorField() != null) {
            final Filter own = personFilter(target.actorField(), target.actorValues(),
                    actorsBy.get(doc.getName()) == PersonScope.By.LOGON
                            ? forms
                            : List.of());
            filters.add(new Filter(own.field(), own.condition(), own.value(), own.caseInsensitive(), true, false));
        }
        return new GivenStep(step.index(), filters, step.anyOf(), step.by(), step.missing(), step.runs(),
                step.fields(), step.keysOnly(), step.transforms(), true);
    }

    // A step restricted to the person: an IN condition on its doc's person field, else its user field holding any
    // of the logon ids. A step read as keys (an entity condition at any time, e.g. a directory) is left as given.
    private GivenStep scoped(final ToolCall request,
                             final ToolCallContext context,
                             final GivenStep step,
                             final String person,
                             final List<String> logonIds,
                             final List<Transform> logonForms,
                             final Map<String, Scope> scopes) {
        if (step.keysOnly()) {
            return step;
        }
        final DocRef doc = dataSource(step.index());
        final Scope scope = scopes.computeIfAbsent(doc.getName(), name -> {
            final Set<String> names = new LinkedHashSet<>();
            EsqlQueryTool.fields(queryServiceProvider.get(), doc).forEach(field -> names.add(field.getFldName()));
            final Scope found = PersonScope.resolve(timelineFields, context.session(), doc, person, logonIds,
                    stringListArg(request, PERSON_FIELD_ARG), stringListArg(request, USER_FIELD_ARG),
                    names::contains);
            if (!found.usable()) {
                throw new WorkspaceException(DataSourceAccess.describe(doc) + " can't be restricted to the person: "
                                             + found.leftOut() + ". Give " + PERSON_FIELD_ARG + " or "
                                             + USER_FIELD_ARG + " (with " + LOGON_IDS_ARG + "), or leave the "
                                             + "step's doc out.");
            }
            return found;
        });
        final List<Filter> filters = new ArrayList<>(NullSafe.list(step.filters()));
        filters.add(personFilter(scope.field().field(), scope.values(), scope.by() == PersonScope.By.LOGON
                ? logonForms
                : List.of()));
        return new GivenStep(step.index(), filters, step.anyOf(), step.by(), step.missing(), step.runs(),
                step.fields(), step.keysOnly(), step.transforms());
    }

    /// The condition restricting a step to the person: their values, exactly by default; with logonForms (only as
    /// the user asks), each logon id in its other written forms: lower case as any case, and a domain stripped as
    /// a pattern of the id with or without a domain before it (`CORP\`) or after it (`@corp.example`).
    ///
    /// @param field  The field filtered on.
    /// @param values The person id, or their logon ids.
    /// @param forms  The logonForms; empty to match exactly.
    /// @return The condition.
    static Filter personFilter(final String field, final List<String> values, final List<Transform> forms) {
        if (NullSafe.isEmptyCollection(forms)) {
            return new Filter(field, Condition.IN, String.join(",", values), false);
        }
        final List<String> transformed = values.stream()
                .map(value -> Transform.applyAll(forms, value))
                .filter(NullSafe::isNonBlankString)
                .distinct()
                .toList();
        final boolean anyCase = forms.contains(Transform.LOWERCASE);
        if (!forms.contains(Transform.STRIP_DOMAIN)) {
            return new Filter(field, Condition.IN, String.join(",", transformed), anyCase);
        }
        return new Filter(field, Condition.MATCHES_REGEX, "(.*\\\\)?(" + String.join("|", transformed.stream()
                .map(SequenceQueryTool::regexLiteral)
                .toList()) + ")(@.*)?", anyCase);
    }

    // The text as a literal in a Lucene regular expression (Elasticsearch's and EQL's): every character that
    // isn't a letter or digit escaped.
    private static String regexLiteral(final String text) {
        final StringBuilder sb = new StringBuilder();
        for (final char c : text.toCharArray()) {
            if (!Character.isLetterOrDigit(c)) {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static void addOnce(final List<String> list, final String value) {
        if (!list.contains(value)) {
            list.add(value);
        }
    }

    // The result, saying by what each doc was restricted to the person.
    private static ToolResult withScopes(final ToolResult result,
                                         final Map<String, Scope> scopes,
                                         final Map<String, PersonScope.Target> targets,
                                         final Map<String, PersonScope.By> actorsBy,
                                         final List<Transform> logonForms) {
        if (result.isError() || (scopes.isEmpty() && targets.isEmpty())) {
            return result;
        }
        final List<Map<String, Object>> listed = new ArrayList<>();
        final StringBuilder sb = new StringBuilder(NullSafe.string(result.text())).append("\n\nRestricted to the "
                                                                                          + "person:");
        boolean byLogon = false;
        final List<String> suggested = new ArrayList<>();
        for (final Map.Entry<String, Scope> entry : scopes.entrySet()) {
            final Scope scope = entry.getValue();
            byLogon |= scope.by() == PersonScope.By.LOGON;
            NullSafe.consume(scope.field().suggestion(), line -> addOnce(suggested, line));
            final String matched = scope.by() == PersonScope.By.LOGON && !logonForms.isEmpty()
                    ? "matched with " + PersonScope.describe(logonForms) + ": " + PersonScope.SCRIPTED
                    : null;
            final Map<String, Object> one = new LinkedHashMap<>(Map.of("index", entry.getKey(), "by",
                    scope.by().label(), "field", scope.field().field(), "field_from", scope.field().from(),
                    "values", scope.values()));
            NullSafe.consume(matched, text -> one.put("matched", text));
            listed.add(one);
            sb.append("\n- ").append(entry.getKey()).append(": ").append(scope.by().label()).append(", ")
                    .append(scope.field().field()).append(" in (").append(String.join(", ", scope.values()))
                    .append(")").append(matched == null
                            ? ""
                            : ", " + matched);
        }
        for (final Map.Entry<String, PersonScope.Target> entry : targets.entrySet()) {
            final PersonScope.Target target = entry.getValue();
            NullSafe.consume(target.field().suggestion(), line -> addOnce(suggested, line));
            final boolean formsApplied = !logonForms.isEmpty() && (target.by() == PersonScope.By.LOGON
                                                                   || actorsBy.get(entry.getKey())
                                                                      == PersonScope.By.LOGON);
            final String matched = formsApplied
                    ? "matched with " + PersonScope.describe(logonForms) + ": " + PersonScope.SCRIPTED
                    : null;
            final Map<String, Object> one = new LinkedHashMap<>(Map.of("index", entry.getKey(), "role",
                    TARGET_ROLE, "by", target.describe(), "field", target.field().field(), "field_from",
                    target.field().from(), "values", target.values()));
            NullSafe.consume(matched, text -> one.put("matched", text));
            listed.add(one);
            sb.append("\n- ").append(entry.getKey()).append(" (").append(TARGET_ROLE).append(" steps): ")
                    .append(target.describe()).append(", ").append(target.field().field()).append(" in (")
                    .append(String.join(", ", target.values())).append(")").append(matched == null
                            ? ""
                            : ", " + matched);
        }
        if (byLogon) {
            sb.append("\n").append(PersonScope.OTHER_ACCOUNTS);
        }
        if (!suggested.isEmpty()) {
            sb.append("\nFields found by a convention or the documentation (offer to make them the site's): ")
                    .append(String.join(" ", suggested));
        }
        final Map<String, Object> structured = new LinkedHashMap<>(NullSafe.map(result.structuredContent()));
        structured.put("scope", listed);
        if (!suggested.isEmpty()) {
            structured.put("fields_lines_to_offer", suggested);
        }
        if (byLogon) {
            structured.put("other_accounts", PersonScope.OTHER_ACCOUNTS);
        }
        final String text = result.text() != null && result.text().stripLeading().startsWith("{")
                ? JsonUtil.writeValueAsString(structured, false)
                : sb.toString();
        ToolResult out = ToolResult.text(text).withStructuredContent(structured);
        for (final ToolResource resource : result.resources()) {
            out = out.withResource(resource);
        }
        for (final ToolUserAction action : result.userActions()) {
            out = out.withUserAction(action);
        }
        return result.chatOutline() == null
                ? out
                : out.withChatOutline(result.chatOutline());
    }

    // Runs the steps given (restricted to a person where the call names one).
    private ToolResult runSteps(final ToolCall request,
                                final ToolCallContext context,
                                final List<GivenStep> given,
                                final GivenStep givenUntil,
                                final List<String> fieldNotes) {
        final SessionWorkspace workspace = sessionWorkspace(context);
        final Duration timeLimit = timeLimit(context);
        final Instant deadline = Instant.now().plus(timeLimit);

        // The docs, in the order the steps first name them, each checked and with its fields.
        final List<DocRef> docs = new ArrayList<>();
        final Map<String, DocRef> byGiven = new LinkedHashMap<>();
        final List<GivenStep> all = new ArrayList<>(given);
        if (givenUntil != null) {
            all.add(givenUntil);
        }
        for (final GivenStep step : all) {
            byGiven.computeIfAbsent(step.index(), name -> {
                final DocRef doc = dataSource(name);
                if (!docs.contains(doc)) {
                    docs.add(doc);
                }
                return doc;
            });
        }
        // The zone the time range's date expressions and times without an offset, and the results' times, are in.
        final ToolTimeZone zone = timeZone(request, context);
        final long referenceTime = System.currentTimeMillis();
        final String engine = engine(request);
        final boolean needsStream = all.stream().anyMatch(GivenStep::needsStream);
        final Optional<DocRef> notElastic = docs.stream().filter(doc -> !isElastic(doc)).findFirst();
        if (notElastic.isPresent()) {
            if (needsStream || ENGINE_STREAM.equals(engine)) {
                throw new WorkspaceException(DataSourceAccess.describe(notElastic.get()) + " isn't an Elastic Index: "
                                             + KEYS_ONLY_KEY + ", " + TRANSFORM_KEY + " and engine=stream need every "
                                             + "step on an Elastic Index.");
            }
            return runInStroom(request, context, given, givenUntil, byGiven, notElastic.get(), zone, referenceTime);
        }
        if (needsStream && ENGINE_EQL.equals(engine)) {
            throw new WorkspaceException(KEYS_ONLY_KEY + " and " + TRANSFORM_KEY + " need the stream engine, which EQL "
                                         + "can't do: leave engine out, or give engine=stream.");
        }
        final Map<DocRef, List<QueryField>> fieldsByDoc = new LinkedHashMap<>();
        for (final DocRef doc : docs) {
            final List<QueryField> fields = EsqlQueryTool.fields(queryServiceProvider.get(), doc);
            if (fields.isEmpty()) {
                throw new WorkspaceException("The fields of Elastic Index '" + doc.getName() + "' can't be read, so "
                                             + "the steps' fields can't be checked. " + FIELDS_HINT + ".");
            }
            fieldsByDoc.put(doc, fields);
        }
        if (needsStream || ENGINE_STREAM.equals(engine)) {
            return runStreamed(request, context, given, givenUntil, byGiven, docs, fieldsByDoc, fieldNotes, zone,
                    referenceTime, needsStream
                            ? STREAM_NEEDED
                            : STREAM_ASKED);
        }
        final Target target;
        try {
            target = elasticEqlProvider.get().target(docs);
        } catch (final Refusal refusal) {
            if (ENGINE_AUTO.equals(engine) && refusal.reason() == ElasticEql.Reason.DIFFERENT_TIME_FIELDS) {
                return runStreamed(request, context, given, givenUntil, byGiven, docs, fieldsByDoc, fieldNotes, zone,
                        referenceTime, STREAM_TIME_FIELDS);
            }
            throw new WorkspaceException(refusal(refusal, Map.of()));
        } catch (final PermissionException e) {
            throw new WorkspaceException(notUsable(docs, e));
        }
        final List<Step> steps = new ArrayList<>();
        for (int i = 0; i < given.size(); i++) {
            steps.add(step(given.get(i), "Step " + (i + 1), byGiven, docs, fieldsByDoc, target, fieldNotes, zone,
                    referenceTime));
        }
        final Step until = givenUntil == null
                ? null
                : step(givenUntil, "The until step", byGiven, docs, fieldsByDoc, target, fieldNotes, zone,
                        referenceTime);
        checkJoins(steps, until);
        checkVersion(target.version(), steps);
        if (docs.size() > 1) {
            steps.replaceAll(step -> step.withRestricted(needsRestriction(step, docs, fieldsByDoc)));
        }
        final Step untilStep = until == null || docs.size() == 1
                ? until
                : until.withRestricted(needsRestriction(until, docs, fieldsByDoc));

        final String givenMaxspan = stringArg(request, MAXSPAN_ARG);
        final Duration maxspan = givenMaxspan != null
                ? EqlQueryWriter.maxspan(givenMaxspan)
                : askMaxspan(request, context);
        final String maxspanSource = maxspanSource(givenMaxspan);
        final TimeRange timeRange = timeRange(request, zone, referenceTime);
        final Integer givenLimit = intArg(request, LIMIT_ARG);
        final int most = mostSequences(steps);
        final int asked = Objects.requireNonNullElse(givenLimit, DEFAULT_LIMIT);
        final int limit = Math.clamp(asked, 1, most);
        final String limitNote;
        if (givenLimit != null && givenLimit > ElasticEql.MAX_SEQUENCES) {
            limitNote = "The limit asked for was over " + ElasticEql.MAX_SEQUENCES + ", the most allowed, so it was "
                        + "lowered" + (most < ElasticEql.MAX_SEQUENCES
                    ? " to " + most + ": " + fetchedNote(steps)
                    : ".");
        } else if (asked > most) {
            limitNote = "The limit was lowered to " + most + ": " + fetchedNote(steps);
        } else {
            limitNote = null;
        }
        final String eql = EqlQueryWriter.eql(steps, untilStep, maxspan, docs.size());

        final Preflight preflight = preflight(steps, docs, timeRange, target.version(), deadline, timeLimit,
                context.stopped());
        if (preflight.tooBig() && !booleanArg(request, RUN_ANYWAY_ARG, false)) {
            if (ENGINE_AUTO.equals(engine)) {
                return runStreamed(request, context, given, givenUntil, byGiven, docs, fieldsByDoc, fieldNotes, zone,
                        referenceTime, "As one EQL sequence, " + preflight.tooBigNote() + " So the sequence ran in "
                                       + "Stroom as a streaming join instead: each step read from Elasticsearch in "
                                       + "join-key order and merged.");
            }
            throw new WorkspaceException("Not run: " + preflight.tooBigNote() + " " + capitalised(NARROW)
                                         + " (fewer events in the first step's conditions), or call again with "
                                         + RUN_ANYWAY_ARG + "=true to run it anyway.\n\nThe EQL would be:\n" + eql);
        }

        final List<String> fetched = fetchedFields(steps, until);
        final Result result;
        try {
            result = elasticEqlProvider.get().search(new ElasticEql.Request(docs, eql, timeRange, fetched, limit,
                    remaining(deadline, timeLimit), context.stopped()));
        } catch (final Refusal refusal) {
            if (refusal.reason() == ElasticEql.Reason.STOPPED) {
                // The user stopped the chat, and the search was deleted in Elasticsearch: the run ends stopped.
                throw new TaskTerminatedException();
            }
            throw new WorkspaceException(refusal(refusal, fieldsByDoc));
        } catch (final PermissionException e) {
            throw new WorkspaceException(notUsable(docs, e));
        }

        final List<Row> rows = EqlSequenceResults.rows(result.sequences(), steps, result.timeField());
        for (int d = 0; d < docs.size(); d++) {
            final EventCount count = EqlSequenceResults.eventCount(rows, steps, d + 1);
            auditLog.searched(context.session(), docs.get(d), eql + searchedNote(timeRange, maxspan, preflight),
                    (long) count.events(), "EQL sequence by the " + NAME + " tool");
        }
        final WorkspacePath givenOut = outArg(request);
        final WorkspacePath out = givenOut != null
                ? givenOut
                : workspace.newFile("/outputs/eql/eql", ".md");
        final List<String> columns = EqlSequenceResults.columns(steps);
        final List<List<String>> cells = rows.stream()
                .map(row -> EqlSequenceResults.cells(row, steps).stream().map(zone::inZone).toList())
                .toList();
        if (!cells.isEmpty()) {
            ToolTables.write(workspace, out, columns, cells, docs.getFirst().getUuid());
        }
        final List<Entity> entities = EqlSequenceResults.entities(rows);
        final List<ReviewNote> reviews = reviews(docs, fieldsByDoc, result.timeField(), rows, steps,
                limits(context).getMaxQueryLength());
        final boolean truncated = result.sequences().size() >= limit;
        final String stroomQl = equivalentStroomQl(steps, untilStep, docs, fieldsByDoc, result.timeField(), maxspan,
                timeRange, limit);
        final Outcome outcome = new Outcome(docs, result, maxspan, maxspanSource, timeRange, limit, limitNote,
                truncated, preflight, steps, columns, cells, entities, EqlSequenceResults.untimed(rows), reviews, out,
                fieldNotes.stream().distinct().toList(), stroomQl, stroomQl == null
                        ? null
                        : invalid(stroomQl), zone);
        final Map<String, Object> structured = SequenceResultText.structured(outcome);
        return context.session().isChat()
                ? ToolResult.text(SequenceResultText.markdown(outcome)).withStructuredContent(structured)
                : ToolResult.text(JsonUtil.writeValueAsString(structured, false)).withStructuredContent(structured);
    }

    // Runs the sequence in Stroom, as a StroomQL windowed join, for steps not all on Elastic Indexes.
    private ToolResult runInStroom(final ToolCall request,
                                   final ToolCallContext context,
                                   final List<GivenStep> given,
                                   final GivenStep givenUntil,
                                   final Map<String, DocRef> byGiven,
                                   final DocRef notElastic,
                                   final ToolTimeZone zone,
                                   final long referenceTime) {
        final String reason = DataSourceAccess.describe(notElastic) + " isn't an Elastic Index, so the sequence ran "
                              + "in Stroom as a StroomQL windowed join (two steps, joined by their by fields) "
                              + "rather than in Elasticsearch as EQL.";
        final StroomQlSequence.Plan plan = stroomQlSequence.plan(given, givenUntil, byGiven, reason);
        final String givenMaxspan = stringArg(request, MAXSPAN_ARG);
        final Duration maxspan = givenMaxspan != null
                ? EqlQueryWriter.maxspan(givenMaxspan)
                : askMaxspan(request, context);
        final Integer givenLimit = intArg(request, LIMIT_ARG);
        final int limit = Math.clamp(Objects.requireNonNullElse(givenLimit, StroomQlSequence.DEFAULT_LIMIT), 1,
                Math.min(ElasticEql.MAX_SEQUENCES, limits(context).getMaxQueryRows()));
        final WorkspacePath givenOut = outArg(request);
        final WorkspacePath out = givenOut != null
                ? givenOut
                : sessionWorkspace(context).newFile("/outputs/sequences/sequence", ".md");
        return stroomQlSequence.run(plan, new StroomQlSequence.Call(maxspan, maxspanSource(givenMaxspan),
                timeRange(request, zone, referenceTime), limit, timeLimit(context), limits(context).getMaxQueryLength(),
                sessionWorkspace(context), out, context.session().isChat(), context.stopped(), zone, referenceTime));
    }

    // Runs the sequence as a streaming join in Stroom (KeyStreamSequence), on Elastic Indexes.
    private ToolResult runStreamed(final ToolCall request,
                                   final ToolCallContext context,
                                   final List<GivenStep> given,
                                   final GivenStep givenUntil,
                                   final Map<String, DocRef> byGiven,
                                   final List<DocRef> docs,
                                   final Map<DocRef, List<QueryField>> fieldsByDoc,
                                   final List<String> fieldNotes,
                                   final ToolTimeZone zone,
                                   final long referenceTime,
                                   final String reason) {
        final ElasticKeyStreams.Target streams = keyStreamSequence.target(docs);
        final List<String> timeFields = streams.docs().stream().map(ElasticKeyStreams.Doc::timeField).toList();
        final Target target = new Target(streams.version(), timeFields.stream()
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null), streams.docs().stream().map(ElasticKeyStreams.Doc::indexName).toList(),
                streams.docs().stream().map(ElasticKeyStreams.Doc::fields).toList());
        final List<JoinStep> steps = new ArrayList<>();
        for (int i = 0; i < given.size(); i++) {
            final String what = "Step " + (i + 1);
            steps.add(joinStep(given.get(i), step(given.get(i), what, byGiven, docs, fieldsByDoc, target, fieldNotes,
                    zone, referenceTime), what, byGiven, target, timeFields));
        }
        final JoinStep until = givenUntil == null
                ? null
                : joinStep(givenUntil, step(givenUntil, "The until step", byGiven, docs, fieldsByDoc, target,
                        fieldNotes, zone, referenceTime), "The until step", byGiven, target, timeFields);
        checkJoins(steps.stream().map(JoinStep::step).toList(), until == null
                ? null
                : until.step());
        final List<JoinStep> timed = steps.stream().filter(step -> !step.keysOnly()).toList();
        if (!timed.isEmpty() && timed.getFirst().step().missing()) {
            throw new WorkspaceException("The first step read in time is missing=true: a sequence starts with an "
                                         + "event that happens.");
        }
        if (steps.stream().allMatch(step -> step.step().missing())) {
            throw new WorkspaceException("Every step is missing=true: at least one step must happen.");
        }
        final List<JoinKeys.Kind> kinds = steps.getFirst().kinds();
        final List<JoinStep> all = new ArrayList<>(steps);
        if (until != null) {
            all.add(until);
        }
        for (int i = 1; i < all.size(); i++) {
            if (!all.get(i).kinds().equals(kinds)) {
                throw new WorkspaceException((i < steps.size()
                        ? "Step " + (i + 1)
                        : "The until step") + "'s join fields " + all.get(i).step().joinFields() + " are "
                                             + kindsText(all.get(i).kinds()) + ", but step 1's "
                                             + steps.getFirst().step().joinFields() + " are " + kindsText(kinds)
                                             + ": join fields compare only with fields of the same kind (text, "
                                             + "number or IP address).");
            }
        }
        final boolean inTime = timed.size() > 1 || (until != null && !timed.isEmpty());
        final String givenMaxspan = stringArg(request, MAXSPAN_ARG);
        final Duration maxspan = !inTime
                ? null
                : givenMaxspan != null
                        ? EqlQueryWriter.maxspan(givenMaxspan)
                        : askMaxspan(request, context);
        final Integer givenLimit = intArg(request, LIMIT_ARG);
        final int limit = Math.clamp(Objects.requireNonNullElse(givenLimit, KeyStreamSequence.DEFAULT_LIMIT), 1,
                KeyStreamSequence.MAX_LIMIT);
        final WorkspacePath givenOut = outArg(request);
        final WorkspacePath out = givenOut != null
                ? givenOut
                : sessionWorkspace(context).newFile("/outputs/sequences/join", ".md");
        return keyStreamSequence.run(new KeyStreamSequence.Call(steps, until, docs, timeFields, fieldsByDoc, maxspan,
                maxspanSource(givenMaxspan), timeRange(request, zone, referenceTime), limit, reason,
                fieldNotes.stream().distinct().toList(), booleanArg(request, RUN_ANYWAY_ARG, false), request.id(),
                context, limits(context), sessionWorkspace(context), out, zone, timeLimit(context), this::invalid,
                limits(context).getMaxQueryLength()));
    }

    // A step of the streaming join: its join fields as read, how they compare, and its doc's time field.
    private static JoinStep joinStep(final GivenStep given,
                                     final Step step,
                                     final String what,
                                     final Map<String, DocRef> byGiven,
                                     final Target target,
                                     final List<String> timeFields) {
        final DocRef doc = byGiven.get(given.index());
        final int position = step.doc() - 1;
        final Map<String, ElasticEql.Field> mapping = position < target.fields().size()
                ? target.fields().get(position)
                : Map.of();
        final List<KeyField> keys = new ArrayList<>();
        final List<JoinKeys.Kind> kinds = new ArrayList<>();
        for (final String field : step.joinFields()) {
            final ElasticEql.Field mapped = mapping.get(field);
            final JoinKeys.Kind kind = JoinKeys.kind(mapped == null
                    ? null
                    : mapped.nativeType());
            if (kind == null) {
                throw new WorkspaceException(what + ": '" + field + "' in Elastic Index '" + doc.getName() + "' is a "
                                             + mapped.nativeType() + " field, which can't be a join key: join on a "
                                             + "keyword, number, date or IP field.");
            }
            if (!given.transforms().isEmpty() && kind != JoinKeys.Kind.TEXT) {
                throw new WorkspaceException(what + ": '" + field + "' in Elastic Index '" + doc.getName() + "' isn't "
                                             + "text, so it can't be transformed: leave transform out of this step.");
            }
            keys.add(new KeyField(field, given.transforms()));
            kinds.add(kind);
        }
        final String timeField = timeFields.get(position);
        if (!given.keysOnly() && timeField == null) {
            throw new WorkspaceException(what + ": Elastic Index '" + doc.getName() + "' has no time field, so its "
                                         + "events can't be put in time order: give it " + KEYS_ONLY_KEY + "=true "
                                         + "(an entity condition at any time).");
        }
        return new JoinStep(step, doc, keys, kinds, given.keysOnly(), timeField);
    }

    private static String kindsText(final List<JoinKeys.Kind> kinds) {
        return String.join(", ", kinds.stream().map(kind -> kind.name().toLowerCase(Locale.ROOT)).toList());
    }

    // The engine asked for: auto (the default), eql or stream.
    private String engine(final ToolCall request) {
        final String given = NullSafe.trim(stringArg(request, ENGINE_ARG)).toLowerCase(Locale.ROOT);
        if (given.isEmpty()) {
            return ENGINE_AUTO;
        }
        if (!List.of(ENGINE_AUTO, ENGINE_EQL, ENGINE_STREAM).contains(given)) {
            throw new WorkspaceException("engine '" + given + "' isn't one of " + ENGINE_AUTO + ", " + ENGINE_EQL
                                         + ", " + ENGINE_STREAM + ".");
        }
        return given;
    }

    private static String maxspanSource(final String givenMaxspan) {
        return givenMaxspan != null
                ? "as asked"
                : "the user's answer";
    }

    // The call's time range, its date expressions and times without an offset as times in the call's zone.
    private TimeRange timeRange(final ToolCall request, final ToolTimeZone zone, final long referenceTime) {
        final String timeFrom = stringArg(request, TIME_FROM_ARG);
        final String timeTo = stringArg(request, TIME_TO_ARG);
        return timeFrom == null && timeTo == null
                ? null
                : new TimeRange(null, StroomQlDatePoints.timeInZone(timeFrom, zone, referenceTime),
                        StroomQlDatePoints.timeInZone(timeTo, zone, referenceTime));
    }

    /// @param doc A data source.
    /// @return True if it is an Elastic Index, which a sequence runs on as EQL.
    static boolean isElastic(final DocRef doc) {
        return ElasticIndexDoc.TYPE.equals(doc.getType());
    }

    // The data source a step names, by uuid or name: any the user can use.
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

    // A step, checked against its doc's fields: a text field searched or joined on is swapped for a subfield EQL
    // can search (said in notes), and a nested field is refused.
    private static Step step(final GivenStep given,
                             final String what,
                             final Map<String, DocRef> byGiven,
                             final List<DocRef> docs,
                             final Map<DocRef, List<QueryField>> fieldsByDoc,
                             final Target target,
                             final List<String> notes,
                             final ToolTimeZone zone,
                             final long referenceTime) {
        final DocRef doc = byGiven.get(given.index());
        final List<QueryField> fields = fieldsByDoc.get(doc);
        final Map<String, FieldType> types = new LinkedHashMap<>();
        fields.forEach(field -> types.put(field.getFldName(), field.getFldType()));
        final int position = docs.indexOf(doc);
        final Map<String, ElasticEql.Field> mapping = position < target.fields().size()
                ? target.fields().get(position)
                : Map.of();
        final Set<String> named = new LinkedHashSet<>();
        given.filters().forEach(filter -> named.add(filter.field()));
        given.anyOf().forEach(filter -> named.add(filter.field()));
        named.addAll(given.by());
        named.addAll(given.fields());
        final List<String> unknown = named.stream().filter(name -> !types.containsKey(name)).toList();
        if (!unknown.isEmpty()) {
            throw new WorkspaceException(what + ": not fields of Elastic Index '" + doc.getName() + "': "
                                         + EsqlQueryTool.said(unknown, types.keySet()) + ". " + FIELDS_HINT + ".");
        }
        final Set<String> searched = new LinkedHashSet<>();
        given.filters().forEach(filter -> searched.add(filter.field()));
        given.anyOf().forEach(filter -> searched.add(filter.field()));
        searched.addAll(given.by());
        final Map<String, String> swapped = new LinkedHashMap<>();
        for (final String name : searched) {
            refuseNested(what, doc, name, mapping, types);
            final String searchable = searchable(what, doc, name, mapping);
            if (!searchable.equals(name)) {
                swapped.put(name, searchable);
                notes.add(swapNote(doc, name, searchable, mapping));
            }
        }
        final List<Filter> filters = given.filters().stream()
                .map(filter -> inZone(filter, types, zone, referenceTime))
                .map(filter -> filter.onField(swapped.getOrDefault(filter.field(), filter.field())))
                .toList();
        final List<Filter> anyOf = given.anyOf().stream()
                .map(filter -> inZone(filter, types, zone, referenceTime))
                .map(filter -> filter.onField(swapped.getOrDefault(filter.field(), filter.field())))
                .toList();
        final List<String> by = given.by().stream().map(name -> swapped.getOrDefault(name, name)).toList();
        for (final Filter filter : concat(filters, anyOf)) {
            if (filter.value().isEmpty() && filter.condition() != Condition.IS_NULL
                && filter.condition() != Condition.IS_NOT_NULL) {
                throw new WorkspaceException(what + ": the condition on '" + filter.field() + "' has no value.");
            }
            // Written now, so a value its field can't take is refused before anything runs.
            EqlQueryWriter.eqlCondition(filter, types.get(filter.field()));
        }
        return new Step(position + 1, filters, anyOf, by, given.missing(), given.runs(), given.fields(), types);
    }

    /// @param filter        A step's condition.
    /// @param types         The types of its document's fields, by name.
    /// @param zone          The call's zone.
    /// @param referenceTime What now is, in epoch milliseconds.
    /// @return The condition with its value as a time in the zone if its field is a date (e.g. `2026-11-21T02:00:00`
    /// or `day()` as `2026-11-21T02:00:00+11:00`), as the time range's are; else the condition as given.
    static Filter inZone(final Filter filter,
                         final Map<String, FieldType> types,
                         final ToolTimeZone zone,
                         final long referenceTime) {
        if (types.get(filter.field()) != FieldType.DATE || filter.length()) {
            return filter;
        }
        return filter.withValue(StroomQlDatePoints.conditionInZone(filter.value(), filter.condition(), zone,
                referenceTime));
    }

    private static List<Filter> concat(final List<Filter> first, final List<Filter> second) {
        final List<Filter> all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }

    // Refuses a field that is, or is inside, a nested field, which EQL can't search.
    private static void refuseNested(final String what,
                                     final DocRef doc,
                                     final String name,
                                     final Map<String, ElasticEql.Field> mapping,
                                     final Map<String, FieldType> types) {
        final ElasticEql.Field field = mapping.get(name);
        if ((field != null && field.nested()) || types.get(name) == FieldType.NESTED) {
            throw new WorkspaceException(what + ": '" + name + "' in Elastic Index '" + doc.getName() + "' is in a "
                                         + "nested field, which EQL can't search. Leave it out of the steps, or "
                                         + "search it with search_events or esql_query instead.");
        }
    }

    /// The field EQL is to search for a field a step names: the field itself, unless its mapping says it is text,
    /// which EQL can't search; then its subfield EQL can search, the most fitting first ([#SUBFIELD_TYPES]; of
    /// those, one without a normaliser first, then by name).
    ///
    /// @param what    The step, for a refusal.
    /// @param doc     Its doc.
    /// @param name    The field it names.
    /// @param mapping The doc's fields, as its mapping has them; empty if not known (the field is then kept).
    /// @return The field to search.
    /// @throws WorkspaceException for a text field with no subfield EQL can search, giving its subfields.
    static String searchable(final String what,
                             final DocRef doc,
                             final String name,
                             final Map<String, ElasticEql.Field> mapping) {
        final ElasticEql.Field field = mapping.get(name);
        if (field == null || !TEXT_NATIVE_TYPES.contains(field.nativeType())) {
            return name;
        }
        final List<ElasticEql.Field> subfields = mapping.values().stream()
                .filter(sub -> name.equals(sub.multiFieldParent()))
                .sorted(Comparator.comparing(ElasticEql.Field::name))
                .toList();
        return subfields.stream()
                .filter(sub -> SUBFIELD_TYPES.contains(sub.nativeType()))
                .min(Comparator
                        .comparingInt((ElasticEql.Field sub) -> SUBFIELD_TYPES.indexOf(sub.nativeType()))
                        .thenComparing(sub -> sub.normaliser() != null))
                .map(ElasticEql.Field::name)
                .orElseThrow(() -> new WorkspaceException(what + ": '" + name + "' in Elastic Index '"
                                                          + doc.getName() + "' is a " + field.nativeType()
                                                          + " field, which EQL can't search, and it has "
                                                          + (subfields.isEmpty()
                        ? "no subfields"
                        : "no keyword subfield (its subfields: " + String.join(", ", subfields.stream()
                                .map(sub -> sub.name() + " (" + sub.nativeType() + ")")
                                .toList()) + ")") + ". Search it with search_events or esql_query instead, or "
                                                          + "use another field."));
    }

    // What the result says of a text field searched as its subfield.
    private static String swapNote(final DocRef doc,
                                   final String name,
                                   final String subfield,
                                   final Map<String, ElasticEql.Field> mapping) {
        final ElasticEql.Field sub = mapping.get(subfield);
        final String normaliser = sub == null || sub.normaliser() == null
                ? ""
                : ", normaliser " + sub.normaliser();
        return "'" + name + "' in Elastic Index '" + doc.getName() + "' is a text field, which EQL can't search, so "
               + "its subfield '" + subfield + "' (" + (sub == null
                ? "keyword"
                : sub.nativeType()) + normaliser + ") was searched instead: it matches the whole value, as "
               + "written.";
    }

    /// Whether a step, with more than one doc, must be kept to its own doc's events ([ElasticEql#DOC_FIELD], a
    /// script on every event): not when one of its `where` conditions (or every condition of its `any_of`) is on a
    /// field no other doc maps and can't hold for an event without the field, as then no other doc's event can
    /// match it. IS_NULL, NOT_EQUALS and NOT_IN can hold for an event without the field, so don't count.
    ///
    /// @param step        The step.
    /// @param docs        The docs, in order.
    /// @param fieldsByDoc Each doc's fields.
    /// @return True if it needs the doc condition.
    static boolean needsRestriction(final Step step,
                                    final List<DocRef> docs,
                                    final Map<DocRef, List<QueryField>> fieldsByDoc) {
        final Set<String> elsewhere = new LinkedHashSet<>();
        for (int d = 0; d < docs.size(); d++) {
            if (d != step.doc() - 1) {
                NullSafe.list(fieldsByDoc.get(docs.get(d))).forEach(field -> elsewhere.add(field.getFldName()));
            }
        }
        if (step.filters().stream().anyMatch(filter -> rulesOut(filter, elsewhere))) {
            return false;
        }
        return step.anyOf().isEmpty() || !step.anyOf().stream().allMatch(filter -> rulesOut(filter, elsewhere));
    }

    // Whether a condition rules out every event of a doc that doesn't map its field.
    private static boolean rulesOut(final Filter filter, final Set<String> elsewhere) {
        return !elsewhere.contains(filter.field())
               && !filter.negated()
               && filter.condition() != Condition.IS_NULL
               && filter.condition() != Condition.NOT_EQUALS;
    }

    // Every step joins on the same number of fields, at least one.
    private static void checkJoins(final List<Step> steps, final Step until) {
        final int count = steps.getFirst().joinFields().size();
        if (count == 0) {
            throw new WorkspaceException("Step 1 has no join fields: give by, the fields that say the events are "
                                         + "the same entity's (e.g. [\"user.name\", \"host.name\"]), in every step.");
        }
        final List<Step> all = new ArrayList<>(steps);
        if (until != null) {
            all.add(until);
        }
        for (int i = 1; i < all.size(); i++) {
            if (all.get(i).joinFields().size() != count) {
                final String what = i < steps.size()
                        ? "Step " + (i + 1)
                        : "The until step";
                throw new WorkspaceException(what + " joins on " + all.get(i).joinFields() + " but step 1 on "
                                             + steps.getFirst().joinFields() + ": give every step the same number "
                                             + "of join fields, in the same order (its own names for the same "
                                             + "things).");
            }
        }
    }

    /// Refuses a cluster too old for what the steps use: missing events need 8.8, runs 8.7.
    ///
    /// @param version The cluster's version.
    /// @param steps   The steps.
    static void checkVersion(final String version, final List<Step> steps) {
        if (steps.stream().anyMatch(Step::missing) && !atLeast(version, MISSING_VERSION)) {
            throw new WorkspaceException("The cluster is Elasticsearch " + version + ": a missing step (missing=true) "
                                         + "needs 8.8 or later. Leave it out, or use run_stroomql.");
        }
        if (steps.stream().anyMatch(step -> step.runs() > 1) && !atLeast(version, RUNS_VERSION)) {
            throw new WorkspaceException("The cluster is Elasticsearch " + version + ": runs needs 8.7 or later. "
                                         + "Repeat the step instead (one step per event).");
        }
    }

    /// @param version A version, e.g. `9.5.3`.
    /// @param least   The least major and minor version.
    /// @return True if the version is at least that; false if it is older or can't be read.
    static boolean atLeast(final String version, final int[] least) {
        if (version == null) {
            return false;
        }
        final String[] parts = version.strip().split("[.-]");
        try {
            final int major = Integer.parseInt(parts[0]);
            final int minor = parts.length > 1
                    ? Integer.parseInt(parts[1])
                    : 0;
            return major > least[0] || (major == least[0] && minor >= least[1]);
        } catch (final NumberFormatException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------------------------------------
    // The window

    // Asks the user for the window in a form, in a chat; refused, with the question to ask, where no form can be
    // shown (over MCP, or while the user is away).
    private Duration askMaxspan(final ToolCall request, final ToolCallContext context) {
        final Duration suggested = defaultMaxspan(context);
        if (!context.session().isChat() || !context.userQuestions().canAsk()) {
            throw new WorkspaceException(SequenceWindow.maxspanRefusal(suggested));
        }
        final Map<String, Duration> options = SequenceWindow.maxspanOptions(suggested);
        final ElicitResult answer = context.userQuestions().ask(NAME,
                UserQuestions.formCallId(request.id(), MAXSPAN_FORM), MAXSPAN_QUESTION + " " + WIDER_NOTE,
                SequenceWindow.maxspanForm(options));
        McpElicitations.unlessSentInstead(answer, MAXSPAN_QUESTION, NAME, "");
        if (answer.action() != ElicitResult.Action.ACCEPT) {
            throw new WorkspaceException("The user didn't choose a window, so nothing was run. Ask them in the chat "
                                         + "how far apart the steps may be, then call again with maxspan.");
        }
        final String chosen = NullSafe.trim(NullSafe.map(answer.content()).get(MAXSPAN_FORM) == null
                ? null
                : String.valueOf(NullSafe.map(answer.content()).get(MAXSPAN_FORM)));
        return SequenceWindow.chosenMaxspan(chosen, options);
    }

    /// @param context The call's context.
    /// @return The configured window to suggest, at most 24 hours.
    static Duration defaultMaxspan(final ToolCallContext context) {
        final Duration configured = NullSafe.getOrElse(limits(context).getEqlDefaultMaxspan(),
                duration -> duration.getDuration(), Duration.ofMinutes(5));
        return configured.isZero() || configured.isNegative() || configured.compareTo(EqlQueryWriter.MAX_MAXSPAN) > 0
                ? Duration.ofMinutes(5)
                : configured;
    }

    // ------------------------------------------------------------------------------------------------
    // Pre-flight

    // Counts each step's events with ES|QL, where the cluster takes it.
    private Preflight preflight(final List<Step> steps,
                                final List<DocRef> docs,
                                final TimeRange timeRange,
                                final String version,
                                final Instant deadline,
                                final Duration timeLimit,
                                final BooleanSupplier stopped) {
        if (!atLeast(version, ESQL_VERSION)) {
            return Preflight.skipped("The steps weren't counted first: that needs ES|QL, from Elasticsearch 8.14.");
        }
        if (isShort(timeRange)) {
            return Preflight.skipped("The steps weren't counted first: the time range is " + SHORT_WINDOW.toHours()
                                     + " hour or less, so the sequence is cheap enough to just run.");
        }
        final List<Long> counts = new ArrayList<>();
        final List<String> pipelines = new ArrayList<>();
        for (final Step step : steps) {
            if (step.missing()) {
                counts.add(null);
                pipelines.add(null);
                continue;
            }
            final String pipeline = EqlQueryWriter.countPipeline(step, COUNT_CAP);
            pipelines.add(pipeline);
            try {
                final ElasticEsql.Result counted = elasticEsqlProvider.get().query(new ElasticEsql.Request(
                        docs.get(step.doc() - 1), pipeline, null, timeRange, 1, remaining(deadline, timeLimit),
                        false, stopped));
                counts.add(count(counted));
            } catch (final ElasticEsql.Refusal refusal) {
                if (refusal.reason() == ElasticEsql.Reason.STOPPED) {
                    // The user stopped the chat while the steps were counted: the sequence isn't run.
                    throw new TaskTerminatedException();
                }
                if (refusal.reason() == ElasticEsql.Reason.TIMEOUT) {
                    throw new WorkspaceException("Counting the steps took longer than the time limit ("
                                                 + timeLimit.toSeconds() + " seconds), so the sequence wasn't run: "
                                                 + NARROW + ".");
                }
                return Preflight.skipped("The steps weren't counted first (" + NullSafe.trim(refusal.getMessage())
                                         + "), so nothing was said of their sizes.");
            } catch (final PermissionException e) {
                throw new WorkspaceException(notUsable(docs, e));
            }
        }
        return assess(steps, counts, pipelines);
    }

    /// @param timeRange The call's time range, or null.
    /// @return True if it has both ends, and they are at most [#SHORT_WINDOW] apart.
    static boolean isShort(final TimeRange timeRange) {
        if (timeRange == null || NullSafe.isBlankString(timeRange.getFrom())
            || NullSafe.isBlankString(timeRange.getTo())) {
            return false;
        }
        try {
            final long now = System.currentTimeMillis();
            final Optional<ZonedDateTime> from = DateExpressionParser.parse(timeRange.getFrom().strip(), now);
            final Optional<ZonedDateTime> to = DateExpressionParser.parse(timeRange.getTo().strip(), now);
            return from.isPresent() && to.isPresent()
                   && Duration.between(from.get(), to.get()).compareTo(SHORT_WINDOW) <= 0;
        } catch (final RuntimeException e) {
            // Not read here: the service refuses it, saying why.
            return false;
        }
    }

    /// What the counts say, before the sequence runs. Each count reads at most [#COUNT_CAP] events, so the
    /// heuristic is by thresholds, not a guess of speed:
    ///
    /// - **Too big**: the first step that must happen matches more than [#COUNT_CAP] events. A sequence reads every
    ///   event of its first step, so it is refused before running (unless `runAnyway`), saying to narrow the window
    ///   or the first step. A later step past the cap is noted.
    /// - **Selective step first**: when the first step matches at least [#SELECTIVITY_MIN_EVENTS] events and at
    ///   least [#SELECTIVITY_FACTOR] times as many as a later step (a capped first count is a lower bound, which is
    ///   enough), and the order allows it (no step is missing, and neither step has runs, as moving either would
    ///   change what a sequence means), it says to put the later step first if the order doesn't matter.
    /// - **Nothing to find**: a step that must happen matching fewer events than its runs means no sequence can
    ///   be found; it is said (the sequence still runs, as the count can be low where a field holds several values).
    ///
    /// @param steps     The steps.
    /// @param counts    Each step's count (`COUNT_CAP + 1` for more than the cap), null for a missing step.
    /// @param pipelines Each step's count pipeline, null for a missing step.
    /// @return The assessment.
    static Preflight assess(final List<Step> steps,
                            final List<Long> counts,
                            final List<String> pipelines) {
        final List<String> notes = new ArrayList<>();
        int firstHappening = -1;
        for (int i = 0; i < steps.size(); i++) {
            final Long count = counts.get(i);
            if (count == null) {
                continue;
            }
            if (firstHappening < 0) {
                firstHappening = i;
            } else if (count > COUNT_CAP) {
                notes.add("Step " + (i + 1) + " matches " + countText(count) + " events: if the sequence times "
                          + "out, narrow that step.");
            }
            if (count < steps.get(i).runs() || count == 0) {
                notes.add("Step " + (i + 1) + " matches " + count + " event" + (count == 1
                        ? ""
                        : "s") + " in the window" + (steps.get(i).runs() > 1
                        ? " (it needs " + steps.get(i).runs() + " in a row)"
                        : "") + ", so no sequence is likely to be found: check its conditions.");
            }
        }
        final boolean orderFree = steps.stream().noneMatch(Step::missing);
        final Long first = counts.getFirst();
        if (orderFree && first != null && steps.getFirst().runs() == 1 && first >= SELECTIVITY_MIN_EVENTS) {
            int best = -1;
            for (int i = 1; i < steps.size(); i++) {
                final Long count = counts.get(i);
                if (count != null && count <= COUNT_CAP && steps.get(i).runs() == 1 && count > 0
                    && first >= count * SELECTIVITY_FACTOR && (best < 0 || count < counts.get(best))) {
                    best = i;
                }
            }
            if (best > 0) {
                notes.add("Step " + (best + 1) + " matches far fewer events (" + counts.get(best) + ") than step 1 ("
                          + countText(first) + "): if the order doesn't matter to the question, put step "
                          + (best + 1) + " first, which is quicker.");
            }
        }
        final boolean tooBig = firstHappening >= 0 && counts.get(firstHappening) > COUNT_CAP;
        final String tooBigNote = tooBig
                ? "step " + (firstHappening + 1) + " matches " + countText(counts.get(firstHappening)) + " events in "
                  + "the window (" + countsText(counts) + "), and a sequence reads every event of its first step, so "
                  + "it is unlikely to finish in the time limit."
                : null;
        return new Preflight(true, counts, pipelines, notes, tooBig, tooBigNote, null);
    }

    // A count, "more than 1000000" when it passed the cap.
    private static String countText(final long count) {
        return count > COUNT_CAP
                ? "more than " + COUNT_CAP
                : String.valueOf(count);
    }

    /// @param counts Each step's pre-flight count.
    /// @return The counts, by step.
    static String countsText(final List<Long> counts) {
        final List<String> parts = new ArrayList<>();
        for (int i = 0; i < counts.size(); i++) {
            if (counts.get(i) != null) {
                parts.add("step " + (i + 1) + ": " + countText(counts.get(i)));
            }
        }
        return String.join(", ", parts);
    }

    private static long count(final ElasticEsql.Result result) {
        if (result.rows().isEmpty() || result.rows().getFirst().isEmpty()
            || !(result.rows().getFirst().getFirst() instanceof final Number number)) {
            return 0;
        }
        return number.longValue();
    }

    private static Duration remaining(final Instant deadline, final Duration timeLimit) {
        final Duration left = Duration.between(Instant.now(), deadline);
        if (left.isNegative() || left.isZero()) {
            throw new WorkspaceException("The time limit (" + timeLimit.toSeconds() + " seconds) ran out before the "
                                         + "sequence could run: " + NARROW + ".");
        }
        return left;
    }

    // ------------------------------------------------------------------------------------------------
    // Running and the result

    // The fields each event is fetched with: its ids, the join fields and the fields asked for.
    private static List<String> fetchedFields(final List<Step> steps, final Step until) {
        final Set<String> fields = new LinkedHashSet<>();
        fields.add(EqlSequenceResults.STREAM_ID);
        fields.add(EqlSequenceResults.EVENT_ID);
        steps.forEach(step -> {
            fields.addAll(step.joinFields());
            fields.addAll(step.fields());
        });
        if (until != null) {
            fields.addAll(until.joinFields());
        }
        return List.copyOf(fields);
    }

    /// What the model is told for a refusal: the reason, and the way forward.
    ///
    /// @param refusal     Why the sequence wasn't run.
    /// @param fieldsByDoc Each doc's fields, as Stroom lists them; empty if not known.
    /// @return The message.
    static String refusal(final Refusal refusal, final Map<DocRef, List<QueryField>> fieldsByDoc) {
        final String message = sentence(refusal.getMessage());
        return switch (refusal.reason()) {
            case TIMEOUT -> sentence(NullSafe.trim(refusal.getMessage()).replaceAll("\\.$", "") + ": " + NARROW)
                            + " " + MANY_KEYS_NOTE;
            case DIFFERENT_CLUSTERS -> message + " Run a sequence per cluster, or search the other with "
                                       + "search_events.";
            case DIFFERENT_TIME_FIELDS -> message + " Use docs whose time field is the same, or run a sequence "
                                          + "per doc.";
            case CLUSTER_TOO_OLD -> message + " Use run_stroomql instead, which works on any version.";
            case UNKNOWN_FIELD -> {
                final Set<String> names = new LinkedHashSet<>();
                fieldsByDoc.values().forEach(fields -> fields.forEach(field -> names.add(field.getFldName())));
                yield message + " Not fields of the docs: " + EsqlQueryTool.said(refusal.names(), names) + ". "
                      + FIELDS_HINT + ".";
            }
            case NOT_FOUND -> DataSourceAccess.NOT_FOUND;
            default -> message;
        };
    }

    /// The most sequences one search can return: as many as fit [#MAX_FETCHED_EVENTS] events, counting each run
    /// of a step and no missing step, and never more than [ElasticEql#MAX_SEQUENCES].
    ///
    /// @param steps The steps.
    /// @return The most sequences.
    static int mostSequences(final List<Step> steps) {
        final int events = steps.stream()
                .filter(step -> !step.missing())
                .mapToInt(Step::runs)
                .sum();
        return events <= 1
                ? ElasticEql.MAX_SEQUENCES
                : Math.min(ElasticEql.MAX_SEQUENCES, MAX_FETCHED_EVENTS / events);
    }

    // Why the most sequences is lower than the tool's own most.
    private static String fetchedNote(final List<Step> steps) {
        final int events = steps.stream()
                .filter(step -> !step.missing())
                .mapToInt(Step::runs)
                .sum();
        return "Elasticsearch fetches at most " + MAX_FETCHED_EVENTS + " events of a search's sequences, and each "
               + "sequence here has " + events + " events.";
    }

    private static String notUsable(final List<DocRef> docs, final PermissionException e) {
        final String text = NullSafe.string(e.getMessage());
        return docs.stream()
                .filter(doc -> text.contains(doc.getUuid()))
                .findFirst()
                .map(doc -> "You may not use Elastic Index '" + doc.getName() + "' (" + doc.getUuid() + "), so the "
                            + "sequence can't search it.")
                .orElse(DataSourceAccess.NOT_FOUND);
    }

    private static String sentence(final String message) {
        final String stripped = NullSafe.trim(message);
        return stripped.endsWith(".") || stripped.endsWith("?") || stripped.endsWith("!")
                ? stripped
                : stripped + ".";
    }

    private static String capitalised(final String text) {
        return text.isEmpty()
                ? text
                : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static String searchedNote(final TimeRange timeRange, final Duration maxspan, final Preflight preflight) {
        final StringBuilder sb = new StringBuilder("\nmaxspan: ").append(EqlQueryWriter.maxspanText(maxspan));
        if (timeRange != null) {
            sb.append("\ntime: ").append(NullSafe.nonBlankStringElse(timeRange.getFrom(), "any"))
                    .append(" to ").append(NullSafe.nonBlankStringElse(timeRange.getTo(), "any"));
        }
        if (preflight.counted()) {
            for (int i = 0; i < preflight.pipelines().size(); i++) {
                if (preflight.pipelines().get(i) != null) {
                    sb.append("\ncount of step ").append(i + 1).append(": ").append(preflight.pipelines().get(i));
                }
            }
        }
        return sb.toString();
    }

    // The review query per doc: exact (by StreamId and EventId) where it fits and the index has them, else the
    // entities in the window; each validated.
    private List<ReviewNote> reviews(final List<DocRef> docs,
                                     final Map<DocRef, List<QueryField>> fieldsByDoc,
                                     final String timeField,
                                     final List<Row> rows,
                                     final List<Step> steps,
                                     final int maxQueryLength) {
        if (rows.isEmpty()) {
            return List.of();
        }
        final List<ReviewNote> reviews = new ArrayList<>();
        for (int d = 0; d < docs.size(); d++) {
            final DocRef doc = docs.get(d);
            final Set<String> names = new LinkedHashSet<>();
            fieldsByDoc.getOrDefault(doc, List.of()).forEach(field -> names.add(field.getFldName()));
            final boolean hasIds = names.contains(EqlSequenceResults.STREAM_ID)
                                   && names.contains(EqlSequenceResults.EVENT_ID);
            final Review review = EqlSequenceResults.review(doc, timeField, hasIds, rows, steps, d + 1,
                    maxQueryLength);
            if (review == null) {
                continue;
            }
            final String invalid = invalid(review.query());
            reviews.add(new ReviewNote(review, invalid, reviewNote(review, maxQueryLength)));
        }
        return reviews;
    }

    // Why the query isn't valid StroomQL, or null if it is.
    private String invalid(final String query) {
        try {
            final ValidateExpressionResult result = queryServiceProvider.get().validateQuery(query);
            return result != null && result.isOk()
                    ? null
                    : NullSafe.nonBlankStringElse(NullSafe.get(result, ValidateExpressionResult::getString),
                            "the parser gave no reason");
        } catch (final RuntimeException e) {
            return NullSafe.nonBlankStringElse(e.getMessage(), e.getClass().getSimpleName());
        }
    }

    /// @param review         A review query.
    /// @param maxQueryLength The longest a query may be.
    /// @return Which form it is, and what it finds.
    static String reviewNote(final Review review, final int maxQueryLength) {
        if (review.exact()) {
            return "It finds exactly the " + review.terms() + " hit event" + (review.terms() == 1
                    ? ""
                    : "s") + " of this doc, by StreamId and EventId.";
        }
        final String why = EqlSequenceResults.NO_IDS.equals(review.why())
                ? "This doc's index has no StreamId and EventId fields (it is filled from outside Stroom), so its "
                  + "hits can't be named one by one, fetched with fetch_events or stepped"
                : "Naming the hits by StreamId and EventId would be longer than a query may be (" + maxQueryLength
                  + " characters)";
        return review.entities()
                ? why + ", so it finds the " + review.terms() + " entit" + (review.terms() == 1
                ? "y's"
                : "ies'") + " events in the window the sequences span: those entities' events in the window, not "
                  + "only the hits."
                : why + ", and the entities are too many to name, so it finds every event in the window the "
                  + "sequences span, not only the hits.";
    }

    /// The same two-step sequence as a StroomQL windowed join, which Stroom runs itself, for the user to save as a
    /// Query: only for what a windowed join can say (two steps, no until, missing or runs, and conditions it can
    /// write exactly).
    ///
    /// @param steps       The steps.
    /// @param until       The until step, or null.
    /// @param docs        The docs, in order.
    /// @param fieldsByDoc Each doc's fields.
    /// @param timeField   The docs' time field.
    /// @param maxspan     The window.
    /// @param timeRange   The time range, or null.
    /// @param limit       The most rows.
    /// @return The query, or null if a windowed join can't say the sequence.
    static String equivalentStroomQl(final List<Step> steps,
                                     final Step until,
                                     final List<DocRef> docs,
                                     final Map<DocRef, List<QueryField>> fieldsByDoc,
                                     final String timeField,
                                     final Duration maxspan,
                                     final TimeRange timeRange,
                                     final int limit) {
        if (steps.size() != 2 || until != null || NullSafe.isBlankString(timeField)
            || steps.stream().anyMatch(step -> step.missing() || step.runs() > 1)) {
            return null;
        }
        final List<SequenceStroomQl.Side> sides = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            final Step step = steps.get(i);
            final DocRef doc = docs.get(step.doc() - 1);
            if (SequenceStroomQl.unwritable("Step " + (i + 1), step, doc) != null) {
                return null;
            }
            final Set<String> names = new LinkedHashSet<>();
            NullSafe.list(fieldsByDoc.get(doc)).forEach(field -> names.add(field.getFldName()));
            sides.add(new SequenceStroomQl.Side(doc, timeField, step, names.contains(EqlSequenceResults.STREAM_ID)
                                                                       && names.contains(EqlSequenceResults.EVENT_ID)));
        }
        if (sides.getLast().step().filters().isEmpty() && sides.getLast().step().anyOf().isEmpty()) {
            return null;
        }
        try {
            return SequenceStroomQl.join(sides.getFirst(), sides.getLast(), maxspan, timeRange, limit);
        } catch (final WorkspaceException e) {
            return null;
        }
    }

    @Override
    public boolean readsUntrustedContent() {
        // Stroom data can hold text written by anyone.
        return true;
    }


    // --------------------------------------------------------------------------------


    /// A step as the model gave it.
    ///
    /// @param index   The doc's uuid or name.
    /// @param filters Its conditions.
    /// @param by      Its join fields.
    /// @param missing True for an event that must not happen.
    /// @param runs    How many times in a row.
    /// @param fields     Fields to show.
    /// @param keysOnly   True if it is read as keys (an entity condition at any time), not events in time.
    /// @param transforms What is done to its join fields' values before they are compared.
    /// @param target     True if, for one person's sequences, the step is of acts done to them (by its doc's target
    ///                   field), not their own.
    record GivenStep(String index,
                             List<Filter> filters,
                             List<Filter> anyOf,
                             List<String> by,
                             boolean missing,
                             int runs,
                             List<String> fields,
                             boolean keysOnly,
                             List<Transform> transforms,
                             boolean target) {

        /// Copies the list.
        GivenStep {
            transforms = transforms == null
                    ? List.of()
                    : List.copyOf(transforms);
        }

        /// The person's own acts (the actor role).
        ///
        /// @param index      The doc's uuid or name.
        /// @param filters    Its conditions.
        /// @param anyOf      Conditions of which one must hold.
        /// @param by         Its join fields.
        /// @param missing    True for an event that must not happen.
        /// @param runs       How many times in a row.
        /// @param fields     Fields to show.
        /// @param keysOnly   True if it is read as keys.
        /// @param transforms What is done to its join fields' values.
        GivenStep(final String index,
                  final List<Filter> filters,
                  final List<Filter> anyOf,
                  final List<String> by,
                  final boolean missing,
                  final int runs,
                  final List<String> fields,
                  final boolean keysOnly,
                  final List<Transform> transforms) {
            this(index, filters, anyOf, by, missing, runs, fields, keysOnly, transforms, false);
        }

        /// @return True if only the stream engine can run the step.
        boolean needsStream() {
            return keysOnly || !transforms.isEmpty();
        }
    }


    // --------------------------------------------------------------------------------


    /// What the counts said before the sequence ran.
    ///
    /// @param counted     True if the steps were counted.
    /// @param counts      Each step's count, null for a missing step; empty if not counted.
    /// @param pipelines   Each step's count pipeline, null for a missing step.
    /// @param notes       What the counts say: a more selective later step, a step with nothing to match.
    /// @param tooBig      True if the counts say the sequence won't finish in the time limit.
    /// @param tooBigNote  Why, or null.
    /// @param skippedNote Why the steps weren't counted, or null.
    record Preflight(boolean counted,
                     List<Long> counts,
                     List<String> pipelines,
                     List<String> notes,
                     boolean tooBig,
                     String tooBigNote,
                     String skippedNote) {

        /// Copies the lists, which may hold nulls.
        Preflight {
            counts = counts == null
                    ? List.of()
                    : Collections.unmodifiableList(new ArrayList<>(counts));
            pipelines = pipelines == null
                    ? List.of()
                    : Collections.unmodifiableList(new ArrayList<>(pipelines));
            notes = notes == null
                    ? List.of()
                    : List.copyOf(notes);
        }

        /// @param note Why the steps weren't counted.
        /// @return A pre-flight that counted nothing.
        static Preflight skipped(final String note) {
            return new Preflight(false, List.of(), List.of(), List.of(), false, null, note);
        }
    }


    // --------------------------------------------------------------------------------


    /// A review query, with whether it is valid and what it finds.
    ///
    /// @param review  The query.
    /// @param invalid Why it isn't valid StroomQL, or null if it is.
    /// @param note    What it finds.
    record ReviewNote(Review review, String invalid, String note) {

    }


    // --------------------------------------------------------------------------------


    /// Everything the result says.
    ///
    /// @param docs          The docs searched.
    /// @param result        What Elasticsearch gave.
    /// @param maxspan       The window used.
    /// @param maxspanSource Where the window came from.
    /// @param timeRange     The time range, or null.
    /// @param limit         The most sequences asked for.
    /// @param limitNote     What was done to the limit, or null.
    /// @param truncated     True if the sequences reached the limit.
    /// @param preflight     What the counts said.
    /// @param steps         The steps.
    /// @param columns       The table's columns.
    /// @param cells         The table's rows.
    /// @param entities      The summary per entity.
    /// @param untimed       How many sequences have no time on any of their events.
    /// @param reviews       The review queries.
    /// @param out             The table's file.
    /// @param fieldNotes      What was done to the steps' fields.
    /// @param stroomQl        The same sequence as a StroomQL windowed join, or null if one can't say it.
    /// @param stroomQlInvalid Why that isn't valid StroomQL, or null.
    /// @param zone            The zone the result's times are in.
    record Outcome(List<DocRef> docs,
                   Result result,
                   Duration maxspan,
                   String maxspanSource,
                   TimeRange timeRange,
                   int limit,
                   String limitNote,
                   boolean truncated,
                   Preflight preflight,
                   List<Step> steps,
                   List<String> columns,
                   List<List<String>> cells,
                   List<Entity> entities,
                   int untimed,
                   List<ReviewNote> reviews,
                   WorkspacePath out,
                   List<String> fieldNotes,
                   String stroomQl,
                   String stroomQlInvalid,
                   ToolTimeZone zone) {

        /// @return The most sequences these steps can return ([#mostSequences(List)]).
        int mostSequences() {
            return SequenceQueryTool.mostSequences(steps);
        }
    }

    /// Whom a call's steps are restricted to.
    ///
    /// @param person     The person id, or null.
    /// @param logonIds   Their logon ids; empty for none.
    /// @param logonForms The logon ids' other written forms to match too; empty to match exactly.
    private record Restriction(String person, List<String> logonIds, List<Transform> logonForms) {

    }
}
