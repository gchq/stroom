/*
 * Copyright 2017 Crown Copyright
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

package stroom.pipeline;


import stroom.data.store.api.InputStreamProvider;
import stroom.data.store.api.SegmentInputStream;
import stroom.data.store.api.Source;
import stroom.data.store.api.SourceUtil;
import stroom.data.store.api.Store;
import stroom.docref.DocRef;
import stroom.io.StreamCloser;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaService;
import stroom.pipeline.destination.RollingDestinations;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.LoggingErrorReceiver;
import stroom.pipeline.factory.Pipeline;
import stroom.pipeline.factory.PipelineDataCache;
import stroom.pipeline.factory.PipelineFactory;
import stroom.pipeline.parser.CombinedParser;
import stroom.pipeline.scope.PipelineScopeRunnable;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.TextConverterDoc.TextConverterType;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineDataUtil;
import stroom.pipeline.state.RecordCount;
import stroom.pipeline.textconverter.TextConverterStore;
import stroom.pipeline.xslt.XsltStore;
import stroom.test.AbstractProcessIntegrationTest;
import stroom.test.StroomPipelineTestFileUtil;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import javax.inject.Provider;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

abstract class AbstractAppenderTest extends AbstractProcessIntegrationTest {
    @Inject
    private Provider<PipelineFactory> pipelineFactoryProvider;
    @Inject
    private Provider<ErrorReceiverProxy> errorReceiverProvider;
    @Inject
    private Provider<RecordCount> recordCountProvider;
    @Inject
    private XsltStore xsltStore;
    @Inject
    private TextConverterStore textConverterStore;
    @Inject
    private PipelineStore pipelineStore;
    @Inject
    private PipelineDataCache pipelineDataCache;
    @Inject
    private RollingDestinations destinations;
    @Inject
    private Provider<StreamCloser> streamCloserProvider;
    @Inject
    private PipelineScopeRunnable pipelineScopeRunnable;
    @Inject
    private Store streamStore;
    @Inject
    private MetaService dataMetaService;

    private LoggingErrorReceiver loggingErrorReceiver;

    void test(final String name, final String type) {
        // Delete everything in the temp dir.
        FileUtil.deleteContents(FileUtil.getTempDir());

        final String dir = name + "/";
        final String stem = dir + name + "_" + type;
        final DocRef textConverterRef = createTextConverter(dir + name + ".ds3.xml", name, TextConverterType.DATA_SPLITTER);
        final DocRef filteredXSLT = createXSLT(stem + ".xsl", name);
        final DocRef pipelineRef = createPipeline(stem + "_Pipeline.xml", textConverterRef, filteredXSLT);

        pipelineScopeRunnable.scopeRunnable(() -> {
            process(pipelineRef, dir, name, null);
            validateProcess();
        });
    }

    private DocRef createPipeline(final String pipelineFile,
                                  final DocRef textConverterRef,
                                  final DocRef xsltRef) {
        // Load the pipeline config.
        final String data = StroomPipelineTestFileUtil.getString(pipelineFile);
        final DocRef pipelineRef = PipelineTestUtil.createTestPipeline(pipelineStore, data);
        final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipelineRef);

        if (textConverterRef != null) {
            pipelineDoc.getPipelineData().addProperty(
                    PipelineDataUtil.createProperty(CombinedParser.DEFAULT_NAME, "textConverter", textConverterRef));
        }
        if (xsltRef != null) {
            pipelineDoc.getPipelineData()
                    .addProperty(PipelineDataUtil.createProperty("translationFilter", "xslt", xsltRef));
        }

        pipelineStore.writeDocument(pipelineDoc);
        return pipelineRef;
    }

    private DocRef createTextConverter(final String textConverterFile, final String name,
                                       final TextConverterType textConverterType) {
        // Create a record for the TextConverter.
        final InputStream textConverterInputStream = StroomPipelineTestFileUtil.getInputStream(textConverterFile);
        final DocRef textConverterRef = textConverterStore.createDocument(name);
        TextConverterDoc textConverter = textConverterStore.readDocument(textConverterRef);
        textConverter.setConverterType(textConverterType);
        textConverter.setData(StreamUtil.streamToString(textConverterInputStream));
        textConverterStore.writeDocument(textConverter);
        return textConverterRef;
    }

    private DocRef createXSLT(final String xsltPath, final String name) {
        // Create a record for the XSLT.
        final InputStream xsltInputStream = StroomPipelineTestFileUtil.getInputStream(xsltPath);
        final DocRef xsltRef = xsltStore.createDocument(name);
        final XsltDoc xsltDoc = xsltStore.readDocument(xsltRef);
        xsltDoc.setData(StreamUtil.streamToString(xsltInputStream));
        xsltStore.writeDocument(xsltDoc);
        return xsltRef;
    }

    // TODO This method is 80% the same in a whole bunch of test classes -
    // refactor some of the repetition out.
    void process(final DocRef pipelineRef,
                 final String dir,
                 final String name,
                 final String encoding) {
        try {
            // Setup the error handler.
            loggingErrorReceiver = new LoggingErrorReceiver();
            errorReceiverProvider.get().setErrorReceiver(loggingErrorReceiver);

            // Create the parser.
            final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipelineRef);
            final PipelineData pipelineData = pipelineDataCache.get(pipelineDoc);
            final Pipeline pipeline = pipelineFactoryProvider.get().create(pipelineData);

            // Get the input streams.
            final Path inputDir = StroomPipelineTestFileUtil.getTestResourcesDir().resolve(dir);
            assertThat(Files.isDirectory(inputDir)).as("Can't find input dir").isTrue();

            final List<Path> inputFiles = new ArrayList<>();
            try (final DirectoryStream<Path> stream = Files.newDirectoryStream(inputDir, name + "*.in")) {
                stream.forEach(inputFiles::add);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
            inputFiles.sort(Comparator.naturalOrder());

            assertThat(inputFiles.size() > 0).as("Can't find any input files").isTrue();

            pipeline.startProcessing();

            for (final Path inputFile : inputFiles) {
                final InputStream inputStream = new BufferedInputStream(Files.newInputStream(inputFile));
                pipeline.process(inputStream, encoding);
                inputStream.close();
            }

            pipeline.endProcessing();

            // Close all streams that have been written.
            streamCloserProvider.get().close();

            // FORCE ROLL SO WE CAN GET OUTPUT
            destinations.forceRoll();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    void validateProcess() {
        final RecordCount recordCount = recordCountProvider.get();
        assertThat(recordCount.getRead() > 0).isTrue();
        assertThat(recordCount.getWritten() > 0).isTrue();
        // assertThat(recordCount.getWritten()).isEqualTo(recordCount.getRead());
        assertThat(recordCount.getRead()).isEqualTo(200);
        assertThat(recordCount.getWritten()).isEqualTo(200 - 59);
        assertThat(loggingErrorReceiver.getRecords(Severity.WARNING)).isEqualTo(0);
        assertThat(loggingErrorReceiver.getRecords(Severity.ERROR)).isEqualTo(59);
        assertThat(loggingErrorReceiver.getRecords(Severity.FATAL_ERROR)).isEqualTo(0);
    }

    void validateOuptut(final String outputReference,
                        final String type) {
        try {
            final List<Meta> list = dataMetaService.find(new FindMetaCriteria());
            assertThat(list.size()).isEqualTo(1);

            final long id = list.get(0).getId();
            checkOuterData(id, type.equalsIgnoreCase("text"));
            checkInnerData(id, type.equalsIgnoreCase("text"));
            checkFull(id, outputReference);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void checkInnerData(final long streamId, final boolean text) throws IOException {
        if (text) {
            final String innerRef = "2013-04-09T00:00:50.000ZTestTestApachetest.test.com123.123.123.123firstuser1234/goodGETHTTP/1.0someagent200\n" +
                    "2013-04-09T00:00:50.000ZTestTestApachetest.test.com123.123.123.123lastuser1234/goodGETHTTP/1.0someagent200\n";

            checkInnerData(streamId, 143, innerRef);

        } else {
            final String innerRef = "<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n" +
                    "<Events xmlns=\"event-logging:3\"\n" +
                    "        xmlns:stroom=\"stroom\"\n" +
                    "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                    "        xsi:schemaLocation=\"event-logging:3 file://event-logging-v3.0.0.xsd\"\n" +
                    "        Version=\"3.0.0\">\n" +
                    "   <Event>\n" +
                    "      <EventTime>\n" +
                    "         <TimeCreated>2013-04-09T00:00:50.000Z</TimeCreated>\n" +
                    "      </EventTime>\n" +
                    "      <EventSource>\n" +
                    "         <System>\n" +
                    "            <Name>Test</Name>\n" +
                    "            <Environment>Test</Environment>\n" +
                    "         </System>\n" +
                    "         <Generator>Apache</Generator>\n" +
                    "         <Device>\n" +
                    "            <HostName>test.test.com</HostName>\n" +
                    "         </Device>\n" +
                    "         <Client>\n" +
                    "            <IPAddress>123.123.123.123</IPAddress>\n" +
                    "         </Client>\n" +
                    "         <User>\n" +
                    "            <Id>firstuser</Id>\n" +
                    "         </User>\n" +
                    "      </EventSource>\n" +
                    "      <EventDetail>\n" +
                    "         <TypeId>1234</TypeId>\n" +
                    "         <View>\n" +
                    "            <Resource>\n" +
                    "               <URL>/good</URL>\n" +
                    "               <HTTPMethod>GET</HTTPMethod>\n" +
                    "               <HTTPVersion>HTTP/1.0</HTTPVersion>\n" +
                    "               <UserAgent>someagent</UserAgent>\n" +
                    "               <ResponseCode>200</ResponseCode>\n" +
                    "            </Resource>\n" +
                    "         </View>\n" +
                    "      </EventDetail>\n" +
                    "   </Event>\n" +
                    "   <Event>\n" +
                    "      <EventTime>\n" +
                    "         <TimeCreated>2013-04-09T00:00:50.000Z</TimeCreated>\n" +
                    "      </EventTime>\n" +
                    "      <EventSource>\n" +
                    "         <System>\n" +
                    "            <Name>Test</Name>\n" +
                    "            <Environment>Test</Environment>\n" +
                    "         </System>\n" +
                    "         <Generator>Apache</Generator>\n" +
                    "         <Device>\n" +
                    "            <HostName>test.test.com</HostName>\n" +
                    "         </Device>\n" +
                    "         <Client>\n" +
                    "            <IPAddress>123.123.123.123</IPAddress>\n" +
                    "         </Client>\n" +
                    "         <User>\n" +
                    "            <Id>lastuser</Id>\n" +
                    "         </User>\n" +
                    "      </EventSource>\n" +
                    "      <EventDetail>\n" +
                    "         <TypeId>1234</TypeId>\n" +
                    "         <View>\n" +
                    "            <Resource>\n" +
                    "               <URL>/good</URL>\n" +
                    "               <HTTPMethod>GET</HTTPMethod>\n" +
                    "               <HTTPVersion>HTTP/1.0</HTTPVersion>\n" +
                    "               <UserAgent>someagent</UserAgent>\n" +
                    "               <ResponseCode>200</ResponseCode>\n" +
                    "            </Resource>\n" +
                    "         </View>\n" +
                    "      </EventDetail>\n" +
                    "   </Event>\n" +
                    "</Events>\n";

            checkInnerData(streamId, 143, innerRef);
        }
    }

    private void checkOuterData(final long streamId, final boolean text) throws IOException {
        if (text) {
            final String outerRef = "";

            checkOuterData(streamId, 143, outerRef);

        } else {
            final String outerRef = "<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n" +
                    "<Events xmlns=\"event-logging:3\"\n" +
                    "        xmlns:stroom=\"stroom\"\n" +
                    "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                    "        xsi:schemaLocation=\"event-logging:3 file://event-logging-v3.0.0.xsd\"\n" +
                    "        Version=\"3.0.0\">\n" +
                    "</Events>\n";

            checkOuterData(streamId, 143, outerRef);
        }
    }

    private void checkFull(final long streamId, final String outputReference) throws IOException {
        try (final Source streamSource = streamStore.openStreamSource(streamId)) {
            final Path refFile = StroomPipelineTestFileUtil.getTestResourcesFile(outputReference);
            final String refData = StreamUtil.fileToString(refFile);
            final String data = SourceUtil.readString(streamSource);
            assertThat(data).isEqualTo(refData);
        }
    }

    private void checkOuterData(final long streamId, final int count, final String ref) throws IOException {
        try (final Source streamSource = streamStore.openStreamSource(streamId)) {
            try (final InputStreamProvider inputStreamProvider = streamSource.get(0)) {
                final SegmentInputStream segmentInputStream = inputStreamProvider.get();

                assertThat(segmentInputStream.count()).isEqualTo(count);

                // Include the first and last segment only.
                segmentInputStream.include(0);
                segmentInputStream.include(segmentInputStream.count() - 1);

                final String data = StreamUtil.streamToString(segmentInputStream);
                assertThat(data).isEqualTo(ref);
            }
        }
    }

    private void checkInnerData(final long streamId, final int count, final String ref) throws IOException {
        try (final Source streamSource = streamStore.openStreamSource(streamId)) {
            try (final InputStreamProvider inputStreamProvider = streamSource.get(0)) {
                final SegmentInputStream segmentInputStream = inputStreamProvider.get();

                assertThat(segmentInputStream.count()).isEqualTo(count);

                // Include the first and last segment only.
                segmentInputStream.include(0);
                segmentInputStream.include(1);
                segmentInputStream.include(segmentInputStream.count() - 2);
                segmentInputStream.include(segmentInputStream.count() - 1);

                final String data = StreamUtil.streamToString(segmentInputStream);
                assertThat(data).isEqualTo(ref);
            }
        }
    }
}
