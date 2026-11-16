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

import stroom.ai.api.ElasticEql;
import stroom.ai.api.ElasticEsql;
import stroom.docref.DocRef;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.TimeRange;
import stroom.search.elastic.ElasticClientCache;
import stroom.search.elastic.ElasticClusterStore;
import stroom.search.elastic.ElasticIndexStore;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticIndexDoc;
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
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch._types.mapping.RuntimeField;
import co.elastic.clients.elasticsearch._types.mapping.RuntimeFieldType;
import co.elastic.clients.elasticsearch._types.query_dsl.FieldAndFormat;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.eql.EqlSearchRequest;
import co.elastic.clients.elasticsearch.eql.EqlSearchResponse;
import co.elastic.clients.elasticsearch.eql.HitsEvent;
import co.elastic.clients.elasticsearch.eql.HitsSequence;
import co.elastic.clients.elasticsearch.indices.ResolveIndexResponse;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.json.JsonpUtils;
import co.elastic.clients.transport.endpoints.SimpleEndpoint;
import co.elastic.clients.util.ApiTypeHelper;
import jakarta.inject.Inject;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Runs EQL searches over the indexes of Elastic Index documents ([ElasticEql]), as the user: Use on every document
/// is checked, as for a search; they must share a cluster and a time field; and the search runs with the cluster's
/// connection. Additive: nothing else in Stroom uses it.
///
/// The documents' index names are resolved to concrete indices (`_resolve/index`: an alias's indices, a data
/// stream's backing indices), so a runtime field ([ElasticEql#DOC_FIELD]) can say which document's index holds
/// each event, for steps that are to match one document's events only. The search runs asynchronously in
/// Elasticsearch, which answers within the time limit (`wait_for_completion_timeout`); one still running then is
/// deleted, so it stops, and nothing is kept once it completes (`keep_on_completion=false`).
public class ElasticEqlService implements ElasticEql {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ElasticEqlService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /// The oldest cluster version searched: EQL is generally available from 7.10.
    static final int[] MIN_VERSION = {7, 10};
    // The first version that takes `allow_partial_search_results` and `allow_partial_sequence_results` on EQL.
    private static final int[] PARTIAL_RESULTS_VERSION = {8, 18};
    private static final Pattern UNKNOWN_COLUMN = Pattern.compile("Unknown column \\[([^\\]]+)]");
    private static final Pattern PROBLEMS_PREFIX = Pattern.compile("^Found \\d+ problems?\\s*", Pattern.MULTILINE);
    // The runtime field's script: the positions of the documents whose index holds the event.
    private static final String DOC_SCRIPT = "def d = params.docs.get(doc['_index'].value); "
                                             + "if (d != null) { for (def x : d) { emit(x); } }";
    // How long Elasticsearch keeps a search it hasn't finished in the time limit, before it is deleted (the least
    // it takes).
    private static final String KEEP_ALIVE = "1m";
    // What of Elasticsearch's answer is read: errors included, as filter_path applies to them too.
    private static final String FILTER_PATH = "id,is_running,is_partial,timed_out,took,error,status,"
                                              + "hits.sequences.join_keys,hits.sequences.events._index,"
                                              + "hits.sequences.events._id,hits.sequences.events.missing,"
                                              + "hits.sequences.events.fields";

    private final SecurityContext securityContext;
    private final ElasticIndexStore elasticIndexStore;
    private final ElasticClusterStore elasticClusterStore;
    private final ElasticClientCache elasticClientCache;

    /// @param securityContext     Checks Use on the index docs, and reads the docs they need.
    /// @param elasticIndexStore   Reads the index docs.
    /// @param elasticClusterStore Reads their cluster doc.
    /// @param elasticClientCache  Gives a client for the cluster's connection.
    @Inject
    public ElasticEqlService(final SecurityContext securityContext,
                             final ElasticIndexStore elasticIndexStore,
                             final ElasticClusterStore elasticClusterStore,
                             final ElasticClientCache elasticClientCache) {
        this.securityContext = securityContext;
        this.elasticIndexStore = elasticIndexStore;
        this.elasticClusterStore = elasticClusterStore;
        this.elasticClientCache = elasticClientCache;
    }

    @Override
    public Target target(final List<DocRef> indexes) {
        final Docs docs = docs(indexes);
        return securityContext.useAsReadResult(() -> elasticClientCache.contextResult(
                docs.cluster().getConnection(), client -> new Target(checkVersion(client), docs.timeField(),
                        docs.indexNames())));
    }

