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
 *
 */

package stroom.pipeline.task;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.data.store.api.Source;
import stroom.data.store.api.SourceUtil;
import stroom.data.store.api.Store;
import stroom.data.store.api.Target;
import stroom.data.store.api.TargetUtil;
import stroom.docref.DocRef;
import stroom.feed.api.FeedProperties;
import stroom.feed.api.FeedStore;
import stroom.feed.shared.FeedDoc;
import stroom.importexport.impl.ImportExportSerializer;
import stroom.importexport.shared.ImportState.ImportMode;
import stroom.meta.shared.AttributeMap;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.MetaProperties;
import stroom.meta.shared.MetaService;
import stroom.meta.shared.StandardHeaderArguments;
import stroom.node.api.NodeInfo;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.SharedElementData;
import stroom.pipeline.shared.SharedStepData;
import stroom.pipeline.shared.StepType;
import stroom.pipeline.shared.SteppingResult;
import stroom.pipeline.stepping.SteppingTask;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.api.ProcessorService;
import stroom.processor.impl.DataProcessorTask;
import stroom.processor.impl.ProcessorTaskManager;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.QueryData;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.receive.common.StreamTargetStroomStreamHandler;
import stroom.receive.common.StroomStreamProcessor;
import stroom.security.api.UserTokenUtil;
import stroom.data.shared.StreamTypeNames;
import stroom.task.api.SimpleTaskContext;
import stroom.task.api.TaskManager;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.ContentImportService;
import stroom.test.common.ComparisonHelper;
import stroom.test.common.StroomCoreServerTestFileUtil;
import stroom.util.date.DateUtil;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.shared.BaseResultList;
import stroom.util.shared.Indicators;

