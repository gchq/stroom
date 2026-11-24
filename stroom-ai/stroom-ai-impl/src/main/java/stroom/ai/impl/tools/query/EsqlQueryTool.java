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

package stroom.ai.impl.tools.query;

import stroom.ai.api.ElasticEsql;
import stroom.ai.api.ElasticEsql.Reason;
import stroom.ai.api.ElasticEsql.Refusal;
import stroom.ai.api.ElasticEsql.Request;
import stroom.ai.api.ElasticEsql.Result;
import stroom.ai.impl.AiAuditLog;
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
import stroom.ai.impl.tools.query.IndexEvents.Term;
import stroom.ai.impl.tools.time.ToolTimeZone;
import stroom.ai.impl.tools.time.UserTimeZones;
import stroom.ai.impl.tools.validation.CloseMatches;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.index.shared.LuceneIndexDoc;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.TimeRange;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.QueryField;
import stroom.query.impl.QueryService;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.security.shared.DocumentPermission;
import stroom.task.api.TaskTerminatedException;
import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// `esql_query` - runs an ES|QL query on an Elastic Index doc's index, as the user, through Stroom's Elasticsearch
/// module ([ElasticEsql]): totals, top N and grouped questions answered by Elasticsearch itself, rather than by
/// scrolling every document through Stroom as run_stroomql does. The model gives the commands after `FROM`; the
/// index is the doc's. The rows are written to the workspace as a table and shown (Markdown in the chat, JSON over
/// MCP), with whether they were cut short and, if so, how many rows (groups) there are in all. The documents'
/// streams aren't looked up, as a Stroom search looks up its hits' (for speed, the user's choice): the tool's
/// description says its counts and sums may differ from run_stroomql's and dashboards'.
public class EsqlQueryTool extends AbstractWorkspaceTool {

    static final String NAME = "esql_query";
    static final String INDEX_ARG = "index";
    static final String PIPELINE_ARG = "pipeline";
    static final String WHERE_ARG = SearchEventsTool.WHERE_ARG;
    static final String TIME_FROM_ARG = "timeFrom";
    static final String TIME_TO_ARG = "timeTo";
    static final String LIMIT_ARG = "limit";
    /// The most rows shown in the result; the rest are in the table file.
    static final int SHOWN_ROWS = 100;
    private static final int FIELDS_NAMED = 60;
    private static final int MAX_FIELDS = 2_000;
    private static final String FIELDS_HINT = "describe_data_source gives the index's fields";
    // Elasticsearch's warning that a single-value function or comparison met a multi-valued field.
    static final String MULTI_VALUE_WARNING = "single-value function encountered multi-value";
    static final String MULTI_VALUE_HINT = "A field the query compares or passes to a function holds several "
                                           + "values in some documents: there the comparison or function gives "
                                           + "null, so WHERE drops those documents and EVAL gives null for them. "
                                           + "MV_EXPAND the field first to test each value (e.g. '| MV_EXPAND "
                                           + "recipient | WHERE recipient == \"x\"'), or use an MV_ function "
                                           + "(e.g. MV_COUNT, MV_FIRST).";
    // Elasticsearch's refusal of a backslash that isn't one of the escapes a "..." string takes.
    private static final Pattern BACKSLASH_IN_STRING = Pattern.compile("token recognition error at: '\"[^']*\\\\");
    static final String BACKSLASH_HINT = "In a \"...\" string a backslash starts an escape (\\t, \\n, \\r, \\\", "
                                         + "\\\\ only): double it for a regex (\"a\\\\.b\"), or write the string "
                                         + "in triple quotes, which keep it as typed (\"\"\"a\\.b\"\"\").";
    // ES|QL's refusal of a keyword written where a name is wanted: 'no viable alternative at input 'first =''
    // (first, last) or 'mismatched input 'by' expecting ...' (the others), as Elasticsearch 9.5.3 gives them.
    private static final Pattern NO_VIABLE_KEYWORD = Pattern.compile(
            "no viable alternative at input '(\\w+)\\s*=");
    private static final Pattern MISMATCHED_KEYWORD = Pattern.compile("mismatched input '(\\w+)' expecting");
    /// ES|QL's keywords that can't name a column unless in backquotes, as Elasticsearch 9.5.3 refuses them.
    static final Set<String> RESERVED_WORDS = Set.of("first", "last", "by", "like", "rlike", "in", "and", "or",
            "not", "null", "true", "false", "on", "with", "where", "desc", "asc", "nulls", "is");
    // ES|QL has no IF (nor IIF): 'Unknown function [IF]'.
    private static final Pattern UNKNOWN_IF = Pattern.compile("(?i)Unknown function \\[i?if]");
    static final String IF_HINT = "ES|QL has no IF function: use CASE(condition, then, else), e.g. "
                                  + "'| EVAL kind = CASE(bytes > 1000000, \"large\", \"small\")'.";
    // An RLIKE pattern: a triple-quoted string, or a "..." one.
    private static final Pattern RLIKE_PATTERN = Pattern.compile(
            "(?i)\\bRLIKE\\s*(?:\"\"\"(.*?)\"\"\"|\"((?:[^\"\\\\]|\\\\.)*)\")", Pattern.DOTALL);
    static final String MAX_ROWS_NOTE = ElasticEsql.MAX_ROWS + " rows is the most a query gives, and ES|QL has no "
                                        + "offset: to see the rest, split the query by time window (timeFrom and "
                                        + "timeTo) or narrow it.";
    static final String RLIKE_AT_NOTE = "The pipeline's RLIKE pattern has an unescaped @, which in Elasticsearch's "
                                        + "regular expressions matches any string, so the rows may be wrong: "
                                        + "escape it (\"a\\\\@b\", or \"\"\"a\\@b\"\"\") or use LIKE for a simple "
                                        + "pattern.";

