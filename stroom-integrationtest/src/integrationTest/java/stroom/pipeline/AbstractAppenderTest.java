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
import stroom.io.StreamCloser;
import stroom.pipeline.destination.RollingDestinations;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.LoggingErrorReceiver;
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
import stroom.pipeline.state.RecordCount;
import stroom.test.AbstractProcessIntegrationTest;
import stroom.test.CommonTestScenarioCreator;
import stroom.test.StroomPipelineTestFileUtil;
import stroom.guice.PipelineScopeRunnable;
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

public abstract class AbstractAppenderTest extends AbstractProcessIntegrationTest {
    @Inject
    private Provider<PipelineFactory> pipelineFactoryProvider;
    @Inject
    private Provider<ErrorReceiverProxy> errorReceiverProvider;
    @Inject
    private Provider<RecordCount> recordCountProvider;
    @Inject
    private XSLTService xsltService;
    @Inject
    private TextConverterService textConverterService;
    @Inject
    private PipelineService pipelineService;
    @Inject
    private PipelineDataCache pipelineDataCache;
    @Inject
    private RollingDestinations destinations;
    @Inject
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Inject
    private Provider<StreamCloser> streamCloserProvider;
    @Inject
    private PipelineScopeRunnable pipelineScopeRunnable;

    void test(final String name, final String type) throws Exception {
        // Ensure a test feed exists.
        commonTestScenarioCreator.createSimpleFeed("TEST", "12345");
        final String dir = name + "/";
        final String stem = dir + name + "_" + type;
        final TextConverter textConverter = createTextConverter(dir + name + ".ds3.xml", name, TextConverterType.DATA_SPLITTER);
        final XSLT filteredXSLT = createXSLT(stem + ".xsl", name);
        final PipelineEntity pipelineEntity = createPipeline(stem + "_Pipeline.xml", textConverter, filteredXSLT);
        test(pipelineEntity, dir, name, type, stem + ".out", null);
    }

    private PipelineEntity createPipeline(final String pipelineFile, final TextConverter textConverter,
                                          final XSLT xslt) {
        // Load the pipeline config.
        final String data = StroomPipelineTestFileUtil.getString(pipelineFile);
        final PipelineEntity pipelineEntity = PipelineTestUtil.createTestPipeline(pipelineService, data);

        if (textConverter != null) {
            pipelineEntity.getPipelineData().addProperty(
                    PipelineDataUtil.createProperty(CombinedParser.DEFAULT_NAME, "textConverter", textConverter));
        }
        if (xslt != null) {
            pipelineEntity.getPipelineData()
                    .addProperty(PipelineDataUtil.createProperty("translationFilter", "xslt", xslt));
        }

        return pipelineService.save(pipelineEntity);
    }

    private TextConverter createTextConverter(final String textConverterFile, final String name,
                                              final TextConverterType textConverterType) {
        // Create a record for the TextConverter.
        final InputStream textConverterInputStream = StroomPipelineTestFileUtil.getInputStream(textConverterFile);
        TextConverter textConverter = textConverterService.create(name);
        textConverter.setConverterType(textConverterType);
        textConverter.setData(StreamUtil.streamToString(textConverterInputStream));
        textConverter = textConverterService.save(textConverter);
        return textConverter;
    }

    private XSLT createXSLT(final String xsltPath, final String name) {
        // Create a record for the XSLT.
        final InputStream xsltInputStream = StroomPipelineTestFileUtil.getInputStream(xsltPath);
        XSLT xslt = xsltService.create(name);
        xslt.setData(StreamUtil.streamToString(xsltInputStream));
        xslt = xsltService.save(xslt);
        return xslt;
    }

    // TODO This method is 80% the same in a whole bunch of test classes -
    // refactor some of the repetition out.
    void test(final PipelineEntity pipelineEntity,
              final String dir,
              final String name,
              final String type,
              final String outputReference,
              final String encoding) throws Exception {
        pipelineScopeRunnable.scopeRunnable(() -> {
            try {
                // Setup the error handler.
                final LoggingErrorReceiver loggingErrorReceiver = new LoggingErrorReceiver();
                errorReceiverProvider.get().setErrorReceiver(loggingErrorReceiver);

                // Create the parser.
                final PipelineData pipelineData = pipelineDataCache.get(pipelineEntity);
                final Pipeline pipeline = pipelineFactoryProvider.get().create(pipelineData);

                // Get the input streams.
                final Path inputDir = StroomPipelineTestFileUtil.getTestResourcesDir().resolve(dir);
                Assert.assertTrue("Can't find input dir", Files.isDirectory(inputDir));

                final List<Path> inputFiles = new ArrayList<>();
                try (final DirectoryStream<Path> stream = Files.newDirectoryStream(inputDir, name + "*.in")) {
                    stream.forEach(inputFiles::add);
                } catch (final IOException e) {
                    throw new UncheckedIOException(e);
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

                // Close all streams that have been written.
                streamCloserProvider.get().close();

                // FORCE ROLL SO WE CAN GET OUTPUT
                destinations.forceRoll();

                Assert.assertTrue(recordCountProvider.get().getRead() > 0);
                Assert.assertTrue(recordCountProvider.get().getWritten() > 0);
                // Assert.assertEquals(recordCountProvider.get().getRead(), recordCountProvider.get().getWritten());
                Assert.assertEquals(200, recordCountProvider.get().getRead());
                Assert.assertEquals(200 - 59, recordCountProvider.get().getWritten());
                Assert.assertEquals(0, loggingErrorReceiver.getRecords(Severity.WARNING));
                Assert.assertEquals(59, loggingErrorReceiver.getRecords(Severity.ERROR));
                Assert.assertEquals(0, loggingErrorReceiver.getRecords(Severity.FATAL_ERROR));
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}
