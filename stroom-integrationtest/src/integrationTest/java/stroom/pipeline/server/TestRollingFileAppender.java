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

import org.junit.Assert;
import org.junit.Test;
import stroom.io.StreamCloser;
import stroom.pipeline.destination.RollingDestinations;
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
import stroom.test.AbstractProcessIntegrationTest;
import stroom.test.ComparisonHelper;
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

public class TestRollingFileAppender extends AbstractProcessIntegrationTest {
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
    private RollingDestinations destinations;
    @Resource
    private StreamCloser streamCloser;

    @Test
    public void testXML() throws Exception {
        final String dir = "TestRollingFileAppender/";
        final TextConverter textConverter = createTextConverter(dir + "TestRollingFileAppender.ds3.xml",
                "TestRollingFileAppender", TextConverterType.DATA_SPLITTER);
        final XSLT filteredXSLT = createXSLT(dir + "TestRollingFileAppender.xsl", "TestRollingFileAppender");
        final PipelineEntity pipelineEntity = createPipeline(dir + "TestRollingFileAppender_XML_Pipeline.xml",
                textConverter, filteredXSLT);
        test(pipelineEntity, dir, "TestRollingFileAppender", dir + "TestRollingFileAppender.xml.out", null, false);
    }

    @Test
    public void testText() throws Exception {
        final String dir = "TestRollingFileAppender/";
        final TextConverter textConverter = createTextConverter(dir + "TestRollingFileAppender.ds3.xml",
                "TestRollingFileAppender", TextConverterType.DATA_SPLITTER);
        final XSLT filteredXSLT = createXSLT(dir + "TestRollingFileAppender_Text.xsl", "TestRollingFileAppender");
        final PipelineEntity pipelineEntity = createPipeline(dir + "TestRollingFileAppender_Text_Pipeline.xml",
                textConverter, filteredXSLT);
        test(pipelineEntity, dir, "TestRollingFileAppender", dir + "TestRollingFileAppender.txt.out", null, true);
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
    private void test(final PipelineEntity pipelineEntity, final String inputPath, final String inputStem,
                      final String outputReference, final String encoding, final boolean text) throws Exception {
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
        final PipelineData pipelineData = pipelineDataCache.getOrCreate(pipelineEntity);
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

        // FORCE ROLL SO WE CAN GET OUTPUT
        destinations.forceRoll();

        final File refFile = StroomProcessTestFileUtil.getTestResourcesFile(outputReference);
        final File tmpFile = new File(FileUtil.getTempDir(), fileName);

        Assert.assertTrue(recordCount.getRead() > 0);
        Assert.assertTrue(recordCount.getWritten() > 0);
        // Assert.assertEquals(recordCount.getRead(), recordCount.getWritten());
        Assert.assertEquals(200, recordCount.getRead());
        Assert.assertEquals(200 - 59, recordCount.getWritten());
        Assert.assertEquals(0, loggingErrorReceiver.getRecords(Severity.WARNING));
        Assert.assertEquals(59, loggingErrorReceiver.getRecords(Severity.ERROR));
        Assert.assertEquals(0, loggingErrorReceiver.getRecords(Severity.FATAL_ERROR));

        ComparisonHelper.compareFiles(refFile, tmpFile, false, false);
    }
}
