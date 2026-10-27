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

package stroom.search.elastic;

import stroom.ai.shared.KeyStoreType;
import stroom.credentials.impl.CredentialsService;
import stroom.credentials.shared.AccessTokenSecret;
import stroom.credentials.shared.Credential;
import stroom.credentials.shared.CredentialType;
import stroom.credentials.shared.KeyStoreSecret;
import stroom.credentials.shared.PutCredentialRequest;
import stroom.credentials.shared.Secret;
import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.OutputStreamProvider;
import stroom.data.store.api.SegmentOutputStream;
import stroom.data.store.api.Store;
import stroom.data.store.api.Target;
import stroom.dictionary.api.DictionaryStore;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.docref.DocRef;
import stroom.meta.api.MetaProperties;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.MetaFields;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineDataUtil;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.api.ProcessorResult;
import stroom.processor.shared.CreateProcessFilterRequest;
import stroom.processor.shared.QueryData;
import stroom.query.api.Column;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.DestroyReason;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.OffsetRange;
import stroom.query.api.ParamUtil;
import stroom.query.api.Query;
import stroom.query.api.Result;
import stroom.query.api.ResultRequest;
import stroom.query.api.ResultRequest.Fetch;
import stroom.query.api.ResultRequest.ResultStyle;
import stroom.query.api.Row;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchResponse;
import stroom.query.api.TableResult;
import stroom.query.api.TableSettings;
import stroom.query.common.v2.ResultStoreManager;
import stroom.resource.api.ResourceStore;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticConnectionConfig;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.test.CommonTranslationTestHelper;
import stroom.test.StoreCreationTool;
import stroom.test.common.ProjectPathUtil;
import stroom.util.io.StreamUtil;
import stroom.util.shared.ErrorMessage;
import stroom.util.shared.ResourceKey;
import stroom.util.shared.Severity;
import stroom.util.shared.http.HttpClientConfig;

import jakarta.inject.Inject;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/// Creates the Stroom side of the Elasticsearch end-to-end tests: the Elastic Cluster and Elastic Index
/// documents, an Elasticsearch indexing pipeline with a processor filter, the event streams to index, and
/// searches of the index through Stroom's search API.
public class ElasticEndToEndTestHelper {

    /// The feed of the translated sample network monitoring events, see [CommonTranslationTestHelper].
    public static final String NETWORK_FEED = CommonTranslationTestHelper.FEED_NAME;
    /// The number of events in the sample network monitoring data.
    public static final int NETWORK_EVENT_COUNT = 26;
    /// The feed of the typed test events.
    public static final String TYPED_FEED = "ELASTIC_TYPED_EVENTS";

    static final Path RESOURCES_DIR = ProjectPathUtil.resolveDir("stroom-app")
            .resolve("src")
            .resolve("test")
            .resolve("resources")
            .resolve("ElasticEndToEndTest");
    static final Path NETWORK_XSLT = RESOURCES_DIR.resolve("network-events-to-json.xsl");
    static final Path NETWORK_INDEX_BODY = RESOURCES_DIR.resolve("network-index.json");
    static final Path TYPED_XSLT = RESOURCES_DIR.resolve("typed-events-to-json.xsl");
    static final Path TYPED_TEMPLATE_BODY = RESOURCES_DIR.resolve("typed-index-template.json");
    static final Path TYPED_EVENTS = RESOURCES_DIR.resolve("typed-events.xml");
    static final Path CONDITIONS_EVENTS = RESOURCES_DIR.resolve("conditions-events.xml");

    // The ids of elements in the standard `Indexing` template pipeline, which we copy
    private static final String INDEXING_TEMPLATE_PIPELINE_UUID = "fcef1b20-083e-436c-ab95-47a6ce453435";
    private static final String XSLT_FILTER = "xsltFilter";
    private static final String LUCENE_INDEXING_FILTER = "indexingFilter";
    private static final String WRITE_RECORD_COUNT_FILTER = "writeRecordCountFilter";
    private static final String ELASTIC_INDEXING_FILTER = "elasticIndexingFilter";
    private static final String ELASTIC_INDEXING_FILTER_TYPE = "ElasticIndexingFilter";

    private static final String TRUST_STORE_PASSWORD = "changeit";
    private static final String COMPONENT_ID = "table-1";
    private static final int MAX_RESULTS = 1_000;

