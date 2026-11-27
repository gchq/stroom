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
import stroom.ai.api.ElasticEql;
import stroom.ai.api.ElasticEsql;
import stroom.ai.api.ElasticKeyStreams;
import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.TimeRange;
import stroom.query.common.v2.IndexFieldCache;
import stroom.search.elastic.ElasticClientCache;
import stroom.search.elastic.ElasticClusterStore;
import stroom.search.elastic.ElasticIndexStore;
import stroom.search.elastic.search.ElasticQueryParams;
import stroom.search.elastic.search.SearchExpressionQueryBuilder;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.elastic.shared.ElasticIndexField;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorCause;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.json.JsonpUtils;
import co.elastic.clients.json.SimpleJsonpMapper;
import co.elastic.clients.transport.endpoints.BinaryEndpoint;
import co.elastic.clients.transport.endpoints.BinaryResponse;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

/// Reads the events of Elastic Index documents in join-key order ([ElasticKeyStreams]), as the user: Use on every
/// document is checked on every call, as for a search, and each call runs with the cluster's connection. Additive:
/// nothing else in Stroom uses it.
///
/// Keys are read by a composite aggregation on the join fields, paged by its `after_key`; events by a point-in-time
/// search sorted by the join fields, then the time field (`numeric_type: date`, so `date_nanos` sorts with `date`),
/// then the point in time's own tiebreaker, paged by `search_after`, with only the fields asked for (`fields`, no
/// `_source`). A join field with a transform is read as a runtime keyword field (a Painless script of the same
/// transform as [Transform#apply(String)]), which the query, the sort and the aggregation all use, so the events
/// come in the order of the transformed key. A side's filter is a Stroom expression made into Query DSL as a search
/// makes it ([SearchExpressionQueryBuilder]); its time range, key range, terms and the join fields' presence are
/// added as filters, and a scan also needs the time field to be present, as it orders each key's events by it.
///
/// Each request is waited for in short slices ([#STOP_CHECK]) up to its time limit, asking between them whether the
/// caller has stopped it: one stopped, or still running at the limit, is cancelled, which closes its connection, so
/// Elasticsearch cancels it. Elasticsearch's circuit breakers and rejections are told apart ([ElasticErrors]).
public class ElasticKeyStreamsService implements ElasticKeyStreams {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ElasticKeyStreamsService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    // Writes a filter's Query DSL as JSON, before a client is at hand.
    private static final JsonpMapper JSONP_MAPPER = new SimpleJsonpMapper();

    /// The oldest cluster version a scan reads: points in time are generally available from 7.10.
    static final int[] MIN_VERSION = {7, 10};
    /// How long a request is waited for before asking again whether the caller has stopped it.
    static final Duration STOP_CHECK = Duration.ofMillis(250);
    /// How long Elasticsearch keeps a scan's point in time between pages.
    static final String KEEP_ALIVE = "2m";
    /// The runtime field a transformed join field is read as: this, then its position from 0.
    static final String KEY_FIELD_PREFIX = "stroom_join_key_";
    private static final String COMPOSITE = "keys";
    private static final Set<String> TEXT_TYPES = Set.of("text", "match_only_text");
    private static final String NESTED_TYPE = "nested";
    private static final Pattern PROBLEMS_PREFIX = Pattern.compile("^Found \\d+ problems?\\s*", Pattern.MULTILINE);
    private static final Pattern EXPIRED = Pattern.compile("search_context_missing_exception|No search context found"
                                                           + "|point in time .* (?:has )?expired",
            Pattern.CASE_INSENSITIVE);
    // The sort value Elasticsearch gives an event without the field, in ascending order: never a real time.
    private static final long MISSING_LONG = Long.MAX_VALUE;
    // What every side's documents need: one cluster; a time field only where a scan orders by it.
    private static final ElasticDocs.Rules RULES = new ElasticDocs.Rules(null,
            "a join's sides are read from one cluster", null);

    private final SecurityContext securityContext;
    private final ElasticIndexStore elasticIndexStore;
    private final ElasticClusterStore elasticClusterStore;
    private final ElasticClientCache elasticClientCache;
    private final Provider<AiService> aiServiceProvider;
    private final IndexFieldCache indexFieldCache;
    private final WordListProvider wordListProvider;

