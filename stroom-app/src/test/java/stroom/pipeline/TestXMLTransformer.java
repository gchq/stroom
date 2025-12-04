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
import stroom.pipeline.errorhandler.LoggingErrorReceiver;
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
import stroom.pipeline.state.RecordCount;
import stroom.pipeline.textconverter.TextConverterStore;
import stroom.pipeline.xslt.XsltStore;
import stroom.task.api.SimpleTaskContext;
import stroom.test.AbstractProcessIntegrationTest;
import stroom.test.common.ComparisonHelper;
import stroom.test.common.StroomPipelineTestFileUtil;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.pipeline.scope.PipelineScopeRunnable;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class TestXMLTransformer extends AbstractProcessIntegrationTest {

    private static final String DIR = "TestXMLTransformer/";

    private static final int NUMBER_OF_RECORDS = 10;

    private static final String REFERENCE = DIR + "TestXMLTransformer.out";

    private static final String INPUT_DEFAULT = DIR + "XML-EVENTS.nxml";
    private static final String INPUT_UTF_8 = DIR + "XML-EVENTS_UTF-8.nxml";
    private static final String INPUT_UTF_8_BOM = DIR + "XML-EVENTS_UTF-8_BOM.nxml";
    private static final String INPUT_UTF_16_LE = DIR + "XML-EVENTS_UTF-16LE.nxml";
    private static final String INPUT_UTF_16_LE_BOM = DIR + "XML-EVENTS_UTF-16LE_BOM.nxml";
    private static final String INPUT_UTF_16_BE = DIR + "XML-EVENTS_UTF-16BE.nxml";
    private static final String INPUT_UTF_16_BE_BOM = DIR + "XML-EVENTS_UTF-16BE_BOM.nxml";
    private static final String INPUT_FRAGMENT = DIR + "XML-EVENTS_fragment.nxml";
    private static final String XSLT_PATH = DIR + "DATA_SPLITTER-EVENTS_no-ref.xsl";
    private static final String FRAGMENT_WRAPPER = DIR + "fragment_wrapper.xml";

    private static final String TRANSFORMER_PIPELINE = DIR + "XMLTransformer.Pipeline.json";
    private static final String FRAGMENT_PIPELINE = DIR + "XMLFragment.Pipeline.json";

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
    private PipelineStore pipelineStore;
    @Inject
    private PipelineDataCache pipelineDataCache;
    @Inject
    private PipelineScopeRunnable pipelineScopeRunnable;

    @Test
    void testDefault() {
        testXMLTransformer(INPUT_DEFAULT, null);
    }

    @Test
    void testUTF8() {
        testXMLTransformer(INPUT_UTF_8, "UTF-8");
    }

    @Test
    void testUTF8BOM() {
        testXMLTransformer(INPUT_UTF_8_BOM, "UTF-8");
    }

    @Test
    void testUTF16LE() {
        testXMLTransformer(INPUT_UTF_16_LE, "UTF-16LE");
    }

    @Test
    void testUTF16LEBOM() {
        testXMLTransformer(INPUT_UTF_16_LE_BOM, "UTF-16LE");
    }

    @Test
    void testUTF16BE() {
        testXMLTransformer(INPUT_UTF_16_BE, "UTF-16BE");
    }

    @Test
    void testUTF16BEBOM() {
        testXMLTransformer(INPUT_UTF_16_BE_BOM, "UTF-16BE");
    }

    @Test
    void testFragment() {
        final DocRef pipelineRef = createFragmentPipeline();
        test(pipelineRef, INPUT_FRAGMENT, null);
    }

    private void testXMLTransformer(final String inputResource, final String encoding) {
        final DocRef pipelineRef = createTransformerPipeline();
        test(pipelineRef, inputResource, encoding);
    }

    private DocRef createFragmentPipeline() {
        // Create a record for the TextConverter.
        final InputStream textConverterInputStream = StroomPipelineTestFileUtil.getInputStream(FRAGMENT_WRAPPER);
        final DocRef docRef = textConverterStore.createDocument("Test Text Converter");
        final TextConverterDoc textConverter = textConverterStore.readDocument(docRef);
        textConverter.setConverterType(TextConverterType.XML_FRAGMENT);
        textConverter.setData(StreamUtil.streamToString(textConverterInputStream));
        textConverterStore.writeDocument(textConverter);

        // Get the pipeline config.
        final String data = StroomPipelineTestFileUtil.getString(FRAGMENT_PIPELINE);
        final DocRef pipelineRef = PipelineTestUtil.createTestPipeline(pipelineStore, data);
        final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipelineRef);
        final PipelineDataBuilder builder = new PipelineDataBuilder(pipelineDoc.getPipelineData());
        builder.addProperty(
                PipelineDataUtil.createProperty(CombinedParser.DEFAULT_NAME, "textConverter", docRef));
        pipelineDoc.setPipelineData(builder.build());
        pipelineDoc.setParentPipeline(createTransformerPipeline());
        pipelineStore.writeDocument(pipelineDoc);
        return pipelineRef;
    }

    private DocRef createTransformerPipeline() {
        // Create a record for the XSLT.
        final InputStream xsltInputStream = StroomPipelineTestFileUtil.getInputStream(XSLT_PATH);
        final DocRef xsltRef = xsltStore.createDocument("Test XSLT");
        final XsltDoc xsltDoc = xsltStore.readDocument(xsltRef);
        xsltDoc.setData(StreamUtil.streamToString(xsltInputStream));
        xsltStore.writeDocument(xsltDoc);

        // Get the pipeline config.
        final String data = StroomPipelineTestFileUtil.getString(TRANSFORMER_PIPELINE);
        final DocRef pipelineRef = PipelineTestUtil.createTestPipeline(pipelineStore, data);
        final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipelineRef);
        final PipelineDataBuilder builder = new PipelineDataBuilder(pipelineDoc.getPipelineData());
        builder.addProperty(PipelineDataUtil.createProperty("translationFilter", "xslt", xsltRef));
        pipelineDoc.setPipelineData(builder.build());
        pipelineStore.writeDocument(pipelineDoc);
        return pipelineRef;
    }

    private void test(final DocRef pipelineRef, final String inputResource, final String encoding) {
        pipelineScopeRunnable.scopeRunnable(() -> {
            try (final InputStream inputStream = StroomPipelineTestFileUtil.getInputStream(inputResource)) {
                // We have to use /tmp here as the pipeline is hard coded to output
                // to ${stroom.temp}/TestXMLTransformer.xml

                // Delete any output file.
                final Path outputFile = getCurrentTestDir().resolve("TestXMLTransformer.xml");
                final Path outputLockFile = getCurrentTestDir().resolve("TestXMLTransformer.xml.lock");
                FileUtil.deleteFile(outputFile);
                FileUtil.deleteFile(outputLockFile);

                // Setup the error handler.
                final LoggingErrorReceiver loggingErrorReceiver = new LoggingErrorReceiver();
                errorReceiverProvider.get().setErrorReceiver(loggingErrorReceiver);

                // Create the parser.
                final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipelineRef);
                final PipelineData pipelineData = pipelineDataCache.get(pipelineDoc);
                final Pipeline pipeline = pipelineFactoryProvider.get().create(pipelineData, new SimpleTaskContext());

                pipeline.startProcessing();

                pipeline.process(inputStream, encoding);

                pipeline.endProcessing();

                assertThat(recordCountProvider.get().getRead() > 0).isTrue();
                assertThat(recordCountProvider.get().getWritten() > 0).isTrue();
                assertThat(recordCountProvider.get().getWritten()).isEqualTo(recordCountProvider.get().getRead());
                assertThat(recordCountProvider.get().getRead()).isEqualTo(NUMBER_OF_RECORDS);
                assertThat(recordCountProvider.get().getWritten()).isEqualTo(NUMBER_OF_RECORDS);
                assertThat(loggingErrorReceiver.getRecords(Severity.WARNING)).isEqualTo(0);
                assertThat(loggingErrorReceiver.getRecords(Severity.ERROR)).isEqualTo(0);
                assertThat(loggingErrorReceiver.getRecords(Severity.FATAL_ERROR)).isEqualTo(0);

                if (!loggingErrorReceiver.isAllOk()) {
                    fail(loggingErrorReceiver.toString());
                }

                final Path refFile = StroomPipelineTestFileUtil.getTestResourcesFile(REFERENCE);
                ComparisonHelper.compareFiles(refFile, outputFile);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}
