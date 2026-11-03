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

package stroom.search.elastic.search;

import stroom.ai.api.AiService;
import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.datasource.FieldType;
import stroom.query.common.v2.IndexFieldCache;
import stroom.search.elastic.ElasticClientCache;
import stroom.search.elastic.ElasticClusterStore;
import stroom.search.elastic.ElasticIndexStore;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticFieldStats;
import stroom.search.elastic.shared.ElasticFieldStatsRequest;
import stroom.search.elastic.shared.ElasticFieldValueCount;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.elastic.shared.ElasticIndexField;
import stroom.search.elastic.shared.ElasticIndexFieldSettings;
import stroom.search.elastic.shared.ElasticVirtualFieldGroup;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PermissionException;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.ArrayPercentilesItem;
import co.elastic.clients.elasticsearch._types.aggregations.SamplerAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StatsAggregate;
import co.elastic.clients.elasticsearch._types.mapping.Property.Kind;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchAllQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.json.JsonpMapper;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.json.stream.JsonParser;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// Gives statistics about one field of an Elasticsearch index doc, in the manner of Kibana's field statistics.
///
/// By default the statistics are estimated from a random sample of the documents in the requested time range on the
/// doc's time field, taken with a `random_sampler` aggregation. They can instead be calculated exactly, across every
/// document in the time range. Which statistics are gathered depends on the field's type.
public class ElasticFieldStatsService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ElasticFieldStatsService.class);

    private static final int DEFAULT_TOP_VALUES_COUNT = 10;
    private static final int MAX_TOP_VALUES_COUNT = 100;
    private static final int HISTOGRAM_BUCKETS = 10;
    private static final String RANGE_SEPARATOR = " – ";
    private static final int MAX_DECIMAL_PLACES = 10;
    private static final Set<FieldType> WHOLE_NUMBER_TYPES = Set.of(FieldType.ID, FieldType.INTEGER, FieldType.LONG);
    private static final String SAMPLE_AGG = "sample";
    // Elasticsearch only accepts a random sampler probability of up to 0.5 (or exactly 1)
    private static final double MAX_SAMPLE_PROBABILITY = 0.5;
    // Characters with a meaning in Lucene regular expressions
    private static final String LUCENE_REGEX_SPECIAL_CHARS = ".?+*|{}[]()\"\\#@&<>~";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String HAS_VALUE_AGG = "has_value";
    private static final String DISTINCT_AGG = "distinct";
    private static final String TOP_AGG = "top";
    private static final String STATS_AGG = "stats";
    private static final String MEDIAN_AGG = "median";
    private static final String HISTOGRAM_AGG = "histogram";
    private static final String GROUPS_AGG = "groups";
    private static final Set<FieldType> NUMERIC_TYPES = Set.of(
            FieldType.ID, FieldType.INTEGER, FieldType.LONG, FieldType.FLOAT, FieldType.DOUBLE);
    // Types whose values can be counted, i.e. that have doc values
    private static final Set<FieldType> TERMS_TYPES = Set.of(
            FieldType.KEYWORD, FieldType.IPV4_ADDRESS, FieldType.BOOLEAN);

    private final SecurityContext securityContext;
    private final ElasticIndexStore elasticIndexStore;
    private final ElasticClusterStore elasticClusterStore;
    private final ElasticClientCache elasticClientCache;
    private final Provider<ElasticSearchConfig> elasticSearchConfigProvider;
    private final Provider<AiService> aiServiceProvider;
    private final IndexFieldCache indexFieldCache;
    private final WordListProvider wordListProvider;

    @Inject
    public ElasticFieldStatsService(final SecurityContext securityContext,
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

    /// Gives statistics about a field, estimated from a random sample of documents or calculated exactly.
    ///
    /// The statistics reveal field values, so the user needs permission to search the index (`USE`).
    ///
    /// @param request the field, sample size, time range and, for a virtual field, its groups as edited
    /// @return the statistics, or a message saying why there are none
    /// @throws PermissionException if the user may not search the index
    public ElasticFieldStats getFieldStats(final ElasticFieldStatsRequest request) {
        final DocRef docRef = request.getIndexDocRef();
        if (docRef == null || !securityContext.hasDocumentPermission(docRef, DocumentPermission.USE)) {
            throw new PermissionException(
                    securityContext.getUserRef(),
                    LogUtil.message("You are not authorised to search {}", docRef));
        }
        // As for a search, permission to use the index is enough to read it, its cluster and its connection. Other
        // docs a virtual field's groups refer to, such as dictionaries, are still read as the user.
        final ElasticIndexDoc indexDoc = securityContext.useAsReadResult(() ->
                elasticIndexStore.readDocument(docRef));
        if (indexDoc == null) {
            throw new IllegalArgumentException("Elasticsearch index " + docRef + " was not found");
        }

        final ElasticSearchConfig config = elasticSearchConfigProvider.get();
        final int maxSampleSize = Math.max(1, config.getFieldStatsMaxSampleSize());
        final int sampleSize = Math.clamp(
                Objects.requireNonNullElse(request.getSampleSize(), config.getFieldStatsSampleSize()),
                1,
                maxSampleSize);
        final String fieldName = request.getFieldName();
        final List<ElasticVirtualFieldGroup> virtualGroups = getVirtualGroups(indexDoc, request);
        final ElasticIndexField field = NullSafe.list(indexDoc.getFields())
                .stream()
                .filter(candidate -> candidate.getFldName().equals(fieldName))
                .findFirst()
                .orElse(null);

        final String message;
        if (field == null && virtualGroups == null) {
            message = "Field '" + fieldName + "' was not found in any source index";
        } else if (field != null && field.hasTypeConflict()) {
            message = "Field '" + fieldName + "' has conflicting types across the source indices";
        } else if (field == null && virtualGroups.isEmpty()) {
            message = "Virtual field '" + fieldName + "' has no groups";
        } else {
            message = null;
        }
        if (message != null) {
            return emptyStats(fieldName, sampleSize, maxSampleSize, message);
        }

        final Map<String, Query> groupQueries = field == null
                ? buildGroupQueries(indexDoc, fieldName, virtualGroups)
                : null;
        return securityContext.useAsReadResult(() -> {
            final ElasticClusterDoc cluster = elasticClusterStore.readDocument(indexDoc.getClusterRef());
            if (cluster == null) {
                throw new IllegalArgumentException("Elasticsearch cluster for " + indexDoc.getName() +
                                                   " was not found");
            }
            return elasticClientCache.contextResult(cluster.getConnection(), elasticClient -> {
                final Sampling sampling = createSampling(elasticClient, indexDoc, request, sampleSize);
                if (sampling.totalDocs() == 0) {
                    return emptyStats(fieldName, sampleSize, maxSampleSize,
                            getNoDocumentsMessage(elasticClient, indexDoc, request));
                }
                return field == null
                        ? getVirtualFieldStats(elasticClient, indexDoc, request, groupQueries, sampling, maxSampleSize)
                        : getMappedFieldStats(elasticClient, indexDoc, request, field, sampling, maxSampleSize);
            });
        });
    }

    /// Builds each group's query as a search would.
    ///
    /// @return the queries keyed by group name, in group order
    private Map<String, Query> buildGroupQueries(final ElasticIndexDoc indexDoc,
                                                 final String fieldName,
                                                 final List<ElasticVirtualFieldGroup> groups) {
        final SearchExpressionQueryBuilder queryBuilder = new SearchExpressionQueryBuilder(
                aiServiceProvider,
                indexDoc,
                indexFieldCache,
                wordListProvider,
                DateTimeSettings.builder().build());
        final Map<String, Query> groupQueries = new LinkedHashMap<>();
        for (final ElasticVirtualFieldGroup group : groups) {
            groupQueries.put(group.getName(), queryBuilder.buildVirtualGroupQuery(fieldName, group, List.of()));
        }
        return groupQueries;
    }

    /// @return the groups of the requested field if it is a virtual field, preferring the groups as edited in the
    /// request; `null` if the field is a mapped field or unknown
    private static List<ElasticVirtualFieldGroup> getVirtualGroups(final ElasticIndexDoc indexDoc,
                                                                   final ElasticFieldStatsRequest request) {
        final boolean isMapped = NullSafe.list(indexDoc.getFields())
                .stream()
                .anyMatch(field -> field.getFldName().equals(request.getFieldName()));
        if (isMapped) {
            return null;
        }
        if (request.getVirtualGroups() != null) {
            checkGroupNames(request.getVirtualGroups());
            return request.getVirtualGroups();
        }
        final ElasticIndexFieldSettings settings = indexDoc.findFieldSettings(request.getFieldName());
        return settings != null && settings.isVirtual()
                ? settings.getVirtualGroups()
                : null;
    }

    private static void checkGroupNames(final List<ElasticVirtualFieldGroup> groups) {
        final Set<String> names = new HashSet<>();
        for (final ElasticVirtualFieldGroup group : groups) {
            if (NullSafe.isBlankString(group.getName())) {
                throw new IllegalArgumentException("A group of the virtual field has no name");
            }
            if (!names.add(group.getName())) {
                throw new IllegalArgumentException("The virtual field has more than one group named '" +
                                                   group.getName() + "'");
            }
        }
    }

    private ElasticFieldStats getMappedFieldStats(final ElasticsearchClient elasticClient,
                                                  final ElasticIndexDoc indexDoc,
                                                  final ElasticFieldStatsRequest request,
                                                  final ElasticIndexField field,
                                                  final Sampling sampling,
                                                  final int maxSampleSize) {
        final String fieldName = field.getFldName();
        final FieldType type = field.getFldType();
        final boolean numeric = NUMERIC_TYPES.contains(type) && !Kind.UnsignedLong.jsonValue().equals(
                field.getNativeType());
        final boolean date = FieldType.DATE.equals(type);
        final boolean terms = TERMS_TYPES.contains(type) && !Kind.Wildcard.jsonValue().equals(field.getNativeType());

        final boolean topValuesOnly = Boolean.TRUE.equals(request.getTopValuesOnly());
        final int topValuesCount = getTopValuesCount(request);
        final String topValuesFilter = NullSafe.trim(request.getTopValuesFilter());
        // Elasticsearch can filter the values of a keyword field itself; other fields' values are filtered here
        final boolean filterInElasticsearch = !topValuesFilter.isEmpty()
                                              && Kind.Keyword.jsonValue().equals(field.getNativeType());

        final Map<String, Aggregation> subAggs = new HashMap<>();
        if (terms) {
            subAggs.put(TOP_AGG, Aggregation.of(a -> a.terms(t -> {
                t.field(fieldName).size(topValuesCount);
                if (filterInElasticsearch) {
                    t.include(i -> i.regexp(toCaseInsensitiveContainsRegex(topValuesFilter)));
                }
                return t;
            })));
        }
        if (topValuesOnly) {
            final Sample sample = search(elasticClient, indexDoc, sampling, subAggs);
            return topValuesStats(fieldName, sampling, maxSampleSize, sample, sample.aggregations().containsKey(TOP_AGG)
                    ? filterValues(getTermsBuckets(sample.aggregations().get(TOP_AGG)), topValuesFilter)
                    : List.of());
        }
        subAggs.put(HAS_VALUE_AGG, Aggregation.of(a -> a.filter(f -> f.exists(e -> e.field(fieldName)))));
        if (terms || numeric || date) {
            subAggs.put(DISTINCT_AGG, Aggregation.of(a -> a.cardinality(c -> c.field(fieldName))));
        }
        if (numeric || date) {
            subAggs.put(STATS_AGG, Aggregation.of(a -> a.stats(st -> st.field(fieldName))));
        }
        if (numeric) {
            subAggs.put(MEDIAN_AGG, Aggregation.of(a -> a.percentiles(p -> p
                    .field(fieldName)
                    .percents(50.0)
                    .keyed(false))));
        }
        if (date) {
            subAggs.put(HISTOGRAM_AGG, Aggregation.of(a -> a.autoDateHistogram(h -> h
                    .field(fieldName)
                    .buckets(HISTOGRAM_BUCKETS))));
        }

        final Sample sample = search(elasticClient, indexDoc, sampling, subAggs);
        final Map<String, Aggregate> aggs = sample.aggregations();
        final StatsAggregate stats = aggs.containsKey(STATS_AGG)
                ? aggs.get(STATS_AGG).stats()
                : null;

        List<ElasticFieldValueCount> histogram = null;
        if (date) {
            final List<String> starts = new ArrayList<>();
            final List<Long> counts = new ArrayList<>();
            aggs.get(HISTOGRAM_AGG).autoDateHistogram().buckets().array().forEach(bucket -> {
                starts.add(Objects.requireNonNullElse(bucket.keyAsString(), String.valueOf(bucket.key())));
                counts.add(bucket.docCount());
            });
            histogram = toRanges(starts, counts, stats == null
                    ? null
                    : getStatValue(stats.maxAsString(), stats.max()));
        } else if (numeric && stats != null && stats.count() > 0) {
            final boolean wholeNumbers = WHOLE_NUMBER_TYPES.contains(type);
            histogram = getNumericHistogram(elasticClient, indexDoc, sampling, fieldName, stats, wholeNumbers);
        }

        return new ElasticFieldStats(
                fieldName,
                sampling.isExact(),
                sampling.sampleSize(),
                maxSampleSize,
                sampling.totalDocs(),
                sample.docCount(),
                aggs.get(HAS_VALUE_AGG).filter().docCount(),
                aggs.containsKey(DISTINCT_AGG)
                        ? aggs.get(DISTINCT_AGG).cardinality().value()
                        : null,
                aggs.containsKey(TOP_AGG)
                        ? filterValues(getTermsBuckets(aggs.get(TOP_AGG)), topValuesFilter)
                        : null,
                stats == null || stats.count() == 0
                        ? null
                        : getStatValue(stats.minAsString(), stats.min()),
                stats == null || stats.count() == 0
                        ? null
                        : getStatValue(stats.maxAsString(), stats.max()),
                numeric && stats != null
                        ? stats.avg()
                        : null,
                aggs.containsKey(MEDIAN_AGG)
                        ? getMedian(aggs.get(MEDIAN_AGG))
                        : null,
                histogram,
                null);
    }

    /// Gets the distribution of a numeric field over [#HISTOGRAM_BUCKETS] even ranges between its smallest and
    /// largest values, which a second request needs, as the range width depends on them.
    ///
    /// For whole number fields the ranges are whole numbers wide, so they start and end on whole numbers. Other
    /// ranges are shown to as many decimal places as their width needs.
    private List<ElasticFieldValueCount> getNumericHistogram(final ElasticsearchClient elasticClient,
                                                             final ElasticIndexDoc indexDoc,
                                                             final Sampling sampling,
                                                             final String fieldName,
                                                             final StatsAggregate stats,
                                                             final boolean wholeNumbers) {
        final double min = stats.min();
        final double max = stats.max();
        final double interval = getHistogramInterval(min, max, wholeNumbers);
        final int decimalPlaces = wholeNumbers
                ? 0
                : getDecimalPlaces(interval);
        final Sample sample = search(elasticClient, indexDoc, sampling, Map.of(
                HISTOGRAM_AGG, Aggregation.of(a -> a.histogram(h -> h
                        .field(fieldName)
                        .interval(interval)
                        .offset(min)
                        .minDocCount(0)))));
        final List<String> starts = new ArrayList<>();
        final List<Long> counts = new ArrayList<>();
        sample.aggregations().get(HISTOGRAM_AGG).histogram().buckets().array().forEach(bucket -> {
            starts.add(formatNumber(bucket.key(), decimalPlaces));
            counts.add(bucket.docCount());
        });
        return toRanges(starts, counts, formatNumber(max, decimalPlaces));
    }

    /// @param min          the smallest value
    /// @param max          the largest value
    /// @param wholeNumbers `true` if the values are whole numbers
    /// @return the width of each of [#HISTOGRAM_BUCKETS] ranges covering the values; a whole number of at least 1
    /// for whole numbers
    static double getHistogramInterval(final double min, final double max, final boolean wholeNumbers) {
        if (max <= min) {
            return 1;
        }
        final double interval = (max - min) / HISTOGRAM_BUCKETS;
        return wholeNumbers
                ? Math.max(1, Math.ceil(interval))
                : interval;
    }

    /// @param interval the width of each range
    /// @return enough decimal places to tell apart the ends of ranges this wide: two significant figures of the
    /// width, and none for widths of 10 or more
    static int getDecimalPlaces(final double interval) {
        if (interval <= 0 || Double.isInfinite(interval) || Double.isNaN(interval)) {
            return 0;
        }
        final int places = 1 - (int) Math.floor(Math.log10(interval));
        return Math.clamp(places, 0, MAX_DECIMAL_PLACES);
    }

    /// Labels each bucket with the range of values it holds, from its start to the next bucket's start, and the
    /// last to the largest value. A bucket starting at the largest value (as the range of the last full bucket
    /// ends there) is counted in the bucket before it, so there are at most [#HISTOGRAM_BUCKETS].
    ///
    /// @param starts the start of each bucket, in order
    /// @param counts the number of documents in each bucket
    /// @param max    the largest value, or `null` if not known
    /// @return the buckets labelled with their ranges
    static List<ElasticFieldValueCount> toRanges(final List<String> starts,
                                                 final List<Long> counts,
                                                 final String max) {
        final List<String> bucketStarts = new ArrayList<>(starts);
        final List<Long> bucketCounts = new ArrayList<>(counts);
        while (bucketStarts.size() > HISTOGRAM_BUCKETS) {
            final int last = bucketStarts.size() - 1;
            bucketCounts.set(last - 1, bucketCounts.get(last - 1) + bucketCounts.get(last));
            bucketStarts.remove(last);
            bucketCounts.remove(last);
        }
        final List<ElasticFieldValueCount> ranges = new ArrayList<>();
        for (int i = 0; i < bucketStarts.size(); i++) {
            final String end = i + 1 < bucketStarts.size()
                    ? bucketStarts.get(i + 1)
                    : max;
            final String label = end == null || end.equals(bucketStarts.get(i))
                    ? bucketStarts.get(i)
                    : bucketStarts.get(i) + RANGE_SEPARATOR + end;
            ranges.add(new ElasticFieldValueCount(label, bucketCounts.get(i)));
        }
        return ranges;
    }

    private ElasticFieldStats getVirtualFieldStats(final ElasticsearchClient elasticClient,
                                                   final ElasticIndexDoc indexDoc,
                                                   final ElasticFieldStatsRequest request,
                                                   final Map<String, Query> groupQueries,
                                                   final Sampling sampling,
                                                   final int maxSampleSize) {
        final boolean topValuesOnly = Boolean.TRUE.equals(request.getTopValuesOnly());
        final String topValuesFilter = NullSafe.trim(request.getTopValuesFilter());
        final Query anyGroup = QueryBuilders.bool(b -> b.should(new ArrayList<>(groupQueries.values())));
        // Only the groups whose names match the filter are counted
        final Map<String, Query> filteredGroupQueries = new LinkedHashMap<>();
        groupQueries.forEach((groupName, query) -> {
            if (containsIgnoreCase(groupName, topValuesFilter)) {
                filteredGroupQueries.put(groupName, query);
            }
        });

        final Map<String, Aggregation> subAggs = new HashMap<>();
        if (!filteredGroupQueries.isEmpty()) {
            subAggs.put(GROUPS_AGG, Aggregation.of(a -> a.filters(f -> f.filters(b -> b.keyed(filteredGroupQueries)))));
        }
        if (!topValuesOnly) {
            subAggs.put(HAS_VALUE_AGG, Aggregation.of(a -> a.filter(anyGroup)));
        }
        final Sample sample = search(elasticClient, indexDoc, sampling, subAggs);
        final Map<String, Aggregate> aggs = sample.aggregations();

        final List<ElasticFieldValueCount> groupCounts = new ArrayList<>();
        if (aggs.containsKey(GROUPS_AGG)) {
            aggs.get(GROUPS_AGG).filters().buckets().keyed().forEach((groupName, bucket) ->
                    groupCounts.add(new ElasticFieldValueCount(groupName, bucket.docCount())));
        }
        groupCounts.sort((a, b) -> Long.compare(b.getCount(), a.getCount()));
        final List<ElasticFieldValueCount> topGroupCounts = groupCounts.subList(
                0, Math.min(groupCounts.size(), getTopValuesCount(request)));
        if (topValuesOnly) {
            return topValuesStats(request.getFieldName(), sampling, maxSampleSize, sample, topGroupCounts);
        }

        return new ElasticFieldStats(
                request.getFieldName(),
                sampling.isExact(),
                sampling.sampleSize(),
                maxSampleSize,
                sampling.totalDocs(),
                sample.docCount(),
                aggs.get(HAS_VALUE_AGG).filter().docCount(),
                null,
                new ArrayList<>(topGroupCounts),
                null,
                null,
                null,
                null,
                null,
                null);
    }

    /// Counts the documents in the requested time range and works out how to sample them. A random sample can only
    /// take up to half of the documents, so a larger sample is taken as all of them.
    private Sampling createSampling(final ElasticsearchClient elasticClient,
                                    final ElasticIndexDoc indexDoc,
                                    final ElasticFieldStatsRequest request,
                                    final int sampleSize) {
        final Query query = getTimeRangeQuery(indexDoc, request);
        try {
            final long totalDocs = elasticClient.count(c -> c
                    .index(indexDoc.getIndexName())
                    .query(query)).count();
            final Double probability = getSampleProbability(
                    Boolean.TRUE.equals(request.getExact()), sampleSize, totalDocs);
            return new Sampling(query, totalDocs, sampleSize, probability);
        } catch (final IOException e) {
            LOGGER.debug(e::getMessage, e);
            throw new UncheckedIOException(e);
        }
    }

    /// Explains that no documents matched, and when the time range is to blame, says what dates the index's
    /// documents do have, so the user can choose a range that holds some.
    private String getNoDocumentsMessage(final ElasticsearchClient elasticClient,
                                         final ElasticIndexDoc indexDoc,
                                         final ElasticFieldStatsRequest request) {
        final String timeField = indexDoc.getTimeField();
        if ((request.getFromMs() == null && request.getToMs() == null) || NullSafe.isBlankString(timeField)) {
            return "No documents were found in the index";
        }
        try {
            final SearchResponse<Void> response = elasticClient.search(s -> s
                            .index(indexDoc.getIndexName())
                            .size(0)
                            .aggregations(STATS_AGG, a -> a.stats(st -> st.field(timeField))),
                    Void.class);
            final StatsAggregate dates = response.aggregations().get(STATS_AGG).stats();
            if (dates.count() == 0) {
                return "No documents were found in the index";
            }
            return "No documents were found in the time range. The index's documents are dated from " +
                   getStatValue(dates.minAsString(), dates.min()) + " to " +
                   getStatValue(dates.maxAsString(), dates.max()) + ".";
        } catch (final IOException | RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
            return "No documents were found in the time range";
        }
    }

    /// @param exact      `true` if every document is to be included
    /// @param sampleSize the approximate number of documents to sample
    /// @param totalDocs  the number of documents to sample from
    /// @return the probability of including each document in a random sample, or `null` to include them all
    static Double getSampleProbability(final boolean exact, final int sampleSize, final long totalDocs) {
        if (exact || totalDocs <= 0) {
            return null;
        }
        final double probability = (double) sampleSize / totalDocs;
        return probability > MAX_SAMPLE_PROBABILITY
                ? null
                : probability;
    }

    /// Runs aggregations over the sampled documents: within a `random_sampler` aggregation for a random sample, or
    /// directly over every document. The counts of aggregations within a `random_sampler` are scaled up by
    /// Elasticsearch to estimate the counts for every document.
    private Sample search(final ElasticsearchClient elasticClient,
                          final ElasticIndexDoc indexDoc,
                          final Sampling sampling,
                          final Map<String, Aggregation> aggregations) {
        try {
            if (sampling.isExact()) {
                final SearchResponse<Void> response = elasticClient.search(s -> s
                                .index(indexDoc.getIndexName())
                                .size(0)
                                .query(sampling.query())
                                .aggregations(aggregations),
                        Void.class);
                return new Sample(sampling.totalDocs(), response.aggregations());
            }
            final SearchResponse<Void> response = elasticClient.search(s -> s
                            .index(indexDoc.getIndexName())
                            .size(0)
                            .query(sampling.query())
                            .aggregations(SAMPLE_AGG, a -> a
                                    .randomSampler(r -> r.probability(sampling.probability()))
                                    .aggregations(aggregations)),
                    Void.class);
            final SamplerAggregate sample = readRandomSampler(
                    response.aggregations().get(SAMPLE_AGG), elasticClient._jsonpMapper());
            return new Sample(sample.docCount(), sample.aggregations());
        } catch (final IOException e) {
            LOGGER.debug(e::getMessage, e);
            throw new UncheckedIOException(e);
        }
    }

    /// The Java client doesn't know the `random_sampler` aggregate, so returns it as raw JSON. Other than its seed
    /// and probability, it has the same form as a `sampler` aggregate, so is read as one.
    static SamplerAggregate readRandomSampler(final Aggregate aggregate, final JsonpMapper mapper) {
        if (aggregate.isSampler()) {
            return aggregate.sampler();
        }
        final JsonNode json = OBJECT_MAPPER.readTree(aggregate._custom().toJson(mapper).toString());
        if (!(json instanceof final ObjectNode objectNode)) {
            throw new IllegalStateException("Unexpected random_sampler aggregate: " + json);
        }
        objectNode.remove("seed");
        objectNode.remove("probability");
        try (final JsonParser parser = mapper.jsonProvider().createParser(new StringReader(objectNode.toString()))) {
            return SamplerAggregate._DESERIALIZER.deserialize(parser, mapper);
        }
    }

    /// @return a query limiting documents to the requested range of the doc's time field, or matching all
    /// documents if no range is requested or the doc has no time field
    static Query getTimeRangeQuery(final ElasticIndexDoc indexDoc, final ElasticFieldStatsRequest request) {
        final String timeField = indexDoc.getTimeField();
        if (NullSafe.isBlankString(timeField) || (request.getFromMs() == null && request.getToMs() == null)) {
            return MatchAllQuery.of(q -> q)._toQuery();
        }
        return QueryBuilders.range(r -> r.untyped(u -> {
            u.field(timeField).format("epoch_millis");
            if (request.getFromMs() != null) {
                u.gte(JsonData.of(request.getFromMs()));
            }
            if (request.getToMs() != null) {
                u.lt(JsonData.of(request.getToMs()));
            }
            return u;
        }));
    }

    private static List<ElasticFieldValueCount> getTermsBuckets(final Aggregate aggregate) {
        final List<ElasticFieldValueCount> values = new ArrayList<>();
        if (aggregate.isSterms()) {
            aggregate.sterms().buckets().array().forEach(bucket ->
                    values.add(new ElasticFieldValueCount(bucket.key().stringValue(), bucket.docCount())));
        } else if (aggregate.isLterms()) {
            aggregate.lterms().buckets().array().forEach(bucket ->
                    values.add(new ElasticFieldValueCount(
                            Objects.requireNonNullElse(bucket.keyAsString(), String.valueOf(bucket.key())),
                            bucket.docCount())));
        } else if (aggregate.isDterms()) {
            aggregate.dterms().buckets().array().forEach(bucket ->
                    values.add(new ElasticFieldValueCount(
                            Objects.requireNonNullElse(bucket.keyAsString(), formatNumber(bucket.key())),
                            bucket.docCount())));
        }
        return values;
    }

    private static Double getMedian(final Aggregate aggregate) {
        final List<ArrayPercentilesItem> items = aggregate.tdigestPercentiles().values().array();
        return items.isEmpty()
                ? null
                : items.getFirst().value();
    }

    private static String getStatValue(final String asString, final Double value) {
        if (asString != null) {
            return asString;
        }
        return value == null
                ? null
                : formatNumber(value);
    }

    /// @param value         the number
    /// @param decimalPlaces the number of decimal places to round to
    /// @return the number rounded, without trailing zeros after the decimal point
    static String formatNumber(final double value, final int decimalPlaces) {
        if (Double.isInfinite(value) || Double.isNaN(value)) {
            return String.valueOf(value);
        }
        return BigDecimal.valueOf(value)
                .setScale(decimalPlaces, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    /// @return the number without a trailing `.0` when it is whole
    static String formatNumber(final double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value) && Math.abs(value) < 1e15) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    private static int getTopValuesCount(final ElasticFieldStatsRequest request) {
        return Math.clamp(
                Objects.requireNonNullElse(request.getTopValuesCount(), DEFAULT_TOP_VALUES_COUNT),
                1,
                MAX_TOP_VALUES_COUNT);
    }

    /// Builds a Lucene regular expression, as a `terms` aggregation's `include` takes, matching any value that
    /// contains the text, ignoring case. Lucene regular expressions have no flag to ignore case, so each letter
    /// matches either case.
    ///
    /// @param text the text values must contain
    /// @return the regular expression
    static String toCaseInsensitiveContainsRegex(final String text) {
        final StringBuilder sb = new StringBuilder(".*");
        text.codePoints().forEach(codePoint -> {
            final int lower = Character.toLowerCase(codePoint);
            final int upper = Character.toUpperCase(codePoint);
            if (lower != upper) {
                sb.append('[').appendCodePoint(lower).appendCodePoint(upper).append(']');
            } else {
                if (LUCENE_REGEX_SPECIAL_CHARS.indexOf(codePoint) >= 0) {
                    sb.append('\\');
                }
                sb.appendCodePoint(codePoint);
            }
        });
        return sb.append(".*").toString();
    }

    private static List<ElasticFieldValueCount> filterValues(final List<ElasticFieldValueCount> values,
                                                             final String filter) {
        return values.stream()
                .filter(value -> containsIgnoreCase(value.getValue(), filter))
                .toList();
    }

    private static boolean containsIgnoreCase(final String value, final String text) {
        return text.isEmpty()
               || (value != null && value.toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT)));
    }

    private static ElasticFieldStats topValuesStats(final String fieldName,
                                                    final Sampling sampling,
                                                    final int maxSampleSize,
                                                    final Sample sample,
                                                    final List<ElasticFieldValueCount> topValues) {
        return new ElasticFieldStats(fieldName, sampling.isExact(), sampling.sampleSize(), maxSampleSize,
                sampling.totalDocs(), sample.docCount(), null, null, new ArrayList<>(topValues), null, null, null,
                null, null, null);
    }

    private static ElasticFieldStats emptyStats(final String fieldName,
                                                final int sampleSize,
                                                final int maxSampleSize,
                                                final String message) {
        return new ElasticFieldStats(fieldName, null, sampleSize, maxSampleSize, null, null, null, null, null, null,
                null, null, null, null, message);
    }

    /// How the documents in the requested time range are sampled.
    ///
    /// @param query       the query selecting the documents in the time range
    /// @param totalDocs   the number of documents in the time range
    /// @param sampleSize  the approximate number of documents asked to be sampled
    /// @param probability the probability of including each document in a random sample, or `null` to include
    ///                    every document
    private record Sampling(Query query, long totalDocs, int sampleSize, Double probability) {

        boolean isExact() {
            return probability == null;
        }
    }

    /// The results of aggregations over sampled documents.
    ///
    /// @param docCount     the number of documents the aggregations ran over
    /// @param aggregations the aggregation results, keyed by name
    private record Sample(long docCount, Map<String, Aggregate> aggregations) {

    }
}