    /// @param securityContext     Checks Use on the index docs, and reads the docs they need.
    /// @param elasticIndexStore   Reads the index docs.
    /// @param elasticClusterStore Reads their cluster doc.
    /// @param elasticClientCache  Gives a client for the cluster's connection.
    /// @param aiServiceProvider   Used by the filter's query builder, as for a search.
    /// @param indexFieldCache     The index's fields, for the filter's query builder.
    /// @param wordListProvider    Dictionaries the filter names.
    @Inject
    public ElasticKeyStreamsService(final SecurityContext securityContext,
                                    final ElasticIndexStore elasticIndexStore,
                                    final ElasticClusterStore elasticClusterStore,
                                    final ElasticClientCache elasticClientCache,
                                    final Provider<AiService> aiServiceProvider,
                                    final IndexFieldCache indexFieldCache,
                                    final WordListProvider wordListProvider) {
        this.securityContext = securityContext;
        this.elasticIndexStore = elasticIndexStore;
        this.elasticClusterStore = elasticClusterStore;
        this.elasticClientCache = elasticClientCache;
        this.aiServiceProvider = aiServiceProvider;
        this.indexFieldCache = indexFieldCache;
        this.wordListProvider = wordListProvider;
    }

    @Override
    public Target target(final List<DocRef> indexes) {
        final ElasticDocs.Checked docs = docs(indexes);
        return securityContext.useAsReadResult(() -> elasticClientCache.contextResult(
                docs.cluster().getConnection(), client -> {
                    final String version = checkVersion(client);
                    final List<Doc> found = new ArrayList<>();
                    for (int i = 0; i < docs.all().size(); i++) {
                        final ElasticIndexDoc indexDoc = docs.all().get(i);
                        found.add(new Doc(indexes.get(i), docs.indexNames().get(i), timeField(indexDoc),
                                ElasticEqlService.fields(indexDoc)));
                    }
                    return new Target(version, found);
                }));
    }

    @Override
    public KeyPage keyPage(final KeyRequest request) {
        Objects.requireNonNull(request, "request");
        final Read read = read(request.side());
        final Instant deadline = deadline(request.timeLimit());
        final ObjectNode body = keyBody(read, request.after(), Math.clamp(request.size(), 1, MAX_PAGE), null);
        return securityContext.useAsReadResult(() -> elasticClientCache.contextResult(
                read.docs().cluster().getConnection(), client -> {
                    final JsonNode response = send(client, "POST", indexPath(read.indexName()) + "/_search",
                            Map.of(), body, deadline, request.timeLimit(), request.stopped());
                    return keyPage(response, read.keys().size());
                }));
    }

    @Override
    public Scan openScan(final ScanRequest request) {
        Objects.requireNonNull(request, "request");
        final Read read = read(request.side());
        if (read.timeField() == null) {
            throw new Refusal(Reason.NO_TIME_FIELD, "Elastic Index '" + read.indexDoc().getName() + "' has no time "
                                                    + "field, which a scan orders each key's events by");
        }
        final Instant deadline = deadline(request.timeLimit());
        return securityContext.useAsReadResult(() -> elasticClientCache.contextResult(
                read.docs().cluster().getConnection(), client -> {
                    checkVersion(client);
                    final JsonNode response = send(client, "POST", indexPath(read.indexName()) + "/_pit",
                            Map.of("keep_alive", KEEP_ALIVE), null, deadline, request.timeLimit(),
                            request.stopped());
                    final String id = response.path("id").asString("");
                    if (id.isEmpty()) {
                        throw new Refusal(Reason.CLUSTER_ERROR, "Elasticsearch opened no point in time");
                    }
                    return new Scan(request, id, null);
                }));
    }

    @Override
    public ScanPage scanPage(final NextPage request) {
        Objects.requireNonNull(request, "request");
        final Scan scan = Objects.requireNonNull(request.scan(), "scan");
        final ScanRequest scanRequest = scan.request();
        final Read read = read(scanRequest.side());
        if (read.timeField() == null) {
            throw new Refusal(Reason.NO_TIME_FIELD, "Elastic Index '" + read.indexDoc().getName() + "' has no time "
                                                    + "field, which a scan orders each key's events by");
        }
        final int size = Math.clamp(scanRequest.size(), 1, MAX_PAGE);
        final ObjectNode body = scanBody(read, scan, scanRequest.fields(), size);
        final Instant deadline = deadline(request.timeLimit());
        return securityContext.useAsReadResult(() -> elasticClientCache.contextResult(
                read.docs().cluster().getConnection(), client -> {
                    final JsonNode response = send(client, "POST", "/_search", Map.of(), body, deadline,
                            request.timeLimit(), scanRequest.stopped());
                    final ScanPage page = scanPage(response, scan, read.keys().size(), size);
                    if (page.next() == null) {
                        close(client, response.path("pit_id").asString(scan.pointInTime()));
                    }
                    return page;
                }));
    }

    @Override
    public void close(final Scan scan) {
        if (scan == null || NullSafe.isBlankString(scan.pointInTime())) {
            return;
        }
        try {
            final Read read = read(scan.request().side());
            securityContext.useAsRead(() -> elasticClientCache.context(read.docs().cluster().getConnection(),
                    client -> close(client, scan.pointInTime())));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Unable to close a point in time: {}", e.getMessage()), e);
        }
    }

