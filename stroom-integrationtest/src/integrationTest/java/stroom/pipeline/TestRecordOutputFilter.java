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
import org.junit.Test;
import stroom.guice.PipelineScopeRunnable;
import stroom.io.StreamCloser;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.LoggingErrorReceiver;
import stroom.pipeline.factory.Pipeline;
import stroom.pipeline.factory.PipelineDataCache;
import stroom.pipeline.factory.PipelineFactory;
import stroom.pipeline.filter.AbstractXMLFilter;
import stroom.pipeline.filter.RecordOutputFilter;
import stroom.pipeline.filter.TestSAXEventFilter;
import stroom.pipeline.filter.XMLFilter;
import stroom.pipeline.filter.XMLFilterFork;
import stroom.pipeline.parser.CombinedParser;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.TextConverterDoc.TextConverterType;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineDataUtil;
import stroom.pipeline.state.RecordCount;
import stroom.query.api.v2.DocRef;
import stroom.test.AbstractProcessIntegrationTest;
import stroom.test.ComparisonHelper;
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

public class TestRecordOutputFilter extends AbstractProcessIntegrationTest {
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
    private PipelineService pipelineService;
    @Inject
    private PipelineDataCache pipelineDataCache;
    @Inject
    private Provider<StreamCloser> streamCloserProvider;
    @Inject
    private PipelineScopeRunnable pipelineScopeRunnable;

    @Test
    public void testAll() {
        final String dir = "TestRecordOutputFilter/";
        final DocRef textConverterRef = createTextConverter(dir + "TestRecordOutputFilter.ds3.xml",
                "TestRecordOutputFilter", TextConverterType.DATA_SPLITTER);
        final DocRef filteredXSLT = createXSLT(dir + "TestRecordOutputFilter.xsl", "TestRecordOutputFilter");
        final PipelineEntity pipelineEntity = createPipeline(dir + "TestRecordOutputFilter Pipeline.xml", textConverterRef,
                filteredXSLT);
        test(pipelineEntity, dir, "TestRecordOutputFilter-all", "TestRecordOutputFilter", "TestRecordOutputFilter-all",
                null);
    }

    @Test
    public void testMultiPart() {
        final String dir = "TestRecordOutputFilter/";
        final DocRef textConverterRef = createTextConverter(dir + "TestRecordOutputFilter.ds3.xml",
                "TestRecordOutputFilter", TextConverterType.DATA_SPLITTER);
        final DocRef filteredXSLT = createXSLT(dir + "TestRecordOutputFilter.xsl", "TestRecordOutputFilter");
        final PipelineEntity pipelineEntity = createPipeline(dir + "TestRecordOutputFilter Pipeline.xml", textConverterRef,
                filteredXSLT);
        test(pipelineEntity, dir, "TestRecordOutputFilter-pt", "TestRecordOutputFilter", "TestRecordOutputFilter-pt",
                null);
    }

    private PipelineEntity createPipeline(final String pipelineFile,
                                          final DocRef textConverterRef,
                                          final DocRef xsltRef) {
        // Load the pipeline config.
        final String data = StroomPipelineTestFileUtil.getString(pipelineFile);
        final PipelineEntity pipelineEntity = PipelineTestUtil.createTestPipeline(pipelineService, data);

        if (textConverterRef != null) {
            pipelineEntity.getPipelineData().addProperty(
                    PipelineDataUtil.createProperty(CombinedParser.DEFAULT_NAME, "textConverter", textConverterRef));
        }
        if (xsltRef != null) {
            pipelineEntity.getPipelineData()
                    .addProperty(PipelineDataUtil.createProperty("translationFilter", "xslt", xsltRef));
        }

        return pipelineService.save(pipelineEntity);
    }

    private DocRef createTextConverter(final String textConverterFile, final String name,
                                       final TextConverterType textConverterType) {
        // Create a record for the TextConverter.
        final InputStream textConverterInputStream = StroomPipelineTestFileUtil.getInputStream(textConverterFile);
        final DocRef docRef = textConverterStore.createDocument(name);
        final TextConverterDoc doc = textConverterStore.readDocument(docRef);
        doc.setConverterType(textConverterType);
        doc.setData(StreamUtil.streamToString(textConverterInputStream));
        textConverterStore.update(doc);
        return docRef;
    }