    @Override
    public Result search(final Request request) {
        Objects.requireNonNull(request, "request");
        final Docs docs = docs(request.indexes());
        final Query filter = filter(docs, request.timeRange());
        final int size = Math.clamp(request.size(), 1, MAX_SEQUENCES);
        final Duration timeLimit = request.timeLimit();
        final Instant deadline = timeLimit == null
                ? null
                : Instant.now().plus(timeLimit);
        return securityContext.useAsReadResult(() -> elasticClientCache.contextResult(
                docs.cluster().getConnection(), client -> {
                    final String version = checkVersion(client);
                    final Map<String, List<String>> docsByIndex = docsByIndex(client, docs.indexNames());
                    final EqlSearchRequest searchRequest = searchRequest(docs, request, filter, size, docsByIndex,
                            version, deadline);
                    final EqlSearchResponse<JsonData> response = run(client, searchRequest, timeLimit);
                    final JsonpMapper mapper = client._jsonpMapper();
                    final List<Sequence> sequences = new ArrayList<>();
                    if (response.hits() != null) {
                        for (final HitsSequence<JsonData> sequence : NullSafe.list(response.hits().sequences())) {
                            sequences.add(sequence(sequence, mapper));
                        }
                    }
                    return new Result(request.query(), docs.indexNames(), docs.timeField(), sequences,
                            Objects.requireNonNullElse(response.took(), 0L), version);
                }));
    }

    // The documents, checked: Use on each, all found, each with an index name, one cluster and one time field.
    private Docs docs(final List<DocRef> indexes) {
        if (NullSafe.isEmptyCollection(indexes)) {
            throw new Refusal(Reason.NOT_FOUND, "No Elastic Index was given");
        }
        for (final DocRef docRef : indexes) {
            if (docRef == null || !securityContext.hasDocumentPermission(docRef, DocumentPermission.USE)) {
                throw new PermissionException(securityContext.getUserRef(),
                        LogUtil.message("You are not authorised to search {}", docRef));
            }
        }
        return securityContext.useAsReadResult(() -> {
            final List<ElasticIndexDoc> found = new ArrayList<>();
            for (final DocRef docRef : indexes) {
                final ElasticIndexDoc indexDoc = elasticIndexStore.readDocument(docRef);
                if (indexDoc == null) {
                    throw new Refusal(Reason.NOT_FOUND, "Elastic Index " + docRef + " was not found");
                }
                if (NullSafe.isBlankString(indexDoc.getIndexName())) {
                    throw new Refusal(Reason.NOT_QUERYABLE, "Elastic Index '" + indexDoc.getName()
                                                            + "' has no index name");
                }
                if (indexDoc.getClusterRef() == null) {
                    throw new Refusal(Reason.NOT_QUERYABLE, "Elastic Index '" + indexDoc.getName() + "' has no "
                                                            + "cluster");
                }
                if (NullSafe.isBlankString(indexDoc.getTimeField())) {
                    throw new Refusal(Reason.NO_TIME_FIELD, "Elastic Index '" + indexDoc.getName() + "' has no time "
                                                            + "field, which an EQL sequence orders its events by");
                }
                found.add(indexDoc);
            }
            final Set<String> clusters = new LinkedHashSet<>();
            found.forEach(doc -> clusters.add(doc.getClusterRef().getUuid()));
            if (clusters.size() > 1) {
                final List<String> names = found.stream()
                        .map(doc -> "'" + doc.getName() + "' on cluster '"
                                    + NullSafe.nonBlankStringElse(doc.getClusterRef().getName(),
                                doc.getClusterRef().getUuid()) + "'")
                        .toList();
                throw new Refusal(Reason.DIFFERENT_CLUSTERS, "The Elastic Indexes are on different clusters ("
                                                             + String.join(", ", names) + "): an EQL sequence "
                                                             + "searches one cluster", names);
            }
            final Set<String> timeFields = new LinkedHashSet<>();
            found.forEach(doc -> timeFields.add(doc.getTimeField().strip()));
            if (timeFields.size() > 1) {
                final List<String> names = found.stream()
                        .map(doc -> "'" + doc.getName() + "' orders by '" + doc.getTimeField().strip() + "'")
                        .toList();
                throw new Refusal(Reason.DIFFERENT_TIME_FIELDS, "The Elastic Indexes have different time fields ("
                                                                + String.join(", ", names) + "): an EQL sequence "
                                                                + "orders every event by one field", names);
            }
            final ElasticClusterDoc cluster = elasticClusterStore.readDocument(found.getFirst().getClusterRef());
            if (cluster == null) {
                throw new Refusal(Reason.NOT_QUERYABLE, "The Elastic Cluster of Elastic Index '"
                                                        + found.getFirst().getName() + "' was not found");
            }
            return new Docs(found.stream().map(doc -> doc.getIndexName().strip()).toList(),
                    timeFields.iterator().next(), cluster, found.getFirst());
        });
    }

