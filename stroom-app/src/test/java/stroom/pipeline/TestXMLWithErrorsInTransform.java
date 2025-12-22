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

package stroom.pipeline;


import stroom.docref.DocRef;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.RecordErrorReceiver;
import stroom.pipeline.factory.Pipeline;
import stroom.pipeline.factory.PipelineDataCache;
import stroom.pipeline.factory.PipelineFactory;
import stroom.pipeline.parser.CombinedParser;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.TextConverterDoc.TextConverterType;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineDataUtil;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.RecordCount;
import stroom.pipeline.textconverter.TextConverterStore;
import stroom.pipeline.xslt.XsltStore;
import stroom.task.api.SimpleTaskContext;
import stroom.test.AbstractProcessIntegrationTest;
import stroom.test.common.StroomPipelineTestFileUtil;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.pipeline.scope.PipelineScopeRunnable;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

// TODO : Add test data
@Disabled("Make new test data")
class TestXMLWithErrorsInTransform extends AbstractProcessIntegrationTest {

    private static final int N4 = 4;
    private static final String PIPELINE = "XMLWithErrorsInTransform/XMLWithErrorsInTransform.Pipeline.json";
    private static final String INPUT = "XMLWithErrorsInTransform/HttpProblem.in";
    private static final String FORMAT = "XMLWithErrorsInTransform/HttpSplitter.xml";
    private static final String XSLT_LOCATION = "XMLWithErrorsInTransform/HttpProblem.xsl";

    @Inject
    private Provider<PipelineFactory> pipelineFactoryProvider;
    @Inject
    private Provider<RecordCount> recordCountProvider;
    @Inject
    private Provider<ErrorReceiverProxy> errorReceiverProvider;
    @Inject
    private Provider<FeedHolder> feedHolderProvider;
    @Inject
    private Provider<RecordErrorReceiver> recordErrorReceiverProvider;
    @Inject
    private TextConverterStore textConverterStore;
    @Inject
    private XsltStore xsltStore;
    @Inject
    private PipelineStore pipelineStore;
    @Inject
    private PipelineDataCache pipelineDataCache;
    @Inject
    private PipelineScopeRunnable pipelineScopeRunnable;

    /**
     * Tests the XMLTransformer on some sample Windows XML events.
     */
    @Test
    void testXMLTransformer() {
        pipelineScopeRunnable.scopeRunnable(() -> {
            final PipelineFactory pipelineFactory = pipelineFactoryProvider.get();
            final RecordCount recordCount = recordCountProvider.get();
            final ErrorReceiverProxy errorReceiver = errorReceiverProvider.get();
            final FeedHolder feedHolder = feedHolderProvider.get();
            final RecordErrorReceiver recordErrorReceiver = recordErrorReceiverProvider.get();

            // Setup the text converter.
            final InputStream textConverterInputStream = StroomPipelineTestFileUtil.getInputStream(FORMAT);
            final DocRef textConverterRef = textConverterStore.createDocument("Test Text Converter");
            final TextConverterDoc textConverter = textConverterStore.readDocument(textConverterRef);
            textConverter.setConverterType(TextConverterType.DATA_SPLITTER);
            textConverter.setData(StreamUtil.streamToString(textConverterInputStream));
            textConverterStore.writeDocument(textConverter);

            // Setup the XSLT.
            final InputStream xsltInputStream = StroomPipelineTestFileUtil.getInputStream(XSLT_LOCATION);
            final DocRef xsltRef = xsltStore.createDocument("Test");
            final XsltDoc xsltDoc = xsltStore.readDocument(xsltRef);
            xsltDoc.setData(StreamUtil.streamToString(xsltInputStream));
            xsltStore.writeDocument(xsltDoc);

            final Path testDir = getCurrentTestDir();

            // Delete any output file.
            final Path outputFile = testDir.resolve("XMLWithErrorsInTransform.xml");
            FileUtil.deleteFile(outputFile);

            // Setup the error handler.
            errorReceiver.setErrorReceiver(recordErrorReceiver);

            // Create the parser.
            final DocRef pipelineRef = PipelineTestUtil.createTestPipeline(pipelineStore,
                    StroomPipelineTestFileUtil.getString(PIPELINE));
            final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipelineRef);
            final PipelineDataBuilder builder = new PipelineDataBuilder(pipelineDoc.getPipelineData());
            builder.addProperty(
                    PipelineDataUtil.createProperty(CombinedParser.DEFAULT_NAME, "textConverter", textConverterRef));
            builder.addProperty(
                    PipelineDataUtil.createProperty("translationFilter", "xslt", xsltRef));
            pipelineDoc.setPipelineData(builder.build());
            pipelineStore.writeDocument(pipelineDoc);

            final PipelineData pipelineData = pipelineDataCache.get(pipelineDoc);
            final Pipeline pipeline = pipelineFactory.create(pipelineData, new SimpleTaskContext());

//            feedHolder.setFeedName("");
//
//            // Setup the meta data holder.
//            metaDataHolder.setMetaDataProvider(new StreamMetaDataProvider(
//            metaHolder, streamProcessorService, pipelineStore));

            // Set the input.
            final InputStream input = StroomPipelineTestFileUtil.getInputStream(INPUT);
            pipeline.process(input);

            assertThat(recordCount.getRead())
                    .as(errorReceiver.toString())
                    .isEqualTo(N4);
            assertThat(recordCount.getWritten())
                    .as(errorReceiver.toString())
                    .isEqualTo(0);
            assertThat(recordErrorReceiver.getRecords(Severity.WARNING))
                    .as(errorReceiver.toString())
                    .isEqualTo(N4);
            assertThat(recordErrorReceiver.getRecords(Severity.ERROR))
                    .as(errorReceiver.toString())
                    .isEqualTo(N4);

            // Make sure no output file was produced.
            assertThat(!Files.isRegularFile(outputFile))
                    .isTrue();

            if (recordErrorReceiver.isAllOk()) {
                fail("Expecting to fail the schema");
            }
        });
    }
}