    private final StoreCreationTool storeCreationTool;
    private final CommonTranslationTestHelper commonTranslationTestHelper;
    private final PipelineStore pipelineStore;
    private final ProcessorFilterService processorFilterService;
    private final ElasticClusterStore elasticClusterStore;
    private final ElasticIndexStore elasticIndexStore;
    private final DictionaryStore dictionaryStore;
    private final Store store;
    private final ResultStoreManager resultStoreManager;
    private final MetaService metaService;
    private final CredentialsService credentialsService;
    private final ResourceStore resourceStore;

    @Inject
    ElasticEndToEndTestHelper(final StoreCreationTool storeCreationTool,
                              final CommonTranslationTestHelper commonTranslationTestHelper,
                              final PipelineStore pipelineStore,
                              final ProcessorFilterService processorFilterService,
                              final ElasticClusterStore elasticClusterStore,
                              final ElasticIndexStore elasticIndexStore,
                              final DictionaryStore dictionaryStore,
                              final Store store,
                              final ResultStoreManager resultStoreManager,
                              final MetaService metaService,
                              final CredentialsService credentialsService,
                              final ResourceStore resourceStore) {
        this.storeCreationTool = storeCreationTool;
        this.commonTranslationTestHelper = commonTranslationTestHelper;
        this.pipelineStore = pipelineStore;
        this.processorFilterService = processorFilterService;
        this.elasticClusterStore = elasticClusterStore;
        this.elasticIndexStore = elasticIndexStore;
        this.dictionaryStore = dictionaryStore;
        this.store = store;
        this.resultStoreManager = resultStoreManager;
        this.metaService = metaService;
        this.credentialsService = credentialsService;
        this.resourceStore = resourceStore;
    }

    /// Index the sample network monitoring events to a new index with explicit mappings, checking that
    /// indexing had no errors, and make them visible to searches.
    ///
    /// @param node The node to index to.
    /// @param clusterRef The Elastic Cluster document for the node.
    /// @param indexName The name of the index to create and index to.
    public void indexNetworkEvents(final ElasticTestNode node, final DocRef clusterRef, final String indexName) {
        node.createIndex(indexName, StreamUtil.fileToString(NETWORK_INDEX_BODY));
        translateNetworkEvents();
        createIndexingPipeline("Network events indexing", NETWORK_XSLT, clusterRef, indexName, NETWORK_FEED);
        final List<ProcessorResult> results = processAll();
        assertThat(results).hasSize(1);
        assertNoErrors(results.getFirst());
        node.refresh(indexName);
    }

    /// Index the typed test events, which exercise Elasticsearch field types needing special handling, to
    /// indices named `<indexBaseName>-<year>` whose mappings come from an index template named
    /// `indexBaseName`. Checks that indexing had no errors and makes the events visible to searches.
    ///
    /// @param node The node to index to.
    /// @param clusterRef The Elastic Cluster document for the node.
    /// @param indexBaseName The name of the index template, and the start of the name of each index.
    /// @return The indexing pipeline.
    public DocRef indexTypedEvents(final ElasticTestNode node, final DocRef clusterRef, final String indexBaseName) {
        return indexTypedEvents(node, clusterRef, indexBaseName, true);
    }

    /// As [#indexTypedEvents(ElasticTestNode, DocRef, String)], choosing whether reprocessing a stream first
    /// deletes the documents indexed from it before.
    ///
    /// @param node The node to index to.
    /// @param clusterRef The Elastic Cluster document for the node.
    /// @param indexBaseName The name of the index template, and the start of the name of each index.
    /// @param purgeOnReprocess The `purgeOnReprocess` property of the indexing filter.
    /// @return The indexing pipeline.
    public DocRef indexTypedEvents(final ElasticTestNode node,
                                   final DocRef clusterRef,
                                   final String indexBaseName,
                                   final boolean purgeOnReprocess) {
        node.createIndexTemplate(
                indexBaseName,
                StreamUtil.fileToString(TYPED_TEMPLATE_BODY).replace("INDEX_PATTERN", indexBaseName + "-*"));
        addEventsStream(TYPED_FEED, TYPED_EVENTS);
        final DocRef pipelineRef = createIndexingPipeline(
                "Typed events indexing",
                TYPED_XSLT,
                clusterRef,
                indexBaseName + "-{year}",
                TYPED_FEED,
                purgeOnReprocess);
        final List<ProcessorResult> results = processAll();
        assertThat(results).hasSize(1);
        assertNoErrors(results.getFirst());
        node.refresh(indexBaseName + "-*");
        return pipelineRef;
    }

