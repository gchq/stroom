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

package stroom.search.elastic.indexing;

import stroom.docref.DocRef;
import stroom.meta.shared.Meta;
import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.state.MetaHolder;
import stroom.pipeline.state.StreamProcessorHolder;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorTask;
import stroom.search.elastic.ElasticClientCache;
import stroom.search.elastic.ElasticClientConfig;
import stroom.search.elastic.ElasticClusterStore;
import stroom.search.elastic.ElasticConfig;
import stroom.search.elastic.search.ElasticSearchConfig;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticConnectionConfig;
import stroom.util.cache.CacheConfig;
import stroom.util.shared.Severity;
import stroom.util.xml.SAXParserFactoryFactory;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.util.BinaryData;
import org.mockito.Mockito;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.xml.parsers.SAXParserFactory;

/// Builds an [ElasticIndexingFilter] with its pipeline dependencies stubbed out, feeds it JSON-in-XML SAX
/// events and records any errors it reports, so that the filter can be tested in isolation.
class ElasticIndexingFilterTestHarness {

    /// The namespace used by the `xml-to-json` style XML that the filter consumes.
    static final String JSON_NAMESPACE = "http://www.w3.org/2013/XSL/json";

    static final long STREAM_ID = 123L;
    static final String PIPELINE_NAME = "Test Pipeline";
    static final String CLUSTER_NAME = "Test Cluster";

    private static final SAXParserFactory PARSER_FACTORY = SAXParserFactoryFactory.newInstance();

    private final List<LoggedError> errors = Collections.synchronizedList(new ArrayList<>());
    private final ElasticClusterStore elasticClusterStore = Mockito.mock(ElasticClusterStore.class);
    private final PipelineStore pipelineStore = Mockito.mock(PipelineStore.class);
    private final StreamProcessorHolder streamProcessorHolder = Mockito.mock(StreamProcessorHolder.class);
    private final ProcessorFilter processorFilter = Mockito.mock(ProcessorFilter.class);
    private final MetaHolder metaHolder = new MetaHolder();
    private final DocRef clusterRef = ElasticClusterDoc.buildDocRef()
            .uuid("cluster-uuid")
            .name(CLUSTER_NAME)
            .build();

    /// Creates a harness whose cluster document has the supplied connection URLs.
    ///
    /// @param connectionUrls The URLs of the cluster, which are only used if a real client is created.
    ElasticIndexingFilterTestHarness(final List<String> connectionUrls) {
        final DocRef pipelineRef = PipelineDoc.buildDocRef().uuid("pipeline-uuid").name(PIPELINE_NAME).build();
        final PipelineDoc pipelineDoc = Mockito.mock(PipelineDoc.class);
        Mockito.when(pipelineDoc.getName()).thenReturn(PIPELINE_NAME);
        Mockito.when(pipelineStore.readDocument(pipelineRef)).thenReturn(pipelineDoc);

        final Processor processor = Mockito.mock(Processor.class);
        Mockito.when(processor.getPipeline()).thenReturn(pipelineRef);
        final ProcessorTask processorTask = Mockito.mock(ProcessorTask.class);
        Mockito.when(processorTask.getProcessorFilter()).thenReturn(processorFilter);
        Mockito.when(streamProcessorHolder.getStreamProcessor()).thenReturn(processor);
        Mockito.when(streamProcessorHolder.getStreamTask()).thenReturn(processorTask);

        final Meta meta = Mockito.mock(Meta.class);
        Mockito.when(meta.getId()).thenReturn(STREAM_ID);
        metaHolder.setMeta(meta);

        final ElasticConnectionConfig connectionConfig = new ElasticConnectionConfig();
        connectionConfig.setConnectionUrls(connectionUrls);
        final ElasticClusterDoc clusterDoc = ElasticClusterDoc.builder()
                .uuid(clusterRef.getUuid())
                .name(CLUSTER_NAME)
                .connection(connectionConfig)
                .build();
        Mockito.when(elasticClusterStore.readDocument(clusterRef)).thenReturn(clusterDoc);
    }

    /// Creates a filter that uses the supplied client for every request, with the cluster and index name set.
    ///
    /// @param elasticClient  The client the filter is given whenever it borrows one from the cache.
    /// @param indexingConfig The indexing settings, e.g. the retry count and delay.
    /// @param indexName      The target index name, which may contain `{variable}` placeholders.
    /// @return The new filter.
    ElasticIndexingFilter createFilter(final ElasticsearchClient elasticClient,
                                       final ElasticIndexingConfig indexingConfig,
                                       final String indexName) {
        final ElasticConfig elasticConfig = new ElasticConfig(
                new ElasticClientConfig(),
                indexingConfig,
                new ElasticSearchConfig(),
                CacheConfig.builder().build(),
                CacheConfig.builder().build());

        final ElasticIndexingFilter filter = new ElasticIndexingFilter(
                new LocationFactoryProxy(),
                new ErrorReceiverProxy((severity, location, elementId, message, errorType, e) ->
                        errors.add(new LoggedError(severity, message, e))),
                () -> elasticConfig,
                clientCache(elasticClient),
                elasticClusterStore,
                pipelineStore,
                streamProcessorHolder,
                metaHolder);
        filter.setCluster(clusterRef);
        filter.setIndexName(indexName);
        return filter;
    }

