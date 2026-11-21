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

package stroom.search.elastic.esql;

import stroom.ai.api.AiService;
import stroom.ai.api.ElasticEsql;
import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.TimeRange;
import stroom.query.common.v2.DateExpressionParser;
import stroom.query.common.v2.IndexFieldCache;
import stroom.search.elastic.ElasticClientCache;
import stroom.search.elastic.ElasticClusterStore;
import stroom.search.elastic.ElasticIndexStore;
import stroom.search.elastic.search.ElasticQueryParams;
import stroom.search.elastic.search.ElasticSearchConfig;
import stroom.search.elastic.search.SearchExpressionQueryBuilder;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.elastic.shared.ElasticIndexField;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PermissionException;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorCause;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch.esql.ElasticsearchEsqlAsyncClient;
import co.elastic.clients.elasticsearch.esql.EsqlFormat;
import co.elastic.clients.elasticsearch.esql.QueryRequest;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.transport.TransportOptions;
import co.elastic.clients.transport.endpoints.BinaryResponse;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/// Runs ES|QL queries on the index of an Elastic Index document ([ElasticEsql]), as the user: Use on the document
/// is checked, as for a search, and the query runs with its cluster's connection.
///
/// The caller's commands ([EsqlPipeline]) follow a `FROM` written from the document's index name, and the rows are
/// capped. A Stroom expression and a time range on the document's time field are made into Query DSL as a search
/// makes them ([SearchExpressionQueryBuilder]), and given as the request's `filter`, which Elasticsearch applies
/// before the commands. The documents' streams aren't looked up, as a search looks up its hits' (for speed): the
/// answer may include the documents of streams the user can't see, or that are no longer in Stroom. Clusters older
/// than 8.14, the first with ES|QL generally available, are refused.
///
/// A query is waited for in short slices ([#STOP_CHECK]) up to its time limit, asking between them whether the
/// caller has stopped it ([Request#stopped()]): one stopped, or still running at the limit, has its request
/// cancelled, which closes its connection, and Elasticsearch cancels a query whose connection closes. Elasticsearch's
/// circuit breakers and rejections are told apart ([ElasticErrors]), so the caller can say whether to try again
/// shortly or ask for less.
public class ElasticEsqlService implements ElasticEsql {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ElasticEsqlService.class);

    /// The oldest cluster version queried: ES|QL is generally available from 8.14.
    static final int[] MIN_VERSION = {8, 14};
    // The first version that takes `allow_partial_results`, which is set false so a failed shard fails the query.
    private static final int[] PARTIAL_RESULTS_VERSION = {8, 18};
    private static final ObjectMapper MAPPER = new ObjectMapper();
    // An index name, pattern or list of them, as FROM takes it unquoted.
    private static final Pattern INDEX_NAME = Pattern.compile("[A-Za-z0-9.*][A-Za-z0-9._\\-*,:+]*");
    private static final Pattern UNKNOWN_COLUMN = Pattern.compile("Unknown column \\[([^\\]]+)]");
    private static final Pattern PROBLEMS_PREFIX = Pattern.compile("^Found \\d+ problems?\\s*", Pattern.MULTILINE);
    private static final Set<String> TEXT_TYPES = Set.of("text", "match_only_text");
    private static final String KEYWORD_TYPE = "keyword";
    /// The most warnings passed on from one query.
    static final int MAX_WARNINGS = 10;
    /// How long a query is waited for before asking again whether the caller has stopped it.
    static final Duration STOP_CHECK = Duration.ofMillis(250);
    // A warning header: code, agent, then the text in quotes (and maybe a date).
    private static final Pattern WARNING_HEADER = Pattern.compile(
            "^\\d{3} \\S+ \"((?:[^\"\\\\]|\\\\.)*)\"(?: \"[^\"]*\")?$");
    private static final Pattern ESCAPED = Pattern.compile("\\\\(.)");
    // The warning that Elasticsearch added a limit, which isn't news: the query always has one.
    private static final Pattern ADDED_LIMIT = Pattern.compile("^No limit defined, adding default limit");

    private final SecurityContext securityContext;
    private final ElasticIndexStore elasticIndexStore;
    private final ElasticClusterStore elasticClusterStore;
    private final ElasticClientCache elasticClientCache;
    private final Provider<ElasticSearchConfig> elasticSearchConfigProvider;
    private final Provider<AiService> aiServiceProvider;
    private final IndexFieldCache indexFieldCache;
    private final WordListProvider wordListProvider;

    /// @param securityContext             Checks Use on the index doc, and reads the docs it needs.
    /// @param elasticIndexStore           Reads the index doc.
    /// @param elasticClusterStore         Reads its cluster doc.
    /// @param elasticClientCache          Gives a client for the cluster's connection.
    /// @param elasticSearchConfigProvider The default row limit and the time limit.
    /// @param aiServiceProvider           Used by the filter's query builder, as for a search.
    /// @param indexFieldCache             The index's fields, for the filter's query builder.
    /// @param wordListProvider            Dictionaries the filter names.
    @Inject
    public ElasticEsqlService(final SecurityContext securityContext,
                              final ElasticIndexStore elasticIndexStore,
                              final ElasticClusterStore elasticClusterStore,
                              final ElasticClientCache elasticClientCache,
                              final Provider<ElasticSearchConfig> elasticSearchConfigProvider,
                              final Provider<AiService> aiServiceProvider,
                              final IndexFieldCache indexFieldCache,
                              final WordListProvider wordListProvider) {
        this.securityContext = securityContext;
        this.elasticIndexStore = elasticIndexStore;
        this.elasticClusterStore = elasticClusterStore;
        this.elasticClientCache = elasticClientCache;
        this.elasticSearchConfigProvider = elasticSearchConfigProvider;
        this.aiServiceProvider = aiServiceProvider;
        this.indexFieldCache = indexFieldCache;
        this.wordListProvider = wordListProvider;
    }

    @Override
    public Result query(final Request request) {
        Objects.requireNonNull(request, "request");
        final DocRef docRef = request.index();
        if (docRef == null || !securityContext.hasDocumentPermission(docRef, DocumentPermission.USE)) {
            throw new PermissionException(
                    securityContext.getUserRef(),
                    LogUtil.message("You are not authorised to search {}", docRef));
        }
        // As for a search, permission to use the index is enough to read it, its cluster and its connection.
        final ElasticIndexDoc indexDoc = securityContext.useAsReadResult(() ->
                elasticIndexStore.readDocument(docRef));
        if (indexDoc == null) {
            throw new Refusal(Reason.NOT_FOUND, "Elastic Index " + docRef + " was not found");
        }
        final String indexName = NullSafe.trim(indexDoc.getIndexName());
        if (indexName.isEmpty()) {
            throw new Refusal(Reason.NOT_QUERYABLE, "Elastic Index '" + indexDoc.getName() + "' has no index name");
        }
        if (!INDEX_NAME.matcher(indexName).matches()) {
            throw new Refusal(Reason.NOT_QUERYABLE, "The index name of Elastic Index '" + indexDoc.getName() + "' ('"
                                                    + indexName + "') can't be read by ES|QL's FROM");
        }
        if (indexDoc.getClusterRef() == null) {
            throw new Refusal(Reason.NOT_QUERYABLE, "Elastic Index '" + indexDoc.getName() + "' has no cluster");
        }

        final EsqlPipeline pipeline = EsqlPipeline.parse(request.pipeline());
        refuseTextGrouping(indexDoc, pipeline);
        final Query filter = filter(indexDoc, request.filter(), request.timeRange());

        final ElasticSearchConfig config = elasticSearchConfigProvider.get();
        final int defaultLimit = Math.clamp(config.getEsqlRowLimit(), 1, MAX_ROWS);
        final int requested = Objects.requireNonNullElse(request.limit(), defaultLimit);
        final int cap = Math.clamp(requested, 1, MAX_ROWS);
        final EsqlPipeline.Limited limited = pipeline.limited(cap);
        final String capNote = requested > MAX_ROWS
                ? "The limit asked for was over " + MAX_ROWS + ", the most rows allowed, so it was lowered."
                : null;
        final Duration configured = NullSafe.getOrElse(config.getEsqlTimeout(), timeout -> timeout.getDuration(),
                Duration.ofMinutes(2));
        final Duration timeLimit = request.timeLimit() == null || request.timeLimit().compareTo(configured) > 0
                ? configured
                : request.timeLimit();
        final Instant deadline = Instant.now().plus(timeLimit);

        return securityContext.useAsReadResult(() -> {
            final ElasticClusterDoc cluster = elasticClusterStore.readDocument(indexDoc.getClusterRef());
            if (cluster == null) {
                throw new Refusal(Reason.NOT_QUERYABLE, "The Elastic Cluster of Elastic Index '"
                                                        + indexDoc.getName() + "' was not found");
            }
            return elasticClientCache.contextResult(cluster.getConnection(), client -> {
                final String version = checkVersion(client);
                final String query = limited.pipeline().query(indexName);
                String timeZone = NullSafe.isBlankString(request.timeZone())
                        ? null
                        : request.timeZone().strip();
                Response response;
                try {
                    response = run(client, query, filter, timeZone, version, deadline, timeLimit,
                            request.stopped());
                } catch (final Refusal e) {
                    if (timeZone == null || !refusesTimeZone(e)) {
                        throw e;
                    }
                    // A cluster that doesn't take time_zone (e.g. outside snapshot builds of older versions): the
                    // query runs in UTC, as the result says.
                    LOGGER.debug(() -> LogUtil.message("The cluster refused time_zone: {}", e.getMessage()));
                    timeZone = null;
                    response = run(client, query, filter, null, version, deadline, timeLimit, request.stopped());
                }
                final boolean truncated = response.rows().size() >= limited.limit();
                Long total = null;
                String totalNote = null;
                if (request.countTotal() && truncated) {
                    try {
                        total = count(client, pipeline.forTotal().query(indexName), filter, timeZone, version,
                                deadline, timeLimit, request.stopped());
                    } catch (final Refusal e) {
                        if (e.reason() == Reason.STOPPED) {
                            throw e;
                        }
                        totalNote = "The total couldn't be counted: " + e.getMessage();
                    }
                }
                return new Result(query, indexName, response.columns(), response.rows(), response.tookMs(),
                        limited.limit(), joinNotes(capNote, limited.note()), truncated, pipeline.groups(), total,
                        totalNote, version, response.warnings(), timeZone);
            });
        });
    }

    @Override
    public Optional<String> esqlVersion(final DocRef index) {
        try {
            if (index == null || !securityContext.hasDocumentPermission(index, DocumentPermission.USE)) {
                return Optional.empty();
            }
            return securityContext.useAsReadResult(() -> {
                final ElasticIndexDoc indexDoc = elasticIndexStore.readDocument(index);
                if (indexDoc == null || indexDoc.getClusterRef() == null) {
                    return Optional.<String>empty();
                }
                final ElasticClusterDoc cluster = elasticClusterStore.readDocument(indexDoc.getClusterRef());
                if (cluster == null) {
                    return Optional.<String>empty();
                }
                return elasticClientCache.contextResult(cluster.getConnection(), client ->
                        Optional.of(checkVersion(client)));
            });
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("ES|QL can't be used on {}: {}", index, e.getMessage()), e);
            return Optional.empty();
        }
    }

    // Refuses a grouping by a text field that has a keyword sub-field: grouping by the sub-field is what's meant, and
    // older clusters can't group by text at all.
    private static void refuseTextGrouping(final ElasticIndexDoc indexDoc, final EsqlPipeline pipeline) {
        final List<ElasticIndexField> fields = NullSafe.list(indexDoc.getFields());
        if (fields.isEmpty()) {
            return;
        }
        final Map<String, ElasticIndexField> byName = fields.stream()
                .filter(field -> field != null && field.getFldName() != null)
                .collect(Collectors.toMap(ElasticIndexField::getFldName, Function.identity(), (a, b) -> a));
        for (final String name : pipeline.groupingFields()) {
            final ElasticIndexField field = byName.get(name);
            if (field != null && TEXT_TYPES.contains(field.getNativeType())) {
                final String keyword = keywordSubField(fields, byName, name);
                if (keyword != null) {
                    throw new Refusal(Reason.TEXT_GROUPING, "'" + name + "' is a text field: group by its keyword "
                                                            + "sub-field '" + keyword + "' instead",
                            List.of(name, keyword));
                }
            }
        }
    }

    private static String keywordSubField(final List<ElasticIndexField> fields,
                                          final Map<String, ElasticIndexField> byName,
                                          final String name) {
        final ElasticIndexField conventional = byName.get(name + ".keyword");
        if (conventional != null && KEYWORD_TYPE.equals(conventional.getNativeType())) {
            return conventional.getFldName();
        }
        return fields.stream()
                .filter(field -> name.equals(field.getMultiFieldParent())
                                 && KEYWORD_TYPE.equals(field.getNativeType()))
                .map(ElasticIndexField::getFldName)
                .findFirst()
                .orElse(null);
    }

    // The Query DSL for the expression and the time range, or null for neither.
    private Query filter(final ElasticIndexDoc indexDoc,
                         final ExpressionOperator expression,
                         final TimeRange timeRange) {
        final DateTimeSettings dateTimeSettings = DateTimeSettings.builder()
                .referenceTime(System.currentTimeMillis())
                .build();
        final List<Query> filters = new ArrayList<>();
        if (expression != null && NullSafe.hasItems(expression.getChildren())) {
            final ElasticQueryParams params;
            try {
                params = new SearchExpressionQueryBuilder(aiServiceProvider, indexDoc, indexFieldCache,
                        wordListProvider, dateTimeSettings).buildQuery(expression);
            } catch (final RuntimeException e) {
                LOGGER.debug(() -> LogUtil.message("Unable to build the filter: {}", e.getMessage()), e);
                throw new Refusal(Reason.FILTER_NOT_SUPPORTED, "The filter can't be used: "
                                                               + NullSafe.nonBlankStringElse(e.getMessage(),
                        e.getClass().getSimpleName()));
            }
            if (!params.getKnnFieldQueries().isEmpty()) {
                throw new Refusal(Reason.FILTER_NOT_SUPPORTED, "The filter has a vector search, which ES|QL's "
                                                               + "filter can't take");
            }
            filters.add(params.getQuery());
        }
        if (timeRange != null && (NullSafe.isNonBlankString(timeRange.getFrom())
                                  || NullSafe.isNonBlankString(timeRange.getTo()))) {
            filters.add(timeRangeQuery(indexDoc, timeRange, dateTimeSettings));
        }
        if (filters.isEmpty()) {
            return null;
        }
        return filters.size() == 1
                ? filters.getFirst()
                : QueryBuilders.bool(b -> b.filter(filters));
    }

    // A range on the doc's time field: from (inclusive) up to (exclusive), in epoch milliseconds.
    static Query timeRangeQuery(final ElasticIndexDoc indexDoc,
                                final TimeRange timeRange,
                                final DateTimeSettings dateTimeSettings) {
        final String timeField = indexDoc.getTimeField();
        if (NullSafe.isBlankString(timeField)) {
            throw new Refusal(Reason.INVALID, "Elastic Index '" + indexDoc.getName() + "' has no time field to "
                                              + "search a time range on");
        }
        final Long from = timeMs(timeField, timeRange.getFrom(), dateTimeSettings);
        final Long to = timeMs(timeField, timeRange.getTo(), dateTimeSettings);
        return QueryBuilders.range(r -> r.untyped(u -> {
            u.field(timeField).format("epoch_millis");
            if (from != null) {
                u.gte(JsonData.of(from));
            }
            if (to != null) {
                u.lt(JsonData.of(to));
            }
            return u;
        }));
    }

    private static Long timeMs(final String timeField, final String value, final DateTimeSettings settings) {
        if (NullSafe.isBlankString(value)) {
            return null;
        }
        try {
            return DateExpressionParser.getMs(timeField, value.strip(), settings);
        } catch (final RuntimeException e) {
            throw new Refusal(Reason.INVALID, "The time '" + value + "' can't be read: give an ISO 8601 time "
                                              + "(e.g. 2026-01-01T00:00:00.000Z) or a Stroom date expression (e.g. "
                                              + "day() - 7d)");
        }
    }

    // The cluster's version, refused if it is older than ES|QL's general availability.
    private static String checkVersion(final ElasticsearchClient client) {
        final String version;
        try {
            version = client.info().version().number();
        } catch (final ElasticsearchException e) {
            throw new Refusal(Reason.CLUSTER_ERROR, "The cluster refused to give its version: " + reason(e));
        } catch (final IOException | RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Unable to read the cluster's version: {}", e.getMessage()), e);
            throw new Refusal(Reason.CLUSTER_ERROR, "The cluster couldn't be reached: "
                                                    + NullSafe.nonBlankStringElse(e.getMessage(),
                    e.getClass().getSimpleName()));
        }
        if (!atLeast(version, MIN_VERSION)) {
            throw new Refusal(Reason.CLUSTER_TOO_OLD, "The cluster is Elasticsearch " + version + ": ES|QL needs "
                                                      + "8.14 or later");
        }
        return version;
    }

    /// @param version A version, e.g. `9.5.3` or `8.14.0-SNAPSHOT`.
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

    // Counts the rows of a counting query, which gives one row of one count.
    private static long count(final ElasticsearchClient client,
                              final String query,
                              final Query filter,
                              final String timeZone,
                              final String version,
                              final Instant deadline,
                              final Duration timeLimit,
                              final BooleanSupplier stopped) {
        final Response response = run(client, query, filter, timeZone, version, deadline, timeLimit, stopped);
        if (response.rows().isEmpty() || response.rows().getFirst().isEmpty()
            || !(response.rows().getFirst().getFirst() instanceof final Number number)) {
            throw new Refusal(Reason.CLUSTER_ERROR, "Elasticsearch gave no count");
        }
        return number.longValue();
    }

    // Runs a query, waiting until the deadline at most, in slices between which it asks whether the caller has
    // stopped it; a query stopped, or still running at the deadline, is cancelled.
    private static Response run(final ElasticsearchClient client,
                                final String query,
                                final Query filter,
                                final String timeZone,
                                final String version,
                                final Instant deadline,
                                final Duration timeLimit,
                                final BooleanSupplier stopped) {
        LOGGER.debug(() -> LogUtil.message("Running ES|QL query: {}, filter: {}", query, filter));
        final QueryRequest queryRequest = QueryRequest.of(b -> {
            b.query(query).format(EsqlFormat.Json).columnar(false);
            if (timeZone != null) {
                b.timeZone(timeZone);
            }
            if (filter != null) {
                b.filter(filter);
            }
            if (atLeast(version, PARTIAL_RESULTS_VERSION)) {
                b.allowPartialResults(false);
            }
            return b;
        });
        if (stopped.getAsBoolean()) {
            throw stoppedRefusal();
        }
        if (!Instant.now().isBefore(deadline)) {
            throw timedOut(timeLimit);
        }
        // Elasticsearch gives warnings in response headers, not the body: e.g. that a comparison met a
        // multi-valued field, so gave null and dropped the row. They are kept, so the caller can say so; the
        // request never fails for them.
        final List<String> warnings = new CopyOnWriteArrayList<>();
        final TransportOptions options = client._transportOptions().with(builder -> builder.onWarnings(found -> {
            warnings.addAll(NullSafe.list(found));
            return false;
        }));
        final CompletableFuture<BinaryResponse> future = new ElasticsearchEsqlAsyncClient(client._transport(),
                options).query(queryRequest);
        try (final BinaryResponse response = await(future, deadline, timeLimit, stopped);
                final InputStream content = response.content()) {
            return parse(MAPPER.readTree(content)).withWarnings(warnings(warnings));
        } catch (final InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new Refusal(Reason.TIMEOUT, "The query was stopped");
        } catch (final ExecutionException | CompletionException e) {
            throw failed(e.getCause() == null
                    ? e
                    : e.getCause());
        } catch (final IOException | JacksonException e) {
            LOGGER.debug(() -> LogUtil.message("Unable to read the ES|QL response: {}", e.getMessage()), e);
            throw new Refusal(Reason.CLUSTER_ERROR, "Elasticsearch's answer couldn't be read: " + e.getMessage());
        }
    }

    // The query's answer, waited for in slices of STOP_CHECK until the deadline; cancelled (which closes its
    // connection, and Elasticsearch cancels a query whose connection closes) when the caller stops it or the
    // deadline passes.
    private static BinaryResponse await(final CompletableFuture<BinaryResponse> future,
                                        final Instant deadline,
                                        final Duration timeLimit,
                                        final BooleanSupplier stopped)
            throws InterruptedException, ExecutionException {
        while (true) {
            final long remainingMs = Duration.between(Instant.now(), deadline).toMillis();
            if (remainingMs <= 0) {
                future.cancel(true);
                throw timedOut(timeLimit);
            }
            try {
                return future.get(Math.min(remainingMs, STOP_CHECK.toMillis()), TimeUnit.MILLISECONDS);
            } catch (final TimeoutException e) {
                if (stopped.getAsBoolean()) {
                    future.cancel(true);
                    LOGGER.debug("ES|QL query stopped by its caller, so cancelled");
                    throw stoppedRefusal();
                }
            }
        }
    }

    private static Refusal stoppedRefusal() {
        return new Refusal(Reason.STOPPED, "The query was stopped, so it was cancelled in Elasticsearch");
    }

    private static Refusal timedOut(final Duration timeLimit) {
        return new Refusal(Reason.TIMEOUT, "The query took longer than " + timeLimit.toSeconds() + " seconds, so "
                                           + "it was cancelled: narrow the time range or the filter");
    }

    /// @param refusal Why Elasticsearch refused a query.
    /// @return True if it refused the query's `time_zone`, as a cluster that doesn't take it does.
    static boolean refusesTimeZone(final Refusal refusal) {
        return (refusal.reason() == Reason.REFUSED || refusal.reason() == Reason.CLUSTER_ERROR)
               && NullSafe.getOrElse(refusal.getMessage(), message -> message.contains("time_zone"), false);
    }

    // What a failed query is refused as.
    private static Refusal failed(final Throwable cause) {
        if (cause instanceof final Refusal refusal) {
            return refusal;
        }
        final Refusal classified = classified(cause);
        if (classified != null) {
            return classified;
        }
        if (cause instanceof final ElasticsearchException e) {
            final String reason = reason(e);
            final Matcher matcher = UNKNOWN_COLUMN.matcher(reason);
            final Set<String> unknown = new LinkedHashSet<>();
            while (matcher.find()) {
                unknown.add(matcher.group(1));
            }
            if (!unknown.isEmpty()) {
                return new Refusal(Reason.UNKNOWN_FIELD, "Elasticsearch refused the query: " + reason,
                        List.copyOf(unknown));
            }
            return new Refusal(Reason.REFUSED, "Elasticsearch refused the query: " + reason);
        }
        LOGGER.debug(() -> LogUtil.message("ES|QL query failed: {}", cause.getMessage()), cause);
        return new Refusal(Reason.CLUSTER_ERROR, "The query failed: "
                                                 + NullSafe.nonBlankStringElse(cause.getMessage(),
                cause.getClass().getSimpleName()));
    }

    // A circuit breaker's or a rejection's refusal, with what to do about it and Elasticsearch's own words; null
    // for any other error.
    private static Refusal classified(final Throwable cause) {
        final ElasticErrors.Classified classified = ElasticErrors.classify(cause);
        if (classified.kind() == ElasticErrors.Kind.OTHER) {
            return null;
        }
        final String said = " Elasticsearch said: " + NullSafe.nonBlankStringElse(classified.reason(),
                cause instanceof final ElasticsearchException e
                        ? reason(e)
                        : NullSafe.string(cause.getMessage()));
        return switch (classified.kind()) {
            case SHORT_OF_MEMORY -> new Refusal(Reason.CLUSTER_BUSY, "Elasticsearch is short of memory right now, "
                                                                     + "so it refused the query: try again "
                                                                     + "shortly." + said);
            case OVERLOADED -> new Refusal(Reason.CLUSTER_BUSY, "Elasticsearch is overloaded right now, so it "
                                                                + "refused the query: try again shortly." + said);
            case TOO_MUCH_MEMORY -> new Refusal(Reason.TOO_MUCH_MEMORY, "The query needs more memory than "
                                                                        + "Elasticsearch allows it: narrow the "
                                                                        + "time range, group by fewer "
                                                                        + "high-cardinality fields, avoid "
                                                                        + "MV_EXPAND on large multi-value fields, "
                                                                        + "or lower the LIMIT." + said);
            case OTHER -> null;
        };
    }

    // Elasticsearch's reason, without the count of problems it starts with.
    private static String reason(final ElasticsearchException e) {
        final ErrorCause error = e.error();
        final String reason = error == null
                ? e.getMessage()
                : NullSafe.nonBlankStringElse(error.reason(), error.type());
        return PROBLEMS_PREFIX.matcher(NullSafe.string(reason)).replaceFirst("").strip();
    }

    // Reads an ES|QL response in JSON, rows as arrays.
    static Response parse(final JsonNode json) {
        if (json.path("is_partial").asBoolean(false)) {
            throw new Refusal(Reason.CLUSTER_ERROR, "Elasticsearch gave only part of the results, as part of the "
                                                    + "index couldn't be read");
        }
        final List<Column> columns = new ArrayList<>();
        for (final JsonNode column : json.path("columns")) {
            columns.add(new Column(column.path("name").asString(), column.path("type").asString()));
        }
        final List<List<Object>> rows = new ArrayList<>();
        for (final JsonNode row : json.path("values")) {
            final List<Object> values = new ArrayList<>();
            for (final JsonNode value : row) {
                values.add(value(value));
            }
            rows.add(values);
        }
        return new Response(columns, rows, json.path("took").asLong(0), List.of());
    }

    /// The warnings to pass on, in Elasticsearch's own words: the text of each warning header, unescaped, without
    /// the one saying a limit was added, each once, at most [#MAX_WARNINGS].
    ///
    /// @param headers The warnings as the client gave them: whole headers (`299 Elasticsearch-9.5.3-... "text"`)
    ///                or their text alone.
    /// @return The warnings.
    static List<String> warnings(final List<String> headers) {
        final Set<String> warnings = new LinkedHashSet<>();
        for (final String header : NullSafe.list(headers)) {
            final String text = warningText(header);
            if (!text.isEmpty() && !ADDED_LIMIT.matcher(text).find()) {
                warnings.add(text);
            }
            if (warnings.size() >= MAX_WARNINGS) {
                break;
            }
        }
        return List.copyOf(warnings);
    }

    private static String warningText(final String header) {
        // The client gives the text taken out of the header, still escaped as in the header; whole headers are
        // read too.
        final String stripped = NullSafe.trim(header);
        final Matcher matcher = WARNING_HEADER.matcher(stripped);
        final String text = matcher.matches()
                ? matcher.group(1)
                : stripped;
        return ESCAPED.matcher(text).replaceAll("$1").strip();
    }

    private static Object value(final JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        } else if (node.isArray()) {
            final List<Object> values = new ArrayList<>();
            for (final JsonNode item : node) {
                values.add(value(item));
            }
            return values;
        } else if (node.isIntegralNumber()) {
            return node.canConvertToLong()
                    ? (Object) node.longValue()
                    : node.bigIntegerValue();
        } else if (node.isFloatingPointNumber()) {
            return node.isBigDecimal()
                    ? node.decimalValue()
                    : (Object) node.doubleValue();
        } else if (node.isBoolean()) {
            return node.booleanValue();
        } else if (node.isString()) {
            return node.stringValue();
        }
        return node.toString();
    }

    private static String joinNotes(final String first, final String second) {
        if (first == null) {
            return second;
        } else if (second == null) {
            return first;
        }
        return first + " " + second;
    }


    // --------------------------------------------------------------------------------


    /// One ES|QL response.
    ///
    /// @param columns Its columns.
    /// @param rows    Its rows.
    /// @param tookMs   How long Elasticsearch took.
    /// @param warnings What Elasticsearch warned of (see [#warnings(List)]).
    record Response(List<Column> columns, List<List<Object>> rows, long tookMs, List<String> warnings) {

        /// @param warnings What Elasticsearch warned of.
        /// @return This response, with those warnings.
        Response withWarnings(final List<String> warnings) {
            return new Response(columns, rows, tookMs, warnings);
        }
    }
}
