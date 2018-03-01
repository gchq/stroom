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
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.RecordErrorReceiver;
import stroom.pipeline.factory.Pipeline;
import stroom.pipeline.factory.PipelineDataCache;
import stroom.pipeline.factory.PipelineFactory;
import stroom.pipeline.parser.CombinedParser;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.TextConverter;
import stroom.pipeline.shared.TextConverter.TextConverterType;
import stroom.pipeline.shared.XSLT;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineDataUtil;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.RecordCount;
import stroom.test.AbstractProcessIntegrationTest;
import stroom.test.StroomPipelineTestFileUtil;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.shared.Severity;

import javax.inject.Inject;
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
    private PipelineFactory pipelineFactory;
    @Inject
    private ErrorReceiverProxy errorReceiver;
    @Inject
    private RecordCount recordCount;
    @Inject
    private RecordErrorReceiver recordErrorReceiver;
    @Inject
    private FeedHolder feedHolder;
    @Inject
    private TextConverterService textConverterService;
    @Inject
    private XSLTService xsltService;
    @Inject
    private PipelineService pipelineService;
    @Inject
    private PipelineMarshaller pipelineMarshaller;
    @Inject
    private PipelineDataCache pipelineDataCache;

    /**
     * Tests the XMLTransformer on some sample Windows XML events.
     *
     * @throws Exception Could be thrown while running the test.
     */
    @Test
    public void testXMLTransformer() throws Exception {
        // Setup the text converter.
        final InputStream textConverterInputStream = StroomPipelineTestFileUtil.getInputStream(FORMAT);
        TextConverter textConverter = new TextConverter();
        textConverter.setName("Test Text Converter");
        textConverter.setConverterType(TextConverterType.DATA_SPLITTER);
        textConverter.setData(StreamUtil.streamToString(textConverterInputStream));
        textConverter = textConverterService.save(textConverter);

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
        final Path outputFile = testDir.resolve("XMLWithErrorsInTransform.xml");
        FileUtil.deleteFile(outputFile);

        // Setup the error handler.
        errorReceiver.setErrorReceiver(recordErrorReceiver);

        // Create the parser.
        PipelineEntity pipelineEntity = PipelineTestUtil.createTestPipeline(pipelineService, StroomPipelineTestFileUtil.getString(PIPELINE));
        pipelineEntity.getPipelineData().addProperty(
                PipelineDataUtil.createProperty(CombinedParser.DEFAULT_NAME, "textConverter", textConverter));
        pipelineEntity.getPipelineData()
                .addProperty(PipelineDataUtil.createProperty("translationFilter", "xslt", xslt));
        pipelineEntity = pipelineService.save(pipelineEntity);

        final PipelineData pipelineData = pipelineDataCache.get(pipelineEntity);
        final Pipeline pipeline = pipelineFactory.create(pipelineData);

        feedHolder.setFeed(new Feed());

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
    }
}
