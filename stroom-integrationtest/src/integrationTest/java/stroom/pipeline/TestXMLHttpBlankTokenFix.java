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
import stroom.pipeline.errorhandler.LoggingErrorReceiver;
import stroom.pipeline.factory.Pipeline;
import stroom.pipeline.factory.PipelineDataCache;
import stroom.pipeline.factory.PipelineFactory;
import stroom.pipeline.parser.CombinedParser;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.TextConverterDoc.TextConverterType;
import stroom.pipeline.shared.XSLT;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineDataUtil;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.RecordCount;
import stroom.query.api.v2.DocRef;
import stroom.test.AbstractProcessIntegrationTest;
import stroom.test.StroomPipelineTestFileUtil;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import javax.inject.Provider;
import java.io.InputStream;
import java.nio.file.Path;

// FIXME : reinstate test
@Ignore("Removed test data")
public class TestXMLHttpBlankTokenFix extends AbstractProcessIntegrationTest {
    private static final int EXPECTED_RESULTS = 4;
    private static final String PIPELINE = "XMLHttpBlankTokenFix/XMLHttpBlankTokenFix.Pipeline.data.xml";
    private static final String INPUT = "XMLHttpBlankTokenFix/HttpProblem.in";
    private static final String FORMAT = "XMLHttpBlankTokenFix/HttpSplitterWithBlankTokenFix.TextConverter.data.xml";
    private static final String XSLT_LOCATION = "XMLHttpBlankTokenFix/HttpProblem.xsl";

    @Inject
    private Provider<PipelineFactory> pipelineFactoryProvider;
    @Inject
    private Provider<RecordCount> recordCountProvider;
    @Inject
    private Provider<ErrorReceiverProxy> errorReceiverProvider;
    @Inject
    private Provider<FeedHolder> feedHolderProvider;
    @Inject
    private TextConverterStore textConverterStore;
    @Inject
    private XSLTService xsltService;
    @Inject
    private PipelineService pipelineService;
    @Inject
    private PipelineMarshaller pipelineMarshaller;
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

            // Setup the text converter.
            final InputStream textConverterInputStream = StroomPipelineTestFileUtil.getInputStream(FORMAT);
            final DocRef textConverterRef = textConverterStore.createDocument("Test Text Converter");
            final TextConverterDoc textConverter = textConverterStore.readDocument(textConverterRef);
            textConverter.setConverterType(TextConverterType.DATA_SPLITTER);
            textConverter.setData(StreamUtil.streamToString(textConverterInputStream));
            textConverterStore.update(textConverter);

            // Setup the XSLT.
            final InputStream xsltInputStream = StroomPipelineTestFileUtil.getInputStream(XSLT_LOCATION);
            XSLT xslt = new XSLT();
            xslt.setName("Test");
            xslt.setData(StreamUtil.streamToString(xsltInputStream));
            xslt = xsltService.save(xslt);

            final Path testDir = getCurrentTestDir();

            // Make sure the config dir is set.
            System.setProperty("stroom.temp", FileUtil.getCanonicalPath(testDir));

            // Delete any output file.
            final Path outputFile = testDir.resolve("XMLHttpBlankTokenFix.xml");
            final Path outputLockFile = testDir.resolve("XMLHttpBlankTokenFix.xml.lock");
            FileUtil.deleteFile(outputFile);
            FileUtil.deleteFile(outputLockFile);

            // Setup the error handler.
            final LoggingErrorReceiver loggingErrorReceiver = new LoggingErrorReceiver();
            errorReceiver.setErrorReceiver(loggingErrorReceiver);

            // Create the pipeline.
            PipelineEntity pipelineEntity = PipelineTestUtil.createTestPipeline(pipelineService,
                    StroomPipelineTestFileUtil.getString(PIPELINE));
            pipelineEntity.getPipelineData().addProperty(
                    PipelineDataUtil.createProperty(CombinedParser.DEFAULT_NAME, "textConverter", textConverterRef));
            pipelineEntity.getPipelineData()
                    .addProperty(PipelineDataUtil.createProperty("translationFilter", "xslt", xslt));
            pipelineEntity = pipelineService.save(pipelineEntity);

            // Create the parser.
            final PipelineData pipelineData = pipelineDataCache.get(pipelineEntity);
            final Pipeline pipeline = pipelineFactory.create(pipelineData);

            feedHolder.setFeed(new Feed());

            // Set the input file to transform.
            final InputStream input = StroomPipelineTestFileUtil.getInputStream(INPUT);
            pipeline.process(input);

            final String xml = StreamUtil.fileToString(outputFile);
            System.out.println(xml);

            Assert.assertTrue(errorReceiver.toString(), recordCount.getRead() > 0);
            Assert.assertTrue(errorReceiver.toString(), recordCount.getWritten() > 0);
            Assert.assertEquals(errorReceiver.toString(), recordCount.getRead(), recordCount.getWritten());
            Assert.assertEquals(errorReceiver.toString(), EXPECTED_RESULTS, recordCount.getRead());
            Assert.assertEquals(errorReceiver.toString(), EXPECTED_RESULTS, recordCount.getWritten());
            Assert.assertEquals(errorReceiver.toString(), 0, loggingErrorReceiver.getRecords(Severity.WARNING));
            Assert.assertEquals(errorReceiver.toString(), 0, loggingErrorReceiver.getRecords(Severity.ERROR));
            Assert.assertEquals(errorReceiver.toString(), 0, loggingErrorReceiver.getRecords(Severity.FATAL_ERROR));

            if (!loggingErrorReceiver.isAllOk()) {
                Assert.fail(errorReceiver.toString());
            }
        });
    }
}