    // The time range as a filter on the documents' time field, or null for none.
    private static Query filter(final Docs docs, final TimeRange timeRange) {
        if (timeRange == null || (NullSafe.isBlankString(timeRange.getFrom())
                                  && NullSafe.isBlankString(timeRange.getTo()))) {
            return null;
        }
        try {
            return ElasticEsqlService.timeRangeQuery(docs.first(), timeRange, DateTimeSettings.builder()
                    .referenceTime(System.currentTimeMillis())
                    .build());
        } catch (final ElasticEsql.Refusal e) {
            throw new Refusal(Reason.INVALID, e.getMessage());
        }
    }

    /// The positions (from 1) of the documents whose index names cover each concrete index: an index named as it
    /// is, an alias's indices, a data stream's backing indices, and what a pattern matches.
    ///
    /// @param indexNames Each document's index name (or pattern, or comma-separated list).
    /// @param resolved   What `_resolve/index` gave for each document's name, in the same order.
    /// @return Each concrete index's documents' positions.
    static Map<String, List<String>> docsByIndex(final List<String> indexNames,
                                                 final List<Set<String>> resolved) {
        final Map<String, List<String>> byIndex = new LinkedHashMap<>();
        for (int i = 0; i < indexNames.size(); i++) {
            final String position = String.valueOf(i + 1);
            for (final String index : resolved.get(i)) {
                final List<String> positions = byIndex.computeIfAbsent(index, key -> new ArrayList<>());
                if (!positions.contains(position)) {
                    positions.add(position);
                }
            }
        }
        return byIndex;
    }

    private static Map<String, List<String>> docsByIndex(final ElasticsearchClient client,
                                                         final List<String> indexNames) {
        final List<Set<String>> resolved = new ArrayList<>();
        for (final String indexName : indexNames) {
            resolved.add(concreteIndices(client, indexName));
        }
        return docsByIndex(indexNames, resolved);
    }

    // The concrete indices an index name, alias, data stream, pattern or list of them covers.
    private static Set<String> concreteIndices(final ElasticsearchClient client, final String indexName) {
        final List<String> names = Arrays.stream(indexName.split(","))
                .map(String::strip)
                .filter(name -> !name.isEmpty())
                .toList();
        final ResolveIndexResponse response;
        try {
            response = client.indices().resolveIndex(r -> r.name(names));
        } catch (final ElasticsearchException e) {
            throw new Refusal(Reason.REFUSED, "Elasticsearch couldn't resolve the index '" + indexName + "': "
                                              + reason(e));
        } catch (final IOException | RuntimeException e) {
            throw new Refusal(Reason.CLUSTER_ERROR, "The index '" + indexName + "' couldn't be resolved: "
                                                    + NullSafe.nonBlankStringElse(e.getMessage(),
                    e.getClass().getSimpleName()));
        }
        final Set<String> concrete = new LinkedHashSet<>();
        NullSafe.list(response.indices()).forEach(item -> concrete.add(item.name()));
        NullSafe.list(response.aliases()).forEach(alias -> concrete.addAll(NullSafe.list(alias.indices())));
        NullSafe.list(response.dataStreams()).forEach(stream ->
                concrete.addAll(NullSafe.list(stream.backingIndices())));
        return concrete;
    }

