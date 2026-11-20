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
import stroom.ai.api.ElasticEql.Refusal;
import stroom.ai.api.ElasticEql.Result;
import stroom.ai.api.ElasticEql.Target;
import stroom.ai.api.ElasticEsql;
import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.tools.EqlQueryWriter.Filter;
import stroom.ai.impl.tools.EqlQueryWriter.Step;
import stroom.ai.impl.tools.EqlSequenceResults.Entity;
import stroom.ai.impl.tools.EqlSequenceResults.EventCount;
import stroom.ai.impl.tools.EqlSequenceResults.Review;
import stroom.ai.impl.tools.EqlSequenceResults.Row;
import stroom.ai.impl.tools.ToolSchema.ArraySchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.ai.shared.AiToolCategory;
import stroom.dashboard.shared.ValidateExpressionResult;
import stroom.docref.DocRef;
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
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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

/// `sequence_query` - an ordered hunt across sources at volume, run in Elasticsearch as one EQL sequence over one or
/// more Elastic Index docs on one cluster ([ElasticEql]): e.g. a device plugged in and then a file copied to it, a
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
class SequenceQueryTool extends AbstractWorkspaceTool {

    static final String NAME = "sequence_query";
    static final String STEPS_ARG = "steps";
    static final String UNTIL_ARG = "until";
    static final String MAXSPAN_ARG = "maxspan";
    static final String TIME_FROM_ARG = "timeFrom";
    static final String TIME_TO_ARG = "timeTo";
    static final String LIMIT_ARG = "limit";
    static final String RUN_ANYWAY_ARG = "runAnyway";
    static final String INDEX_KEY = "index";
    static final String WHERE_KEY = "where";
    static final String BY_KEY = "by";
    static final String MISSING_KEY = "missing";
    static final String RUNS_KEY = "runs";
    static final String FIELDS_KEY = "fields";
    static final String CASE_INSENSITIVE_KEY = "caseInsensitive";
    static final String ANY_OF_KEY = "any_of";
    static final String LENGTH_KEY = "length";
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
    static final String MANY_KEYS_NOTE = "EQL is slowest with very many distinct join values (it keeps a partial "
                                         + "sequence for each): narrow the first step to fewer entities, or use a "
                                         + "shorter time range.";
    /// The most events Elasticsearch fetches by id for one search's sequences (`index.max_result_window`, 10,000 by
    /// default): asking for more sequences than fit is refused ("Too many ids specified, allowed max result window
    /// is [10000]", seen in the benchmark of 2026-11-20 with 10,000 two-step sequences), so the limit is lowered to
    /// what fits ([#mostSequences]).
    static final int MAX_FETCHED_EVENTS = 10_000;
    private static final List<Duration> OTHER_WINDOWS = List.of(Duration.ofMinutes(1), Duration.ofMinutes(15),
            Duration.ofHours(1));
    private static final int[] ESQL_VERSION = {8, 14};
    private static final int[] MISSING_VERSION = {8, 8};
    private static final int[] RUNS_VERSION = {8, 7};
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String FIELDS_HINT = "describe_data_source gives the index's fields";
    private static final Set<String> TEXT_NATIVE_TYPES = Set.of("text", "match_only_text");
    // The types a text field's subfield may have for EQL to search it in its place, the most fitting first.
    private static final List<String> SUBFIELD_TYPES = List.of("keyword", "constant_keyword", "wildcard");

    private final Provider<ElasticEql> elasticEqlProvider;
    private final Provider<ElasticEsql> elasticEsqlProvider;
    private final DataSourceAccess dataSourceAccess;
    private final Provider<QueryService> queryServiceProvider;
    private final AiAuditLog auditLog;

    @Inject
    SequenceQueryTool(final ChatWorkspace workspace,
                    final Provider<ElasticEql> elasticEqlProvider,
                    final Provider<ElasticEsql> elasticEsqlProvider,
                    final DataSourceAccess dataSourceAccess,
                    final Provider<QueryService> queryServiceProvider,
                    final AiAuditLog auditLog) {
        super(workspace);
        this.elasticEqlProvider = elasticEqlProvider;
        this.elasticEsqlProvider = elasticEsqlProvider;
        this.dataSourceAccess = dataSourceAccess;
        this.queryServiceProvider = queryServiceProvider;
        this.auditLog = auditLog;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .checksDocuments(DocumentPermission.USE)
                .note("Use on every Elastic Index doc is checked, as for any search, and again by Stroom's "
                      + "Elasticsearch module when the sequence runs.")
                .build();
    }

