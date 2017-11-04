/*
 * Copyright 2016 Crown Copyright
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

package stroom.pipeline.server;

import stroom.AbstractProcessIntegrationTest;
import stroom.feed.shared.Feed;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.errorhandler.LoggingErrorReceiver;
import stroom.pipeline.server.factory.Pipeline;
import stroom.pipeline.server.factory.PipelineDataCache;
import stroom.pipeline.server.factory.PipelineFactory;
import stroom.pipeline.server.parser.CombinedParser;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.PipelineEntityService;
import stroom.pipeline.shared.TextConverter;
import stroom.pipeline.shared.TextConverter.TextConverterType;
import stroom.pipeline.shared.TextConverterService;
import stroom.pipeline.shared.XSLT;
import stroom.pipeline.shared.XSLTService;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineDataUtil;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.RecordCount;
import stroom.test.StroomProcessTestFileUtil;
import stroom.test.PipelineTestUtil;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.shared.Severity;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import javax.annotation.Resource;
import java.io.File;
import java.io.InputStream;

// FIXME : reinstate test
@Ignore("Removed test data")
public class TestXMLHttpBlankTokenFix extends AbstractProcessIntegrationTest {
    private static final int EXPECTED_RESULTS = 4;
    private static final String PIPELINE = "XMLHttpBlankTokenFix/XMLHttpBlankTokenFix.Pipeline.data.xml";
    private static final String INPUT = "XMLHttpBlankTokenFix/HttpProblem.in";
    private static final String FORMAT = "XMLHttpBlankTokenFix/HttpSplitterWithBlankTokenFix.TextConverter.data.xml";
    private static final String XSLT_LOCATION = "XMLHttpBlankTokenFix/HttpProblem.xsl";

    @Resource
    private PipelineFactory pipelineFactory;
    @Resource
    private RecordCount recordCount;
    @Resource
    private ErrorReceiverProxy errorReceiver;
    @Resource
    private FeedHolder feedHolder;
    @Resource
    private TextConverterService textConverterService;
    @Resource
    private XSLTService xsltService;
    @Resource
    private PipelineEntityService pipelineEntityService;
    @Resource
    private PipelineMarshaller pipelineMarshaller;
    @Resource
    private PipelineDataCache pipelineDataCache;

    /**
     * Tests the XMLTransformer on some sample Windows XML events.
     *
     * @throws Exception
     *             Could be thrown while running the test.
     */
    @Test
    public void testXMLTransformer() throws Exception {
        // Setup the text converter.
        final InputStream textConverterInputStream = StroomProcessTestFileUtil.getInputStream(FORMAT);
        TextConverter textConverter = new TextConverter();
        textConverter.setName("Test Text Converter");
        textConverter.setConverterType(TextConverterType.DATA_SPLITTER);
        textConverter.setData(StreamUtil.streamToString(textConverterInputStream));
        textConverter = textConverterService.save(textConverter);

        // Setup the XSLT.
        final InputStream xsltInputStream = StroomProcessTestFileUtil.getInputStream(XSLT_LOCATION);
        XSLT xslt = new XSLT();
        xslt.setName("Test");
        xslt.setData(StreamUtil.streamToString(xsltInputStream));
        xslt = xsltService.save(xslt);

        final File testDir = getCurrentTestDir();

        // Make sure the config dir is set.
        System.setProperty("stroom.temp", testDir.getCanonicalPath());

        // Delete any output file.
        final File outputFile = new File(testDir, "XMLHttpBlankTokenFix.xml");
        final File outputLockFile = new File(testDir, "XMLHttpBlankTokenFix.xml.lock");
        FileUtil.deleteFile(outputFile);
        FileUtil.deleteFile(outputLockFile);

        // Setup the error handler.
        final LoggingErrorReceiver loggingErrorReceiver = new LoggingErrorReceiver();
        errorReceiver.setErrorReceiver(loggingErrorReceiver);

        // Create the pipeline.
        PipelineEntity pipelineEntity = PipelineTestUtil.createTestPipeline(pipelineEntityService, pipelineMarshaller,
                StroomProcessTestFileUtil.getString(PIPELINE));
        pipelineEntity.getPipelineData().addProperty(
                PipelineDataUtil.createProperty(CombinedParser.DEFAULT_NAME, "textConverter", textConverter));
        pipelineEntity.getPipelineData()
                .addProperty(PipelineDataUtil.createProperty("translationFilter", "xslt", xslt));
        pipelineEntity = pipelineEntityService.save(pipelineEntity);

        // Create the parser.
        final PipelineData pipelineData = pipelineDataCache.get(pipelineEntity);
        final Pipeline pipeline = pipelineFactory.create(pipelineData);

        feedHolder.setFeed(new Feed());

        // Set the input file to transform.
        final InputStream input = StroomProcessTestFileUtil.getInputStream(INPUT);
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
    }
}