    @Override
    public Sample sampleKeys(final SampleRequest request) {
        Objects.requireNonNull(request, "request");
        final Read read = read(request.side());
        final Instant deadline = deadline(request.timeLimit());
        final long everyRows = Math.max(1, request.everyRows());
        final int most = Math.max(0, request.most());
        return securityContext.useAsReadResult(() -> elasticClientCache.contextResult(
                read.docs().cluster().getConnection(), client -> {
                    final List<Object> boundaries = new ArrayList<>();
                    long keys = 0;
                    long rows = 0;
                    long tookMs = 0;
                    long nextCut = everyRows;
                    List<Object> after = null;
                    do {
                        final ObjectNode body = keyBody(read, after, MAX_PAGE, 1);
                        final JsonNode response = send(client, "POST", indexPath(read.indexName()) + "/_search",
                                Map.of(), body, deadline, request.timeLimit(), request.stopped());
                        final KeyPage page = keyPage(response, 1);
                        tookMs += page.tookMs();
                        for (final KeyCount key : page.keys()) {
                            keys++;
                            rows += key.count();
                            if (rows >= nextCut && boundaries.size() < most) {
                                boundaries.add(key.key().getFirst());
                                while (nextCut <= rows) {
                                    nextCut += everyRows;
                                }
                            }
                        }
                        after = page.after();
                    } while (after != null);
                    return new Sample(boundaries, keys, rows, tookMs);
                }));
    }

    // ------------------------------------------------------------------------------------------------
    // The side

    // The side's document, checked, with its join fields as read and its filter as Query DSL.
    private Read read(final Side side) {
        Objects.requireNonNull(side, "side");
        if (side.keys().isEmpty()) {
            throw new Refusal(Reason.INVALID, "No join field was given");
        }
        final ElasticDocs.Checked docs = docs(List.of(side.index()));
        final ElasticIndexDoc indexDoc = docs.first();
        final Map<String, ElasticEql.Field> mapping = ElasticEqlService.fields(indexDoc);
        final List<String> keys = new ArrayList<>();
        final List<String> sourceFields = new ArrayList<>();
        final Map<String, String> scripts = new LinkedHashMap<>();
        for (int i = 0; i < side.keys().size(); i++) {
            final KeyField key = side.keys().get(i);
            checkKey(indexDoc, key, mapping);
            sourceFields.add(key.field());
            if (key.transforms().isEmpty()) {
                keys.add(key.field());
            } else {
                final String name = KEY_FIELD_PREFIX + i;
                keys.add(name);
                scripts.put(name, script(key));
            }
        }
        for (int i = 0; i < side.terms().size(); i++) {
            if (side.terms().get(i).size() > MAX_TERMS) {
                throw new Refusal(Reason.INVALID, "The terms of join field " + (i + 1) + " are "
                                                  + side.terms().get(i).size() + " values: at most " + MAX_TERMS
                                                  + " may be given at once");
            }
        }
        final String timeField = timeField(indexDoc);
        final ObjectNode query = query(indexDoc, side, keys, timeField);
        return new Read(docs, indexDoc, docs.indexNames().getFirst(), timeField, keys, sourceFields, scripts, query);
    }

    /// A document's time field: none if it names none, or if its mapping is known and doesn't hold it (a
    /// document's time field is `@timestamp` unless set, so a directory of users without one has it unmapped).
    ///
    /// @param indexDoc The document.
    /// @return Its time field, or null.
    static String timeField(final ElasticIndexDoc indexDoc) {
        if (NullSafe.isBlankString(indexDoc.getTimeField())) {
            return null;
        }
        final String timeField = indexDoc.getTimeField().strip();
        final List<ElasticIndexField> fields = NullSafe.list(indexDoc.getFields());
        return fields.isEmpty() || fields.stream().anyMatch(field -> field != null
                                                                     && timeField.equals(field.getFldName()))
                ? timeField
                : null;
    }

