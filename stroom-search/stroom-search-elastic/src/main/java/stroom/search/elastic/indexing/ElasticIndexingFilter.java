/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.search.elastic.indexing;

import stroom.docref.DocRef;
import stroom.meta.shared.Meta;
import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.LoggedException;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.factory.PipelineProperty;
import stroom.pipeline.factory.PipelinePropertyDocRef;
import stroom.pipeline.filter.AbstractXMLFilter;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.pipeline.state.MetaHolder;
import stroom.pipeline.state.StreamProcessorHolder;
import stroom.pipeline.xml.converter.json.JSONParser;
import stroom.processor.shared.ProcessorFilter;
import stroom.search.elastic.ElasticClientCache;
import stroom.search.elastic.ElasticClusterStore;
import stroom.search.elastic.ElasticConfig;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticConnectionConfig;
import stroom.search.elastic.shared.ElasticIndexConstants;
import stroom.svg.shared.SvgImage;
import stroom.util.CharBuffer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Severity;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.aggregations.AggregationBuilders;
import co.elastic.clients.elasticsearch._types.aggregations.CompositeAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.CompositeAggregation;
import co.elastic.clients.elasticsearch._types.aggregations.CompositeAggregationSource;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkRequest.Builder;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.transport.rest5_client.low_level.ResponseException;
import co.elastic.clients.util.BinaryData;
import co.elastic.clients.util.ContentType;
import co.elastic.clients.util.NamedValue;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.NotFoundException;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Accepts `json` schema XML and sends documents to Elasticsearch as batches for indexing
 */
@ConfigurableElement(
        type = "ElasticIndexingFilter",
        category = Category.FILTER,
        description = """
                A filter consuming XML events in the `records:2` namespace to index/store the fields
                and their values in an Elasticsearch Index.
                """,
        roles = {
                PipelineElementType.ROLE_TARGET,
                PipelineElementType.ROLE_HAS_TARGETS,
                PipelineElementType.VISABILITY_SIMPLE},
        icon = SvgImage.PIPELINE_ELASTIC_INDEX)
