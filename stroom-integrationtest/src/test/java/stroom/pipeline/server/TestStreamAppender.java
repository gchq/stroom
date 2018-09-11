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

package stroom.pipeline.server;

import org.junit.Assert;
import org.junit.Test;
import stroom.AbstractProcessIntegrationTest;
import stroom.CommonTestScenarioCreator;
import stroom.feed.shared.Feed;
import stroom.feed.shared.FeedService;
import stroom.io.StreamCloser;
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
import stroom.pipeline.state.RecordCount;
import stroom.streamstore.server.StreamSource;
import stroom.streamstore.server.StreamStore;
import stroom.streamstore.server.fs.serializable.RASegmentInputStream;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.Stream;
import stroom.test.PipelineTestUtil;
import stroom.test.StroomProcessTestFileUtil;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.shared.Severity;

import javax.annotation.Resource;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

public class TestStreamAppender extends AbstractProcessIntegrationTest {
    @Resource
    private PipelineFactory pipelineFactory;
    @Resource
    private ErrorReceiverProxy errorReceiver;
    @Resource
    private RecordCount recordCount;
    @Resource
    private XSLTService xsltService;
    @Resource
    private TextConverterService textConverterService;
    @Resource
    private PipelineEntityService pipelineEntityService;
    @Resource
    private PipelineMarshaller pipelineMarshaller;
    @Resource
    private PipelineDataCache pipelineDataCache;
    @Resource
    private StreamStore streamStore;
    @Resource
    private FeedService feedService;
    @Resource
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Resource
    private StreamCloser streamCloser;

    @Test
    public void testXML() throws Exception {
        final Feed feed = commonTestScenarioCreator.createSimpleFeed("TEST", "12345");
        final String dir = "TestStreamAppender/";
        final TextConverter textConverter = createTextConverter(dir + "TestStreamAppender.ds3.xml",
                "TestStreamAppender", TextConverterType.DATA_SPLITTER);
        final XSLT filteredXSLT = createXSLT(dir + "TestStreamAppender.xsl", "TestStreamAppender");
        final PipelineEntity pipelineEntity = createPipeline(dir + "TestStreamAppender_XML_Pipeline.xml",
                textConverter, filteredXSLT);
        test(pipelineEntity, dir, "TestStreamAppender", dir + "TestStreamAppender.xml.out", null, false, 1);
    }

    @Test
    public void testXMLRolling() throws Exception {
        final Feed feed = commonTestScenarioCreator.createSimpleFeed("TEST", "12345");
        final String dir = "TestStreamAppender/";
        final TextConverter textConverter = createTextConverter(dir + "TestStreamAppender.ds3.xml",
                "TestStreamAppender", TextConverterType.DATA_SPLITTER);
        final XSLT filteredXSLT = createXSLT(dir + "TestStreamAppender.xsl", "TestStreamAppender");
        final PipelineEntity pipelineEntity = createPipeline(dir + "TestStreamAppender_XML_Pipeline_Rolling.xml",
                textConverter, filteredXSLT);
        test(pipelineEntity, dir, "TestStreamAppender", dir + "TestStreamAppender.xml.out", null, false, 141);
    }

    @Test
    public void testText() throws Exception {
        final Feed feed = commonTestScenarioCreator.createSimpleFeed("TEST", "12345");
        final String dir = "TestStreamAppender/";
        final TextConverter textConverter = createTextConverter(dir + "TestStreamAppender.ds3.xml",
                "TestStreamAppender", TextConverterType.DATA_SPLITTER);
        final XSLT filteredXSLT = createXSLT(dir + "TestStreamAppender_Text.xsl", "TestStreamAppender");
        final PipelineEntity pipelineEntity = createPipeline(dir + "TestStreamAppender_Text_Pipeline.xml",
                textConverter, filteredXSLT);
        test(pipelineEntity, dir, "TestStreamAppender", dir + "TestStreamAppender.txt.out", null, true, 1);
    }

    private PipelineEntity createPipeline(final String pipelineFile, final TextConverter textConverter,
                                          final XSLT xslt) {
        // Load the pipeline config.
        final String data = StroomProcessTestFileUtil.getString(pipelineFile);
        final PipelineEntity pipelineEntity = PipelineTestUtil.createTestPipeline(pipelineEntityService, pipelineMarshaller, data);

        if (textConverter != null) {
            pipelineEntity.getPipelineData().addProperty(
                    PipelineDataUtil.createProperty(CombinedParser.DEFAULT_NAME, "textConverter", textConverter));
        }
        if (xslt != null) {
            pipelineEntity.getPipelineData()
                    .addProperty(PipelineDataUtil.createProperty("translationFilter", "xslt", xslt));
        }

        return pipelineEntityService.save(pipelineEntity);
    }

    private TextConverter createTextConverter(final String textConverterFile, final String name,
                                              final TextConverterType textConverterType) {
        // Create a record for the TextConverter.
        final InputStream textConverterInputStream = StroomProcessTestFileUtil.getInputStream(textConverterFile);
        TextConverter textConverter = textConverterService.create(null, name);
        textConverter.setConverterType(textConverterType);
        textConverter.setData(StreamUtil.streamToString(textConverterInputStream));
        textConverter = textConverterService.save(textConverter);
        return textConverter;
    }

    private XSLT createXSLT(final String xsltPath, final String name) {
        // Create a record for the XSLT.
        final InputStream xsltInputStream = StroomProcessTestFileUtil.getInputStream(xsltPath);
        XSLT xslt = xsltService.create(null, name);
        xslt.setData(StreamUtil.streamToString(xsltInputStream));
        xslt = xsltService.save(xslt);
        return xslt;
    }

