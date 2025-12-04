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

import stroom.data.store.api.InputStreamProvider;
import stroom.data.store.api.SegmentInputStream;
import stroom.data.store.api.Source;
import stroom.data.store.api.SourceUtil;
import stroom.data.store.api.Store;
import stroom.docref.DocRef;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.pipeline.destination.RollingDestinations;
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
import stroom.test.common.StroomPipelineTestFileUtil;
import stroom.util.io.ByteCountInputStream;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.pipeline.scope.PipelineScopeRunnable;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

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
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

abstract class AbstractAppenderTest extends AbstractProcessIntegrationTest {

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
    private RollingDestinations destinations;
    @Inject
    private PipelineScopeRunnable pipelineScopeRunnable;
    @Inject
    private MetaService metaService;
    @Inject
    private Store streamStore;

    private LoggingErrorReceiver loggingErrorReceiver;

    void test(final String name, final String type) {
        final String dir = name + "/";
        final String stem = dir + name + "_" + type;
        final DocRef textConverterRef = createTextConverter(dir + name + ".ds3.xml",
                name,
                TextConverterType.DATA_SPLITTER);
        final DocRef filteredXSLT = createXSLT(stem + ".xsl", name);
        final DocRef pipelineRef = createPipeline(stem + "_Pipeline.json", textConverterRef, filteredXSLT);

        pipelineScopeRunnable.scopeRunnable(() -> {
            process(pipelineRef, dir, name, null);
            validateProcess();
        });
    }

    private DocRef createPipeline(final String pipelineFile,
                                  final DocRef textConverterRef,
                                  final DocRef xsltRef) {
        // Load the pipeline config.
        final String data = StroomPipelineTestFileUtil.getString(pipelineFile);
        final DocRef pipelineRef = PipelineTestUtil.createTestPipeline(pipelineStore, data);
        final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipelineRef);
        final PipelineDataBuilder builder = new PipelineDataBuilder(pipelineDoc.getPipelineData());

        if (textConverterRef != null) {
            builder.addProperty(
                    PipelineDataUtil.createProperty(CombinedParser.DEFAULT_NAME, "textConverter", textConverterRef));
        }
        if (xsltRef != null) {
            builder.addProperty(
                    PipelineDataUtil.createProperty("translationFilter", "xslt", xsltRef));
        }