class ElasticIndexingFilter extends AbstractXMLFilter {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ElasticIndexingFilter.class);
    private static final JsonFactory JSON_FACTORY = new JsonFactory();
    private static final int INITIAL_JSON_STREAM_SIZE_BYTES = 1024;
    private static final int ES_COMPOSITE_BATCH_SIZE = 100; // Number of index names to retrieve per request
    private static final int ES_MAX_EXCEPTION_CHARS = 4096;
    private static final int ES_TOO_MANY_REQUESTS_STATUS = 429;
    private static final Pattern INDEX_NAME_VALUE_PATTERN = Pattern.compile("(\\{[^}]+?})");
    private static final Pattern INDEX_BASE_NAME_PATTERN = Pattern.compile("^([^{]+)");

    // Dependencies
    private final LocationFactoryProxy locationFactory;
    private final ErrorReceiverProxy errorReceiverProxy;
    private final Provider<ElasticConfig> elasticConfigProvider;
    private final ElasticClientCache elasticClientCache;
    private final ElasticClusterStore elasticClusterStore;
    private final PipelineStore pipelineStore;
    private final StreamProcessorHolder streamProcessorHolder;
    private final MetaHolder metaHolder;

    // Pipeline filter configuration options
    private int batchSize = 10000;
    private boolean purgeOnReprocess = true;
    private String ingestPipelineName = null;
    private boolean refreshAfterEachBatch = false;
    private DocRef clusterRef;
    private String indexName;

    // Cached entities
    ElasticClusterDoc elasticCluster;
    String pipelineName;

    // State
    private final List<BulkOperation> bulkOperations;
    private final ByteArrayOutputStream currentDocument;
    private final CharBuffer content = new CharBuffer();
    private Set<String> indexNameVariables = new HashSet<>();
    private final Map<String, String> currentDocIndexNameVariables = new HashMap<>();
    private String currentDocFieldName = null;
    private int currentDocPropertyCount = 0;
    private boolean inOuterArray = false;
    private int currentDepth = 0;
    private JsonGenerator jsonGenerator;
    private int currentRetry;

    private Locator locator;

    @Inject
    ElasticIndexingFilter(
            final LocationFactoryProxy locationFactory,
            final ErrorReceiverProxy errorReceiverProxy,
            final Provider<ElasticConfig> elasticConfigProvider,
            final ElasticClientCache elasticClientCache,
            final ElasticClusterStore elasticClusterStore,
            final PipelineStore pipelineStore,
            final StreamProcessorHolder streamProcessorHolder,
            final MetaHolder metaHolder) {
        this.locationFactory = locationFactory;
        this.errorReceiverProxy = errorReceiverProxy;
        this.elasticConfigProvider = elasticConfigProvider;
        this.elasticClientCache = elasticClientCache;
        this.elasticClusterStore = elasticClusterStore;
        this.pipelineStore = pipelineStore;
        this.streamProcessorHolder = streamProcessorHolder;
        this.metaHolder = metaHolder;

        bulkOperations = new ArrayList<>();
        currentDocument = new ByteArrayOutputStream(INITIAL_JSON_STREAM_SIZE_BYTES);
        currentRetry = 0;
    }

    /**
     * Initialise
     */
    @Override
    public void startProcessing() {
        try {
            if (clusterRef == null) {
                fatalError("Elasticsearch cluster ref has not been set", new NotFoundException());
            }

            if (indexName == null || indexName.isEmpty()) {
                fatalError("Index name has not been set", new InvalidParameterException());
            }

            pipelineName = pipelineStore.readDocument(streamProcessorHolder.getStreamProcessor().getPipeline())
                    .getName();
            elasticCluster = elasticClusterStore.readDocument(clusterRef);
            final ElasticConnectionConfig connectionConfig = elasticCluster.getConnection();

            elasticClientCache.context(connectionConfig, elasticClient -> {
                try {
                    // If this is a reprocessing filter, delete any documents from the target index that have the same
                    // `StreamId` as the stream that's being reprocessed. This prevents duplicate documents.
                    final ProcessorFilter processorFilter = this.streamProcessorHolder.getStreamTask()
                            .getProcessorFilter();
                    if (purgeOnReprocess && processorFilter.isReprocess()) {
                        final Meta meta = this.metaHolder.getMeta();
                        final long streamId = meta.getId();
                        if (!purgeDocumentsForStream(elasticClient, streamId)) {
                            throw new RuntimeException("Failed to purge existing documents for StreamId " + streamId);
                        }
                    }
                } catch (final RuntimeException e) {
                    fatalError(e.getMessage(), e);
                }
            });

            jsonGenerator = JSON_FACTORY.createGenerator(currentDocument);

            populateIndexNameVariableNames();

        } catch (final IOException e) {
            fatalError("Failed to initialise JsonGenerator", e);
        } finally {
            super.startProcessing();
        }
    }

    private void populateIndexNameVariableNames() {
        indexNameVariables = INDEX_NAME_VALUE_PATTERN.matcher(indexName).results()
                .map(matchResult -> {
                    final String fieldNameMatch = matchResult.group();
                    return fieldNameMatch.substring(1, fieldNameMatch.length() - 1);
                })
                .collect(Collectors.toSet());
    }

    @Override
    public void endProcessing() {
        try {
            // Send any remaining documents
            indexDocuments();
        } finally {
            super.endProcessing();
        }
    }

    /**
     * Sets the locator to use when reporting errors.
     *
     * @param locator The locator to use.
     */
    @Override
    public void setDocumentLocator(final Locator locator) {
        this.locator = locator;
        super.setDocumentLocator(locator);
    }

    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes attributes)
            throws SAXException {

        if (!inOuterArray && currentDepth == 0) {
            // We are now inside the outer `array` element
            if (localName.equals(JSONParser.XML_ELEMENT_ARRAY)) {
                // Starting a new root JSON document array. Any `map` elements from here will be added as documents
                // for indexing
                inOuterArray = true;
            } else {
                // Terminate processing as this is a fatal error
                fatalError("Expected an array at start of document, got '" + localName + "' instead",
                        new IllegalArgumentException());
            }
        } else {
            // Get the name of the JSON property
            currentDocFieldName = attributes.getValue(JSONParser.XML_ATTRIBUTE_KEY);

            switch (localName) {
                case JSONParser.XML_ELEMENT_MAP:
                    try {
                        incrementDepth();
                        writeFieldName();
                        jsonGenerator.writeStartObject();
                        if (currentDepth == 1) {
                            enterDocumentRoot();
                        }
                    } catch (final IOException e) {
                        fatalError("Invalid start of object", e);
                    }
                    break;
                case JSONParser.XML_ELEMENT_ARRAY:
                    try {
                        incrementDepth();
                        writeFieldName();
                        jsonGenerator.writeStartArray();
                    } catch (final IOException e) {
                        fatalError("Invalid start of array", e);
                    }
                    break;
            }
        }

        // Starting a new value, so clear the existing one
        content.clear();
        super.startElement(uri, localName, qName, attributes);
    }

    private void enterDocumentRoot() {
        currentDocIndexNameVariables.clear();
    }

    private void incrementDepth() {
        final int maxDepth = elasticConfigProvider.get().getIndexingConfig().getMaxNestedElementDepth();

        currentDepth++;

        if (currentDepth > maxDepth) {
            fatalError("Maximum nested element depth of " + maxDepth + " exceeded", new RuntimeException());
        }
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        final String value;

        if (inOuterArray && currentDepth == 0) {
            // We are at the end of the outer `array` element
            if (localName.equals(JSONParser.XML_ELEMENT_ARRAY)) {
                // Consider this to be the end of the stream part
                inOuterArray = false;
            } else {
                fatalError("Expected an end array to close the document, got '" + localName + "' instead",
                        new IllegalArgumentException());
            }
        } else {
            switch (localName) {
                case JSONParser.XML_ELEMENT_MAP:
                    try {
                        currentDepth--;
                        jsonGenerator.writeEndObject();
                        if (currentDepth == 0) {
                            // We have closed out an outer `map`, so queue the document for indexing
                            processDocument();
                        }
                    } catch (final IOException e) {
                        fatalError("Invalid end of object", e);
                    }
                    break;
                case JSONParser.XML_ELEMENT_ARRAY:
                    try {
                        currentDepth--;
                        jsonGenerator.writeEndArray();
                    } catch (final IOException e) {
                        fatalError("Invalid end of array", e);
                    }
                    break;
                case JSONParser.XML_ELEMENT_STRING:
                    value = content.toString();
                    try {
                        if (!value.isEmpty()) {
                            if (includeField(currentDocFieldName)) {
                                writeFieldName();
                                jsonGenerator.writeString(value);
                                currentDocPropertyCount++;
                            }
                            storeIndexNameVariableValue(value);
                        }
                    } catch (final IOException e) {
                        fatalError("Invalid string value '" + value + "' for property '" +
                                   currentDocFieldName + "'", e);
                    }
                    break;
                case JSONParser.XML_ELEMENT_BOOLEAN:
                    value = content.toString();
                    try {
                        if (!value.isEmpty()) {
                            if (includeField(currentDocFieldName)) {
                                writeFieldName();
                                jsonGenerator.writeBoolean(Boolean.parseBoolean(value));
                                currentDocPropertyCount++;
                            }
                            storeIndexNameVariableValue(value);
                        }
                    } catch (final IOException e) {
                        fatalError("Invalid boolean value '" + value + "' for property '" +
                                   currentDocFieldName + "'", e);
                    }
                    break;
                case JSONParser.XML_ELEMENT_NULL:
                    try {
                        if (includeField(currentDocFieldName)) {
                            writeFieldName();
                            jsonGenerator.writeNull();
                            currentDocPropertyCount++;
                        }
                        storeIndexNameVariableValue("");
                    } catch (final IOException e) {
                        fatalError("Invalid null value for property '" + currentDocFieldName + "'", e);
                    }
                    break;
                case JSONParser.XML_ELEMENT_NUMBER:
                    value = content.toString();
                    try {
                        if (!value.isEmpty()) {
                            if (includeField(currentDocFieldName)) {
                                writeFieldName();
                                jsonGenerator.writeNumber(value);
                                currentDocPropertyCount++;
                            }
                            storeIndexNameVariableValue(value);
                        }
                    } catch (final IOException e) {
                        fatalError("Invalid number value '" + value + "' for property '" +
                                   currentDocFieldName + "'", e);
                    }
                    break;
            }
        }

        currentDocFieldName = null;
        content.clear();
        super.endElement(uri, localName, qName);
    }

    /**
     * Whether to include the field in the destination document
     */
    private boolean includeField(final String fieldName) {
        return fieldName == null || !fieldName.startsWith("_");
    }

    private void storeIndexNameVariableValue(final String value) {
        try {
            if (!value.isEmpty() && indexNameVariables.contains(currentDocFieldName)) {
                currentDocIndexNameVariables.put(currentDocFieldName, value);
            }
        } catch (final IllegalArgumentException e) {
            fatalError("Index variable '" + currentDocFieldName + "' specified more than once in document", e);
        } catch (final Exception e) {
            fatalError("Unexpected error parsing index name variable value", e);
        }
    }

    /**
     * @param ch     the characters from the XML document
     * @param start  the start position in the array
     * @param length the number of characters to read from the array
     * @throws org.xml.sax.SAXException any SAX exception, possibly wrapping another exception
     * @see #ignorableWhitespace
     * @see org.xml.sax.Locator
     * @see stroom.pipeline.filter.AbstractXMLFilter#characters(char[],
     * int, int)
     */
    @Override
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
        content.append(ch, start, length);
        super.characters(ch, start, length);
    }

    private boolean writeFieldName() throws IOException {
        if (currentDocFieldName != null) {
            jsonGenerator.writeFieldName(currentDocFieldName);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Queue a JSON document for indexing
     */
    private void processDocument() {
        try {
            if (currentDocPropertyCount > 0) {
                jsonGenerator.flush();

                bulkOperations.add(BulkOperation.of(b -> b
                        .create(createOp -> {
                            createOp.index(formatIndexName());

                            // If an ingest pipeline name is specified, execute it when ingesting the document
                            if (ingestPipelineName != null && !ingestPipelineName.isEmpty()) {
                                createOp.pipeline(ingestPipelineName);
                            }

                            createOp.document(BinaryData.of(currentDocument.toByteArray(),
                                    ContentType.APPLICATION_JSON));
                            return createOp;
                        })
                ));

                // If the maximum batch size has been reached, send the current batch for indexing
                if (bulkOperations.size() >= batchSize) {
                    indexDocuments();
                }
            }
        } catch (final IOException e) {
            fatalError("Failed to flush JSON to stream", e);
        } catch (final Exception e) {
            fatalError(e.getMessage(), e);
        } finally {
            clearDocument();
        }
    }

    private void clearDocument() {
        // Discard the document
        currentDocument.reset();

        // Reset the count of how many fields we have indexed for the current event
        currentDocPropertyCount = 0;
    }

    /**
     * Delete documents from the target index, where the indexed field `StreamId` matches one of the provided IDs
     */
    private boolean purgeDocumentsForStream(final ElasticsearchClient elasticClient, final Long streamId)
            throws LoggedException {
        final List<String> indexNames = getTargetIndexNames(elasticClient, streamId);
        if (indexNames != null && !indexNames.isEmpty()) {
            LOGGER.debug("Purging documents for StreamId {} from indices: {}", streamId, indexNames);
            try {
                // Delete against one index at a time. We don't delete against all indices, as this may cause
                // the cluster limit `search.max_open_scroll_context` to be exceeded.
                for (final String indexName : indexNames) {
                    final DeleteByQueryRequest deleteRequest = DeleteByQueryRequest.of(r -> r
                            .index(indexName)
                            .scrollSize((long) batchSize)
                            .refresh(true)
                            .query(q -> q
                                    .term(t -> t
                                            .field(ElasticIndexConstants.STREAM_ID)
                                            .value(streamId)
                                    )
                            )
                    );

                    final DeleteByQueryResponse deleteResponse = elasticClient.deleteByQuery(deleteRequest);
                    final long tookSeconds = deleteResponse.took() != null
                            ? deleteResponse.took() / 1000
                            : 0;

                    LOGGER.info("Deleted {} documents matching StreamId: {} from index: {}, took {} seconds",
                            deleteResponse.deleted(), streamId, indexName, tookSeconds);
                }
            } catch (final IOException e) {
                fatalError("Failed to purge documents for StreamId: " + streamId, e);
                return false;
            }
        }
        return true;
    }

    /**
     * Given a StreamId, retrieve a list of names of all indices containing matching documents
     */
    private List<String> getTargetIndexNames(final ElasticsearchClient elasticClient, final Long streamId)
            throws LoggedException {
        final String indicesAggregationKey = "indices";
        final String indexNameSourceKey = "index_name";

        final NamedValue<CompositeAggregationSource> compositeAggregationSource = NamedValue.of(
                indexNameSourceKey,
                CompositeAggregationSource.of(source -> source
                        .terms(t -> t.field(ElasticIndexConstants.INDEX_NAME))
                )
        );

        // Create a composite aggregation to collect all the unique indices that we need to issue delete requests
        // against
        try {
            final List<String> allIndexNames = new ArrayList<>();
            Map<String, FieldValue> afterKey = null;
            int bucketSize = -1;
            while (bucketSize != 0) {
                final TermQuery query = TermQuery.of(m -> m
                        .field(ElasticIndexConstants.STREAM_ID)
                        .value(streamId)
                );

                final CompositeAggregation.Builder compositeAggBuilder = AggregationBuilders.composite()
                        .sources(List.of(compositeAggregationSource));
                if (afterKey != null) {
                    compositeAggBuilder.after(afterKey);
                }

                final SearchResponse<Void> response = elasticClient.search(s -> s
                                .index(getIndexBaseName() + "*")
                                .size(ES_COMPOSITE_BATCH_SIZE)
                                .query(query._toQuery())
                                .aggregations(indicesAggregationKey, compositeAggBuilder.build()._toAggregation()),
                        Void.class
                );

                if (response.aggregations() != null && !response.aggregations().isEmpty()) {
                    final CompositeAggregate compositeAgg = response.aggregations()
                            .get(indicesAggregationKey)
                            .composite();

                    final List<String> indexNames = compositeAgg.buckets().array()
                            .stream().map(bucket -> bucket.key().get(indexNameSourceKey).stringValue())
                            .toList();
                    allIndexNames.addAll(indexNames);
                    afterKey = compositeAgg.afterKey();
                    bucketSize = indexNames.size();
                } else {
                    // No index exists for this stream, so no deletion necessary
                    return null;
                }
            }
            return allIndexNames;
        } catch (final IOException e) {
            fatalError("Failed to list indices for reindex purge. StreamId: " + streamId + ". " +
                       "Base name: '" + indexName + "'", e);
            return null;
        }
    }

    /**
     * Get the index name stem, which is the text up to the first index name variable, as denoted by curly braces
     */
    private String getIndexBaseName() {
        final Matcher indexNameMatcher = INDEX_BASE_NAME_PATTERN.matcher(indexName);
        if (indexNameMatcher.find()) {
            return indexNameMatcher.group(1);
        } else {
            throw new RuntimeException("Expected one or more characters in the index name before the first curly " +
                                       "brace.");
        }
    }

    /**
     * Index the current batch of documents
     */
    private void indexDocuments() {
        if (bulkOperations.isEmpty()) {
            return;
        }

        try {
            final AtomicBoolean succeeded = new AtomicBoolean(false);
            while (!succeeded.get()) {
                elasticClientCache.context(elasticCluster.getConnection(), elasticClient -> {
                    try {
                        // Create a new bulk indexing request, containing the current batch of documents
                        final Builder bulkRequestBuilder = new BulkRequest.Builder();

                        // For each document, create an indexing request and append to the bulk request
                        bulkRequestBuilder.operations(bulkOperations);

                        if (refreshAfterEachBatch) {
                            // Refresh upon completion of the batch index request
                            bulkRequestBuilder.refresh(Refresh.True);
                        } else {
                            bulkRequestBuilder.refresh(Refresh.False);
                        }

                        final BulkResponse response = elasticClient.bulk(bulkRequestBuilder.build());
                        if (response.errors()) {
                            final boolean overloaded = response.items().stream()
                                    .anyMatch(item -> item.status() == ES_TOO_MANY_REQUESTS_STATUS);
                            if (overloaded) {
                                // Failure was due to Elasticsearch being overloaded, so retry after a delay
                                deleteBulkRequestDocuments(elasticClient, response);
                                throw new ElasticsearchOverloadedException(response.toString());
                            } else {
                                // Request failed for some other reason, so abort without retry
                                throw new IOException("Bulk indexing request failed: " + response);
                            }
                        } else {
                            succeeded.set(true);
                            final String retryMessage = currentRetry > 0
                                    ? " (retries: " + currentRetry + ")"
                                    : "";
                            LOGGER.info("Pipeline '{}' indexed {} documents from stream {} to Elasticsearch cluster " +
                                        "'{}' in {} seconds{}", pipelineName, bulkOperations.size(),
                                    metaHolder.getMeta().getId(), elasticCluster.getName(), response.took() / 1000,
                                    retryMessage);
                        }
                    } catch (final ElasticsearchOverloadedException e) {
                        handleElasticsearchOverloadedException(e);
                    } catch (final ElasticsearchException e) {
                        if (e.status() == ES_TOO_MANY_REQUESTS_STATUS) {
                            handleElasticsearchOverloadedException(e);
                        } else {
                            handleElasticsearchException(e);
                        }
                    } catch (final ResponseException e) {
                        if (e.getResponse().getStatusCode() == ES_TOO_MANY_REQUESTS_STATUS) {
                            handleElasticsearchOverloadedException(e);
                        } else {
                            handleElasticsearchException(e);
                        }
                    } catch (final RuntimeException | IOException e) {
                        fatalError(e.getMessage() != null
                                ? e.getMessage().substring(0,
                                Math.min(ES_MAX_EXCEPTION_CHARS, e.getMessage().length()))
                                : "", e);
                    } finally {
                        currentRetry++;
                    }
                });
            }
        } finally {
            currentRetry = 0;
            bulkOperations.clear();
        }
    }

    /**
     * Delete any documents that were successfully indexed during a failed bulk request.
     * This removes duplicates in the destination index.
     */
    private void deleteBulkRequestDocuments(final ElasticsearchClient elasticClient,
                                            final BulkResponse response) {
        final List<BulkOperation> deleteOperations = new ArrayList<>();

        for (final BulkResponseItem item : response.items()) {
            if (item.error() == null) {
                deleteOperations.add(BulkOperation.of(b -> b
                        .delete(deleteOp -> deleteOp
                                .index(item.index())
                                .id(item.id())
                        )
                ));
            }
        }

        if (!deleteOperations.isEmpty()) {
            try {
                final BulkRequest bulkRequest = new BulkRequest.Builder()
                        .operations(deleteOperations)
                        .build();

                elasticClient.bulk(bulkRequest);

                LOGGER.info("Deleted {} documents from failed bulk indexing request for stream {}, pipeline '{}'",
                        deleteOperations.size(), metaHolder.getMeta().getId(), pipelineName);
            } catch (final IOException e) {
                LOGGER.warn("Error occurred when deleting documents indexed during a failed bulk request. " +
                            "Stream: {}, pipeline: '{}'", metaHolder.getMeta().getId(), pipelineName, e);
            }
        }
    }

    /**
     * Elasticsearch rejected the indexing request, because it is overloaded and
     * cannot queue the batched payload. Retry after a delay, if we haven't exceeded the retry
     * limit. Otherwise, terminate this thread and create an `Error` stream.
     */
    private void handleElasticsearchOverloadedException(final Exception e) {
        final String errorDetailMsg = e.getMessage() != null
                ? e.getMessage().substring(0,
                Math.min(ES_MAX_EXCEPTION_CHARS, e.getMessage().length()))
                : "";
        final ElasticIndexingConfig indexingConfig = elasticConfigProvider.get().getIndexingConfig();

        if (currentRetry < indexingConfig.getRetryCount()) {
            // Backoff by the initial interval plus an exponential amount
            final long sleepDurationMs = indexingConfig.getInitialRetryBackoffPeriodMs() +
                                         (long) currentRetry * currentRetry;
            try {
                LOGGER.warn("Indexing request by pipeline '{}' for stream {} was rejected by Elasticsearch. " +
                            "Retrying in {} milliseconds (retries: {})", pipelineName, metaHolder.getMeta().getId(),
                        sleepDurationMs, currentRetry);
                Thread.sleep(sleepDurationMs);
            } catch (final InterruptedException ex) {
                Thread.currentThread().interrupt();
                fatalError("Indexing terminated after " + currentRetry + " retries: " + errorDetailMsg, ex);
            }
        } else {
            fatalError("Indexing failed to complete after " + currentRetry + " retries: " + errorDetailMsg, e);
        }
    }

    /**
     * A general Elasticsearch indexing error occurred. Terminate processing and create an `Error` stream.
     */
    private void handleElasticsearchException(final Exception e) {
        final String errorDetailMsg = e.getMessage() != null
                ? e.getMessage().substring(0, Math.min(ES_MAX_EXCEPTION_CHARS, e.getMessage().length()))
                : "";

        fatalError("Indexing failed to complete after " + currentRetry + " retries: " + errorDetailMsg, e);
    }

    /**
     * Build the index name, substituting values from the source document where specified.
     * For instance, consider an index name of `stroom-index-{_year}`. A field `<string key='_year'>2023</string>` in
     * the doc root will result in a destination index of `stroom-index-2023` for that document. In this case, because
     * the field `_year` starts with an underscore, it is not rendered to the doc and instead is solely used in the
     * index name.
     */
    private String formatIndexName() {
        final Matcher indexNameVariableMatcher = INDEX_NAME_VALUE_PATTERN.matcher(indexName);
        return indexNameVariableMatcher.replaceAll(matchResult -> {
            final String fieldNameMatch = matchResult.group();
            final String fieldName = fieldNameMatch.substring(1, fieldNameMatch.length() - 1);
            final String fieldValue = currentDocIndexNameVariables.get(fieldName);
            if (fieldValue == null) {
                throw new IllegalArgumentException("Field '" + fieldName + "' not found in document when " +
                                                   "building index name with pattern: '" + indexName + "'");
            }
            return fieldValue;
        });
    }

    @PipelineProperty(
            description = "Target Elasticsearch cluster.",
            displayPriority = 1
    )
    @PipelinePropertyDocRef(types = ElasticClusterDoc.TYPE)
    public void setCluster(final DocRef clusterRef) {
        this.clusterRef = clusterRef;
    }

    @PipelineProperty(
            description = "Maximum number of documents to index in each bulk request.",
            defaultValue = "10000",
            displayPriority = 2
    )
    public void setBatchSize(final int batchSize) {
        this.batchSize = batchSize;
    }

    @PipelineProperty(
            description = "Refresh the index after each batch is processed, making the indexed documents visible to " +
                          "searches.",
            defaultValue = "false",
            displayPriority = 3
    )
    public void setRefreshAfterEachBatch(final boolean refreshAfterEachBatch) {
        this.refreshAfterEachBatch = refreshAfterEachBatch;
    }

    @PipelineProperty(
            description = "Name of the Elasticsearch ingest pipeline to execute when indexing.",
            displayPriority = 4
    )
    public void setIngestPipeline(final String ingestPipelineName) {
        this.ingestPipelineName = ingestPipelineName;
    }

    @PipelineProperty(
            description = "Name of the Elasticsearch index. Variables specified such as `{year}` are replaced with " +
                          "the corresponding field values contained in the document root. Field names beginning with " +
                          "an underscore are not written to the document and are only used in the index name pattern.",
            displayPriority = 5
    )
    public void setIndexName(final String indexName) {
        this.indexName = indexName;
    }

    @PipelineProperty(
            description = "When reprocessing a stream, first delete any documents from the index matching the " +
                          "source stream ID.",
            defaultValue = "true",
            displayPriority = 11
    )
    public void setPurgeOnReprocess(final boolean purgeOnReprocess) {
        this.purgeOnReprocess = purgeOnReprocess;
    }

    private void log(final Severity severity, final String message, final Exception e) {
        errorReceiverProxy.log(severity, locationFactory.create(locator), getElementId(), message, e);
    }

    /**
     * @param message - Message to send to the error receiver and log target
     * @param e       - Original exception (optional - omit if output is likely to be excessive)
     * @throws LoggedException
     */
    private void fatalError(final String message, final Exception e) throws LoggedException {
        // Terminate processing as this is a fatal error
        log(Severity.FATAL_ERROR, message, e);

        if (e != null) {
            throw LoggedException.wrap(message, e);
        } else {
            throw LoggedException.create(message);
        }
    }

    private static class ElasticsearchOverloadedException extends RuntimeException {

        public ElasticsearchOverloadedException(final String message) {
            super(message);
        }
    }
}
