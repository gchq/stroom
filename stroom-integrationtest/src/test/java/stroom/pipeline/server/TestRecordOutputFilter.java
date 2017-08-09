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
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.errorhandler.LoggingErrorReceiver;
import stroom.pipeline.server.factory.Pipeline;
import stroom.pipeline.server.factory.PipelineDataCache;
import stroom.pipeline.server.factory.PipelineFactory;
import stroom.pipeline.server.filter.AbstractXMLFilter;
import stroom.pipeline.server.filter.RecordOutputFilter;
import stroom.pipeline.server.filter.TestSAXEventFilter;
import stroom.pipeline.server.filter.XMLFilter;
import stroom.pipeline.server.filter.XMLFilterFork;
import stroom.pipeline.server.parser.CombinedParser;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.TextConverter;
import stroom.pipeline.shared.TextConverter.TextConverterType;
import stroom.pipeline.shared.XSLT;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineDataUtil;
import stroom.pipeline.state.RecordCount;
import stroom.test.AbstractProcessIntegrationTest;
import stroom.test.ComparisonHelper;
import stroom.test.StroomProcessTestFileUtil;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.shared.Severity;

import javax.annotation.Resource;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

public class TestRecordOutputFilter extends AbstractProcessIntegrationTest {
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
    private PipelineDataCache pipelineDataCache;

    @Test
    public void testAll() throws Exception {
        final String dir = "TestRecordOutputFilter/";
        final TextConverter textConverter = createTextConverter(dir + "TestRecordOutputFilter.ds3.xml",
                "TestRecordOutputFilter", TextConverterType.DATA_SPLITTER);
        final XSLT filteredXSLT = createXSLT(dir + "TestRecordOutputFilter.xsl", "TestRecordOutputFilter");
        final PipelineEntity pipelineEntity = createPipeline(dir + "TestRecordOutputFilter Pipeline.xml", textConverter,
                filteredXSLT);
        test(pipelineEntity, dir, "TestRecordOutputFilter-all", "TestRecordOutputFilter", "TestRecordOutputFilter-all",
                null);
    }

    @Test
    public void testMultiPart() throws Exception {
        final String dir = "TestRecordOutputFilter/";
        final TextConverter textConverter = createTextConverter(dir + "TestRecordOutputFilter.ds3.xml",
                "TestRecordOutputFilter", TextConverterType.DATA_SPLITTER);
        final XSLT filteredXSLT = createXSLT(dir + "TestRecordOutputFilter.xsl", "TestRecordOutputFilter");
        final PipelineEntity pipelineEntity = createPipeline(dir + "TestRecordOutputFilter Pipeline.xml", textConverter,
                filteredXSLT);
        test(pipelineEntity, dir, "TestRecordOutputFilter-pt", "TestRecordOutputFilter", "TestRecordOutputFilter-pt",
                null);
    }

    private PipelineEntity createPipeline(final String pipelineFile, final TextConverter textConverter,
                                          final XSLT xslt) {
        // Load the pipeline config.
        final String data = StroomProcessTestFileUtil.getString(pipelineFile);
        final PipelineEntity pipelineEntity = PipelineTestUtil.createTestPipeline(pipelineEntityService, data);

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
        TextConverter textConverter = textConverterService.create(name);
        textConverter.setConverterType(textConverterType);
        textConverter.setData(StreamUtil.streamToString(textConverterInputStream));
        textConverter = textConverterService.save(textConverter);
        return textConverter;
    }

    private XSLT createXSLT(final String xsltPath, final String name) {
        // Create a record for the XSLT.
        final InputStream xsltInputStream = StroomProcessTestFileUtil.getInputStream(xsltPath);
        XSLT xslt = xsltService.create(name);
        xslt.setData(StreamUtil.streamToString(xsltInputStream));
        xslt = xsltService.save(xslt);
        return xslt;
    }

    private void test(final PipelineEntity pipelineEntity, final String dir, final String inputStem,
                      final String outputXMLStem, final String outputSAXStem, final String encoding) throws Exception {
        final File tempDir = getCurrentTestDir();

        final File outputFile = new File(tempDir, "TestRecordOutputFilter.xml");
        final File outputLockFile = new File(tempDir, "TestRecordOutputFilter.xml.lock");

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

        // Add a SAX event filter.
        final TestSAXEventFilter testSAXEventFilter = new TestSAXEventFilter();
        insertFilter(pipeline, RecordOutputFilter.class, testSAXEventFilter);

        // Get the input streams.
        final File inputDir = new File(StroomProcessTestFileUtil.getTestResourcesDir(), dir);
        Assert.assertTrue("Can't find input dir", inputDir.isDirectory());

        final File[] inputFiles = inputDir
                .listFiles((dir1, name) -> name.startsWith(inputStem) && name.endsWith(".in"));
        Arrays.sort(inputFiles);

        Assert.assertTrue("Can't find any input files", inputFiles.length > 0);

        pipeline.startProcessing();

        for (final File inputFile : inputFiles) {
            final InputStream inputStream = new BufferedInputStream(new FileInputStream(inputFile));
            pipeline.process(inputStream, encoding);
            inputStream.close();
        }

        pipeline.endProcessing();

        Assert.assertTrue(recordCount.getRead() > 0);
        Assert.assertTrue(recordCount.getWritten() > 0);
        // Assert.assertEquals(recordCount.getRead(), recordCount.getWritten());
        Assert.assertEquals(200, recordCount.getRead());
        Assert.assertEquals(200 - 59, recordCount.getWritten());
        Assert.assertEquals(0, loggingErrorReceiver.getRecords(Severity.WARNING));
        Assert.assertEquals(59, loggingErrorReceiver.getRecords(Severity.ERROR));
        Assert.assertEquals(0, loggingErrorReceiver.getRecords(Severity.FATAL_ERROR));

        final File refFile = StroomProcessTestFileUtil.getTestResourcesFile(dir + outputXMLStem + ".out");
        final File tmpFile = new File(refFile.getParentFile(), outputXMLStem + ".out_tmp");
        tmpFile.delete();
        StreamUtil.copyFile(outputFile, tmpFile);
        ComparisonHelper.compareFiles(refFile, tmpFile);

        final File refSAXFile = new File(refFile.getParentFile(), outputSAXStem + ".sax");
        final File tmpSAXFile = new File(refFile.getParentFile(), outputSAXStem + ".sax_tmp");
        tmpSAXFile.delete();
        final String actualSax = testSAXEventFilter.getOutput().trim();
        StreamUtil.stringToFile(actualSax, tmpSAXFile);
        ComparisonHelper.compareFiles(refSAXFile, tmpSAXFile);
    }

    private <T extends XMLFilter> void insertFilter(final Pipeline pipeline, final Class<T> parentFilterType,
                                                    final XMLFilter filterToAdd) {
        final List<T> parentFilters = pipeline.findFilters(parentFilterType);
        final AbstractXMLFilter parentFilter = (AbstractXMLFilter) parentFilters.get(0);
        final XMLFilter existingChild = parentFilter.getFilter();
        final XMLFilter[] filters = new XMLFilter[2];
        filters[0] = existingChild;
        filters[1] = filterToAdd;
        final XMLFilterFork fork = new XMLFilterFork(filters);
        parentFilter.setTarget(fork);
    }
}