        pipelineDoc.setPipelineData(builder.build());
        pipelineStore.writeDocument(pipelineDoc);
        return pipelineRef;
    }

    private DocRef createTextConverter(final String textConverterFile, final String name,
                                       final TextConverterType textConverterType) {
        // Create a record for the TextConverter.
        final InputStream textConverterInputStream = StroomPipelineTestFileUtil.getInputStream(textConverterFile);
        final DocRef textConverterRef = textConverterStore.createDocument(name);
        final TextConverterDoc textConverter = textConverterStore.readDocument(textConverterRef);
        textConverter.setConverterType(textConverterType);
        textConverter.setData(StreamUtil.streamToString(textConverterInputStream));
        textConverterStore.writeDocument(textConverter);
        return textConverterRef;
    }

    private DocRef createXSLT(final String xsltPath, final String name) {
        // Create a record for the XSLT.
        final InputStream xsltInputStream = StroomPipelineTestFileUtil.getInputStream(xsltPath);
        final DocRef xsltRef = xsltStore.createDocument(name);
        final XsltDoc xsltDoc = xsltStore.readDocument(xsltRef);
        xsltDoc.setData(StreamUtil.streamToString(xsltInputStream));
        xsltStore.writeDocument(xsltDoc);
        return xsltRef;
    }

    // TODO This method is 80% the same in a whole bunch of test classes -
    // refactor some of the repetition out.
    void process(final DocRef pipelineRef,
                 final String dir,
                 final String name,
                 final String encoding) {
        try {
            // Setup the error handler.
            loggingErrorReceiver = new LoggingErrorReceiver();
            errorReceiverProvider.get().setErrorReceiver(loggingErrorReceiver);

            // Create the parser.
            final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipelineRef);
            final PipelineData pipelineData = pipelineDataCache.get(pipelineDoc);
            final Pipeline pipeline = pipelineFactoryProvider.get().create(pipelineData, new SimpleTaskContext());

            // Get the input streams.
            final Path inputDir = StroomPipelineTestFileUtil.getTestResourcesDir().resolve(dir);
            assertThat(Files.isDirectory(inputDir)).as("Can't find input dir").isTrue();

            final List<Path> inputFiles = new ArrayList<>();
            try (final DirectoryStream<Path> stream = Files.newDirectoryStream(inputDir, name + "*.in")) {
                stream.forEach(inputFiles::add);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
            inputFiles.sort(Comparator.naturalOrder());

            assertThat(inputFiles.size() > 0).as("Can't find any input files").isTrue();

            pipeline.startProcessing();

            for (final Path inputFile : inputFiles) {
                final InputStream inputStream = new BufferedInputStream(Files.newInputStream(inputFile));
                pipeline.process(inputStream, encoding);
                inputStream.close();
            }

            pipeline.endProcessing();

            // FORCE ROLL SO WE CAN GET OUTPUT
            destinations.forceRoll();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    void validateProcess() {
        final RecordCount recordCount = recordCountProvider.get();
        assertThat(recordCount.getRead() > 0).isTrue();
        assertThat(recordCount.getWritten() > 0).isTrue();
        // assertThat(recordCount.getWritten()).isEqualTo(recordCount.getRead());
        assertThat(recordCount.getRead()).isEqualTo(200);
        assertThat(recordCount.getWritten()).isEqualTo(200 - 59);
        assertThat(loggingErrorReceiver.getRecords(Severity.WARNING)).isEqualTo(0);
        assertThat(loggingErrorReceiver.getRecords(Severity.ERROR)).isEqualTo(59);
        assertThat(loggingErrorReceiver.getRecords(Severity.FATAL_ERROR)).isEqualTo(0);
    }

    void checkSegments(final int streamCount,
                       final int segmentTotal,
                       final long[] segmentCounts,
                       final long[] byteCounts) throws IOException {
        final List<Meta> list = metaService.find(new FindMetaCriteria()).getValues();
        assertThat(list.size()).isEqualTo(streamCount);

//        final List<Long> byteCountList = new ArrayList<>();
//        final List<Long> segmentCountList = new ArrayList<>();
        int segments = 0;

        for (int i = 0; i < streamCount; i++) {
            final Meta meta = list.get(i);
            final long id = meta.getId();
            final long segmentCount = segmentCounts[i];
            final long byteCount = byteCounts[i];

            segments += segmentCount; // Add to the total segments.
            segments -= 2; // Remove header and footer.

            try (final Source streamSource = streamStore.openSource(id)) {
                try (final InputStreamProvider inputStreamProvider = streamSource.get(0)) {
                    final SegmentInputStream segmentInputStream = inputStreamProvider.get();
                    final ByteCountInputStream byteCountInputStream = new ByteCountInputStream(segmentInputStream);
                    StreamUtil.streamToString(byteCountInputStream);
                    assertThat(segmentInputStream.count()).isEqualTo(segmentCount);
                    assertThat(byteCountInputStream.getCount()).isEqualTo(byteCount);

//                    byteCountList.add(byteCountInputStream.getCount());
//                    segmentCountList.add(segmentInputStream.count());
                }
            }
        }

        assertThat(segments).isEqualTo(segmentTotal);

//        System.out.println(segments);
//        System.out.println(byteCountList.stream().map(Objects::toString).collect(Collectors.joining(",")));
//        System.out.println(segmentCountList.stream().map(Objects::toString).collect(Collectors.joining(",")));
    }

    void validateOutput(final String name,
                        final String type) {
        try {
            final Path refDir = StroomPipelineTestFileUtil.getTestResourcesDir().resolve(name);
            final List<Meta> list = metaService.find(new FindMetaCriteria()).getValues();
            int num = 0;
            for (final Meta meta : list) {
                final long id = meta.getId();
                num++;
                if (type.toLowerCase(Locale.ROOT).contains("text")) {
                    checkFirstData(id, refDir.resolve(name + "_" + type + "-" + num + ".first.out.txt"));
                    checkLastData(id, refDir.resolve(name + "_" + type + "-" + num + ".last.out.txt"));
                    checkFull(id, refDir.resolve(name + "_" + type + "-" + num + ".full.out.txt"));
                } else {
                    checkOuterData(id, refDir.resolve(name + "_" + type + "-" + num + ".outer.out.xml"));
                    checkInnerData(id, refDir.resolve(name + "_" + type + "-" + num + ".inner.out.xml"));
                    checkFirstData(id, refDir.resolve(name + "_" + type + "-" + num + ".first.out.xml"));
                    checkLastData(id, refDir.resolve(name + "_" + type + "-" + num + ".last.out.xml"));
                    checkFull(id, refDir.resolve(name + "_" + type + "-" + num + ".full.out.xml"));
                }
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void checkFull(final long streamId, final Path refFile) throws IOException {
        try (final Source streamSource = streamStore.openSource(streamId)) {
            final String refData = StreamUtil.fileToString(refFile);
            final String data = SourceUtil.readString(streamSource);
            assertThat(data).isEqualTo(refData);

//            Files.writeString(refFile, data);
        }
    }

    private void checkFirstData(final long streamId, final Path refFile) throws IOException {
        try (final Source streamSource = streamStore.openSource(streamId)) {
            try (final InputStreamProvider inputStreamProvider = streamSource.get(0)) {
                final SegmentInputStream segmentInputStream = inputStreamProvider.get();

                // Include the first and last segment as these are header and footer.
                segmentInputStream.include(0);
                segmentInputStream.include(segmentInputStream.count() - 1);

                // Include the first data segment.
                segmentInputStream.include(1);

                final String refData = StreamUtil.fileToString(refFile);
                final String data = StreamUtil.streamToString(segmentInputStream);
                assertThat(data).isEqualTo(refData);

//                Files.writeString(refFile, data);
            }
        }
    }

    private void checkLastData(final long streamId, final Path refFile) throws IOException {
        try (final Source streamSource = streamStore.openSource(streamId)) {
            try (final InputStreamProvider inputStreamProvider = streamSource.get(0)) {
                final SegmentInputStream segmentInputStream = inputStreamProvider.get();

                // Include the first and last segment as these are header and footer.
                segmentInputStream.include(0);
                segmentInputStream.include(segmentInputStream.count() - 1);

                // Include the last data segment.
                segmentInputStream.include(segmentInputStream.count() - 2);

                final String refData = StreamUtil.fileToString(refFile);
                final String data = StreamUtil.streamToString(segmentInputStream);
                assertThat(data).isEqualTo(refData);

//                Files.writeString(refFile, data);
            }
        }
    }

    private void checkOuterData(final long streamId, final Path refFile) throws IOException {
        try (final Source streamSource = streamStore.openSource(streamId)) {
            try (final InputStreamProvider inputStreamProvider = streamSource.get(0)) {
                final SegmentInputStream segmentInputStream = inputStreamProvider.get();

                // Include the first and last segment only.
                segmentInputStream.include(0);
                segmentInputStream.include(segmentInputStream.count() - 1);

                final String refData = StreamUtil.fileToString(refFile);
                final String data = StreamUtil.streamToString(segmentInputStream);
                assertThat(data).withFailMessage(() -> FileUtil.getCanonicalPath(refFile)).isEqualTo(refData);

//                Files.writeString(refFile, data);
            }
        }
    }

    private void checkInnerData(final long streamId, final Path refFile) throws IOException {
        try (final Source streamSource = streamStore.openSource(streamId)) {
            try (final InputStreamProvider inputStreamProvider = streamSource.get(0)) {
                final SegmentInputStream segmentInputStream = inputStreamProvider.get();

                // Include the first and last segment only.
                segmentInputStream.include(0);
                segmentInputStream.include(1);
                segmentInputStream.include(segmentInputStream.count() - 2);
                segmentInputStream.include(segmentInputStream.count() - 1);

                final String refData = StreamUtil.fileToString(refFile);
                final String data = StreamUtil.streamToString(segmentInputStream);
                assertThat(data).withFailMessage(() -> FileUtil.getCanonicalPath(refFile)).isEqualTo(refData);

//                Files.writeString(refFile, data);
            }
        }
    }
}