    private DocRef createXSLT(final String xsltPath, final String name) {
        // Create a record for the XSLT.
        final InputStream xsltInputStream = StroomPipelineTestFileUtil.getInputStream(xsltPath);
        final DocRef docRef = xsltStore.createDocument(name);
        final XsltDoc doc = xsltStore.readDocument(docRef);
        doc.setData(StreamUtil.streamToString(xsltInputStream));
        xsltStore.update(doc);
        return docRef;
    }

    private void test(final PipelineEntity pipelineEntity, final String dir, final String inputStem,
                      final String outputXMLStem, final String outputSAXStem, final String encoding) {
        pipelineScopeRunnable.scopeRunnable(() -> {
            try {
                final Path tempDir = getCurrentTestDir();

                final Path outputFile = tempDir.resolve("TestRecordOutputFilter.xml");
                final Path outputLockFile = tempDir.resolve("TestRecordOutputFilter.xml.lock");

                // Make sure the config dir is set.
                System.setProperty("stroom.temp", FileUtil.getCanonicalPath(tempDir));

                // Delete any output file.
                FileUtil.deleteFile(outputFile);
                FileUtil.deleteFile(outputLockFile);

                // Setup the error handler.
                final LoggingErrorReceiver loggingErrorReceiver = new LoggingErrorReceiver();
                errorReceiverProvider.get().setErrorReceiver(loggingErrorReceiver);

                // Create the parser.
                final PipelineData pipelineData = pipelineDataCache.get(pipelineEntity);
                final Pipeline pipeline = pipelineFactoryProvider.get().create(pipelineData);

                // Add a SAX event filter.
                final TestSAXEventFilter testSAXEventFilter = new TestSAXEventFilter();
                insertFilter(pipeline, RecordOutputFilter.class, testSAXEventFilter);

                // Get the input streams.
                final Path inputDir = StroomPipelineTestFileUtil.getTestResourcesDir().resolve(dir);
                Assert.assertTrue("Can't find input dir", Files.isDirectory(inputDir));

                List<Path> inputFiles = new ArrayList<>();
                try (final DirectoryStream<Path> stream = Files.newDirectoryStream(inputDir, inputStem + "*.in")) {
                    stream.forEach(inputFiles::add);
                }
                inputFiles.sort(Comparator.naturalOrder());

                Assert.assertTrue("Can't find any input files", inputFiles.size() > 0);

                pipeline.startProcessing();

                for (final Path inputFile : inputFiles) {
                    final InputStream inputStream = new BufferedInputStream(Files.newInputStream(inputFile));
                    pipeline.process(inputStream, encoding);
                    inputStream.close();
                }

                pipeline.endProcessing();

                // Close all streams that have been written.,
                streamCloserProvider.get().close();

                Assert.assertTrue(recordCountProvider.get().getRead() > 0);
                Assert.assertTrue(recordCountProvider.get().getWritten() > 0);
                // Assert.assertEquals(recordCount.getRead(), recordCount.getWritten());
                Assert.assertEquals(200, recordCountProvider.get().getRead());
                Assert.assertEquals(200 - 59, recordCountProvider.get().getWritten());
                Assert.assertEquals(0, loggingErrorReceiver.getRecords(Severity.WARNING));
                Assert.assertEquals(59, loggingErrorReceiver.getRecords(Severity.ERROR));
                Assert.assertEquals(0, loggingErrorReceiver.getRecords(Severity.FATAL_ERROR));

                final Path refFile = StroomPipelineTestFileUtil.getTestResourcesFile(dir + outputXMLStem + ".out");
                final Path tmpFile = refFile.getParent().resolve(outputXMLStem + ".out_tmp");
                Files.delete(tmpFile);
                StreamUtil.copyFile(outputFile, tmpFile);
                ComparisonHelper.compareFiles(refFile, tmpFile);

                final Path refSAXFile = refFile.getParent().resolve(outputSAXStem + ".sax");
                final Path tmpSAXFile = refFile.getParent().resolve(outputSAXStem + ".sax_tmp");
                Files.delete(tmpSAXFile);
                final String actualSax = testSAXEventFilter.getOutput().trim();
                StreamUtil.stringToFile(actualSax, tmpSAXFile);
                ComparisonHelper.compareFiles(refSAXFile, tmpSAXFile);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        });
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