    /// What a result in a zone other than UTC says when the cluster refused the zone (Elasticsearch's
    /// `time_zone`), so the pipeline's own date work ran in UTC: only the time range (timeFrom, timeTo) and the
    /// rows' times shown are in the zone.
    static final String ESQL_UTC_NOTE = "The cluster didn't take the time zone, so the pipeline's own date "
                                        + "functions and dates without an offset (BUCKET, DATE_TRUNC, "
                                        + "DATE_EXTRACT, DATE_FORMAT) work in UTC.";

    private final Provider<ElasticEsql> elasticEsqlProvider;
    private final DataSourceAccess dataSourceAccess;
    private final Provider<QueryService> queryServiceProvider;
    private final AiAuditLog auditLog;

    @Inject
    EsqlQueryTool(final ChatWorkspace workspace,
                  final Provider<ElasticEsql> elasticEsqlProvider,
                  final DataSourceAccess dataSourceAccess,
                  final Provider<QueryService> queryServiceProvider,
                  final AiAuditLog auditLog) {
        super(workspace);
        this.elasticEsqlProvider = elasticEsqlProvider;
        this.dataSourceAccess = dataSourceAccess;
        this.queryServiceProvider = queryServiceProvider;
        this.auditLog = auditLog;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .checksDocuments(DocumentPermission.USE)
                .note("Use on the Elastic Index doc is checked, as for any search, and again by Stroom's "
                      + "Elasticsearch module when the query runs.")
                .build();
    }