import javax.inject.Inject;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public abstract class TranslationTest extends AbstractCoreIntegrationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TranslationTest.class);

    @Inject
    private NodeInfo nodeInfo;
    @Inject
    private ProcessorTaskManager processorTaskManager;
    @Inject
    private TaskManager taskManager;
    @Inject
    private FeedStore feedStore;
    @Inject
    private PipelineStore pipelineStore;
    @Inject
    private ProcessorService streamProcessorService;
    @Inject
    private ProcessorFilterService processorFilterService;
    @Inject
    private Store streamStore;
    @Inject
    private MetaService metaService;
    @Inject
    private FeedProperties feedProperties;
    @Inject
    private ImportExportSerializer importExportSerializer;
    @Inject
    private ContentImportService contentImportService;

    /**
     * NOTE some of the input data for this test is buried in the following zip file so you will need
     * to crack it open to see what is being loaded.
     * stroom/stroom-core/src/test/resources/samples/input/ZIP_TEST-DATA_SPLITTER-EVENTS~1.zip
     */
    protected void testTranslationTask(final boolean translate, final boolean compareOutput) {
        final List<Exception> exceptions = new ArrayList<>();

        final Path dir = StroomCoreServerTestFileUtil.getTestResourcesDir().resolve("samples");
        final Path configDir = dir.resolve("config");
        final Path inputDir = dir.resolve("input");
        final Path outputDir = dir.resolve("output");

        FileUtil.mkdirs(outputDir);

        importExportSerializer.read(configDir, null, ImportMode.IGNORE_CONFIRMATION);

        contentImportService.importXmlSchemas();

        // Process reference data.
        processData(inputDir, outputDir, true, compareOutput, exceptions);
        // Process event data.
        processData(inputDir, outputDir, false, compareOutput, exceptions);

        if (exceptions.size() > 0) {
            fail(exceptions.get(0).getMessage());
        }
    }

    private void processData(final Path inputDir, final Path outputDir, final boolean reference,
                             final boolean compareOutput, final List<Exception> exceptions) {
        // Create a stream processor for each pipeline.
        final List<DocRef> pipelines = pipelineStore.list();
        for (final DocRef pipelineRef : pipelines) {
            final List<DocRef> feedRefs = feedStore.findByName(pipelineRef.getName());

            FeedDoc feed = null;
            if (feedRefs.size() > 0) {
                feed = feedStore.readDocument(feedRefs.get(0));
            }
            final FeedDoc feedDoc = feed;

            if (feedDoc != null && feedDoc.isReference() == reference) {
                int priority = 1;
                if (feed.isReference()) {
                    priority++;
                }

                final String streamType = feed.isReference() ?
                        StreamTypeNames.RAW_REFERENCE : StreamTypeNames.RAW_EVENTS;
                final QueryData findStreamQueryData = new QueryData.Builder()
                        .dataSource(MetaFields.STREAM_STORE_DOC_REF)
                        .expression(new ExpressionOperator.Builder(Op.AND)
                                .addTerm(MetaFields.FEED_NAME, ExpressionTerm.Condition.EQUALS, feedDoc.getName())
                                .addTerm(MetaFields.TYPE_NAME, ExpressionTerm.Condition.EQUALS, streamType)
                                .build())
                        .build();

                processorFilterService.create(pipelineRef, findStreamQueryData, priority, true);

                // Add data.
                final List<Path> files = new ArrayList<>();
                addFiles(inputDir, files, feed.getName(), "in");
                addFiles(inputDir, files, feed.getName(), "zip");
                files.sort(Comparator.naturalOrder());
                files.forEach(p -> {
                    // Add and test each file.
                    final String fileName = p.getFileName().toString();
                    final int index = fileName.lastIndexOf(".");
                    final String stem = fileName.substring(0, index);

                    try {
                        test(p, feedDoc, outputDir, stem, compareOutput, exceptions);
                    } catch (final IOException | RuntimeException e) {
                        fail(e.getMessage());
                    }
                });
            }
        }
    }

    private void addFiles(final Path dir, final List<Path> files, final String feed, final String extension) {
        try (final DirectoryStream<Path> stream = Files.newDirectoryStream(dir, feed + "*." + extension)) {
            stream.forEach(files::add);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void test(final Path inputFile, final FeedDoc feed, final Path outputDir, final String stem,
                      final boolean compareOutput, final List<Exception> exceptions) throws IOException {
        LOGGER.info("Testing input {}, feed {}, output {}, stem {}",
                inputFile.getFileName().toString(), feed.getName(), outputDir.getFileName().toString(), stem);

        addStream(inputFile, feed);

        processorTaskManager.createTasks(new SimpleTaskContext());

        List<DataProcessorTask> tasks = getTasks();
        assertThat(tasks.size()).as("There should be one task here").isEqualTo(1);

        for (final DataProcessorTask task : tasks) {
            final long startStreamId = getLatestStreamId();
            taskManager.exec(task);
            final long endStreamId = getLatestStreamId();

            if (compareOutput) {
                final List<Meta> processedMeta = new ArrayList<>();

                for (long streamId = startStreamId + 1; streamId <= endStreamId; streamId++) {
                    final Meta meta = metaService.getMeta(streamId);
                    final String streamTypeName = meta.getTypeName();
                    if (!StreamTypeNames.ERROR.equals(streamTypeName)) {
                        processedMeta.add(meta);
                    } else {
                        try (Source errorStreamSource = streamStore.openSource(streamId)) {
                            String errorStreamStr = SourceUtil.readString(errorStreamSource);

//                            try (final InputStreamProvider inputStreamProvider = errorStreamSource.get(0)) {
                                //got an error stream so dump it to console

                                Meta parentMeta = metaService.getMeta(meta.getParentMetaId());

//                                String errorStreamStr = StreamUtil.streamToString(inputStreamProvider.get());
//                                java.util.stream.Stream<String> errorStreamLines = StreamUtil.streamToLines(inputStreamProvider.get());
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
                assertThat(processedMeta.size()).isGreaterThan(0);

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

                    try (final OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(actualFile))) {
                        copyStream(meta, outputStream);
                    }

                    compareFiles(expectedFile, actualFile, exceptions);

                    i++;
                }
            }
        }

        // Make sure there are no more tasks.
        tasks = getTasks();
        assertThat(tasks.size()).as("There should not be any more tasks here").isZero();
    }

    private void addStream(final Path file, final FeedDoc feed) throws IOException {
        if (file.getFileName().toString().endsWith(".zip")) {
            loadZipData(file, feed);

        } else {
            // Add the associated data to the stream store.
            String streamTypeName;
            long millis;
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
            final MetaProperties metaProperties = new MetaProperties.Builder()
                    .feedName(feed.getName())
                    .typeName(streamTypeName)
                    .createMs(millis)
                    .build();

            Meta meta;
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

        final List<StreamTargetStroomStreamHandler> handlerList = StreamTargetStroomStreamHandler
                .buildSingleHandlerList(streamStore, feedProperties, null, feed.getName(), feed.getStreamType());

        final StroomStreamProcessor stroomStreamProcessor = new StroomStreamProcessor(attributeMap, handlerList, new byte[1000],
                "DefaultDataFeedRequest-");

        stroomStreamProcessor.process(Files.newInputStream(file), "test");
        stroomStreamProcessor.closeHandlers();
    }

    /**
     * Gets the next task to be processed.
     *
     * @return The next task or null if there are currently no more tasks.
     */
    private List<DataProcessorTask> getTasks() {
        List<DataProcessorTask> dataProcessorTasks = Collections.emptyList();

        List<ProcessorTask> processorTasks = processorTaskManager.assignTasks(nodeInfo.getThisNodeName(), 100);
        while (processorTasks.size() > 0) {
            dataProcessorTasks = new ArrayList<>(processorTasks.size());
            for (final ProcessorTask processorTask : processorTasks) {
                dataProcessorTasks.add(new DataProcessorTask(processorTask));
            }
            processorTasks = processorTaskManager.assignTasks(nodeInfo.getThisNodeName(), 100);
        }

        return dataProcessorTasks;
    }

    protected void testSteppingTask(final String feedName, final Path dir) throws IOException {
        final List<Exception> exceptions = new ArrayList<>();

        // feedCriteria.setFeedType(FeedType.REFERENCE);
//        final Optional<FeedDoc> feeds = feedDocCache.get(feedName);
//        assertThat(feeds.isPresent()).as("No feeds found").isTrue();
        final List<DocRef> pipelines = pipelineStore.findByName(feedName);
        assertThat(pipelines != null && pipelines.size() > 0).as("No pipelines found").isTrue();
        assertThat(pipelines.size()).as("Expected 1 pipeline").isEqualTo(1);

        final DocRef pipelineRef = pipelines.get(0);
//        final FeedDoc feed = feeds.get();

        final ExpressionOperator expression = new ExpressionOperator.Builder(Op.AND)
                .addTerm(MetaFields.FEED_NAME, Condition.EQUALS, feedName)
                .addOperator(new ExpressionOperator.Builder(Op.OR)
                        .addTerm(MetaFields.TYPE_NAME, Condition.EQUALS, StreamTypeNames.RAW_REFERENCE)
                        .addTerm(MetaFields.TYPE_NAME, Condition.EQUALS, StreamTypeNames.RAW_EVENTS)
                        .build())
                .build();

        final FindMetaCriteria findMetaCriteria = new FindMetaCriteria();
        findMetaCriteria.setExpression(expression);
        findMetaCriteria.obtainSelectedIdSet().setMatchAll(Boolean.TRUE);

        final SteppingTask action = new SteppingTask(UserTokenUtil.processingUser());
        action.setPipeline(pipelineRef);
        action.setCriteria(findMetaCriteria);

        SteppingResult response = new SteppingResult();
        response = step(StepType.FORWARD, 40, action, response);
        // Check that we get no overflow.
        response = step(StepType.FORWARD, 10, action, response);
        response = step(StepType.BACKWARD, 9, action, response);
        response = step(StepType.REFRESH, 9, action, response);
        response = step(StepType.BACKWARD, 30, action, response);
        // Check that we get no overflow.
        response = step(StepType.BACKWARD, 10, action, response);
        response = step(StepType.FORWARD, 9, action, response);
        response = step(StepType.REFRESH, 9, action, response);

        // Jump to the last record.
        response = step(StepType.LAST, 2, action, response);
        // Make sure there is no overflow.
        response = step(StepType.FORWARD, 1, action, response);
        // Come back 2.
        response = step(StepType.BACKWARD, 2, action, response);
        // Jump to the first record.
        response = step(StepType.FIRST, 2, action, response);
        // Make sure there is no overflow.
        response = step(StepType.BACKWARD, 1, action, response);
        // Go forward 2.
        response = step(StepType.FORWARD, 2, action, response);

        final SharedStepData stepData = response.getStepData();
        for (final String elementId : stepData.getElementMap().keySet()) {
            final SharedElementData elementData = stepData.getElementData(elementId);
            assertThat(elementData.getOutputIndicators() != null
                    && elementData.getOutputIndicators().getMaxSeverity() != null).as("Translation stepping has output indicators.").isFalse();
            assertThat(elementData.getCodeIndicators() != null
                    && elementData.getCodeIndicators().getMaxSeverity() != null).as("Translation stepping has code indicators.").isFalse();

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

        if (exceptions.size() > 0) {
            fail(exceptions.get(0).getMessage());
        }
    }

    private SteppingResult step(final StepType direction, final int steps, final SteppingTask request,
                                final SteppingResult existingResponse) {
        SteppingResult newResponse = existingResponse;

        for (int i = 0; i < steps; i++) {
            request.setStepType(direction);
            final SteppingResult stepResponse = taskManager.exec(request);

            if (stepResponse.getGeneralErrors() != null && stepResponse.getGeneralErrors().size() > 0) {
                throw new RuntimeException(stepResponse.getGeneralErrors().iterator().next());
            }

            if (stepResponse.isFoundRecord()) {
                final SharedStepData stepData = stepResponse.getStepData();
                for (final String elementId : stepData.getElementMap().keySet()) {
                    String input = null;
                    String output = null;
                    Indicators codeIndicators = null;
                    Indicators outputIndicators = null;

                    // Get existing data.
                    if (newResponse != null) {
                        final SharedStepData existingStepData = newResponse.getStepData();
                        if (existingStepData != null) {
                            final SharedElementData existingElementData = existingStepData.getElementData(elementId);
                            if (existingElementData != null) {
                                input = existingElementData.getInput();
                                output = existingElementData.getOutput();
                                codeIndicators = existingElementData.getCodeIndicators();
                                outputIndicators = existingElementData.getOutputIndicators();
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

                        if (codeIndicators == null) {
                            codeIndicators = elementData.getCodeIndicators();
                        } else {
                            codeIndicators.addAll(elementData.getCodeIndicators());
                        }

                        if (outputIndicators == null) {
                            outputIndicators = elementData.getOutputIndicators();
                        } else {
                            outputIndicators.addAll(elementData.getOutputIndicators());
                        }

                        final SharedElementData newElementData = new SharedElementData(input, output, codeIndicators,
                                outputIndicators, elementData.isFormatInput(), elementData.isFormatOutput());
                        SharedStepData newStepData = newResponse.getStepData();
                        if (newStepData == null) {
                            newStepData = new SharedStepData(stepResponse.getStepData().getSourceHighlights(),
                                    new HashMap<>());
                        }
                        newStepData.getElementMap().put(elementId, newElementData);
                        newResponse = new SteppingResult(stepResponse.getStepFilterMap(),
                                stepResponse.getStepLocation(), newStepData, stepResponse.getCurrentStreamOffset(),
                                stepResponse.isFoundRecord(), null);
                    }
                }

                // Set the request to use the last response location to move on
                // from.
                request.setStepLocation(stepResponse.getStepLocation());
            }
        }

        return newResponse;
    }

    private void write(final Path file, final String data) throws IOException {
        // We need to remove event id's because they change every time.
        final String tmp = data.replaceAll("<Event Id=\"[^\"]+\"", "<Event");
        StreamUtil.stringToFile(tmp, file);
    }

    private long getLatestStreamId() {
        final BaseResultList<Meta> list = metaService.find(new FindMetaCriteria());
        if (list == null || list.size() == 0) {
            return 0;
        }
        list.sort(Comparator.comparing(Meta::getId));
        final Meta latest = list.get(list.size() - 1);
        return latest.getId();
    }

    private void copyStream(final Meta meta, final OutputStream outputStream) throws IOException {
        try (final Source streamSource = streamStore.openSource(meta.getId())) {
            SourceUtil.read(streamSource, outputStream);
        }
    }

    private void compareFiles(final Path expectedFile, final Path actualFile, final List<Exception> exceptions) {
        try {
            ComparisonHelper.compare(expectedFile, actualFile, false, true);
            Files.deleteIfExists(actualFile);
        } catch (final IOException | RuntimeException e) {
            exceptions.add(e);
        }
    }
}