    // Refuses a join field that can't be a key: nested, text, or transformed when it isn't a string.
    private static void checkKey(final ElasticIndexDoc indexDoc,
                                 final KeyField key,
                                 final Map<String, ElasticEql.Field> mapping) {
        if (NullSafe.isBlankString(key.field())) {
            throw new Refusal(Reason.INVALID, "A join field has no name");
        }
        final ElasticEql.Field field = mapping.get(key.field());
        if (field == null) {
            if (!mapping.isEmpty()) {
                throw new Refusal(Reason.UNKNOWN_FIELD, "'" + key.field() + "' is not a field of Elastic Index '"
                                                        + indexDoc.getName() + "'", List.of(key.field()));
            }
            return;
        }
        if (field.nested() || NESTED_TYPE.equals(field.nativeType())) {
            throw new Refusal(Reason.KEY_NOT_USABLE, "'" + key.field() + "' in Elastic Index '" + indexDoc.getName()
                                                     + "' is in a nested field, so it can't be a join key",
                    List.of(key.field()));
        }
        if (TEXT_TYPES.contains(field.nativeType())) {
            throw new Refusal(Reason.KEY_NOT_USABLE, "'" + key.field() + "' in Elastic Index '" + indexDoc.getName()
                                                     + "' is a " + field.nativeType() + " field, so it can't be a "
                                                     + "join key: join on its keyword subfield",
                    List.of(key.field()));
        }
        if (!key.transforms().isEmpty() && !isString(field.nativeType())) {
            throw new Refusal(Reason.KEY_NOT_USABLE, "'" + key.field() + "' in Elastic Index '" + indexDoc.getName()
                                                     + "' is a " + field.nativeType() + " field: only a text value "
                                                     + "(a keyword field) can be transformed",
                    List.of(key.field()));
        }
    }

    private static boolean isString(final String nativeType) {
        return "keyword".equals(nativeType) || "constant_keyword".equals(nativeType)
               || "wildcard".equals(nativeType);
    }

    /// The Painless script of a transformed join field's runtime field: each value of the field, transformed in
    /// order as [Transform#apply(String)] does, emitted.
    ///
    /// @param key The join field.
    /// @return The script's source.
    static String script(final KeyField key) {
        final StringBuilder sb = new StringBuilder("def f = params.f; if (doc.containsKey(f)) { "
                                                   + "for (def raw : doc[f]) { String v = raw.toString(); ");
        for (final Transform transform : key.transforms()) {
            sb.append(switch (transform) {
                case LOWERCASE -> "v = v.toLowerCase(); ";
                case STRIP_DOMAIN -> "int s = v.lastIndexOf('\\\\'); if (s >= 0) { v = v.substring(s + 1); } "
                                     + "int a = v.indexOf('@'); if (a > 0) { v = v.substring(0, a); } ";
                case FILE_NAME -> "int e = Math.max(v.lastIndexOf('/'), v.lastIndexOf('\\\\')); "
                                  + "if (e >= 0) { v = v.substring(e + 1); } ";
            });
        }
        return sb.append("emit(v); } }").toString();
    }

    /// The side's query: its filter, its time range, its key range on the first join field, its terms, every join
    /// field present, and (for a scan, which orders by it) the time field present.
    ///
    /// @param indexDoc  The document.
    /// @param side      The side.
    /// @param keys      The join fields as read (a transformed one's runtime field).
    /// @param timeField The document's time field, or null.
    /// @return The query, as JSON.
    private ObjectNode query(final ElasticIndexDoc indexDoc,
                             final Side side,
                             final List<String> keys,
                             final String timeField) {
        final ArrayNode filters = MAPPER.createArrayNode();
        final Query filter = filter(indexDoc, side.filter());
        if (filter != null) {
            filters.add(json(filter));
        }
        if (side.timeRange() != null && (NullSafe.isNonBlankString(side.timeRange().getFrom())
                                         || NullSafe.isNonBlankString(side.timeRange().getTo()))) {
            try {
                filters.add(json(ElasticEsqlService.timeRangeQuery(indexDoc, side.timeRange(),
                        DateTimeSettings.builder().referenceTime(System.currentTimeMillis()).build())));
            } catch (final ElasticEsql.Refusal e) {
                throw new Refusal(Reason.INVALID, e.getMessage());
            }
        }
        for (final String key : keys) {
            filters.add(exists(key));
        }
        if (timeField != null) {
            filters.add(exists(timeField));
        }
        final KeyRange range = side.range();
        if (!range.all()) {
            final ObjectNode bounds = MAPPER.createObjectNode();
            if (range.after() != null) {
                bounds.set("gt", MAPPER.valueToTree(range.after()));
            }
            if (range.upTo() != null) {
                bounds.set("lte", MAPPER.valueToTree(range.upTo()));
            }
            final ObjectNode on = MAPPER.createObjectNode();
            on.set(keys.getFirst(), bounds);
            final ObjectNode rangeQuery = MAPPER.createObjectNode();
            rangeQuery.set("range", on);
            filters.add(rangeQuery);
        }
        for (int i = 0; i < side.terms().size() && i < keys.size(); i++) {
            final List<Object> values = side.terms().get(i);
            if (!values.isEmpty()) {
                final ObjectNode on = MAPPER.createObjectNode();
                on.set(keys.get(i), MAPPER.valueToTree(values));
                final ObjectNode terms = MAPPER.createObjectNode();
                terms.set("terms", on);
                filters.add(terms);
            }
        }
        final ObjectNode bool = MAPPER.createObjectNode();
        bool.set("filter", filters);
        final ObjectNode query = MAPPER.createObjectNode();
        query.set("bool", bool);
        return query;
    }