    @Override
    public boolean usesRunSlot() {
        // It runs a search.
        return true;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.SEARCH;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(ToolDescription.builder()
                        .general("Runs an ES|QL query on an Elastic Index, answered by Elasticsearch itself: for "
                                 + "totals, counts, top N and grouped or bucketed questions (e.g. events per hour "
                                 + "with BUCKET(@timestamp, 1 hour))")
                        .chatOnly("; use run_stroomql for row-level work or any other data source")
                        .general(". Not for ordered sequences (X then Y within N, in one doc or across several): "
                                 + "joins of STATS results get them wrong")
                        .chatOnly("; use sequence_query")
                        .general(". Not for one person's activity: what they did and when, or how it differs from "
                                 + "their own baseline or their peers'")
                        .chatOnly("; use activity_timeline or compare_activity")
                        .general(". Give pipeline: the commands after FROM (written for you), e.g. '| WHERE "
                                 + "event.action == \"allowed\" | STATS total = SUM(bytes) BY user.name | SORT total "
                                 + "DESC | LIMIT 10'. Allowed: WHERE, EVAL, STATS ... BY, SORT, LIMIT, KEEP, DROP, "
                                 + "RENAME, DISSECT, GROK, MV_EXPAND. Field names are the index's own")
                        .chatOnly(" (describe_data_source gives them)")
                        .general("; group by a keyword field, not text. STATS ... BY a multi-valued field counts "
                                 + "a document under each value. Work out derived values and cut-offs in the "
                                 + "query, not in your reply: EVAL for a conversion (e.g. '| EVAL hours = "
                                 + "ROUND(seconds / 3600.0, 1)'), WHERE after STATS for a threshold (e.g. '| WHERE "
                                 + "hours > 8'). A LIMIT is added if the pipeline has none; the result says when "
                                 + "rows were cut short and how many there are in all: never call cut-short rows a "
                                 + "full ranking or a total. Counts and sums come straight from Elasticsearch and "
                                 + "may include documents from streams the user can't see or that have been "
                                 + "deleted, so they can differ from Stroom searches")
                        .chatOnly(" (run_stroomql)")
                        .general(" and dashboards: when the user needs figures that match those exactly, use a "
                                 + "Stroom search")
                        .chatOnly(" (run_stroomql)")
                        .general(". FIRST and LAST are reserved: name columns e.g. first_seen and last_seen. In "
                                 + "RLIKE, @ matches any string: escape it, or use LIKE for simple "
                                 + "patterns. ES|QL has no IF: use CASE(condition, then, else). Patterns: first "
                                 + "seen, MIN(@timestamp) BY user, value; spraying or scans, COUNT_DISTINCT(target) "
                                 + "BY source; an index's baseline vs recent, one STATS with COUNT(*) WHERE "
                                 + "@timestamp < x per period; a cold-start guard, WHERE on a history count.")
                        .chatOnly(" The rows are written to a table in the workspace too, for the table tool.")
                        .build())
                .parameters(ObjectSchema.builder()
                        .addStringProperty(INDEX_ARG, ToolDescription.builder()
                                .general("The Elastic Index doc's uuid or name")
                                .chatOnly(", as list_data_sources gives it")
                                .general(".")
                                .build())
                        .addStringProperty(PIPELINE_ARG, "The ES|QL commands after FROM, each after '|'.")
                        .addProperty(WHERE_ARG, ArraySchema.builder()
                                .items(ObjectSchema.builder()
                                        .addStringProperty("field", (String) null)
                                        .addEnumProperty("condition", IndexEvents.CONDITIONS.stream()
                                                .map(Condition::name)
                                                .toList(), "The condition (default EQUALS).")
                                        .addStringProperty("value", "BETWEEN: 'from,to'; IN: values separated by "
                                                                    + "commas; EQUALS takes * wildcards.")
                                        .required("field")
                                        .build())
                                .description(ToolDescription.builder()
                                        .general("Optional: conditions every document must meet, all of them")
                                        .chatOnly(", as search_events takes them")
                                        .general("; applied before the pipeline, as Stroom searches apply them.")
                                        .build())
                                .build())
                        .addStringProperty(TIME_FROM_ARG, "Optional: from this time on the index's time field "
                                                          + "(ISO 8601, or Stroom's day()-7d).")
                        .addStringProperty(TIME_TO_ARG, "Optional: up to this time (ISO 8601, or Stroom's now()).")
                        .addStringProperty(UserTimeZones.ARG, UserTimeZones.DESCRIPTION)
                        .addIntegerProperty(LIMIT_ARG, "The most rows (default " + ElasticEsql.MAX_ROWS + ", at most "
                                                       + ElasticEsql.MAX_ROWS + "); a LIMIT in the pipeline "
                                                       + "lowers it.")
                        .addStringProperty(OUT_ARG, ToolDescription.builder()
                                .chatOnly("A .md file in /outputs/ for the rows (default "
                                          + "/outputs/esql/esql-<time>.md).")
                                .build())
                        .required(INDEX_ARG, PIPELINE_ARG)
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
        final String given = stringArg(request, INDEX_ARG);
        if (given == null) {
            throw new WorkspaceException("Give index: the Elastic Index doc's uuid or name.");
        }
        final String pipeline = NullSafe.string(rawArg(request, PIPELINE_ARG));
        final int maxQueryLength = limits(context).getMaxQueryLength();
        if (pipeline.length() > maxQueryLength) {
            throw new WorkspaceException("The pipeline may be at most " + maxQueryLength + " characters.");
        }
        final DocRef index = index(given);
        final List<QueryField> fields = fields(index);
        // The zone timeFrom, timeTo and the where conditions' times are read in, and the rows' times shown in.
        final ToolTimeZone zone = timeZone(request, context);
        final long referenceTime = System.currentTimeMillis();
        final List<Term> terms = terms(request, index, fields, zone, referenceTime);
        final ExpressionOperator filter = terms.isEmpty()
                ? null
                : expression(terms);
        final String timeFrom = StroomQlDatePoints.timeInZone(stringArg(request, TIME_FROM_ARG), zone, referenceTime);
        final String timeTo = StroomQlDatePoints.timeInZone(stringArg(request, TIME_TO_ARG), zone, referenceTime);
        final TimeRange timeRange = timeFrom == null && timeTo == null
                ? null
                : new TimeRange(null, timeFrom, timeTo);
        final WorkspacePath givenOut = outArg(request);
        final WorkspacePath out = givenOut != null
                ? givenOut
                : workspace.newFile("/outputs/esql/esql", ".md");

        final Result result;
        try {
            result = elasticEsqlProvider.get().query(new Request(index, pipeline, filter, timeRange,
                    Objects.requireNonNullElse(intArg(request, LIMIT_ARG), ElasticEsql.MAX_ROWS), timeLimit(context),
                    true, context.stopped(), zone.isUtc()
                    ? null
                    : zone.id()));
        } catch (final Refusal refusal) {
            if (refusal.reason() == Reason.STOPPED) {
                // The user stopped the chat, and the query was cancelled in Elasticsearch: the run ends stopped.
                throw new TaskTerminatedException();
            }
            throw new WorkspaceException(refusal(refusal, fields));
        } catch (final PermissionException e) {
            throw new WorkspaceException(DataSourceAccess.NOT_FOUND);
        }
        auditLog.searched(context.session(), index, result.query() + searchedNote(terms, timeRange),
                (long) result.rows().size(), "ES|QL query by the " + NAME + " tool");

        final List<String> columns = result.columns().stream().map(ElasticEsql.Column::name).toList();
        final List<List<String>> cells = result.rows().stream()
                .map(row -> row.stream().map(value -> zone.inZone(cell(value))).toList())
                .toList();
        ToolTables.write(workspace, out, columns, cells, index.getUuid());
        final Map<String, Object> structured = structured(index, terms, timeRange, result, out, zone);
        return context.session().isChat()
                ? ToolResult.text(markdown(index, terms, timeRange, result, columns, cells, out, zone))
                        .withStructuredContent(structured)
                : ToolResult.text(JsonUtil.writeValueAsString(structured, false)).withStructuredContent(structured);
    }

    // The Elastic Index doc: refused, saying what to use instead, for anything else.
    private DocRef index(final String given) {
        return elasticIndex(dataSourceAccess, given, "ES|QL");
    }

    /// The Elastic Index doc a uuid or name gives, which the user can use.
    ///
    /// @param dataSourceAccess Finds the data sources the user can use.
    /// @param given            The doc's uuid or name.
    /// @param language         The query language, for saying it runs on Elasticsearch only, e.g. `ES|QL`.
    /// @return The doc.
    /// @throws WorkspaceException if there is none the user can use (naming it), several have the name, or it isn't
    ///                            an Elastic Index (saying to use run_stroomql).
    static DocRef elasticIndex(final DataSourceAccess dataSourceAccess, final String given, final String language) {
        final List<DocRef> found = dataSourceAccess.find(given);
        final List<DocRef> elastic = found.stream()
                .filter(docRef -> ElasticIndexDoc.TYPE.equals(docRef.getType()))
                .toList();
        if (elastic.size() == 1) {
            return elastic.getFirst();
        } else if (elastic.size() > 1) {
            final StringBuilder sb = new StringBuilder("Several Elastic Index docs you can use are called '")
                    .append(given).append("'. Give the uuid of the one you mean:");
            elastic.forEach(docRef -> sb.append("\n- ").append(DataSourceAccess.describe(docRef)));
            throw new WorkspaceException(sb.toString());
        } else if (!found.isEmpty()) {
            final DocRef other = found.getFirst();
            throw new WorkspaceException(DataSourceAccess.describe(other) + " isn't an Elastic Index: " + language
                                         + " runs on Elasticsearch only. Use run_stroomql for "
                                         + (LuceneIndexDoc.TYPE.equals(other.getType())
                    ? "a Lucene index."
                    : "it."));
        }
        throw new WorkspaceException("No Elastic Index doc with uuid or name '" + given + "' that you can use. "
                                     + "list_data_sources gives those you can.");
    }

    // The fields Stroom lists for the index.
    private List<QueryField> fields(final DocRef index) {
        return fields(queryServiceProvider.get(), index);
    }

    /// @param queryService Lists a data source's fields.
    /// @param index        The index.
    /// @return The fields Stroom lists for the index, or none if they can't be read.
    public static List<QueryField> fields(final QueryService queryService, final DocRef index) {
        try {
            final ResultPage<QueryField> found = queryService.findFields(new FindFieldCriteria(
                    new PageRequest(0, MAX_FIELDS), FindFieldCriteria.DEFAULT_SORT_LIST, index, null, null));
            return NullSafe.list(NullSafe.get(found, ResultPage::getValues)).stream()
                    .filter(field -> field != null && NullSafe.isNonEmptyString(field.getFldName()))
                    .toList();
        } catch (final RuntimeException e) {
            // Only for help with names: the query still runs, and Elasticsearch names a field it doesn't have.
            return List.of();
        }
    }

    // The where conditions, checked as search_events checks them: on fields the index has, and refused where the
    // answer through Stroom's query builder would mislead (IS_NULL on Elasticsearch matches every document). A
    // condition's time on a date field is put in the call's zone, as timeFrom's is (Stroom's query builder reads
    // one in UTC).
    private static List<Term> terms(final ToolCall request,
                                    final DocRef index,
                                    final List<QueryField> fields,
                                    final ToolTimeZone zone,
                                    final long referenceTime) {
        final List<Term> given = SearchEventsTool.terms(request);
        if (given.isEmpty()) {
            return given;
        }
        final Set<String> names = new LinkedHashSet<>(fields.stream().map(QueryField::getFldName).toList());
        if (!names.isEmpty()) {
            final List<String> unknown = given.stream()
                    .map(Term::field)
                    .filter(field -> !names.contains(field))
                    .distinct()
                    .toList();
            if (!unknown.isEmpty()) {
                throw new WorkspaceException("Not fields of index '" + index.getName() + "': "
                                             + said(unknown, names) + ". " + FIELDS_HINT + ".");
            }
        }
        final Map<String, String> types = new LinkedHashMap<>();
        fields.forEach(field -> types.put(field.getFldName(), NullSafe.get(field.getFldType(), Enum::name)));
        final List<Term> terms = given.stream()
                .map(term -> MisleadingSearches.elasticsearchNullAs(term.condition(), types.get(term.field()))
                        .map(search -> new Term(term.field(), search.condition(), search.value()))
                        .orElse(term))
                .map(term -> FieldType.DATE.name().equals(types.get(term.field()))
                        ? new Term(term.field(), term.condition(), StroomQlDatePoints.conditionInZone(term.value(),
                        term.condition(), zone, referenceTime))
                        : term)
                .toList();
        final Optional<String> misleading = MisleadingSearches.refusal(MisleadingSearches.ELASTICSEARCH,
                terms.stream()
                        .map(term -> new MisleadingSearches.Search(term.field(), term.condition(), term.value()))
                        .toList(),
                MisleadingSearches.ipFields(fields), List.of(), Map.of(),
                MisleadingSearches.unindexed(index, fields, terms.stream().map(Term::field).toList()));
        if (misleading.isPresent()) {
            throw new WorkspaceException(misleading.get() + ". Or put the condition in the pipeline's WHERE.");
        }
        return terms;
    }

    private static ExpressionOperator expression(final List<Term> terms) {
        final List<ExpressionItem> items = new ArrayList<>();
        for (final Term term : terms) {
            items.add(ExpressionTerm.builder()
                    .field(term.field())
                    .condition(term.condition())
                    .value(term.value())
                    .build());
        }
        return ExpressionOperator.builder().op(Op.AND).children(items).build();
    }

    /// What the model is told for a refusal: the reason, and the way forward.
    ///
    /// @param refusal Why the query wasn't run.
    /// @param fields  The index's fields, as Stroom lists them; empty if not known.
    /// @return The message.
    static String refusal(final Refusal refusal, final List<QueryField> fields) {
        final String message = refusal.getMessage();
        final Set<String> names = new LinkedHashSet<>(fields.stream().map(QueryField::getFldName).toList());
        return switch (refusal.reason()) {
            case UNKNOWN_FIELD -> sentence(message) + " Not fields of the index: " + said(refusal.names(), names) + ". "
                                  + FIELDS_HINT + (names.isEmpty()
                    ? "."
                    : "; they include: " + String.join(", ", new TreeSet<>(names).stream()
                            .limit(FIELDS_NAMED).toList()) + ".");
            case TEXT_GROUPING -> message + ". A text field is analysed for searching; its keyword sub-field holds "
                                  + "each value whole, which is what to group by.";
            case CLUSTER_TOO_OLD -> message + ". Use run_stroomql instead, which works on any version.";
            case TIMEOUT -> message + ", or ask for less (a narrower STATS, or a LIMIT).";
            case COMMAND_NOT_ALLOWED, INVALID -> sentence(message);
            case NOT_FOUND -> DataSourceAccess.NOT_FOUND;
            case REFUSED -> refused(message);
            default -> sentence(message);
        };
    }

    // Elasticsearch's refusal, with a hint for the traps that are easy to fall into.
    private static String refused(final String message) {
        if (BACKSLASH_IN_STRING.matcher(message).find()) {
            return sentence(message) + " " + BACKSLASH_HINT;
        }
        if (UNKNOWN_IF.matcher(message).find()) {
            return sentence(message) + " " + IF_HINT;
        }
        final String reserved = reservedWord(message);
        if (reserved != null) {
            return sentence(message) + " If '" + reserved + "' is meant as a column's name: it is a reserved word in "
                   + "ES|QL, so rename the column (e.g. " + reserved.toLowerCase(Locale.ROOT) + "_seen) or put it in "
                   + "backquotes (`" + reserved + "`).";
        }
        return sentence(message);
    }

    // The keyword a refusal says was written where a name was wanted, or null.
    private static String reservedWord(final String message) {
        for (final Pattern pattern : List.of(NO_VIABLE_KEYWORD, MISMATCHED_KEYWORD)) {
            final Matcher matcher = pattern.matcher(message);
            if (matcher.find() && RESERVED_WORDS.contains(matcher.group(1).toLowerCase(Locale.ROOT))) {
                return matcher.group(1);
            }
        }
        return null;
    }

    /// @param pipeline The ES|QL commands after FROM.
    /// @return True if an RLIKE pattern in it holds an `@` that isn't escaped (`\@`) or in a class (`[@]`), which
    /// Elasticsearch's regular expressions read as "any string".
    static boolean unescapedAtInRlike(final String pipeline) {
        final Matcher matcher = RLIKE_PATTERN.matcher(NullSafe.string(pipeline));
        while (matcher.find()) {
            final String regex = matcher.group(1) != null
                    ? matcher.group(1)
                    : unescaped(matcher.group(2));
            boolean escaped = false;
            int depth = 0;
            for (final char c : regex.toCharArray()) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '[') {
                    depth++;
                } else if (c == ']' && depth > 0) {
                    depth--;
                } else if (c == '@' && depth == 0) {
                    return true;
                }
            }
        }
        return false;
    }

    // A "..." string's text, its escapes read.
    private static String unescaped(final String text) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                final char next = text.charAt(++i);
                sb.append(switch (next) {
                    case 't' -> '\t';
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    default -> next;
                });
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // The message as a sentence: a full stop added unless it ends with one already, or with '?' or '!'.
    private static String sentence(final String message) {
        final String stripped = NullSafe.trim(message);
        return stripped.endsWith(".") || stripped.endsWith("?") || stripped.endsWith("!")
                ? stripped
                : stripped + ".";
    }

    /// What Elasticsearch warned of while running the query, with what to do about a multi-valued field.
    ///
    /// @param result What the query gave.
    /// @return The note, or empty if there were no warnings.
    static String warningsNote(final Result result) {
        if (result.warnings().isEmpty()) {
            return "";
        }
        final StringBuilder sb = new StringBuilder("Elasticsearch warned:");
        result.warnings().forEach(warning -> sb.append("\n- ").append(warning));
        if (result.warnings().stream().anyMatch(warning -> warning.contains(MULTI_VALUE_WARNING))) {
            sb.append("\n").append(MULTI_VALUE_HINT);
        }
        return sb.toString();
    }

    /// @param unknown Names that aren't fields of an index.
    /// @param names   The index's fields.
    /// @return The unknown names, each with the index's fields most like it.
    public static String said(final List<String> unknown, final Set<String> names) {
        final List<String> candidates = new ArrayList<>(names);
        final List<String> said = new ArrayList<>();
        for (final String name : unknown) {
            final List<String> near = candidates.isEmpty()
                    ? List.of()
                    : CloseMatches.closeMatches(name, candidates, 3, 0.5);
            said.add(near.isEmpty()
                    ? name
                    : name + " (like " + String.join(", ", near) + ")");
        }
        return String.join("; ", said);
    }

    private static String searchedNote(final List<Term> terms, final TimeRange timeRange) {
        final StringBuilder sb = new StringBuilder();
        if (!terms.isEmpty()) {
            sb.append("\nfilter: ").append(String.join(" AND ", terms.stream().map(EsqlQueryTool::said).toList()));
        }
        if (timeRange != null) {
            sb.append("\ntime: ").append(NullSafe.nonBlankStringElse(timeRange.getFrom(), "any"))
                    .append(" to ").append(NullSafe.nonBlankStringElse(timeRange.getTo(), "any"));
        }
        return sb.toString();
    }

    private static String said(final Term term) {
        return term.field() + " " + term.condition().name() + " " + term.value();
    }

    // The result for the chat: the query, the first rows as a Markdown table, and what the rows are and aren't.
    private static String markdown(final DocRef index,
                                   final List<Term> terms,
                                   final TimeRange timeRange,
                                   final Result result,
                                   final List<String> columns,
                                   final List<List<String>> cells,
                                   final WorkspacePath out,
                                   final ToolTimeZone zone) {
        final StringBuilder sb = new StringBuilder("Ran the ES|QL query on ")
                .append(DataSourceAccess.describe(index))
                .append(" (Elasticsearch ").append(result.version()).append(", ").append(result.tookMs())
                .append(" ms):\n\n```\n").append(result.query()).append("\n```\n");
        final String searched = searchedNote(terms, timeRange);
        if (!searched.isEmpty()) {
            sb.append("Filtered before the pipeline by:").append(searched.replace("\n", "\n- ")).append("\n");
        }
        sb.append('\n');
        final String warned = warningsNote(result);
        if (!warned.isEmpty()) {
            sb.append(warned).append("\n\n");
        }
        if (unescapedAtInRlike(result.query())) {
            sb.append(RLIKE_AT_NOTE).append("\n\n");
        }
        if (cells.isEmpty()) {
            sb.append("No rows.");
        } else {
            sb.append(ToolTables.preview(columns, cells, SHOWN_ROWS));
        }
        sb.append("\n\n").append(zoneNote(zone, result)).append(' ').append(rowsNote(result));
        if (!cells.isEmpty()) {
            sb.append(" The rows are in ").append(out).append('.');
        }
        return sb.toString();
    }

    /// @param zone The call's zone.
    /// @return What the result says of its times' zone: in a zone other than UTC, also that ES|QL's own date
    /// functions work in UTC, if the cluster refused the zone.
    ///
    /// @param result What the query gave, with the zone its date work was in.
    static String zoneNote(final ToolTimeZone zone, final Result result) {
        return utcPipeline(zone, result)
                ? zone.stated() + " " + ESQL_UTC_NOTE
                : zone.stated();
    }

    // Whether the pipeline's own date work ran in UTC though the call's zone isn't UTC (the cluster refused it).
    private static boolean utcPipeline(final ToolTimeZone zone, final Result result) {
        return !zone.isUtc() && result.timeZone() == null;
    }

    /// What the rows are: how many, whether they were cut short, and how many there are in all.
    ///
    /// @param result What the query gave.
    /// @return The note.
    static String rowsNote(final Result result) {
        final StringBuilder sb = new StringBuilder();
        final String unit = result.grouped()
                ? "group"
                : "row";
        sb.append(result.rows().size()).append(' ').append(unit).append(result.rows().size() == 1
                ? ""
                : "s").append('.');
        if (result.limitNote() != null) {
            sb.append(' ').append(result.limitNote());
        }
        if (result.truncated()) {
            sb.append(" They reached the limit (").append(result.limit()).append("), so they are cut short");
            if (result.totalRows() != null) {
                sb.append(": there are ").append(result.totalRows()).append(' ').append(unit).append("s in all");
                if (result.grouped()) {
                    sb.append(", so these are the first ").append(result.rows().size()).append(" of ")
                            .append(result.totalRows()).append(" groups in the query's order, not all of them");
                }
                sb.append('.');
            } else {
                sb.append(", and how many there are in all isn't known")
                        .append(result.totalNote() == null
                                ? ""
                                : " (" + result.totalNote() + ")")
                        .append(": don't present them as complete.");
            }
            if (result.limit() >= ElasticEsql.MAX_ROWS) {
                sb.append(" ").append(MAX_ROWS_NOTE);
            }
        }
        return sb.toString();
    }

    // The result as data: for MCP clients, and the chat's structured content.
    private static Map<String, Object> structured(final DocRef index,
                                                  final List<Term> terms,
                                                  final TimeRange timeRange,
                                                  final Result result,
                                                  final WorkspacePath out,
                                                  final ToolTimeZone zone) {
        final Map<String, Object> indexRef = new LinkedHashMap<>();
        indexRef.put("type", index.getType());
        indexRef.put("uuid", index.getUuid());
        indexRef.put("name", index.getName());
        indexRef.put("index_name", result.indexName());
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("index", indexRef);
        map.put("query", result.query());
        if (!terms.isEmpty()) {
            map.put("filter", terms.stream().map(EsqlQueryTool::said).toList());
        }
        if (timeRange != null) {
            final Map<String, Object> time = new LinkedHashMap<>();
            time.put("from", timeRange.getFrom());
            time.put("to", timeRange.getTo());
            map.put("time_range", time);
        }
        map.putAll(zone.structured());
        if (utcPipeline(zone, result)) {
            map.put("time_zone_esql_note", ESQL_UTC_NOTE);
        }
        map.put("version", result.version());
        map.put("took_ms", result.tookMs());
        map.put("columns", result.columns().stream()
                .map(column -> Map.of("name", column.name(), "type", column.type()))
                .toList());
        map.put("rows", result.rows().stream().limit(SHOWN_ROWS)
                .map(row -> row.stream().map(value -> value instanceof final String text
                        ? zone.inZone(text)
                        : value).toList())
                .toList());
        map.put("row_count", result.rows().size());
        if (result.rows().size() > SHOWN_ROWS) {
            map.put("rows_shown", SHOWN_ROWS);
        }
        map.put("limit", result.limit());
        map.put("truncated", result.truncated());
        map.put("grouped", result.grouped());
        if (result.totalRows() != null) {
            map.put(result.grouped()
                    ? "total_groups"
                    : "total_rows", result.totalRows());
        }
        map.put("note", rowsNote(result));
        if (!result.warnings().isEmpty()) {
            map.put("warnings", result.warnings());
            map.put("warnings_note", warningsNote(result));
        }
        if (unescapedAtInRlike(result.query())) {
            map.put("rlike_note", RLIKE_AT_NOTE);
        }
        if (!result.rows().isEmpty()) {
            map.put("table", out.toString());
        }
        return map;
    }

    private static String cell(final Object value) {
        if (value == null) {
            return "";
        } else if (value instanceof final List<?> list) {
            return String.join(", ", list.stream().map(EsqlQueryTool::cell).toList());
        } else if (value instanceof final Double number && number == Math.rint(number) && !number.isInfinite()
                   && Math.abs(number) < 1e15) {
            return String.valueOf(number.longValue());
        }
        return String.valueOf(value);
    }

    @Override
    public boolean readsUntrustedContent() {
        // Stroom data can hold text written by anyone.
        return true;
    }
}
