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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Runs EQL searches over the indexes of Elastic Index documents ([ElasticEql]), as the user: Use on every document
/// is checked, as for a search; they must share a cluster and a time field; and the search runs with the cluster's
/// connection. Additive: nothing else in Stroom uses it.
///
/// The documents' index names are resolved to concrete indices (`_resolve/index`: an alias's indices, a data
/// stream's backing indices), so a runtime field ([ElasticEql#DOC_FIELD]) can say which document's index holds
/// each event, for steps that are to match one document's events only. The search runs asynchronously in
/// Elasticsearch: it is submitted waiting a short while ([#STOP_CHECK], `wait_for_completion_timeout`), and nothing
/// is kept when it completes in that time (`keep_on_completion=false`). One still running is then asked for again
/// in slices as short, asking between them whether the caller has stopped it ([Request#stopped()]); one stopped, or
/// still running at the time limit, is deleted, so it stops in Elasticsearch, and one read once complete is deleted
/// too, so its results aren't kept. Elasticsearch's circuit breakers and rejections are told apart
/// ([ElasticErrors]), so the caller can say whether to try again shortly or ask for less.
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
    private static final String NESTED_TYPE = "nested";
    // The runtime field named in a query, as a whole word.
    private static final Pattern DOC_FIELD_NAMED = Pattern.compile("\\b" + DOC_FIELD + "\\b");
    private static final String NORMALIZER = "normalizer";
    /// How long Elasticsearch waits for a search to complete before answering, at submission and at each poll:
    /// how soon a stop reaches a running search.
    static final Duration STOP_CHECK = Duration.ofMillis(250);
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
                        docs.indexNames(), docs.all().stream().map(ElasticEqlService::fields).toList())));
    }

    /// What a document's mapping says of its fields, for the caller to write EQL that can search them.
    ///
    /// @param indexDoc The document, its fields read from its mapping.
    /// @return Its fields, by name.
    static Map<String, Field> fields(final ElasticIndexDoc indexDoc) {
        final List<ElasticIndexField> fields = NullSafe.list(indexDoc.getFields());
        final Set<String> nestedContainers = new LinkedHashSet<>();
        fields.stream()
                .filter(field -> field != null && NESTED_TYPE.equals(field.getNativeType()))
                .forEach(field -> nestedContainers.add(field.getFldName()));
        final Map<String, Field> byName = new LinkedHashMap<>();
        for (final ElasticIndexField field : fields) {
            if (field == null || field.getFldName() == null) {
                continue;
            }
            final String name = field.getFldName();
            final boolean nested = nestedContainers.stream()
                    .anyMatch(container -> name.equals(container) || name.startsWith(container + "."));
            byName.put(name, new Field(name, field.getNativeType(), field.getMultiFieldParent(),
                    NullSafe.map(field.getMappingParameters()).get(NORMALIZER), nested));
        }
        return byName;
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
                    final EqlSearchResponse<JsonData> response = run(client, searchRequest, deadline, timeLimit,
                            request.stopped());
                    final JsonpMapper mapper = client._jsonpMapper();
                    final List<Sequence> sequences = new ArrayList<>();
                    if (response.hits() != null) {
                        for (final HitsSequence<JsonData> sequence : NullSafe.list(response.hits().sequences())) {
                            sequences.add(sequence(sequence, mapper, docsByIndex));
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
                    timeFields.iterator().next(), cluster, found);
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
                    .keepOnCompletion(false);
            // The script runs on every event a step that names it reads: only when a step needs it.
            if (usesDocField(request.query())) {
                b.runtimeMappings(Map.of(DOC_FIELD, docField));
            }
            if (filter != null) {
                b.filter(filter);
            }
            b.waitForCompletionTimeout(Time.of(t -> t.time(waitMs(deadline) + "ms")))
                    .keepAlive(Time.of(t -> t.time(KEEP_ALIVE)));
            if (ElasticEsqlService.atLeast(version, PARTIAL_RESULTS_VERSION)) {
                b.allowPartialSearchResults(false).allowPartialSequenceResults(false);
            }
            return b;
        });
    }

    // How long Elasticsearch is to wait for the search to complete before answering: STOP_CHECK, or what is left
    // before the deadline if less (at least 1 ms).
    private static long waitMs(final Instant deadline) {
        final long sliceMs = STOP_CHECK.toMillis();
        return deadline == null
                ? sliceMs
                : Math.clamp(Duration.between(Instant.now(), deadline).toMillis(), 1, sliceMs);
    }

    // Runs the search: submitted, then, while it runs, asked for again in short slices until it completes, the
    // caller stops it or the deadline passes; one stopped, or still running at the deadline, is deleted, so it stops
    // in Elasticsearch. Only what is read is returned (filter_path): not the events' whole _source, which the client
    // otherwise requires, so its check of required properties is turned off while this thread reads the answer.
    // The body is sent as the client writes it but with `filter` as one query: the client writes a list, which
    // Elasticsearch's EQL refuses ("filter doesn't support values of type: START_ARRAY", 9.5.3).
    private static EqlSearchResponse<JsonData> run(final ElasticsearchClient client,
                                                   final EqlSearchRequest request,
                                                   final Instant deadline,
                                                   final Duration timeLimit,
                                                   final BooleanSupplier stopped) {
        LOGGER.debug(() -> LogUtil.message("Running EQL search: {}", request.query()));
        if (stopped.getAsBoolean()) {
            throw stoppedRefusal();
        }
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
        EqlSearchResponse<JsonData> response = perform(client, body, endpoint, timeLimit);
        final String id = response.id();
        boolean polled = false;
        while (Boolean.TRUE.equals(response.isRunning())) {
            if (stopped.getAsBoolean()) {
                delete(client, id);
                LOGGER.debug(() -> LogUtil.message("EQL search {} stopped by its caller, so deleted", id));
                throw stoppedRefusal();
            }
            if (deadline != null && !Instant.now().isBefore(deadline)) {
                delete(client, id);
                throw timedOut(timeLimit);
            }
            if (NullSafe.isBlankString(id)) {
                throw new Refusal(Reason.CLUSTER_ERROR, "Elasticsearch said the search is still running, but gave "
                                                        + "no id to ask for it by");
            }
            try {
                response = perform(client, id, pollEndpoint(id, waitMs(deadline)), timeLimit);
                polled = true;
            } catch (final Refusal refusal) {
                delete(client, id);
                throw refusal;
            }
        }
        if (polled && NullSafe.isNonBlankString(id) && lacksFields(response)) {
            // Elasticsearch (seen on 9.5.3) leaves every event's fields out of the answer to a poll the search
            // completes during, though the stored result has them: read that once.
            LOGGER.debug(() -> LogUtil.message("EQL search {} completed during a poll with no fields on its events, "
                                               + "so its stored result is read", id));
            try {
                response = perform(client, id, pollEndpoint(id, 0), timeLimit);
            } catch (final Refusal refusal) {
                delete(client, id);
                throw refusal;
            }
        }
        if (NullSafe.isNonBlankString(id)) {
            // Read once complete: its results aren't needed again.
            delete(client, id);
        }
        if (Boolean.TRUE.equals(response.isPartial()) || Boolean.TRUE.equals(response.timedOut())) {
            throw new Refusal(Reason.CLUSTER_ERROR, "Elasticsearch gave only part of the results, as part of the "
                                                    + "index couldn't be read in time");
        }
        return response;
    }

    /// @param response A completed search's answer.
    /// @return True if one of its events that happened has no fields, as Elasticsearch gives every event the time
    /// field it was asked for.
    static boolean lacksFields(final EqlSearchResponse<JsonData> response) {
        if (response.hits() == null) {
            return false;
        }
        return NullSafe.list(response.hits().sequences()).stream()
                .flatMap(sequence -> NullSafe.list(sequence.events()).stream())
                .anyMatch(event -> !Boolean.TRUE.equals(event.missing()) && NullSafe.isEmptyMap(event.fields()));
    }

    // The request that asks for a running search again, waiting that long at most for it to complete, keeping it
    // alive for KEEP_ALIVE from now.
    private static SimpleEndpoint<String, EqlSearchResponse<JsonData>> pollEndpoint(final String id,
                                                                                   final long waitMs) {
        final String path = "/_eql/search/" + URLEncoder.encode(id, StandardCharsets.UTF_8);
        return new SimpleEndpoint<>(
                "es/eql.get",
                r -> "GET",
                r -> path,
                r -> Map.of(),
                r -> Map.of("filter_path", FILTER_PATH,
                        "wait_for_completion_timeout", waitMs + "ms",
                        "keep_alive", KEEP_ALIVE),
                r -> Map.of(),
                false,
                EqlSearchResponse.createEqlSearchResponseDeserializer(JsonData._DESERIALIZER));
    }

    // Sends one request of the search, reading only what filter_path keeps.
    private static <T> EqlSearchResponse<JsonData> perform(
            final ElasticsearchClient client,
            final T request,
            final SimpleEndpoint<T, EqlSearchResponse<JsonData>> endpoint,
            final Duration timeLimit) {
        try (final ApiTypeHelper.DisabledChecksHandle ignored =
                ApiTypeHelper.DANGEROUS_disableRequiredPropertiesCheck(true)) {
            return client._transport().performRequest(request, endpoint, client._transportOptions());
        } catch (final IOException | RuntimeException e) {
            throw timedOut(e)
                    ? timedOut(timeLimit)
                    : failed(e);
        }
    }

    private static Refusal stoppedRefusal() {
        return new Refusal(Reason.STOPPED, "The search was stopped, so it was deleted in Elasticsearch");
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
                                                                     + "so it refused the search: try again "
                                                                     + "shortly." + said);
            case OVERLOADED -> new Refusal(Reason.CLUSTER_BUSY, "Elasticsearch is overloaded right now, so it "
                                                                + "refused the search: try again shortly." + said);
            case TOO_MUCH_MEMORY -> new Refusal(Reason.TOO_MUCH_MEMORY, "The search needs more memory than "
                                                                        + "Elasticsearch allows it: narrow the "
                                                                        + "time window, shorten maxspan, or put "
                                                                        + "the more selective step first." + said);
            case OTHER -> null;
        };
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

    /// @param query An EQL query.
    /// @return True if it names [ElasticEql#DOC_FIELD], so needs its runtime field.
    static boolean usesDocField(final String query) {
        return DOC_FIELD_NAMED.matcher(NullSafe.string(query)).find();
    }

    private static Sequence sequence(final HitsSequence<JsonData> sequence,
                                     final JsonpMapper mapper,
                                     final Map<String, List<String>> docsByIndex) {
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
            // Which docs hold it, from its concrete index (as the runtime field would say), without a script.
            final List<String> positions = docsByIndex.get(NullSafe.string(event.index()));
            if (positions != null && !Boolean.TRUE.equals(event.missing())) {
                fields.put(DOC_FIELD, List.copyOf(positions));
            }
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
    /// @param all        The documents, in order.
    private record Docs(List<String> indexNames, String timeField, ElasticClusterDoc cluster,
                        List<ElasticIndexDoc> all) {

        /// @return The first document.
        ElasticIndexDoc first() {
            return all.getFirst();
        }
    }
}