    /// Offered in the chat only to a user who can use an Elastic Index. No cluster's version is looked up, so no
    /// cluster is called as a chat starts: EQL sequences work from Elasticsearch 7.10, older than the clusters
    /// Stroom's Elasticsearch client works with, and a call on an older one is still refused as it runs.
    ///
    /// @return True if the user can use at least one Elastic Index doc.
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

    // The definition is full, as it is offered only once the model turns on its group (sequence_hunting, see
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
                .addEnumProperty("condition", conditionNames(), "The condition (default EQUALS).")
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
                .addStringProperty(INDEX_KEY, "The Elastic Index doc's uuid or name.")
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
                .required(INDEX_KEY, BY_KEY)
                .build();
        final ObjectSchema until = ObjectSchema.builder()
                .description("Optional: a step that ends a sequence before it is complete: its doc, conditions and "
                             + "join fields, as a step's (no missing, runs or fields).")
                .addStringProperty(INDEX_KEY, "The Elastic Index doc's uuid or name.")
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
                        .general("Use for any 'X, then Y, by the same user/host, within N' question on Elastic "
                                 + "Indexes, in one doc or across several, rather than joining the results of "
                                 + "separate queries")
                        .chatOnly(" (esql_query, search_events)")
                        .general(" by hand. Finds ordered sequences of events across one or more Elastic Index "
                                 + "docs on one cluster, matched in Elasticsearch as one EQL sequence: e.g. a USB "
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
                                 + "selective, and a sequence too big for the time limit is refused before it runs "
                                 + "(narrow the window or the first step, or runAnyway=true). The result gives the "
                                 + "EQL, a summary per entity, the sequences with each step's StreamId/EventId, and "
                                 + "a validated StroomQL review query per doc, for a saved query")
                        .chatOnly(" (propose_query)")
                        .general(" or a dashboard with the event preview")
                        .chatOnly(" (propose_dashboard)")
                        .general(". Each reads one doc: when the hunt spans several, ask the user which doc to "
                                 + "review")
                        .chatOnly(" (ask_user, the docs as options)")
                        .general(", then offer that doc's. Sequences come straight from Elasticsearch, as ES|QL "
                                 + "counts do, and may include streams the user can't see or deleted ones. Refused "
                                 + "for a Lucene index")
                        .chatOnly(" (use run_stroomql)")
                        .general(", docs on different clusters or with different time fields, and nested fields. "
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
                        .addStringProperty(TIME_FROM_ARG, "Optional: from this time on the docs' time field (ISO "
                                                          + "8601, or Stroom's day()-7d).")
                        .addStringProperty(TIME_TO_ARG, "Optional: up to this time (ISO 8601, or Stroom's now()).")
                        .addIntegerProperty(LIMIT_ARG, "The most sequences (default " + DEFAULT_LIMIT + ", at most "
                                                       + ElasticEql.MAX_SEQUENCES + ").")
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

    @Override
    protected ToolResult run(final ToolCall request, final ToolCallContext context) {
        final SessionWorkspace workspace = sessionWorkspace(context);
        final Duration timeLimit = timeLimit(context);
        final Instant deadline = Instant.now().plus(timeLimit);
        final List<String> fieldNotes = new ArrayList<>();
        final List<GivenStep> given = givenSteps(request, fieldNotes);
        final GivenStep givenUntil = givenUntil(request, fieldNotes);

        // The docs, in the order the steps first name them, each checked and with its fields.
        final List<DocRef> docs = new ArrayList<>();
        final Map<String, DocRef> byGiven = new LinkedHashMap<>();
        final List<GivenStep> all = new ArrayList<>(given);
        if (givenUntil != null) {
            all.add(givenUntil);
        }
        for (final GivenStep step : all) {
            byGiven.computeIfAbsent(step.index(), name -> {
                final DocRef doc = EsqlQueryTool.elasticIndex(dataSourceAccess, name, "EQL");
                if (!docs.contains(doc)) {
                    docs.add(doc);
                }
                return doc;
            });
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
        final Target target = target(docs);
        final List<Step> steps = new ArrayList<>();
        for (int i = 0; i < given.size(); i++) {
            steps.add(step(given.get(i), "Step " + (i + 1), byGiven, docs, fieldsByDoc, target, fieldNotes));
        }
        final Step until = givenUntil == null
                ? null
                : step(givenUntil, "The until step", byGiven, docs, fieldsByDoc, target, fieldNotes);
        checkJoins(steps, until);
        checkVersion(target.version(), steps);
        if (docs.size() > 1) {
            steps.replaceAll(step -> step.withRestricted(needsRestriction(step, docs, fieldsByDoc)));
        }
        final Step untilStep = until == null || docs.size() == 1
                ? until
                : until.withRestricted(needsRestriction(until, docs, fieldsByDoc));

        final String givenMaxspan = stringArg(request, MAXSPAN_ARG);
        final Duration maxspan;
        final String maxspanSource;
        if (givenMaxspan != null) {
            maxspan = EqlQueryWriter.maxspan(givenMaxspan);
            maxspanSource = "as asked";
        } else {
            maxspan = askMaxspan(request, context);
            maxspanSource = "the user's answer";
        }
        final String timeFrom = stringArg(request, TIME_FROM_ARG);
        final String timeTo = stringArg(request, TIME_TO_ARG);
        final TimeRange timeRange = timeFrom == null && timeTo == null
                ? null
                : new TimeRange(null, timeFrom, timeTo);
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
                : WorkspacePath.parse("/outputs/eql/eql-" + FILE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC))
                                      + ".md");
        final List<String> columns = EqlSequenceResults.columns(steps);
        final List<List<String>> cells = rows.stream().map(row -> EqlSequenceResults.cells(row, steps)).toList();
        if (!cells.isEmpty()) {
            ToolTables.write(workspace, out, columns, cells, docs.getFirst().getUuid());
        }
        final List<Entity> entities = EqlSequenceResults.entities(rows);
        final List<ReviewNote> reviews = reviews(docs, fieldsByDoc, result.timeField(), rows, steps,
                limits(context).getMaxQueryLength());
        final boolean truncated = result.sequences().size() >= limit;
        final Outcome outcome = new Outcome(docs, result, maxspan, maxspanSource, timeRange, limit, limitNote,
                truncated, preflight, steps, columns, cells, entities, EqlSequenceResults.untimed(rows), reviews, out,
                fieldNotes.stream().distinct().toList());
        final Map<String, Object> structured = structured(outcome);
        return context.session().isChat()
                ? ToolResult.text(markdown(outcome)).withStructuredContent(structured)
                : ToolResult.text(JsonUtil.writeValueAsString(structured, false)).withStructuredContent(structured);
    }