    private static ObjectNode exists(final String field) {
        final ObjectNode on = MAPPER.createObjectNode();
        on.put("field", field);
        final ObjectNode exists = MAPPER.createObjectNode();
        exists.set("exists", on);
        return exists;
    }

    // The Query DSL for the expression, or null for none.
    private Query filter(final ElasticIndexDoc indexDoc, final ExpressionOperator expression) {
        if (expression == null || !NullSafe.hasItems(expression.getChildren())) {
            return null;
        }
        final ElasticQueryParams params;
        try {
            params = new SearchExpressionQueryBuilder(aiServiceProvider, indexDoc, indexFieldCache,
                    wordListProvider, DateTimeSettings.builder().referenceTime(System.currentTimeMillis()).build())
                    .buildQuery(expression);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Unable to build the filter: {}", e.getMessage()), e);
            throw new Refusal(Reason.FILTER_NOT_SUPPORTED, "The filter can't be used: "
                                                           + NullSafe.nonBlankStringElse(e.getMessage(),
                    e.getClass().getSimpleName()));
        }
        if (!params.getKnnFieldQueries().isEmpty()) {
            throw new Refusal(Reason.FILTER_NOT_SUPPORTED, "The filter has a vector search, which a join can't "
                                                           + "take");
        }
        return params.getQuery();
    }

    private static JsonNode json(final Query query) {
        return MAPPER.readTree(JsonpUtils.toJsonString(query, JSONP_MAPPER));
    }

    // The documents, checked: Use on each, found, each with an index name, one cluster.
    private ElasticDocs.Checked docs(final List<DocRef> indexes) {
        return ElasticDocs.check(securityContext, elasticIndexStore, elasticClusterStore, indexes, RULES,
                (problem, message, names) -> new Refusal(reason(problem), message, names));
    }

    private static Reason reason(final ElasticDocs.Problem problem) {
        return switch (problem) {
            case NOT_FOUND -> Reason.NOT_FOUND;
            case NOT_QUERYABLE -> Reason.NOT_QUERYABLE;
            case NO_TIME_FIELD -> Reason.NO_TIME_FIELD;
            case DIFFERENT_CLUSTERS -> Reason.DIFFERENT_CLUSTERS;
            // Never asked for: each side orders by its own time field.
            case DIFFERENT_TIME_FIELDS -> Reason.INVALID;
        };
    }

    // ------------------------------------------------------------------------------------------------
    // Bodies

    /// The body of a page of keys: a composite aggregation on the join fields (the first `fields` of them, all if
    /// null), the side's query, no hits.
    ///
    /// @param read   The side, as read.
    /// @param after  The key the page starts after, or null.
    /// @param size   The most keys.
    /// @param fields How many of the join fields to aggregate on (from the first), or null for all.
    /// @return The body.
    static ObjectNode keyBody(final Read read, final List<Object> after, final int size, final Integer fields) {
        final int count = fields == null
                ? read.keys().size()
                : Math.min(fields, read.keys().size());
        final ObjectNode body = MAPPER.createObjectNode();
        body.put("size", 0);
        body.put("track_total_hits", false);
        runtimeMappings(body, read);
        body.set("query", read.query());
        final ArrayNode sources = MAPPER.createArrayNode();
        for (int i = 0; i < count; i++) {
            final ObjectNode terms = MAPPER.createObjectNode();
            terms.put("field", read.keys().get(i));
            final ObjectNode source = MAPPER.createObjectNode();
            source.set("terms", terms);
            final ObjectNode named = MAPPER.createObjectNode();
            named.set("k" + i, source);
            sources.add(named);
        }
        final ObjectNode composite = MAPPER.createObjectNode();
        composite.put("size", size);
        composite.set("sources", sources);
        if (after != null && !after.isEmpty()) {
            final ObjectNode afterKey = MAPPER.createObjectNode();
            for (int i = 0; i < count && i < after.size(); i++) {
                afterKey.set("k" + i, MAPPER.valueToTree(after.get(i)));
            }
            composite.set("after", afterKey);
        }
        final ObjectNode agg = MAPPER.createObjectNode();
        agg.set("composite", composite);
        final ObjectNode aggs = MAPPER.createObjectNode();
        aggs.set(COMPOSITE, agg);
        body.set("aggs", aggs);
        return body;
    }

