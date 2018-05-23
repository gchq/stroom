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

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import stroom.feed.shared.Feed;
import stroom.guice.PipelineScopeRunnable;
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
import stroom.pipeline.shared.data.PipelineDataUtil;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.RecordCount;
import stroom.pipeline.task.StreamMetaDataProvider;
import stroom.docref.DocRef;
import stroom.test.AbstractProcessIntegrationTest;
import stroom.test.StroomPipelineTestFileUtil;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import javax.inject.Provider;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

// TODO : Add test data
@Ignore("Make new test data")
public class TestXMLWithErrorsInTransform extends AbstractProcessIntegrationTest {
    private static final int N4 = 4;
    private static final String PIPELINE = "XMLWithErrorsInTransform/XMLWithErrorsInTransform.Pipeline.data.xml";
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
    public void testXMLTransformer() {
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

            // Make sure the config dir is set.
            System.setProperty("stroom.temp", FileUtil.getCanonicalPath(testDir));

            // Delete any output file.
            final Path outputFile = testDir.resolve("XMLWithErrorsInTransform.xml");
            FileUtil.deleteFile(outputFile);

            // Setup the error handler.
            errorReceiver.setErrorReceiver(recordErrorReceiver);

            // Create the parser.
            final DocRef pipelineRef = PipelineTestUtil.createTestPipeline(pipelineStore, StroomPipelineTestFileUtil.getString(PIPELINE));
            final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipelineRef);
            pipelineDoc.getPipelineData().addProperty(
                    PipelineDataUtil.createProperty(CombinedParser.DEFAULT_NAME, "textConverter", textConverterRef));
            pipelineDoc.getPipelineData()
                    .addProperty(PipelineDataUtil.createProperty("translationFilter", "xslt", xsltRef));
            pipelineStore.writeDocument(pipelineDoc);

            final PipelineData pipelineData = pipelineDataCache.get(pipelineDoc);
            final Pipeline pipeline = pipelineFactory.create(pipelineData);

//            feedHolder.setFeedName("");
//
//            // Setup the meta data holder.
//            metaDataHolder.setMetaDataProvider(new StreamMetaDataProvider(streamHolder, streamProcessorService, pipelineStore));

            // Set the input.
            final InputStream input = StroomPipelineTestFileUtil.getInputStream(INPUT);
            pipeline.process(input);

            Assert.assertEquals(errorReceiver.toString(), N4, recordCount.getRead());
            Assert.assertEquals(errorReceiver.toString(), 0, recordCount.getWritten());
            Assert.assertEquals(errorReceiver.toString(), N4, recordErrorReceiver.getRecords(Severity.WARNING));
            Assert.assertEquals(errorReceiver.toString(), N4, recordErrorReceiver.getRecords(Severity.ERROR));

            // Make sure no output file was produced.
            Assert.assertTrue(!Files.isRegularFile(outputFile));

            if (recordErrorReceiver.isAllOk()) {
                Assert.fail("Expecting to fail the schema");
            }
        });
    }
}