    private static EqlSearchRequest searchRequest(final Docs docs,
                                                  final Request request,
                                                  final Query filter,
                                                  final int size,
                                                  final Map<String, List<String>> docsByIndex,
                                                  final String version,
                                                  final Instant deadline) {
        final List<FieldAndFormat> fields = new ArrayList<>();
        fields.add(FieldAndFormat.of(f -> f.field(docs.timeField()).format("epoch_millis")));
        fields.add(FieldAndFormat.of(f -> f.field(DOC_FIELD)));
        for (final String field : new LinkedHashSet<>(request.fields())) {
            if (!field.equals(docs.timeField()) && !field.equals(DOC_FIELD)) {
                fields.add(FieldAndFormat.of(f -> f.field(field)));
            }
        }
        final RuntimeField docField = RuntimeField.of(r -> r
                .type(RuntimeFieldType.Keyword)
                .script(s -> s
                        .source(source -> source.scriptString(DOC_SCRIPT))
                        .params("docs", JsonData.of(docsByIndex))));
        return EqlSearchRequest.of(b -> {
            b.index(docs.indexNames().stream()
                            .flatMap(name -> Arrays.stream(name.split(",")))
                            .map(String::strip)
                            .filter(name -> !name.isEmpty())
                            .distinct()
                            .toList())
                    .query(request.query())
                    .timestampField(docs.timeField())
                    .size(size)
                    .fields(fields)
                    .runtimeMappings(Map.of(DOC_FIELD, docField))
                    .keepOnCompletion(false);
            if (filter != null) {
                b.filter(filter);
            }
            if (deadline != null) {
                final long remainingMs = Math.max(1, Duration.between(Instant.now(), deadline).toMillis());
                b.waitForCompletionTimeout(Time.of(t -> t.time(remainingMs + "ms")))
                        .keepAlive(Time.of(t -> t.time(KEEP_ALIVE)));
            }
            if (ElasticEsqlService.atLeast(version, PARTIAL_RESULTS_VERSION)) {
                b.allowPartialSearchResults(false).allowPartialSequenceResults(false);
            }
            return b;
        });
    }

    // Runs the search; Elasticsearch waits until the deadline at most, and one still running then is deleted, so it
    // stops. Only what is read is returned (filter_path): not the events' whole _source, which the client otherwise
    // requires, so its check of required properties is turned off while this thread reads the answer. The body is
    // sent as the client writes it but with `filter` as one query: the client writes a list, which Elasticsearch's
    // EQL refuses ("filter doesn't support values of type: START_ARRAY", 9.5.3).
    private static EqlSearchResponse<JsonData> run(final ElasticsearchClient client,
                                                   final EqlSearchRequest request,
                                                   final Duration timeLimit) {
        LOGGER.debug(() -> LogUtil.message("Running EQL search: {}", request.query()));
        final JsonData body = body(request, client._jsonpMapper());
        final String path = "/" + String.join(",", request.index()) + "/_eql/search";
        final SimpleEndpoint<JsonData, EqlSearchResponse<JsonData>> endpoint = new SimpleEndpoint<>(
                "es/eql.search",
                r -> "POST",
                r -> path,
                r -> Map.of(),
                r -> Map.of("filter_path", FILTER_PATH),
                r -> Map.of(),
                true,
                EqlSearchResponse.createEqlSearchResponseDeserializer(JsonData._DESERIALIZER));
        final EqlSearchResponse<JsonData> response;
        try (final ApiTypeHelper.DisabledChecksHandle ignored =
                ApiTypeHelper.DANGEROUS_disableRequiredPropertiesCheck(true)) {
            response = client._transport().performRequest(body, endpoint, client._transportOptions());
        } catch (final IOException | RuntimeException e) {
            throw timedOut(e)
                    ? timedOut(timeLimit)
                    : failed(e);
        }
        if (Boolean.TRUE.equals(response.isRunning())) {
            delete(client, response.id());
            throw timedOut(timeLimit);
        }
        if (Boolean.TRUE.equals(response.isPartial()) || Boolean.TRUE.equals(response.timedOut())) {
            throw new Refusal(Reason.CLUSTER_ERROR, "Elasticsearch gave only part of the results, as part of the "
                                                    + "index couldn't be read in time");
        }
        return response;
    }

    /// The request's body, as the client writes it, with a `filter` of one query given as that query.
    ///
    /// @param request The request.
    /// @param mapper  The client's mapper.
    /// @return The body.
    static JsonData body(final EqlSearchRequest request, final JsonpMapper mapper) {
        final ObjectNode json = (ObjectNode) MAPPER.readTree(JsonpUtils.toJsonString(request, mapper));
        final JsonNode filter = json.get("filter");
        if (filter != null && filter.isArray()) {
            if (filter.size() == 1) {
                json.set("filter", filter.get(0));
            } else if (filter.isEmpty()) {
                json.remove("filter");
            }
        }
        return JsonData.fromJson(json.toString());
    }