    /// The body of a page of a scan: the side's query on the point in time, sorted by the join fields then the time
    /// field, after the last event read, with the fields asked for and no `_source`.
    ///
    /// @param read   The side, as read.
    /// @param scan   The scan.
    /// @param fields The fields to fetch.
    /// @param size   The most events.
    /// @return The body.
    static ObjectNode scanBody(final Read read, final Scan scan, final List<String> fields, final int size) {
        final ObjectNode body = MAPPER.createObjectNode();
        body.put("size", size);
        body.put("track_total_hits", false);
        body.put("_source", false);
        runtimeMappings(body, read);
        body.set("query", read.query());
        final ArrayNode sort = MAPPER.createArrayNode();
        for (final String key : read.keys()) {
            final ObjectNode order = MAPPER.createObjectNode();
            order.put("order", "asc");
            final ObjectNode on = MAPPER.createObjectNode();
            on.set(key, order);
            sort.add(on);
        }
        final ObjectNode time = MAPPER.createObjectNode();
        time.put("order", "asc");
        time.put("numeric_type", "date");
        final ObjectNode onTime = MAPPER.createObjectNode();
        onTime.set(read.timeField(), time);
        sort.add(onTime);
        body.set("sort", sort);
        final ObjectNode pit = MAPPER.createObjectNode();
        pit.put("id", scan.pointInTime());
        pit.put("keep_alive", KEEP_ALIVE);
        body.set("pit", pit);
        if (scan.searchAfter() != null) {
            body.set("search_after", MAPPER.valueToTree(scan.searchAfter()));
        }
        if (!fields.isEmpty()) {
            final ArrayNode wanted = MAPPER.createArrayNode();
            fields.stream().distinct().forEach(wanted::add);
            body.set("fields", wanted);
        }
        return body;
    }

    private static void runtimeMappings(final ObjectNode body, final Read read) {
        if (read.scripts().isEmpty()) {
            return;
        }
        final ObjectNode mappings = MAPPER.createObjectNode();
        for (int i = 0; i < read.keys().size(); i++) {
            final String name = read.keys().get(i);
            final String source = read.scripts().get(name);
            if (source == null) {
                continue;
            }
            final ObjectNode params = MAPPER.createObjectNode();
            params.put("f", read.fields().get(i));
            final ObjectNode script = MAPPER.createObjectNode();
            script.put("source", source);
            script.set("params", params);
            final ObjectNode field = MAPPER.createObjectNode();
            field.put("type", "keyword");
            field.set("script", script);
            mappings.set(name, field);
        }
        body.set("runtime_mappings", mappings);
    }

    // ------------------------------------------------------------------------------------------------
    // Answers

    /// Reads a page of keys from a composite aggregation's answer.
    ///
    /// @param response Elasticsearch's answer.
    /// @param fields   How many join fields each key has.
    /// @return The page; its `after` is null once a page has fewer keys than asked for, or none.
    static KeyPage keyPage(final JsonNode response, final int fields) {
        final JsonNode composite = response.path("aggregations").path(COMPOSITE);
        final List<KeyCount> keys = new ArrayList<>();
        for (final JsonNode bucket : composite.path("buckets")) {
            keys.add(new KeyCount(key(bucket.path("key"), fields), bucket.path("doc_count").asLong(0)));
        }
        final JsonNode afterKey = composite.path("after_key");
        final List<Object> after = keys.isEmpty() || afterKey.isMissingNode() || afterKey.isNull()
                ? null
                : key(afterKey, fields);
        return new KeyPage(keys, after, response.path("took").asLong(0));
    }

    private static List<Object> key(final JsonNode key, final int fields) {
        final List<Object> values = new ArrayList<>();
        for (int i = 0; i < fields; i++) {
            values.add(value(key.path("k" + i)));
        }
        return values;
    }

    /// Reads a page of a scan from a search's answer.
    ///
    /// @param response Elasticsearch's answer.
    /// @param scan     The scan the page was read from.
    /// @param keys     How many join fields each event's key has (its first sort values).
    /// @param size     The most events asked for: fewer means the scan is at its end.
    /// @return The page.
    static ScanPage scanPage(final JsonNode response, final Scan scan, final int keys, final int size) {
        final List<Row> rows = new ArrayList<>();
        List<Object> last = null;
        for (final JsonNode hit : response.path("hits").path("hits")) {
            final List<Object> sort = new ArrayList<>();
            hit.path("sort").forEach(value -> sort.add(value(value)));
            final List<Object> key = new ArrayList<>(sort.subList(0, Math.min(keys, sort.size())));
            final Object time = sort.size() > keys
                    ? sort.get(keys)
                    : null;
            final Long ms = time instanceof final Number number && number.longValue() != MISSING_LONG
                    ? number.longValue()
                    : null;
            final Map<String, List<Object>> fields = new LinkedHashMap<>();
            hit.path("fields").properties().forEach(entry -> {
                final List<Object> values = new ArrayList<>();
                entry.getValue().forEach(value -> values.add(value(value)));
                fields.put(entry.getKey(), values);
            });
            rows.add(new Row(key, ms, hit.path("_index").asString(""), hit.path("_id").asString(""), fields));
            last = sort;
        }
        final String pit = response.path("pit_id").asString(scan.pointInTime());
        final Scan next = rows.size() < size || last == null
                ? null
                : new Scan(scan.request(), pit, last);
        return new ScanPage(rows, next, response.path("took").asLong(0));
    }