    /// Runs a whole stream through the filter: initialises it, parses the XML into SAX events, then ends
    /// processing so that any remaining documents are flushed.
    ///
    /// @param filter The filter to run.
    /// @param xml    The JSON-in-XML to parse.
    void process(final ElasticIndexingFilter filter, final String xml) {
        filter.startProcessing();
        parse(filter, xml);
        filter.endProcessing();
    }

    /// Parses the XML, sending its SAX events to the filter.
    ///
    /// @param filter The filter to send the events to.
    /// @param xml    The JSON-in-XML to parse.
    void parse(final ElasticIndexingFilter filter, final String xml) {
        try {
            final XMLReader xmlReader = PARSER_FACTORY.newSAXParser().getXMLReader();
            xmlReader.setContentHandler(filter);
            xmlReader.parse(new InputSource(new StringReader(xml)));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    /// Wraps the supplied JSON-in-XML elements in an outer `array` element in the JSON namespace.
    ///
    /// @param content The documents, e.g. `<map><string key="a">b</string></map>`.
    /// @return The complete XML.
    static String jsonXml(final String content) {
        return "<array xmlns=\"" + JSON_NAMESPACE + "\">" + content + "</array>";
    }

    /// Creates the supplied number of simple documents, each with a field `n` holding its zero based index.
    ///
    /// @param count The number of documents.
    /// @return The `map` elements, to be wrapped with [#jsonXml(String)].
    static String documents(final int count) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append("<map><number key=\"n\">").append(i).append("</number></map>");
        }
        return sb.toString();
    }

    /// Gets the source of a bulk `create` operation as a string.
    ///
    /// @param operation The bulk operation.
    /// @return The JSON document the filter produced.
    static String documentJson(final BulkOperation operation) {
        final BinaryData binaryData = (BinaryData) operation.create().document();
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            binaryData.writeTo(outputStream);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return outputStream.toString(StandardCharsets.UTF_8);
    }

    /// Gets the sources of all the `create` operations in the bulk requests, in order.
    ///
    /// @param bulkRequests The bulk requests the filter sent.
    /// @return The JSON documents.
    static List<String> documentJsons(final List<BulkRequest> bulkRequests) {
        return bulkRequests.stream()
                .flatMap(bulkRequest -> bulkRequest.operations().stream())
                .map(ElasticIndexingFilterTestHarness::documentJson)
                .toList();
    }

    /// Creates indexing settings that keep retries fast.
    ///
    /// @param maxNestedElementDepth The maximum JSON depth allowed.
    /// @param retryCount            The number of times an overloaded request is retried.
    /// @return The settings.
    static ElasticIndexingConfig indexingConfig(final int maxNestedElementDepth, final int retryCount) {
        return new ElasticIndexingConfig(maxNestedElementDepth, 1, retryCount);
    }

    /// @return The errors the filter has reported to the error receiver, in order.
    List<LoggedError> getErrors() {
        synchronized (errors) {
            return List.copyOf(errors);
        }
    }

    /// @return The messages of the errors reported with the supplied severity.
    List<String> getMessages(final Severity severity) {
        return getErrors().stream()
                .filter(error -> error.severity() == severity)
                .map(LoggedError::message)
                .toList();
    }

    /// Marks the current stream task as being from a reprocessing filter (or not).
    ///
    /// @param reprocess Whether the stream is being reprocessed.
    void setReprocess(final boolean reprocess) {
        Mockito.when(processorFilter.isReprocess()).thenReturn(reprocess);
    }

    /// @return The mock store holding the cluster document, so tests can change what it returns.
    ElasticClusterStore getElasticClusterStore() {
        return elasticClusterStore;
    }

    /// @return The ref of the cluster document the filter is configured with.
    DocRef getClusterRef() {
        return clusterRef;
    }

    private static ElasticClientCache clientCache(final ElasticsearchClient elasticClient) {
        return new ElasticClientCache() {
            @Override
            public void context(final ElasticConnectionConfig key, final Consumer<ElasticsearchClient> consumer) {
                consumer.accept(elasticClient);
            }

            @Override
            public <R> R contextResult(final ElasticConnectionConfig key,
                                       final Function<ElasticsearchClient, R> function) {
                return function.apply(elasticClient);
            }
        };
    }


    // --------------------------------------------------------------------------------


    /// An error the filter reported to the error receiver.
    ///
    /// @param severity  The severity of the error.
    /// @param message   The message.
    /// @param throwable The cause, if any.
    record LoggedError(Severity severity, String message, Throwable throwable) {

    }
}