    // Deletes a search still running, so Elasticsearch stops it.
    private static void delete(final ElasticsearchClient client, final String id) {
        if (NullSafe.isBlankString(id)) {
            return;
        }
        try {
            client.eql().delete(d -> d.id(id));
        } catch (final IOException | RuntimeException e) {
            // Kept for KEEP_ALIVE at most, then removed by Elasticsearch.
            LOGGER.debug(() -> LogUtil.message("Unable to delete EQL search {}: {}", id, e.getMessage()), e);
        }
    }

    private static Refusal timedOut(final Duration timeLimit) {
        return new Refusal(Reason.TIMEOUT, "The search took longer than "
                                           + (timeLimit == null
                ? "it may"
                : timeLimit.toMillis() < 1_000
                        ? timeLimit.toMillis() + " ms"
                        : timeLimit.toSeconds() + " seconds") + ", so it was stopped");
    }

    // Whether a failure is the client giving up waiting for the answer.
    private static boolean timedOut(final Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException
                || cause instanceof TimeoutException) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }

    // What a failed search is refused as.
    private static Refusal failed(final Throwable cause) {
        if (cause instanceof final Refusal refusal) {
            return refusal;
        }
        if (cause instanceof final ElasticsearchException e) {
            final String reason = reason(e);
            final Matcher matcher = UNKNOWN_COLUMN.matcher(reason);
            final Set<String> unknown = new LinkedHashSet<>();
            while (matcher.find()) {
                unknown.add(matcher.group(1));
            }
            if (!unknown.isEmpty()) {
                return new Refusal(Reason.UNKNOWN_FIELD, "Elasticsearch refused the search: " + reason,
                        List.copyOf(unknown));
            }
            return new Refusal(Reason.REFUSED, "Elasticsearch refused the search: " + reason);
        }
        LOGGER.debug(() -> LogUtil.message("EQL search failed: {}", cause.getMessage()), cause);
        return new Refusal(Reason.CLUSTER_ERROR, "The search failed: "
                                                 + NullSafe.nonBlankStringElse(cause.getMessage(),
                cause.getClass().getSimpleName()));
    }

    // Elasticsearch's reason (its root cause's, which says where), without the count of problems it starts with.
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

    // The cluster's version, refused if it is older than EQL's general availability.
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
            throw new Refusal(Reason.CLUSTER_TOO_OLD, "The cluster is Elasticsearch " + version + ": EQL sequences "
                                                      + "need 7.10 or later");
        }
        return version;
    }

    private static Sequence sequence(final HitsSequence<JsonData> sequence, final JsonpMapper mapper) {
        final List<Object> joinKeys = new ArrayList<>();
        for (final JsonData key : NullSafe.list(sequence.joinKeys())) {
            joinKeys.add(value(key, mapper));
        }
        final List<Event> events = new ArrayList<>();
        for (final HitsEvent<JsonData> event : NullSafe.list(sequence.events())) {
            final Map<String, List<Object>> fields = new LinkedHashMap<>();
            NullSafe.map(event.fields()).forEach((name, values) -> {
                final List<Object> converted = new ArrayList<>();
                NullSafe.list(values).forEach(value -> converted.add(value(value, mapper)));
                fields.put(name, converted);
            });
            events.add(new Event(NullSafe.string(event.index()), NullSafe.string(event.id()),
                    Boolean.TRUE.equals(event.missing()), fields));
        }
        return new Sequence(joinKeys, events);
    }

    // A value as plain Java: a string, number, boolean or null (anything else as its JSON text).
    private static Object value(final JsonData data, final JsonpMapper mapper) {
        if (data == null) {
            return null;
        }
        final Object value = data.to(Object.class, mapper);
        if (value == null || value instanceof String || value instanceof Boolean) {
            return value;
        } else if (value instanceof final Number number) {
            return number instanceof Double || number instanceof Float
                    ? (Object) number.doubleValue()
                    : (Object) number.longValue();
        }
        return String.valueOf(value);
    }


    // --------------------------------------------------------------------------------


    /// The documents of a search, checked.
    ///
    /// @param indexNames Each document's index name, in order.
    /// @param timeField  Their time field.
    /// @param cluster    Their cluster.
    /// @param first      The first document.
    private record Docs(List<String> indexNames, String timeField, ElasticClusterDoc cluster, ElasticIndexDoc first) {

    }
}