    // TODO This method is 80% the same in a whole bunch of test classes -
    // refactor some of the repetition out.
    private void test(final PipelineEntity pipelineEntity,
                      final String inputPath,
                      final String inputStem,
                      final String outputReference,
                      final String encoding,
                      final boolean text,
                      final int outputCount) throws Exception {
        String fileName = inputStem;
        if (text) {
            fileName += ".txt";
        } else {
            fileName += ".xml";
        }

        final File tempDir = getCurrentTestDir();

        final File outputFile = new File(tempDir, fileName);
        final File outputLockFile = new File(tempDir, fileName + ".lock");

        // Make sure the config dir is set.
        System.setProperty("stroom.temp", tempDir.getCanonicalPath());

        // Delete any output file.
        FileUtil.deleteFile(outputFile);
        FileUtil.deleteFile(outputLockFile);

        // Setup the error handler.
        final LoggingErrorReceiver loggingErrorReceiver = new LoggingErrorReceiver();
        errorReceiver.setErrorReceiver(loggingErrorReceiver);

        // Create the parser.
        final PipelineData pipelineData = pipelineDataCache.get(pipelineEntity);
        final Pipeline pipeline = pipelineFactory.create(pipelineData);

        // Get the input streams.
        final File inputDir = new File(StroomProcessTestFileUtil.getTestResourcesDir(), inputPath);
        Assert.assertTrue("Can't find input dir", inputDir.isDirectory());

        final File[] inputFiles = inputDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(final File dir, final String name) {
                return name.startsWith(inputStem) && name.endsWith(".in");
            }
        });
        Arrays.sort(inputFiles);

        Assert.assertTrue("Can't find any input files", inputFiles.length > 0);

        pipeline.startProcessing();

        for (final File inputFile : inputFiles) {
            final InputStream inputStream = new BufferedInputStream(new FileInputStream(inputFile));
            pipeline.process(inputStream, encoding);
            inputStream.close();
        }

        pipeline.endProcessing();

        // Close all streams that have been written.,
        streamCloser.close();

        Assert.assertTrue(recordCount.getRead() > 0);
        Assert.assertTrue(recordCount.getWritten() > 0);
        // Assert.assertEquals(recordCount.getRead(), recordCount.getWritten());
        Assert.assertEquals(200, recordCount.getRead());
        Assert.assertEquals(200 - 59, recordCount.getWritten());
        Assert.assertEquals(0, loggingErrorReceiver.getRecords(Severity.WARNING));
        Assert.assertEquals(59, loggingErrorReceiver.getRecords(Severity.ERROR));
        Assert.assertEquals(0, loggingErrorReceiver.getRecords(Severity.FATAL_ERROR));

        final List<Stream> streams = streamStore.find(new FindStreamCriteria());
        Assert.assertEquals(outputCount, streams.size());

        if (outputCount == 1) {
            final StreamSource streamSource = streamStore.openStreamSource(streams.get(0).getId());

            final long streamId = streams.get(0).getId();
            checkHeaderFooterOnly(streamId, text);
            checkFull(streamId, outputReference);
        }
    }

    private void checkHeaderFooterOnly(final long streamId, final boolean text) throws Exception {
        if (text) {
            final String outerRef = "";

            checkOuterData(streamId, 143, outerRef);

            final String innerRef = "2013-04-09T00:00:50.000ZTestTestApachetest.test.com123.123.123.123firstuser1234/goodGETHTTP/1.0someagent200\n" +
                    "2013-04-09T00:00:50.000ZTestTestApachetest.test.com123.123.123.123lastuser1234/goodGETHTTP/1.0someagent200\n";

            checkInnerData(streamId, 143, innerRef);

        } else {
            final String outerRef = "<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n" +
                    "<Events xmlns=\"event-logging:3\"\n" +
                    "        xmlns:stroom=\"stroom\"\n" +
                    "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                    "        xsi:schemaLocation=\"event-logging:3 file://event-logging-v3.0.0.xsd\"\n" +
                    "        Version=\"3.0.0\">\n" +
                    "</Events>\n";

            checkOuterData(streamId, 143, outerRef);

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

    private void checkFull(final long streamId, final String outputReference) {
        final StreamSource streamSource = streamStore.openStreamSource(streamId);
        final File refFile = StroomProcessTestFileUtil.getTestResourcesFile(outputReference);
        final String refData = StreamUtil.fileToString(refFile);
        final String data = StreamUtil.streamToString(streamSource.getInputStream());
        Assert.assertEquals(refData, data);
        streamStore.closeStreamSource(streamSource);
    }

    private void checkOuterData(final long streamId, final int count, final String ref) throws Exception {
        final StreamSource streamSource = streamStore.openStreamSource(streamId);
        final RASegmentInputStream segmentInputStream = new RASegmentInputStream(streamSource);

        Assert.assertEquals(count, segmentInputStream.count());

        // Include the first and last segment only.
        segmentInputStream.include(0);
        segmentInputStream.include(segmentInputStream.count() - 1);

        final String data = StreamUtil.streamToString(segmentInputStream);
        Assert.assertEquals(ref, data);

        streamStore.closeStreamSource(streamSource);
    }

    private void checkInnerData(final long streamId, final int count, final String ref) throws Exception {
        final StreamSource streamSource = streamStore.openStreamSource(streamId);
        final RASegmentInputStream segmentInputStream = new RASegmentInputStream(streamSource);

        Assert.assertEquals(count, segmentInputStream.count());

        // Include the first and last segment only.
        segmentInputStream.include(0);
        segmentInputStream.include(1);
        segmentInputStream.include(segmentInputStream.count() - 2);
        segmentInputStream.include(segmentInputStream.count() - 1);

        final String data = StreamUtil.streamToString(segmentInputStream);
        Assert.assertEquals(ref, data);

        streamStore.closeStreamSource(streamSource);
    }
}