    /// Translate the sample network monitoring data into `Events` streams on [#NETWORK_FEED], checking that
    /// the translation (and the reference data loads it relies on) had no errors.
    public void translateNetworkEvents() {
        commonTranslationTestHelper.setup();
        final List<ProcessorResult> results = commonTranslationTestHelper.processAll();
        // 3 reference data streams plus the one event stream
        assertThat(results).hasSize(4);
        results.forEach(ElasticEndToEndTestHelper::assertNoErrors);
    }

    /// Add an `Events` stream directly to the stream store, i.e. data that needs no translation.
    ///
    /// The stream is segmented as a pipeline writes one, with the XML before the first `Event` element, each
    /// `Event` element, and the XML after the last one in segments of their own. Search extraction needs the
    /// segments to find the event with an event id.
    ///
    /// @param feedName The feed to add the stream to. The feed is created if it doesn't exist.
    /// @param dataFile The file holding the XML events, whose `Event` elements have no attributes.
    /// @return The id of the new stream.
    public long addEventsStream(final String feedName, final Path dataFile) {
        storeCreationTool.getOrCreateFeedDoc(feedName);
        final MetaProperties metaProperties = MetaProperties.builder()
                .feedName(feedName)
                .typeName(StreamTypeNames.EVENTS)
                .build();
        try (final Target target = store.openTarget(metaProperties)) {
            try (final OutputStreamProvider outputStreamProvider = target.next();
                    final SegmentOutputStream outputStream = outputStreamProvider.get()) {
                writeSegmented(outputStream, StreamUtil.fileToString(dataFile));
            }
            return target.getMeta().getId();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writeSegmented(final SegmentOutputStream outputStream,
                                       final String xml) throws IOException {
        final String eventStart = "<Event>";
        final String eventEnd = "</Event>";
        int start = xml.indexOf(eventStart);
        if (start == -1) {
            outputStream.write(xml.getBytes(StandardCharsets.UTF_8));
            return;
        }
        // The XML before the first event
        outputStream.write(xml.substring(0, start).getBytes(StandardCharsets.UTF_8));
        while (start != -1) {
            outputStream.addSegment();
            final int end = xml.indexOf(eventEnd, start) + eventEnd.length();
            outputStream.write(xml.substring(start, end).getBytes(StandardCharsets.UTF_8));
            final int next = xml.indexOf(eventStart, end);
            if (next == -1) {
                // The XML after the last event
                outputStream.addSegment();
                outputStream.write(xml.substring(end).getBytes(StandardCharsets.UTF_8));
            }
            start = next;
        }
    }

    /// Create an Elastic Cluster document connecting to the supplied node.
    ///
    /// @param node The node to connect to.
    /// @return The new document's reference.
    public DocRef createCluster(final ElasticTestNode node) {
        return createCluster(List.of(node.getUrl()), null, null);
    }

    /// Create an Elastic Cluster document connecting to the supplied URLs, with an API key and HTTP client
    /// settings.
    ///
    /// @param connectionUrls The URLs of the nodes.
    /// @param apiKeyName The name of the API key credential, or `null` for none.
    /// @param httpClientConfig The HTTP client settings, or `null` to use the default ones.
    /// @return The new document's reference.
    public DocRef createCluster(final List<String> connectionUrls,
                                final String apiKeyName,
                                final HttpClientConfig httpClientConfig) {
        final DocRef clusterRef = elasticClusterStore.createDocument("E2E cluster");
        final ElasticConnectionConfig connection = new ElasticConnectionConfig();
        connection.setConnectionUrls(connectionUrls);
        connection.setApiKeyName(apiKeyName);
        connection.setHttpClientConfiguration(httpClientConfig);
        final ElasticClusterDoc clusterDoc = elasticClusterStore.readDocument(clusterRef)
                .copy()
                .connection(connection)
                .build();
        elasticClusterStore.writeDocument(clusterDoc);
        return clusterRef;
    }

    /// Create an Elastic Index document for searching an index (or indices) on the cluster.
    ///
    /// @param clusterRef The cluster holding the index.
    /// @param indexName The name or pattern of the indices to search.
    /// @param timeField The name of the index's time field.
    /// @param searchScrollSize The number of hits to fetch in each scroll request.
    /// @return The new document's reference.
    public DocRef createIndexDoc(final DocRef clusterRef,
                                 final String indexName,
                                 final String timeField,
                                 final int searchScrollSize) {
        final DocRef indexRef = elasticIndexStore.createDocument("E2E index " + indexName);
        final ElasticIndexDoc indexDoc = elasticIndexStore.readDocument(indexRef)
                .copy()
                .clusterRef(clusterRef)
                .indexName(indexName)
                .timeField(timeField)
                .searchScrollSize(searchScrollSize)
                .build();
        elasticIndexStore.writeDocument(indexDoc);
        return indexRef;
    }

    /// Create an Elasticsearch indexing pipeline, by copying the standard `Indexing` template pipeline and
    /// replacing its Lucene indexing filter with an Elasticsearch one, and a processor filter that runs it on
    /// the `Events` streams of a feed.
    ///
    /// @param name The name of the pipeline, which must be unique.
    /// @param xsltFile The XSLT translating each event to JSON XML.
    /// @param clusterRef The cluster to index to.
    /// @param indexName The `indexName` property of the indexing filter, which may contain variables.
    /// @param feedName The feed whose `Events` streams are indexed.
    /// @return The pipeline's reference.
    public DocRef createIndexingPipeline(final String name,
                                         final Path xsltFile,
                                         final DocRef clusterRef,
                                         final String indexName,
                                         final String feedName) {
        return createIndexingPipeline(name, xsltFile, clusterRef, indexName, feedName, true);
    }

    /// As [#createIndexingPipeline(String, Path, DocRef, String, String)], choosing whether reprocessing a
    /// stream first deletes the documents indexed from it before.
    ///
    /// @param name The name of the pipeline, which must be unique.
    /// @param xsltFile The XSLT translating each event to JSON XML.
    /// @param clusterRef The cluster to index to.
    /// @param indexName The `indexName` property of the indexing filter, which may contain variables.
    /// @param feedName The feed whose `Events` streams are indexed.
    /// @param purgeOnReprocess The `purgeOnReprocess` property of the indexing filter.
    /// @return The pipeline's reference.
    public DocRef createIndexingPipeline(final String name,
                                         final Path xsltFile,
                                         final DocRef clusterRef,
                                         final String indexName,
                                         final String feedName,
                                         final boolean purgeOnReprocess) {
        final DocRef pipelineRef = storeCreationTool.duplicatePipeline(
                new DocRef(PipelineDoc.TYPE, INDEXING_TEMPLATE_PIPELINE_UUID),
                name,
                null);
        final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipelineRef);
        final PipelineDataBuilder builder = new PipelineDataBuilder(pipelineDoc.getPipelineData());

        builder.removeLink(XSLT_FILTER, LUCENE_INDEXING_FILTER);
        builder.removeLink(LUCENE_INDEXING_FILTER, WRITE_RECORD_COUNT_FILTER);
        builder.removeElement(PipelineDataUtil.createElement(
                LUCENE_INDEXING_FILTER, "IndexingFilter", null, null));
        builder.addElement(PipelineDataUtil.createElement(
                ELASTIC_INDEXING_FILTER, ELASTIC_INDEXING_FILTER_TYPE, null, null));
        builder.addLink(XSLT_FILTER, ELASTIC_INDEXING_FILTER);
        builder.addLink(ELASTIC_INDEXING_FILTER, WRITE_RECORD_COUNT_FILTER);

        final DocRef xsltRef = storeCreationTool.getXSLT(name, xsltFile);
        builder.addProperty(PipelineDataUtil.createProperty(XSLT_FILTER, "xslt", xsltRef));
        builder.addProperty(PipelineDataUtil.createProperty(ELASTIC_INDEXING_FILTER, "cluster", clusterRef));
        builder.addProperty(PipelineDataUtil.createProperty(ELASTIC_INDEXING_FILTER, "indexName", indexName));
        // Make everything indexed visible to searches as soon as processing completes
        builder.addProperty(PipelineDataUtil.createProperty(
                ELASTIC_INDEXING_FILTER, "refreshAfterEachBatch", true));
        // A small batch size, so a stream is indexed in several bulk requests
        builder.addProperty(new PipelineProperty(
                ELASTIC_INDEXING_FILTER, "batchSize", new PipelinePropertyValue(10)));
        builder.addProperty(PipelineDataUtil.createProperty(
                ELASTIC_INDEXING_FILTER, "purgeOnReprocess", purgeOnReprocess));

        pipelineStore.writeDocument(pipelineDoc.copy().pipelineData(builder.build()).build());
        createProcessorFilter(pipelineRef, feedName, StreamTypeNames.EVENTS, false);
        return pipelineRef;
    }

    /// Create a processor filter that runs a pipeline on the streams of a feed.
    ///
    /// @param pipelineRef The pipeline to run.
    /// @param feedName The feed whose streams are processed.
    /// @param streamType The type of the streams the filter selects. For a reprocessing filter, these are the
    ///                   streams the pipeline produced (e.g. `Error`), whose parents are reprocessed.
    /// @param reprocess Whether the filter reprocesses streams the pipeline has already processed.
    public void createProcessorFilter(final DocRef pipelineRef,
                                      final String feedName,
                                      final String streamType,
                                      final boolean reprocess) {
        final DocRef feedRef = storeCreationTool.getOrCreateFeedDoc(feedName);
        final QueryData queryData = QueryData.builder()
                .dataSource(MetaFields.STREAM_STORE_DOC_REF)
                .expression(ExpressionOperator.builder()
                        .addDocRefTerm(MetaFields.FEED, Condition.IS_DOC_REF, feedRef)
                        .addTextTerm(MetaFields.TYPE, Condition.EQUALS, streamType)
                        .build())
                .build();
        processorFilterService.create(CreateProcessFilterRequest.builder()
                .pipeline(pipelineRef)
                .queryData(queryData)
                .priority(1)
                .reprocess(reprocess)
                .enabled(true)
                .build());
    }

    /// Count the streams of a type on a feed.
    ///
    /// @param feedName The feed.
    /// @param streamType The stream type, e.g. `Error`.
    /// @return The number of streams.
    public long countStreams(final String feedName, final String streamType) {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addDocRefTerm(MetaFields.FEED, Condition.IS_DOC_REF, storeCreationTool.getOrCreateFeedDoc(feedName))
                .addTextTerm(MetaFields.TYPE, Condition.EQUALS, streamType)
                .build();
        return metaService.find(new FindMetaCriteria(expression)).getValues().size();
    }

    /// Process every task that the processor filters create, e.g. to index the streams.
    ///
    /// @return The result of each task.
    public List<ProcessorResult> processAll() {
        return commonTranslationTestHelper.processAll();
    }

    /// Create a key store credential holding a single trusted certificate, as a trust store.
    ///
    /// @param name The name of the credential.
    /// @param certificate The certificate to trust, e.g. that of a CA.
    /// @return The name of the credential.
    public String createTrustStoreCredential(final String name, final X509Certificate certificate) {
        // Upload the trust store, as the UI does, before storing the credential that refers to it
        final ResourceKey resourceKey = resourceStore.createTempFile(name + ".p12");
        final Path path = resourceStore.getTempFile(resourceKey);
        try {
            final java.security.KeyStore trustStore = java.security.KeyStore.getInstance("PKCS12");
            trustStore.load(null, null);
            trustStore.setCertificateEntry("ca", certificate);
            try (final OutputStream outputStream = Files.newOutputStream(path)) {
                trustStore.store(outputStream, TRUST_STORE_PASSWORD.toCharArray());
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        } catch (final GeneralSecurityException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
        storeCredential(name,
                CredentialType.KEY_STORE,
                KeyStoreType.PKCS12,
                new KeyStoreSecret(UUID.randomUUID().toString(), KeyStoreType.PKCS12, TRUST_STORE_PASSWORD,
                        resourceKey));
        return name;
    }

    /// Create an access token credential holding an Elasticsearch API key.
    ///
    /// @param name The name of the credential.
    /// @param apiKey The encoded API key.
    /// @return The name of the credential.
    public String createApiKeyCredential(final String name, final String apiKey) {
        storeCredential(name, CredentialType.ACCESS_TOKEN, null, new AccessTokenSecret(apiKey));
        return name;
    }

    private void storeCredential(final String name,
                                 final CredentialType credentialType,
                                 final KeyStoreType keyStoreType,
                                 final Secret secret) {
        final Credential credential = new Credential(
                UUID.randomUUID().toString(),
                name,
                null,
                null,
                null,
                null,
                credentialType,
                keyStoreType,
                null);
        credentialsService.storeCredentials(new PutCredentialRequest(credential, secret));
    }

    /// Create a dictionary.
    ///
    /// @param name The dictionary's name.
    /// @param data The dictionary's content, one term per line.
    /// @return The dictionary's reference.
    public DocRef createDictionary(final String name, final String data) {
        final DocRef dictionaryRef = dictionaryStore.createDocument(name);
        final DictionaryDoc dictionaryDoc = dictionaryStore.readDocument(dictionaryRef)
                .copy()
                .data(data)
                .build();
        dictionaryStore.writeDocument(dictionaryDoc);
        return dictionaryRef;
    }

    /// Create a search extraction pipeline, by copying the standard `Search Extraction` template pipeline.
    ///
    /// @param name The name of the pipeline, which must be unique.
    /// @param xsltFile The XSLT translating each event to `records:2` XML.
    /// @return The pipeline's reference.
    public DocRef createExtractionPipeline(final String name, final Path xsltFile) {
        return storeCreationTool.getSearchResultPipeline(name, xsltFile);
    }

    /// Search an Elastic Index document through Stroom's search API, returning a table of the values of the
    /// requested fields, as Elasticsearch stored them (i.e. without extracting them from the source events).
    ///
    /// @param indexRef The Elastic Index document to search.
    /// @param expression The query expression.
    /// @param fieldNames The fields to return a column for.
    /// @return The rows of the table, plus any errors the search reported.
    public SearchResult search(final DocRef indexRef,
                               final ExpressionOperator expression,
                               final List<String> fieldNames) {
        return search(indexRef, expression, fieldNames, null);
    }

    /// As [#search(DocRef, ExpressionOperator, List)], optionally extracting the values from the source events
    /// of each hit with an extraction pipeline, as a dashboard table does when it extracts values.
    ///
    /// @param indexRef The Elastic Index document to search.
    /// @param expression The query expression.
    /// @param fieldNames The fields to return a column for.
    /// @param extractionPipelineRef The pipeline to extract the values with, or `null` to use the values
    ///                              Elasticsearch stored.
    /// @return The rows of the table, plus any errors the search reported.
    public SearchResult search(final DocRef indexRef,
                               final ExpressionOperator expression,
                               final List<String> fieldNames,
                               final DocRef extractionPipelineRef) {
        final List<Column> columns = new ArrayList<>(fieldNames.size());
        for (int i = 0; i < fieldNames.size(); i++) {
            final String fieldName = fieldNames.get(i);
            columns.add(Column.builder()
                    .id(String.valueOf(i))
                    .name(fieldName)
                    .expression(ParamUtil.create(fieldName))
                    .build());
        }
        final TableSettings tableSettings = TableSettings.builder()
                .addColumns(columns)
                .extractValues(extractionPipelineRef != null)
                .extractionPipeline(extractionPipelineRef)
                .build();
        final ResultRequest resultRequest = new ResultRequest(
                COMPONENT_ID,
                null,
                List.of(tableSettings),
                new OffsetRange(0L, (long) MAX_RESULTS),
                null,
                null,
                ResultStyle.TABLE,
                Fetch.ALL,
                null,
                null);
        final SearchRequest searchRequest = new SearchRequest(
                null,
                null,
                Query.builder().dataSource(indexRef).expression(expression).build(),
                List.of(resultRequest),
                DateTimeSettings.builder().build(),
                false);

        final SearchResponse response = resultStoreManager.search(searchRequest);
        resultStoreManager.destroy(response.getKey(), DestroyReason.NO_LONGER_NEEDED);
        assertThat(response.complete())
                .as("Search is complete")
                .isTrue();

        final List<String> errors = response.getErrorMessages() == null
                ? List.of()
                : response.getErrorMessages().stream()
                        .map(ErrorMessage::getMessage)
                        .toList();
        final List<List<String>> rows = new ArrayList<>();
        if (response.getResults() != null) {
            for (final Result result : response.getResults()) {
                final TableResult tableResult = (TableResult) result;
                if (tableResult.getRows() != null) {
                    for (final Row row : tableResult.getRows()) {
                        rows.add(row.getValues());
                    }
                }
            }
        }
        return new SearchResult(rows, errors);
    }

    /// Assert that a processing task had no errors (or worse) and wrote some records.
    ///
    /// @param result The result of the processing task.
    public static void assertNoErrors(final ProcessorResult result) {
        assertThat(result.getMarkerCount(Severity.ERROR, Severity.FATAL_ERROR))
                .as("Processing errors")
                .isZero();
        assertThat(result.getWritten())
                .as("Records written")
                .isPositive();
    }

    /// The result of a search through Stroom's search API.
    ///
    /// @param rows The values of each row of the result table, in the order of the requested fields.
    /// @param errors The error messages the search reported.
    public record SearchResult(List<List<String>> rows, List<String> errors) {

        /// @param columnIndex The index of the column.
        /// @return The values of a column, in row order.
        public List<String> column(final int columnIndex) {
            return rows.stream()
                    .map(row -> row.get(columnIndex))
                    .toList();
        }
    }
}