    // ------------------------------------------------------------------------------------------------
    // Arguments

    private static List<GivenStep> givenSteps(final ToolCall request, final List<String> notes) {
        final Object value = argsMap(request).get(STEPS_ARG);
        final List<?> items = value instanceof final List<?> list
                ? list
                : parsedList(value);
        if (items.size() < 2) {
            throw new WorkspaceException("Give steps: 2 or more, in order, each with its index, where conditions and "
                                         + "join fields (by).");
        }
        final List<GivenStep> steps = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (!(items.get(i) instanceof final Map<?, ?> map)) {
                throw new WorkspaceException("Step " + (i + 1) + " isn't an object with index, where and by.");
            }
            steps.add(givenStep(map, "Step " + (i + 1), notes));
        }
        if (steps.stream().allMatch(GivenStep::missing)) {
            throw new WorkspaceException("Every step is missing=true: at least one step must be an event that "
                                         + "happens.");
        }
        return steps;
    }

    private static GivenStep givenUntil(final ToolCall request, final List<String> notes) {
        final Object value = argsMap(request).get(UNTIL_ARG);
        if (value == null || (value instanceof final String text && text.isBlank())) {
            return null;
        }
        final Object parsed = value instanceof final String text
                ? parsedObject(text)
                : value;
        if (!(parsed instanceof final Map<?, ?> map)) {
            throw new WorkspaceException("until is one step: an object with index, where and by.");
        }
        final GivenStep until = givenStep(map, "The until step", notes);
        if (until.missing() || until.runs() > 1 || !until.fields().isEmpty()) {
            throw new WorkspaceException("The until step takes index, where and by only.");
        }
        return until;
    }

    private static GivenStep givenStep(final Map<?, ?> map, final String what, final List<String> notes) {
        final String index = map.get(INDEX_KEY) == null
                ? ""
                : String.valueOf(map.get(INDEX_KEY)).strip();
        if (index.isEmpty()) {
            throw new WorkspaceException(what + " has no index: give the Elastic Index doc's uuid or name.");
        }
        final List<Filter> filters = filters(map.get(WHERE_KEY), what, WHERE_KEY);
        final List<Filter> anyOf = filters(map.get(ANY_OF_KEY), what, ANY_OF_KEY);
        if (anyOf.size() == 1) {
            throw new WorkspaceException(what + "'s " + ANY_OF_KEY + " has one condition: give 2 or more (at least "
                                         + "one must hold), or put the one in where.");
        }
        final boolean missing = truthy(map.get(MISSING_KEY));
        final int runs = runs(map.get(RUNS_KEY), what);
        if (missing && runs > 1) {
            throw new WorkspaceException(what + " has missing=true and runs: an event that must not happen can't "
                                         + "repeat. Give one or the other.");
        }
        final List<String> fields = names(map.get(FIELDS_KEY), what, FIELDS_KEY, notes);
        if (fields.size() > MAX_STEP_FIELDS) {
            throw new WorkspaceException(what + " asks for " + fields.size() + " fields: show at most "
                                         + MAX_STEP_FIELDS + ".");
        }
        if (missing && !fields.isEmpty()) {
            throw new WorkspaceException(what + " is missing=true, so it has no events to show fields of.");
        }
        return new GivenStep(index, filters, anyOf, names(map.get(BY_KEY), what, BY_KEY, notes), missing, runs, fields);
    }

    // A list of conditions in the where form: where's, or an any_of group's (one level: no group inside).
    private static List<Filter> filters(final Object given, final String what, final String key) {
        final List<?> conditions = given == null
                ? List.of()
                : given instanceof final List<?> list
                        ? list
                        : given instanceof final Map<?, ?> one
                                ? List.of(one)
                                : parsedList(given);
        final List<Filter> filters = new ArrayList<>();
        for (final Object item : conditions) {
            if (item instanceof final Map<?, ?> term && term.containsKey(ANY_OF_KEY)) {
                throw new WorkspaceException(what + "'s " + key + " has a condition with its own " + ANY_OF_KEY
                                             + ": give one " + ANY_OF_KEY + " per step, of plain conditions.");
            }
            if (!(item instanceof final Map<?, ?> term) || term.get("field") == null
                || String.valueOf(term.get("field")).isBlank()) {
                throw new WorkspaceException(what + "'s " + key + " is a list of {field, condition, value}, each "
                                             + "with a field.");
            }
            final String condition = conditionName(term.get("condition"), what);
            filters.add(new Filter(String.valueOf(term.get("field")).strip(), NOT_IN.equals(condition)
                    ? Condition.IN
                    : Condition.valueOf(condition),
                    term.get("value") == null
                            ? ""
                            : String.valueOf(term.get("value")),
                    truthy(term.get(CASE_INSENSITIVE_KEY)),
                    NOT_IN.equals(condition),
                    truthy(term.get(LENGTH_KEY))));
        }
        return filters;
    }

    // The conditions a step takes: search_events's, and NOT_IN.
    private static List<String> conditionNames() {
        final List<String> names = new ArrayList<>(IndexEvents.CONDITIONS.stream()
                .filter(EqlQueryWriter::takes)
                .map(Condition::name)
                .toList());
        names.add(NOT_IN);
        return names;
    }

    // The condition's name, checked: one of conditionNames().
    private static String conditionName(final Object given, final String what) {
        if (given == null || String.valueOf(given).isBlank()) {
            return Condition.EQUALS.name();
        }
        final String name = String.valueOf(given).strip().toUpperCase(Locale.ROOT);
        if (conditionNames().contains(name)) {
            return name;
        }
        throw new WorkspaceException(what + ": condition '" + given + "' isn't one of "
                                     + String.join(", ", conditionNames()) + ".");
    }

    private static int runs(final Object given, final String what) {
        if (given == null || String.valueOf(given).isBlank()) {
            return 1;
        }
        final int runs;
        try {
            final double number = Double.parseDouble(String.valueOf(given).strip());
            runs = number == Math.rint(number)
                    ? (int) number
                    : -1;
        } catch (final NumberFormatException e) {
            throw new WorkspaceException(what + ": runs is a whole number, from 1 to " + EqlQueryWriter.MAX_RUNS
                                         + ".");
        }
        if (runs < 1 || runs > EqlQueryWriter.MAX_RUNS) {
            throw new WorkspaceException(what + ": runs is a whole number, from 1 to " + EqlQueryWriter.MAX_RUNS
                                         + ".");
        }
        return runs;
    }

    private static boolean truthy(final Object value) {
        return value instanceof final Boolean flag
                ? flag
                : value != null && "true".equalsIgnoreCase(String.valueOf(value).strip());
    }

    // Field names: a list of strings, or comma-separated text. A list of one-name lists (seen: a small model sent
    // by as [["username"], ["host"]]) is read as the names, said in notes; any other shape is refused.
    private static List<String> names(final Object value,
                                      final String what,
                                      final String key,
                                      final List<String> notes) {
        final List<String> items = new ArrayList<>();
        if (value instanceof final List<?> list) {
            for (final Object item : list) {
                if (item instanceof final String name) {
                    items.add(name.strip());
                } else if (item instanceof final List<?> one && one.size() == 1
                           && one.getFirst() instanceof final String name) {
                    items.add(name.strip());
                    notes.add("Read the one-name lists in " + key + " as the names: give field names as strings, e.g. "
                              + "[\"user\", \"host\"].");
                } else if (item != null) {
                    throw new WorkspaceException(what + "'s " + key + ": give field names as strings, e.g. "
                                                 + "[\"user\", \"host\"].");
                }
            }
        } else if (value != null) {
            for (final String item : String.valueOf(value).split(",")) {
                items.add(item.strip());
            }
        }
        return items.stream().filter(item -> !item.isEmpty()).toList();
    }

    private static List<?> parsedList(final Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return List.of();
        }
        final Object parsed = parsedObject(String.valueOf(value));
        if (parsed instanceof final List<?> list) {
            return list;
        }
        throw new WorkspaceException("Give steps as a list of objects, each with index, where and by.");
    }

    private static Object parsedObject(final String text) {
        try {
            return JsonUtil.readValue(text, Object.class);
        } catch (final RuntimeException e) {
            throw new WorkspaceException("The steps can't be read: give them as JSON objects.");
        }
    }

    // A step, checked against its doc's fields: a text field searched or joined on is swapped for a subfield EQL
    // can search (said in notes), and a nested field is refused.
    private static Step step(final GivenStep given,
                             final String what,
                             final Map<String, DocRef> byGiven,
                             final List<DocRef> docs,
                             final Map<DocRef, List<QueryField>> fieldsByDoc,
                             final Target target,
                             final List<String> notes) {
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
                .map(filter -> filter.onField(swapped.getOrDefault(filter.field(), filter.field())))
                .toList();
        final List<Filter> anyOf = given.anyOf().stream()
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

    // The docs' cluster and time field, refused naming what differs.
    private Target target(final List<DocRef> docs) {
        try {
            return elasticEqlProvider.get().target(docs);
        } catch (final Refusal refusal) {
            throw new WorkspaceException(refusal(refusal, Map.of()));
        } catch (final PermissionException e) {
            throw new WorkspaceException(notUsable(docs, e));
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
            throw new WorkspaceException(maxspanRefusal(suggested));
        }
        final Map<String, Duration> options = maxspanOptions(suggested);
        final ElicitResult answer = context.userQuestions().ask(NAME,
                UserQuestions.formCallId(request.id(), MAXSPAN_FORM), MAXSPAN_QUESTION + " " + WIDER_NOTE,
                maxspanForm(options));
        if (answer.action() != ElicitResult.Action.ACCEPT) {
            throw new WorkspaceException("The user didn't choose a window, so nothing was run. Ask them in the chat "
                                         + "how far apart the steps may be, then call again with maxspan.");
        }
        final String chosen = NullSafe.trim(NullSafe.map(answer.content()).get(MAXSPAN_FORM) == null
                ? null
                : String.valueOf(NullSafe.map(answer.content()).get(MAXSPAN_FORM)));
        return chosenMaxspan(chosen, options);
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

    /// @param suggested The configured window.
    /// @return The form's choices, the suggested one first (marked the default), then a few others, then another
    /// interval, each with its window (null for another interval).
    static Map<String, Duration> maxspanOptions(final Duration suggested) {
        final Map<String, Duration> options = new LinkedHashMap<>();
        options.put(windowLabel(suggested) + " (the default)", suggested);
        for (final Duration other : OTHER_WINDOWS) {
            if (!other.equals(suggested)) {
                options.put(windowLabel(other), other);
            }
        }
        options.put(ANOTHER_INTERVAL, null);
        return options;
    }

    /// @param options The choices.
    /// @return The form: one text answer, the choices as its suggestions, the first its default.
    static Map<String, Object> maxspanForm(final Map<String, Duration> options) {
        final Map<String, Object> field = new LinkedHashMap<>();
        field.put("type", "string");
        field.put("title", MAXSPAN_QUESTION);
        field.put("examples", List.copyOf(options.keySet()));
        field.put("default", options.keySet().iterator().next());
        field.put("maxLength", 60);
        final Map<String, Object> form = new LinkedHashMap<>();
        form.put("type", "object");
        form.put("properties", Map.of(MAXSPAN_FORM, field));
        form.put("required", List.of(MAXSPAN_FORM));
        return form;
    }

    /// @param chosen  The user's answer: a choice, or an interval in their own words.
    /// @param options The choices.
    /// @return The window.
    static Duration chosenMaxspan(final String chosen, final Map<String, Duration> options) {
        if (NullSafe.isBlankString(chosen) || ANOTHER_INTERVAL.equals(chosen)) {
            throw new WorkspaceException("The user chose another interval but didn't give it, so nothing was run. "
                                         + "Ask them in the chat how far apart the steps may be, then call again "
                                         + "with maxspan.");
        }
        final Duration option = options.get(chosen);
        if (option != null) {
            return option;
        }
        try {
            return EqlQueryWriter.maxspan(chosen.replaceAll("(?i)\\s*\\(the default\\)$", ""));
        } catch (final WorkspaceException e) {
            throw new WorkspaceException("The user's window '" + chosen + "' can't be read, so nothing was run. Ask "
                                         + "them in the chat how far apart the steps may be (e.g. 10m), then call "
                                         + "again with maxspan.");
        }
    }

    /// @param suggested The configured window.
    /// @return What a call without a window is told where the user can't be asked in a form.
    static String maxspanRefusal(final Duration suggested) {
        return "Give maxspan: how far apart the first and last events may be, at most 24h. Ask the user: '"
               + MAXSPAN_QUESTION + "' Suggest " + EqlQueryWriter.maxspanText(suggested) + " (the default), or "
               + String.join(", ", OTHER_WINDOWS.stream()
                .filter(other -> !other.equals(suggested))
                .map(EqlQueryWriter::maxspanText)
                .toList()) + ", or another interval. " + WIDER_NOTE + " Then call again with maxspan= their answer.";
    }

    private static String windowLabel(final Duration duration) {
        final long ms = duration.toMillis();
        if (ms % 3_600_000 == 0) {
            return plural(ms / 3_600_000, "hour");
        } else if (ms % 60_000 == 0) {
            return plural(ms / 60_000, "minute");
        } else if (ms % 1_000 == 0) {
            return plural(ms / 1_000, "second");
        }
        return plural(ms, "millisecond");
    }

    private static String plural(final long n, final String unit) {
        return n + " " + unit + (n == 1
                ? ""
                : "s");
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

    private static String countsText(final List<Long> counts) {
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

    private static String markdown(final Outcome outcome) {
        final Result result = outcome.result();
        final StringBuilder sb = new StringBuilder("Ran the EQL sequence on ")
                .append(String.join(", ", outcome.docs().stream().map(DataSourceAccess::describe).toList()))
                .append(" (Elasticsearch ").append(result.version()).append(", ").append(result.tookMs())
                .append(" ms), with maxspan ").append(EqlQueryWriter.maxspanText(outcome.maxspan())).append(" (")
                .append(outcome.maxspanSource()).append("):\n\n```\n").append(result.query()).append("\n```\n");
        outcome.fieldNotes().forEach(note -> sb.append(note).append('\n'));
        if (outcome.timeRange() != null) {
            sb.append("Time: ").append(NullSafe.nonBlankStringElse(outcome.timeRange().getFrom(), "any"))
                    .append(" to ").append(NullSafe.nonBlankStringElse(outcome.timeRange().getTo(), "any"))
                    .append(".\n");
        }
        final String counted = preflightText(outcome.preflight());
        if (!counted.isEmpty()) {
            sb.append('\n').append(counted).append('\n');
        }
        sb.append('\n').append(sequencesNote(outcome)).append('\n');
        if (!outcome.entities().isEmpty()) {
            final List<String> columns = new ArrayList<>(outcome.steps().getFirst().joinFields());
            columns.addAll(List.of("sequences", "first", "last", "shortest gap (s)"));
            final List<List<String>> cells = outcome.entities().stream().map(SequenceQueryTool::entityCells).toList();
            sb.append("\nBy entity (sequences found, at least these):\n\n")
                    .append(ToolTables.preview(columns, cells, SHOWN_ROWS)).append('\n');
            sb.append("\nSequences:\n\n").append(ToolTables.preview(outcome.columns(), outcome.cells(), SHOWN_ROWS))
                    .append("\n\nThe sequences are in ").append(outcome.out()).append(" (each step's StreamId and "
                                                                                    + "EventId, for fetch_events and "
                                                                                    + "search_events).\n");
        }
        sb.append('\n').append(CAVEAT);
        if (!outcome.reviews().isEmpty()) {
            sb.append("\n\n").append(reviewHint(outcome.reviews().size()));
            for (final ReviewNote review : outcome.reviews()) {
                sb.append("\n\n").append(DataSourceAccess.describe(review.review().doc())).append(": ")
                        .append(review.note());
                if (review.invalid() != null) {
                    sb.append(" It isn't valid StroomQL (").append(review.invalid()).append("), so don't offer it.");
                }
                sb.append("\n\n```\n").append(review.review().query()).append("\n```");
            }
        }
        return sb.toString();
    }

    /// @param reviews How many docs have a review query.
    /// @return What to do with them.
    static String reviewHint(final int reviews) {
        return reviews == 1
                ? "The review query (validated StroomQL) looks at the events in Stroom: offer it with propose_query, "
                  + "or propose_dashboard for a table with the event preview."
                : "There is a review query (validated StroomQL) per doc, and a query or dashboard reads one doc: ask "
                  + "the user which doc to review (ask_user, the docs as options), then offer that doc's with "
                  + "propose_query, or propose_dashboard for a table with the event preview.";
    }

    private static List<String> entityCells(final Entity entity) {
        final List<String> cells = new ArrayList<>(entity.keys());
        cells.add(String.valueOf(entity.sequences()));
        cells.add(EqlSequenceResults.iso(entity.first()));
        cells.add(EqlSequenceResults.iso(entity.last()));
        cells.add(EqlSequenceResults.seconds(entity.shortestSpan()));
        return cells;
    }

    private static String preflightText(final Preflight preflight) {
        final StringBuilder sb = new StringBuilder();
        if (preflight.counted()) {
            sb.append("Counted first: ").append(countsText(preflight.counts())).append(" events.");
        } else if (preflight.skippedNote() != null) {
            sb.append(preflight.skippedNote());
        }
        preflight.notes().forEach(note -> sb.append(' ').append(note));
        return sb.toString().strip();
    }

    /// What the sequences are: how many, and whether the limit cut them short.
    ///
    /// @param outcome The call's outcome.
    /// @return The note.
    static String sequencesNote(final Outcome outcome) {
        final int count = outcome.result().sequences().size();
        final StringBuilder sb = new StringBuilder().append(count).append(" sequence").append(count == 1
                ? ""
                : "s").append(" found, for ").append(outcome.entities().size()).append(" entit")
                .append(outcome.entities().size() == 1
                        ? "y"
                        : "ies").append('.');
        if (count > 0) {
            sb.append(' ').append(AT_LEAST);
        } else if (outcome.docs().size() > 1) {
            sb.append(' ').append(NO_MATCH_ACROSS_DOCS);
        }
        if (outcome.untimed() > 0) {
            sb.append(' ').append(untimedNote(outcome.untimed(), count));
        }
        if (outcome.limitNote() != null) {
            sb.append(' ').append(outcome.limitNote());
        }
        if (outcome.truncated()) {
            sb.append(" They reached the limit (").append(outcome.limit()).append("), so they are cut short: these "
                                                                                + "are the latest ")
                    .append(outcome.limit()).append(", and there may be more. Narrow the window or the steps, or "
                                                    + "raise limit (at most " + outcome.mostSequences() + "), and "
                                                    + "don't present them as all.");
        }
        return sb.toString();
    }

    /// @param untimed   How many sequences have no time.
    /// @param sequences How many sequences there are.
    /// @return What that means for their times.
    static String untimedNote(final int untimed, final int sequences) {
        return untimed + " of the " + sequences + " sequence" + (sequences == 1
                ? ""
                : "s") + " came back with no time on " + (untimed == 1
                ? "its"
                : "their") + " events (Elasticsearch didn't give the time field), so " + (untimed == 1
                ? "its"
                : "their") + " times and gaps are empty: don't read them as quick, and check the events with "
               + "fetch_events (by StreamId and EventId) before giving times.";
    }

    private static Map<String, Object> structured(final Outcome outcome) {
        final Result result = outcome.result();
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("indexes", outcome.docs().stream().map(doc -> {
            final Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("type", doc.getType());
            ref.put("uuid", doc.getUuid());
            ref.put("name", doc.getName());
            return ref;
        }).toList());
        map.put("index_names", result.indexNames());
        map.put("query", result.query());
        map.put("maxspan", EqlQueryWriter.maxspanText(outcome.maxspan()));
        map.put("maxspan_source", outcome.maxspanSource());
        if (outcome.timeRange() != null) {
            final Map<String, Object> time = new LinkedHashMap<>();
            time.put("from", outcome.timeRange().getFrom());
            time.put("to", outcome.timeRange().getTo());
            map.put("time_range", time);
        }
        map.put("time_field", result.timeField());
        if (!outcome.fieldNotes().isEmpty()) {
            map.put("field_notes", outcome.fieldNotes());
        }
        map.put("version", result.version());
        map.put("took_ms", result.tookMs());
        final Preflight preflight = outcome.preflight();
        if (preflight.counted()) {
            final List<Object> counts = new ArrayList<>(preflight.counts());
            map.put("step_counts", counts);
        }
        if (!preflight.notes().isEmpty() || preflight.skippedNote() != null) {
            map.put("preflight_notes", preflightText(preflight));
        }
        map.put("sequence_count", result.sequences().size());
        map.put("entity_count", outcome.entities().size());
        if (outcome.untimed() > 0) {
            map.put("untimed_sequence_count", outcome.untimed());
        }
        map.put("limit", outcome.limit());
        map.put("truncated", outcome.truncated());
        map.put("note", sequencesNote(outcome));
        map.put("entities", outcome.entities().stream().limit(SHOWN_ROWS).map(entity -> {
            final Map<String, Object> row = new LinkedHashMap<>();
            row.put("keys", entity.keys());
            row.put("sequences", entity.sequences());
            row.put("first", EqlSequenceResults.iso(entity.first()));
            row.put("last", EqlSequenceResults.iso(entity.last()));
            row.put("shortest_gap_s", EqlSequenceResults.seconds(entity.shortestSpan()));
            return row;
        }).toList());
        map.put("columns", outcome.columns());
        map.put("rows", outcome.cells().stream().limit(SHOWN_ROWS).toList());
        if (!outcome.cells().isEmpty()) {
            map.put("table", outcome.out().toString());
        }
        map.put("caveat", CAVEAT);
        map.put("review_queries", outcome.reviews().stream().map(review -> {
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("index", review.review().doc().getName());
            entry.put("uuid", review.review().doc().getUuid());
            entry.put("query", review.review().query());
            entry.put("exact", review.review().exact());
            entry.put("valid", review.invalid() == null);
            if (review.invalid() != null) {
                entry.put("invalid", review.invalid());
            }
            entry.put("note", review.note());
            return entry;
        }).toList());
        if (!outcome.reviews().isEmpty()) {
            map.put("review_hint", reviewHint(outcome.reviews().size()));
        }
        return map;
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
    /// @param fields  Fields to show.
    private record GivenStep(String index,
                             List<Filter> filters,
                             List<Filter> anyOf,
                             List<String> by,
                             boolean missing,
                             int runs,
                             List<String> fields) {

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
    /// @param out           The table's file.
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
                   List<String> fieldNotes) {

        /// @return The most sequences these steps can return ([#mostSequences(List)]).
        int mostSequences() {
            return SequenceQueryTool.mostSequences(steps);
        }
    }
}