    private static Object value(final JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        } else if (node.isIntegralNumber()) {
            return node.canConvertToLong()
                    ? (Object) node.longValue()
                    : node.asString();
        } else if (node.isFloatingPointNumber()) {
            return node.doubleValue();
        } else if (node.isBoolean()) {
            return node.booleanValue();
        } else if (node.isString()) {
            return node.stringValue();
        }
        return node.toString();
    }

    // ------------------------------------------------------------------------------------------------
    // Sending

    private static Instant deadline(final Duration timeLimit) {
        return timeLimit == null
                ? null
                : Instant.now().plus(timeLimit);
    }

    /// @param indexName A document's index name (or pattern, or comma-separated list).
    /// @return Its path, each name encoded.
    static String indexPath(final String indexName) {
        return "/" + String.join(",", Arrays.stream(indexName.split(","))
                .map(String::strip)
                .filter(name -> !name.isEmpty())
                .map(name -> URLEncoder.encode(name, StandardCharsets.UTF_8).replace("%2A", "*"))
                .toList());
    }

    // Sends a request, waiting until the deadline at most, in slices between which it asks whether the caller has
    // stopped it; a request stopped, or still running at the deadline, is cancelled.
    private static JsonNode send(final ElasticsearchClient client,
                                 final String method,
                                 final String path,
                                 final Map<String, String> params,
                                 final ObjectNode body,
                                 final Instant deadline,
                                 final Duration timeLimit,
                                 final BooleanSupplier stopped) {
        if (stopped.getAsBoolean()) {
            throw stoppedRefusal();
        }
        if (deadline != null && !Instant.now().isBefore(deadline)) {
            throw timedOut(timeLimit);
        }
        LOGGER.debug(() -> LogUtil.message("Key stream request {} {}: {}", method, path, body));
        final Map<String, String> query = new LinkedHashMap<>(params);
        query.put("allow_partial_search_results", "false");
        if (path.endsWith("/_pit")) {
            query.remove("allow_partial_search_results");
        }
        final BinaryEndpoint<JsonData> endpoint = new BinaryEndpoint<>(
                "es/stroom.key_streams",
                r -> method,
                r -> path,
                r -> Map.of(),
                r -> query,
                r -> Map.of(),
                body != null,
                null);
        // A request without a body still passes an object, which the endpoint doesn't send.
        final JsonData request = JsonData.fromJson(body == null
                ? "{}"
                : body.toString());
        final CompletableFuture<BinaryResponse> future = client._transport().performRequestAsync(request, endpoint,
                client._transportOptions());
        try (final BinaryResponse response = await(future, deadline, timeLimit, stopped);
                final InputStream content = response.content()) {
            return MAPPER.readTree(content);
        } catch (final InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw stoppedRefusal();
        } catch (final ExecutionException | CompletionException e) {
            throw failed(e.getCause() == null
                    ? e
                    : e.getCause());
        } catch (final IOException | JacksonException e) {
            LOGGER.debug(() -> LogUtil.message("Unable to read Elasticsearch's answer: {}", e.getMessage()), e);
            throw new Refusal(Reason.CLUSTER_ERROR, "Elasticsearch's answer couldn't be read: " + e.getMessage());
        }
    }

    private static BinaryResponse await(final CompletableFuture<BinaryResponse> future,
                                        final Instant deadline,
                                        final Duration timeLimit,
                                        final BooleanSupplier stopped)
            throws InterruptedException, ExecutionException {
        while (true) {
            final long waitMs = deadline == null
                    ? STOP_CHECK.toMillis()
                    : Math.min(Duration.between(Instant.now(), deadline).toMillis(), STOP_CHECK.toMillis());
            if (waitMs <= 0) {
                future.cancel(true);
                throw timedOut(timeLimit);
            }
            try {
                return future.get(waitMs, TimeUnit.MILLISECONDS);
            } catch (final TimeoutException e) {
                if (stopped.getAsBoolean()) {
                    future.cancel(true);
                    LOGGER.debug("Key stream request stopped by its caller, so cancelled");
                    throw stoppedRefusal();
                }
            }
        }
    }

    // Closes a point in time; one that can't be is removed by Elasticsearch when its keep-alive passes.
    private static void close(final ElasticsearchClient client, final String pointInTime) {
        if (NullSafe.isBlankString(pointInTime)) {
            return;
        }
        try {
            final ObjectNode body = MAPPER.createObjectNode();
            body.put("id", pointInTime);
            send(client, "DELETE", "/_pit", Map.of(), body, deadline(Duration.ofSeconds(30)),
                    Duration.ofSeconds(30), () -> false);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Unable to close a point in time: {}", e.getMessage()), e);
        }
    }

    private static Refusal stoppedRefusal() {
        return new Refusal(Reason.STOPPED, "The read was stopped, so it was cancelled in Elasticsearch");
    }

    private static Refusal timedOut(final Duration timeLimit) {
        return new Refusal(Reason.TIMEOUT, "The read took longer than " + (timeLimit == null
                ? "it may"
                : timeLimit.toMillis() < 1_000
                        ? timeLimit.toMillis() + " ms"
                        : timeLimit.toSeconds() + " seconds") + ", so it was cancelled");
    }

    // What a failed request is refused as.
    private static Refusal failed(final Throwable cause) {
        if (cause instanceof final Refusal refusal) {
            return refusal;
        }
        final ElasticErrors.Classified classified = ElasticErrors.classify(cause);
        final String said = " Elasticsearch said: " + NullSafe.nonBlankStringElse(classified.reason(),
                cause instanceof final ElasticsearchException e
                        ? reason(e)
                        : NullSafe.string(cause.getMessage()));
        switch (classified.kind()) {
            case SHORT_OF_MEMORY -> {
                return new Refusal(Reason.CLUSTER_BUSY, "Elasticsearch is short of memory right now, so it refused "
                                                        + "the read: try again shortly." + said);
            }
            case OVERLOADED -> {
                return new Refusal(Reason.CLUSTER_BUSY, "Elasticsearch is overloaded right now, so it refused the "
                                                        + "read: try again shortly." + said);
            }
            case TOO_MUCH_MEMORY -> {
                return new Refusal(Reason.TOO_MUCH_MEMORY, "The read needs more memory than Elasticsearch allows "
                                                           + "it: narrow the time range or the steps, or use fewer "
                                                           + "workers." + said);
            }
            case OTHER -> {
                // Below.
            }
        }
        if (cause instanceof final ElasticsearchException e) {
            final String reason = reason(e);
            if (EXPIRED.matcher(reason).find() || EXPIRED.matcher(NullSafe.string(e.getMessage())).find()) {
                return new Refusal(Reason.SCAN_EXPIRED, "The scan's point in time has gone: " + reason);
            }
            return new Refusal(Reason.REFUSED, "Elasticsearch refused the read: " + reason);
        }
        LOGGER.debug(() -> LogUtil.message("Key stream request failed: {}", cause.getMessage()), cause);
        return new Refusal(Reason.CLUSTER_ERROR, "The read failed: "
                                                 + NullSafe.nonBlankStringElse(cause.getMessage(),
                cause.getClass().getSimpleName()));
    }

    // Elasticsearch's reason (its root cause's, which says where).
    private static String reason(final ElasticsearchException e) {
        final ErrorCause error = e.error();
        String reason = null;
        if (error != null) {
            reason = NullSafe.list(error.rootCause()).stream()
                    .map(ErrorCause::reason)
                    .filter(NullSafe::isNonBlankString)
                    .findFirst()
                    .orElse(NullSafe.nonBlankStringElse(error.reason(), error.type()));
        }
        return PROBLEMS_PREFIX.matcher(NullSafe.nonBlankStringElse(reason, NullSafe.string(e.getMessage())))
                .replaceFirst("").strip();
    }

    // The cluster's version, refused if it is older than points in time.
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
        if (!ElasticEsqlService.atLeast(version, MIN_VERSION)) {
            throw new Refusal(Reason.CLUSTER_TOO_OLD, "The cluster is Elasticsearch " + version + ": a join's scans "
                                                      + "need 7.10 or later");
        }
        return version;
    }


    // --------------------------------------------------------------------------------


    /// A side, as it is read.
    ///
    /// @param docs      Its document, checked.
    /// @param indexDoc  The document.
    /// @param indexName Its index name.
    /// @param timeField Its time field, or null.
    /// @param keys      Its join fields as read: the field, or a transformed one's runtime field.
    /// @param fields    Its join fields as the document names them, in order (what a runtime field reads).
    /// @param scripts   The runtime fields' scripts, by name.
    /// @param query     Its query.
    record Read(ElasticDocs.Checked docs,
                ElasticIndexDoc indexDoc,
                String indexName,
                String timeField,
                List<String> keys,
                List<String> fields,
                Map<String, String> scripts,
                ObjectNode query) {

    }
}
