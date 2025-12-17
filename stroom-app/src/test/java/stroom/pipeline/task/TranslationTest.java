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

package stroom.pipeline.task;

import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.Source;
import stroom.data.store.api.SourceUtil;
import stroom.data.store.api.Store;
import stroom.data.store.api.Target;
import stroom.data.store.api.TargetUtil;
import stroom.docref.DocRef;
import stroom.feed.api.FeedStore;
import stroom.feed.shared.FeedDoc;
import stroom.importexport.api.ImportExportSerializer;
import stroom.importexport.shared.ImportSettings;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.MetaProperties;
import stroom.meta.api.MetaService;
import stroom.meta.api.StandardHeaderArguments;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaExpressionUtil;
import stroom.meta.shared.MetaFields;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.SharedElementData;
import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.shared.stepping.SharedStepData;
import stroom.pipeline.shared.stepping.StepType;
import stroom.pipeline.shared.stepping.SteppingResult;
import stroom.pipeline.stepping.SteppingService;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.impl.ProcessorTaskTestHelper;
import stroom.processor.shared.CreateProcessFilterRequest;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.ProcessorTaskList;
import stroom.processor.shared.QueryData;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.receive.common.ProgressHandler;
import stroom.receive.common.StreamTargetStreamHandlers;
import stroom.receive.common.StroomStreamProcessor;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTranslationTestHelper;
import stroom.test.ContentStoreTestSetup;
import stroom.test.common.StroomCoreServerTestFileUtil;
import stroom.util.date.DateUtil;
import stroom.util.io.DiffUtil;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Indicators;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public abstract class TranslationTest extends AbstractCoreIntegrationTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TranslationTest.class);

    @Inject
    private ProcessorTaskTestHelper processorTaskTestHelper;
    @Inject
    private SteppingService steppingService;
    @Inject
    private FeedStore feedStore;
    @Inject
    private PipelineStore pipelineStore;
    @Inject
    private ProcessorFilterService processorFilterService;
    @Inject
    private Store streamStore;
    @Inject
    private MetaService metaService;
    @Inject
    private ImportExportSerializer importExportSerializer;
    @Inject
    private ContentStoreTestSetup contentStoreTestSetup;
    @Inject
    private CommonTranslationTestHelper commonTranslationTestHelper;
    @Inject
    private StreamTargetStreamHandlers streamHandlers;

    /**
     * NOTE some of the input data for this test is buried in the following zip file so you will need
     * to crack it open to see what is being loaded.
     * stroom/stroom-core/src/test/resources/samples/input/ZIP_TEST-DATA_SPLITTER-EVENTS~1.zip
     */
    protected void testTranslationTask(final boolean translate, final boolean compareOutput) {
        final List<Exception> exceptions = new ArrayList<>();

        final Path samplesDir = getSamplesDir();
        final Path inputDir = samplesDir.resolve("input");
        final Path outputDir = samplesDir.resolve("output");

        FileUtil.mkdirs(outputDir);

        // Process reference data.
        processData(inputDir, outputDir, true, compareOutput, exceptions);
        // Process event data.
        processData(inputDir, outputDir, false, compareOutput, exceptions);

        assertNoExceptions(exceptions);
    }

    private void assertNoExceptions(final List<Exception> exceptions) {
        if (!exceptions.isEmpty()) {
            final StringBuilder sb = new StringBuilder("Test failed with ")
                    .append(exceptions.size())
                    .append(" exceptions:");
            exceptions.forEach(e -> {
                sb.append("\n")
                        .append(e.getMessage());
            });
            sb.append("\nLook further up in the logs for any file diffs.");

            fail(sb.toString());
        }
    }

    protected void testTranslationTask(final String name,
                                       final boolean isReference,
                                       final boolean compareOutput) {
        final List<Exception> exceptions = new ArrayList<>();

        final Path samplesDir = getSamplesDir();
        final Path inputDir = samplesDir.resolve("input");
        final Path outputDir = samplesDir.resolve("output");

        FileUtil.mkdirs(outputDir);

        LOGGER.info("Processing data for {} in {}", name, inputDir.toAbsolutePath().normalize());
        processData(name, inputDir, outputDir, isReference, compareOutput, exceptions);

        assertNoExceptions(exceptions);
    }

    protected void loadAllRefData() {
        final Path samplesDir = getSamplesDir();
        final Path inputDir = samplesDir.resolve("input");
        final Path outputDir = samplesDir.resolve("output");
        FileUtil.mkdirs(outputDir);

        LOGGER.info("Processing ref data in {}", inputDir.toAbsolutePath().normalize());
        // Process reference data.
        final List<Exception> exceptions = new ArrayList<>();
        processData(inputDir, outputDir, true, false, exceptions);
        assertNoExceptions(exceptions);
    }

    protected void importConfig() {
        final Path samplesDir = getSamplesDir();
        final Path configDir = samplesDir.resolve("config");

        importExportSerializer.read(
                configDir,
                null,
                ImportSettings.auto());

        contentStoreTestSetup.installStandardPacks();
    }

    @NotNull
    private Path getSamplesDir() {
        return StroomCoreServerTestFileUtil.getTestResourcesDir().resolve("samples");
    }

    private void processData(final String name,
                             final Path inputDir,
                             final Path outputDir,
                             final boolean reference,
                             final boolean compareOutput,
                             final List<Exception> exceptions) {
        // Create a stream processor for each pipeline.
        final List<DocRef> pipelines = pipelineStore.findByName(name);

        assertThat(pipelines)
                .hasSize(1);

        final DocRef pipelineRef = pipelines.getFirst();

        final List<DocRef> feedRefs = feedStore.findByName(pipelineRef.getName());

        FeedDoc feed = null;
        if (!feedRefs.isEmpty()) {
            feed = feedStore.readDocument(feedRefs.getFirst());
        }
        final FeedDoc feedDoc = feed;

        if (feedDoc != null && feedDoc.isReference() == reference) {
            int priority = 1;
            if (feed.isReference()) {
                priority++;
            }

            final String streamType = feed.isReference()
                    ? StreamTypeNames.RAW_REFERENCE
                    : StreamTypeNames.RAW_EVENTS;

            final QueryData findStreamQueryData = QueryData.builder()
                    .dataSource(MetaFields.STREAM_STORE_DOC_REF)
                    .expression(ExpressionOperator.builder()
                            .addTextTerm(MetaFields.FEED, ExpressionTerm.Condition.EQUALS, feedDoc.getName())
                            .addTextTerm(MetaFields.TYPE, ExpressionTerm.Condition.EQUALS, streamType)
                            .build())
                    .build();

            processorFilterService.create(
                    CreateProcessFilterRequest
                            .builder()
                            .pipeline(pipelineRef)
                            .queryData(findStreamQueryData)
                            .priority(priority)
                            .build());

            // Add data.
            final List<Path> files = new ArrayList<>();
            addFiles(inputDir, files, feed.getName(), "in");
            addFiles(inputDir, files, feed.getName(), "zip");

            files.sort(Comparator.naturalOrder());

            LOGGER.info("Testing {} files: {}",
                    files.size(),
                    files.stream()
                            .map(file ->
                                    file.getFileName().toString())
                            .collect(Collectors.joining(", ")));

            files.forEach(filePath -> {
                // Add and test each file.
                final String fileName = filePath.getFileName().toString();
                final int index = fileName.lastIndexOf(".");
                final String stem = fileName.substring(0, index);

                try {
                    test(filePath, feedDoc, outputDir, stem, compareOutput, exceptions);
                } catch (final IOException | RuntimeException e) {
                    fail(e.getMessage());
                }
            });
        }
    }

    private void processData(final Path inputDir,
                             final Path outputDir,
                             final boolean reference,
                             final boolean compareOutput,
                             final List<Exception> exceptions) {
        // Create a stream processor for each pipeline.
        final List<DocRef> pipelines = pipelineStore.list();
        for (final DocRef pipelineRef : pipelines) {
            // Don't run for trace pipelines as they don't produce output.
            if (!pipelineRef.getName().contains("TRACE")) {
                final FindMetaCriteria findMetaCriteria =
                        new FindMetaCriteria(MetaExpressionUtil.createFeedExpression(pipelineRef.getName()));
                final ResultPage<Meta> metaResultPage = metaService.find(findMetaCriteria);
                if (metaResultPage.isEmpty()) {
                    final List<DocRef> feedRefs = feedStore.findByName(pipelineRef.getName());

                    FeedDoc feed = null;
                    if (!feedRefs.isEmpty()) {
                        feed = feedStore.readDocument(feedRefs.getFirst());
                    }
                    final FeedDoc feedDoc = feed;

                    if (feedDoc != null && feedDoc.isReference() == reference) {
                        int priority = 1;
                        if (feed.isReference()) {
                            priority++;
                        }

                        final String streamType = feed.isReference()
                                ? StreamTypeNames.RAW_REFERENCE
                                : StreamTypeNames.RAW_EVENTS;

                        final QueryData findStreamQueryData = QueryData.builder()
                                .dataSource(MetaFields.STREAM_STORE_DOC_REF)
                                .expression(ExpressionOperator.builder()
                                        .addTextTerm(MetaFields.FEED,
                                                ExpressionTerm.Condition.EQUALS,
                                                feedDoc.getName())
                                        .addTextTerm(MetaFields.TYPE, ExpressionTerm.Condition.EQUALS, streamType)
                                        .build())
                                .build();

                        processorFilterService.create(
                                CreateProcessFilterRequest
                                        .builder()
                                        .pipeline(pipelineRef)
                                        .queryData(findStreamQueryData)
                                        .priority(priority)
                                        .build());

                        // Add data.
                        final List<Path> files = new ArrayList<>();
                        addFiles(inputDir, files, feed.getName(), "in");
                        addFiles(inputDir, files, feed.getName(), "zip");
                        files.sort(Comparator.naturalOrder());
                        files.forEach(filePath -> {
                            // Add and test each file.
                            final String fileName = filePath.getFileName().toString();
                            final int index = fileName.lastIndexOf(".");
                            final String stem = fileName.substring(0, index);

                            try {
                                test(filePath, feedDoc, outputDir, stem, compareOutput, exceptions);
                            } catch (final IOException | RuntimeException e) {
                                fail(e.getMessage());
                            }
                        });
                    }
                }
            }
        }
    }

    private void addFiles(final Path dir,
                          final List<Path> files,
                          final String feed,
                          final String extension) {
        try (final DirectoryStream<Path> stream = Files.newDirectoryStream(dir, feed + "*." + extension)) {
            stream.forEach(files::add);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void test(final Path inputFile,
                      final FeedDoc feed,
                      final Path outputDir,
                      final String stem,
                      final boolean compareOutput,
                      final List<Exception> exceptions) throws IOException {
        LOGGER.info("""
                        Testing:
                        --------------------------------------------------------------------------------
                        input:  {}
                        feed:   {}
                        output: {}
                        stem:   {}
                        --------------------------------------------------------------------------------""",
                inputFile.toAbsolutePath(),
                feed.getName(),
                outputDir.toAbsolutePath(),
                stem);

        addStream(inputFile, feed);

        processorTaskTestHelper.createAndQueueTasks();

        final List<ProcessorTask> tasks = getTasks();
        assertThat(tasks.size())
                .as(() -> "There should be one task here. We found:\n" + tasks.stream()
                        .map(Objects::toString)
                        .collect(Collectors.joining("\n")))
                .isEqualTo(1);

        for (final ProcessorTask task : tasks) {
            final long startStreamId = getLatestStreamId();
            commonTranslationTestHelper.process(task);
            final long endStreamId = getLatestStreamId();

            if (compareOutput) {
                final List<Meta> processedMeta = new ArrayList<>();

                for (long streamId = startStreamId + 1; streamId <= endStreamId; streamId++) {
                    final Meta meta = metaService.getMeta(streamId);
                    final String streamTypeName = meta.getTypeName();
                    if (!StreamTypeNames.ERROR.equals(streamTypeName)) {
                        processedMeta.add(meta);
                    } else {
                        try (final Source errorStreamSource = streamStore.openSource(streamId)) {
                            final String errorStreamStr = SourceUtil.readString(errorStreamSource);

//                            try (final InputStreamProvider inputStreamProvider = errorStreamSource.get(0)) {
                            //got an error stream so dump it to console

                            final Meta parentMeta = metaService.getMeta(meta.getParentMetaId());

//                                String errorStreamStr = StreamUtil.streamToString(inputStreamProvider.get());
//                                java.util.stream.Stream<String> errorStreamLines = StreamUtil.streamToLines(
//                                inputStreamProvider.get());
                            LOGGER.warn("Meta {} with parent {} of type {} has errors:\n{}",
                                    meta, parentMeta.getId(), parentMeta.getTypeName(), errorStreamStr);

//                            // only dump warning if debug enabled
//                            if (LOGGER.isDebugEnabled()) {
//                                errorStreamLines.forEach(System.out::println);
//
//                            } else {
//                                errorStreamLines
//                                        .filter(line -> line.contains("ERROR:"))
//                                        .forEach(System.out::println);
//                            }
//                            }
                        }
                    }
                }

                // Make sure we have at least one processed stream else it indicates an error in processing somewhere
                // If we get an error stream you can just run the pipeline in stroom, to try and diagnose the fault
                // if the above error stream dump doesn't help
                assertThat(processedMeta.size())
                        .withFailMessage("Processed count should be > 0")
                        .isGreaterThan(0);

                // Copy the contents of the latest written stream to the output.
                int i = 1;
                for (final Meta meta : processedMeta) {
                    String num = "";
                    // If we are going to output more than one file then number
                    // them.
                    if (processedMeta.size() > 1) {
                        num = "_" + i;
                    }

                    final Path actualFile = outputDir.resolve(stem + num + ".out_tmp");
                    final Path expectedFile = outputDir.resolve(stem + num + ".out");

                    try (final OutputStream outputStream = new BufferedOutputStream(
                            Files.newOutputStream(actualFile))) {
                        copyStream(meta, outputStream);
                    }

                    compareFiles(expectedFile, actualFile, exceptions);

                    i++;
                }
            }
        }

        // Make sure there are no more tasks.
        final List<ProcessorTask> tasks2 = getTasks();
        assertThat(tasks2.size())
                .as(() -> "There should not be any more tasks here. We found:\n" + tasks.stream()
                        .map(Objects::toString)
                        .collect(Collectors.joining("\n")))
                .isZero();
    }

    private void addStream(final Path file, final FeedDoc feed) throws IOException {
        if (file.getFileName().toString().endsWith(".zip")) {
            loadZipData(file, feed);

        } else {
            // Add the associated data to the stream store.
            final String streamTypeName;
            final long millis;
            if (feed.isReference()) {
                streamTypeName = StreamTypeNames.RAW_REFERENCE;

                // We need to ensure the reference data is older then the earliest
                // event we are going to see. In the case of these component tests
                // we have some events from 2007.
                millis = DateUtil.parseNormalDateTimeString("2006-01-01T00:00:00.000Z");
            } else {
                streamTypeName = StreamTypeNames.RAW_EVENTS;
                millis = DateUtil.parseNormalDateTimeString("2006-04-01T00:00:00.000Z");
            }

            // Create the stream.
            final MetaProperties metaProperties = MetaProperties.builder()
                    .feedName(feed.getName())
                    .typeName(streamTypeName)
                    .createMs(millis)
                    .build();

            final Meta meta;
            try (final Target target = streamStore.openTarget(metaProperties)) {
                meta = target.getMeta();
                final InputStream inputStream = new BufferedInputStream(Files.newInputStream(file));
                TargetUtil.write(target, inputStream);
            }

            // Check that what was written to the store is the same as the
            // contents of the file.
            try (final Source checkSource = streamStore.openSource(meta.getId())) {
                final byte[] original = Files.readAllBytes(file);
                final byte[] stored = SourceUtil.read(checkSource);
                assertThat(Arrays.equals(original, stored)).isTrue();
            }
        }
    }

    private void loadZipData(final Path file, final FeedDoc feed) throws IOException {
        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put(StandardHeaderArguments.COMPRESSION, StandardHeaderArguments.COMPRESSION_ZIP);

        final ProgressHandler progressHandler = new ProgressHandler("Test");
        try (final InputStream inputStream = Files.newInputStream(file)) {
            streamHandlers.handle(feed.getName(), feed.getStreamType(), attributeMap, handler -> {
                final StroomStreamProcessor stroomStreamProcessor = new StroomStreamProcessor(
                        attributeMap,
                        handler,
                        progressHandler);
                stroomStreamProcessor.processInputStream(inputStream, "test");
            });
        }
    }

    /**
     * Gets the next task to be processed.
     *
     * @return The next task or null if there are currently no more tasks.
     */
    private List<ProcessorTask> getTasks() {
        ProcessorTaskList processorTasks = processorTaskTestHelper.assignTasks(100);
        List<ProcessorTask> list = processorTasks.getList();
        final List<ProcessorTask> dataProcessorTasks = new ArrayList<>(list.size());
        while (!list.isEmpty()) {
            dataProcessorTasks.addAll(list);
            processorTasks = processorTaskTestHelper.assignTasks(100);
            list = processorTasks.getList();
        }

        return dataProcessorTasks;
    }

    protected void testSteppingTask(final String feedName, final Path dir) {
        final List<Exception> exceptions = new ArrayList<>();

        // feedCriteria.setFeedType(FeedType.REFERENCE);
//        final Optional<FeedDoc> feeds = feedDocCache.get(feedName);
//        assertThat(feeds.isPresent()).as("No feeds found").isTrue();
        final List<DocRef> pipelines = pipelineStore.findByName(feedName);
        assertThat(pipelines != null && !pipelines.isEmpty())
                .as("No pipelines found")
                .isTrue();
        assertThat(pipelines.size()).as("Expected 1 pipeline")
                .isEqualTo(1);

        final DocRef pipelineRef = pipelines.getFirst();
//        final FeedDoc feed = feeds.get();

        LOGGER.info("Testing: {}, {}, {}",
                feedName,
                dir.toAbsolutePath().normalize(),
                pipelineRef.getName());

        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTextTerm(MetaFields.FEED, Condition.EQUALS, feedName)
                .addOperator(ExpressionOperator.builder().op(Op.OR)
                        .addTextTerm(MetaFields.TYPE, Condition.EQUALS, StreamTypeNames.RAW_REFERENCE)
                        .addTextTerm(MetaFields.TYPE, Condition.EQUALS, StreamTypeNames.RAW_EVENTS)
                        .build())
                .build();

        final FindMetaCriteria findMetaCriteria = new FindMetaCriteria(expression);

        final PipelineStepRequest.Builder requestBuilder = PipelineStepRequest.builder();
        requestBuilder.pipeline(pipelineRef);
        requestBuilder.criteria(findMetaCriteria);
        requestBuilder.timeout(Long.MAX_VALUE);

        SteppingResult response = new SteppingResult(
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                false,
                false);
        response = step(StepType.FORWARD, 40, requestBuilder, response);
        // Check that we get no overflow.
        response = step(StepType.FORWARD, 10, requestBuilder, response);
        response = step(StepType.BACKWARD, 9, requestBuilder, response);
        response = step(StepType.REFRESH, 9, requestBuilder, response);
        response = step(StepType.BACKWARD, 30, requestBuilder, response);
        // Check that we get no overflow.
        response = step(StepType.BACKWARD, 10, requestBuilder, response);
        response = step(StepType.FORWARD, 9, requestBuilder, response);
        response = step(StepType.REFRESH, 9, requestBuilder, response);

        // Jump to the last record.
        response = step(StepType.LAST, 2, requestBuilder, response);
        // Make sure there is no overflow.
        response = step(StepType.FORWARD, 1, requestBuilder, response);
        // Come back 2.
        response = step(StepType.BACKWARD, 2, requestBuilder, response);
        // Jump to the first record.
        response = step(StepType.FIRST, 2, requestBuilder, response);
        // Make sure there is no overflow.
        response = step(StepType.BACKWARD, 1, requestBuilder, response);
        // Go forward 2.
        response = step(StepType.FORWARD, 2, requestBuilder, response);

        final SharedStepData stepData = response.getStepData();
        for (final String elementId : stepData.getElementMap().keySet()) {
            final SharedElementData elementData = stepData.getElementData(elementId);
            assertThat(elementData.getIndicators() != null
                       && elementData.getIndicators().getMaxSeverity() != null).as(
                    "Translation stepping has indicators.").isFalse();
//            assertThat(elementData.getCodeIndicators() != null
//                    && elementData.getCodeIndicators().getMaxSeverity() != null).as(
//                    "Translation stepping has code indicators.").isFalse();

            final String stem = feedName + "~STEPPING~" + elementId;
            if (elementData.getInput() != null) {
                final Path actualFile = dir.resolve(stem + "~input.out_tmp");
                final Path expectedFile = dir.resolve(stem + "~input.out");
                write(actualFile, elementData.getInput());
                compareFiles(expectedFile, actualFile, exceptions);
            }
            if (elementData.getOutput() != null) {
                final Path actualFile = dir.resolve(stem + "~output.out_tmp");
                final Path expectedFile = dir.resolve(stem + "~output.out");
                write(actualFile, elementData.getOutput());
                compareFiles(expectedFile, actualFile, exceptions);
            }
        }

        assertNoExceptions(exceptions);
    }

    private SteppingResult step(final StepType direction,
                                final int steps,
                                final PipelineStepRequest.Builder requestBuilder,
                                final SteppingResult existingResponse) {
        SteppingResult newResponse = existingResponse;

        for (int i = 0; i < steps; i++) {
            requestBuilder.stepType(direction);
            final SteppingResult stepResponse = steppingService.step(requestBuilder.build());

            if (stepResponse.getGeneralErrors() != null && !stepResponse.getGeneralErrors().isEmpty()) {
                throw new RuntimeException(stepResponse.getGeneralErrors().iterator().next());
            }

            if (stepResponse.isFoundRecord()) {
                final SharedStepData stepData = stepResponse.getStepData();
                for (final String elementId : stepData.getElementMap().keySet()) {
                    String input = null;
                    String output = null;
//                    Indicators codeIndicators = null;
//                    Indicators outputIndicators = null;
                    Indicators indicators = null;

                    // Get existing data.
                    if (newResponse != null) {
                        final SharedStepData existingStepData = newResponse.getStepData();
                        if (existingStepData != null) {
                            final SharedElementData existingElementData = existingStepData.getElementData(elementId);
                            if (existingElementData != null) {
                                input = existingElementData.getInput();
                                output = existingElementData.getOutput();
//                                codeIndicators = existingElementData.getCodeIndicators();
//                                outputIndicators = existingElementData.getOutputIndicators();
                                indicators = existingElementData.getIndicators();
                            }
                        }
                    }

                    // Append new data.
                    final SharedElementData elementData = stepData.getElementData(elementId);
                    if (elementData != null) {
                        if (input == null) {
                            input = elementData.getInput();
                        } else {
                            input += "\n" + elementData.getInput();
                        }

                        if (output == null) {
                            output = elementData.getOutput();
                        } else {
                            output += "\n" + elementData.getOutput();
                        }

                        if (indicators == null) {
                            indicators = elementData.getIndicators();
                        } else {
                            indicators.addAll(elementData.getIndicators());
                        }

//                        if (outputIndicators == null) {
//                            outputIndicators = elementData.getOutputIndicators();
//                        } else {
//                            outputIndicators.addAll(elementData.getOutputIndicators());
//                        }

                        final SharedElementData newElementData = new SharedElementData(
                                input, output, indicators, elementData.isFormatInput(), elementData.isFormatOutput());
                        final SharedStepData newStepData = NullSafe.getOrElseGet(
                                newResponse,
                                SteppingResult::getStepData,
                                () -> new SharedStepData(stepResponse.getStepData().getSourceLocation(),
                                        new HashMap<>()));
                        newStepData.getElementMap().put(elementId, newElementData);
                        newResponse = new SteppingResult(
                                null,
                                stepResponse.getStepFilterMap(),
                                stepResponse.getProgressLocation(),
                                stepResponse.getFoundLocation(),
                                newStepData,
                                stepResponse.getCurrentStreamOffset(),
                                stepResponse.isFoundRecord(),
                                null,
                                stepResponse.isSegmentedData(),
                                true);
                    }
                }

                // Set the request to use the last response location to move on
                // from.
                requestBuilder.stepLocation(stepResponse.getFoundLocation());
            }
        }

        return newResponse;
    }

    private void write(final Path file, final String data) {
        // We need to remove event id's because they change every time.
        final String tmp = data.replaceAll("<Event Id=\"[^\"]+\"", "<Event");
        StreamUtil.stringToFile(tmp, file);
    }

    private long getLatestStreamId() {
        final List<Meta> list = metaService.find(new FindMetaCriteria()).getValues();
        if (list == null || list.isEmpty()) {
            return 0;
        }
        list.sort(Comparator.comparing(Meta::getId));
        final Meta latest = list.getLast();
        return latest.getId();
    }

    private void copyStream(final Meta meta, final OutputStream outputStream) throws IOException {
        try (final Source streamSource = streamStore.openSource(meta.getId())) {
            SourceUtil.read(streamSource, outputStream);
        }
    }

    private void compareFiles(final Path expectedFile, final Path actualFile, final List<Exception> exceptions) {
        try {
            final boolean areFilesTheSame = !DiffUtil.unifiedDiff(
                    expectedFile, actualFile, true, 3);
            if (areFilesTheSame) {
                Files.deleteIfExists(actualFile);
            } else {
                LOGGER.error("Differences exist between the expected and actual output");
                LOGGER.info("\nvimdiff {} {}", expectedFile, actualFile);
                LOGGER.info("If you are satisfied the actual output is correct then copy " +
                            "the actual over the expected and re-run.");
                throw new RuntimeException(LogUtil.message("Files are not the same:\n{}\n{}",
                        FileUtil.getCanonicalPath(actualFile),
                        FileUtil.getCanonicalPath(expectedFile)));
            }
        } catch (final IOException | RuntimeException e) {
            exceptions.add(e);
        }
    }
}
