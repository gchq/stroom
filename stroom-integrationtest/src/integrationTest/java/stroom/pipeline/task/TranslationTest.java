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

import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docref.DocRef;
import stroom.entity.shared.BaseResultList;
import stroom.feed.FeedDocCache;
import stroom.feed.FeedStore;
import stroom.feed.MetaMap;
import stroom.feed.StroomHeaderArguments;
import stroom.feed.shared.FeedDoc;
import stroom.importexport.ImportExportSerializer;
import stroom.importexport.shared.ImportState.ImportMode;
import stroom.node.NodeCache;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.SharedElementData;
import stroom.pipeline.shared.SharedStepData;
import stroom.pipeline.shared.StepType;
import stroom.pipeline.shared.SteppingResult;
import stroom.pipeline.stepping.SteppingTask;
import stroom.proxy.repo.StroomStreamProcessor;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.security.UserTokenUtil;
import stroom.streamstore.api.StreamSource;
import stroom.streamstore.api.StreamStore;
import stroom.streamstore.api.StreamTarget;
import stroom.streamstore.fs.serializable.RASegmentOutputStream;
import stroom.streamstore.fs.serializable.RawInputSegmentWriter;
import stroom.streamstore.meta.api.FindStreamCriteria;
import stroom.streamstore.meta.api.Stream;
import stroom.streamstore.meta.api.StreamMetaService;
import stroom.streamstore.meta.api.StreamProperties;
import stroom.streamstore.shared.QueryData;
import stroom.streamstore.shared.StreamDataSource;
import stroom.streamstore.shared.StreamTypeNames;
import stroom.streamtask.StreamProcessorFilterService;
import stroom.streamtask.StreamProcessorService;
import stroom.streamtask.StreamProcessorTask;
import stroom.streamtask.StreamTargetStroomStreamHandler;
import stroom.streamtask.StreamTaskCreator;
import stroom.streamtask.shared.Processor;
import stroom.streamtask.shared.ProcessorFilterTask;
import stroom.task.SimpleTaskContext;
import stroom.task.TaskManager;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.ComparisonHelper;
import stroom.test.ContentImportService;
import stroom.test.StroomCoreServerTestFileUtil;
import stroom.util.date.DateUtil;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
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
import java.util.Optional;

public abstract class TranslationTest extends AbstractCoreIntegrationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TranslationTest.class);

    @Inject
    private NodeCache nodeCache;
    @Inject
    private StreamTaskCreator streamTaskCreator;
    @Inject
    private TaskManager taskManager;
    @Inject
    private FeedStore feedStore;
    @Inject
    private PipelineStore pipelineStore;
    @Inject
    private StreamProcessorService streamProcessorService;
    @Inject
    private StreamProcessorFilterService streamProcessorFilterService;
    @Inject
    private StreamStore streamStore;
    @Inject
    private StreamMetaService streamMetaService;
    @Inject
    private FeedDocCache feedDocCache;
    @Inject
    private ImportExportSerializer importExportSerializer;
    @Inject
    private ContentImportService contentImportService;

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
            Assert.fail(exceptions.get(0).getMessage());
        }
    }

    protected void processData(final Path inputDir, final Path outputDir, final boolean reference,
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
                Processor streamProcessor = new Processor();
                streamProcessor.setPipelineUuid(pipelineRef.getUuid());
                streamProcessor.setEnabled(true);
                streamProcessor = streamProcessorService.save(streamProcessor);

                int priority = 1;
                if (feed.isReference()) {
                    priority++;
                }

                final String streamType = feed.isReference() ?
                        StreamTypeNames.RAW_REFERENCE : StreamTypeNames.RAW_EVENTS;
                final QueryData findStreamQueryData = new QueryData.Builder()
                        .dataSource(StreamDataSource.STREAM_STORE_DOC_REF)
                        .expression(new ExpressionOperator.Builder(Op.AND)
                                .addTerm(StreamDataSource.FEED, ExpressionTerm.Condition.EQUALS, feedDoc.getName())
                                .addTerm(StreamDataSource.STREAM_TYPE, ExpressionTerm.Condition.EQUALS, streamType)
                                .build())
                        .build();

                streamProcessorFilterService.addFindStreamCriteria(streamProcessor, priority, findStreamQueryData);

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
                        Assert.fail(e.getMessage());
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

        streamTaskCreator.createTasks(new SimpleTaskContext());

        List<StreamProcessorTask> tasks = getTasks();
        Assert.assertTrue("There should be one task here", tasks.size() == 1);

        for (final StreamProcessorTask task : tasks) {
            final long startStreamId = getLatestStreamId();
            taskManager.exec(task);
            final long endStreamId = getLatestStreamId();

            if (compareOutput) {
                final List<Stream> processedStreams = new ArrayList<>();

                for (long streamId = startStreamId + 1; streamId <= endStreamId; streamId++) {
                    final Stream stream = streamMetaService.getStream(streamId);
                    final String streamTypeName = stream.getStreamTypeName();
                    if (!StreamTypeNames.ERROR.equals(streamTypeName)) {
                        processedStreams.add(stream);
                    }
                }

                // Make sure we have at least one processed stream else it indicates an error in processing somewhere
                // TODO : If we get an error stream would be good to dump it out to make debugging easier
                // or you can just run the pipeline in stroom
                Assert.assertTrue(processedStreams.size() > 0);

                // Copy the contents of the latest written stream to the output.
                int i = 1;
                for (final Stream processedStream : processedStreams) {
                    String num = "";
                    // If we are going to output more than one file then number
                    // them.
                    if (processedStreams.size() > 1) {
                        num = "_" + String.valueOf(i);
                    }

                    final Path actualFile = outputDir.resolve(stem + num + ".out_tmp");
                    final Path expectedFile = outputDir.resolve(stem + num + ".out");

                    try (final OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(actualFile))) {
                        copyStream(processedStream, outputStream);
                    }

                    compareFiles(expectedFile, actualFile, exceptions);

                    i++;
                }
            }
        }

        // Make sure there are no more tasks.
        tasks = getTasks();
        Assert.assertTrue("There should not be any more tasks here", tasks.size() == 0);
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
            final StreamProperties streamProperties = new StreamProperties.Builder()
                    .feedName(feed.getName())
                    .streamTypeName(streamTypeName)
                    .createMs(millis)
                    .build();
            final StreamTarget target = streamStore.openStreamTarget(streamProperties);

            final InputStream inputStream = new BufferedInputStream(Files.newInputStream(file));
            final RASegmentOutputStream outputStream = new RASegmentOutputStream(target);

            final RawInputSegmentWriter writer = new RawInputSegmentWriter();
            writer.write(inputStream, outputStream);

            streamStore.closeStreamTarget(target);

            // Check that what was written to the store is the same as the
            // contents of the file.
            final StreamSource checkSource = streamStore.openStreamSource(target.getStream().getId());
            final byte[] original = Files.readAllBytes(file);
            final byte[] stored = StreamUtil.streamToBytes(checkSource.getInputStream());
            streamStore.closeStreamSource(checkSource);
            Assert.assertTrue(Arrays.equals(original, stored));
        }
    }

    private void loadZipData(final Path file, final FeedDoc feed) throws IOException {
        final MetaMap metaMap = new MetaMap();
        metaMap.put(StroomHeaderArguments.COMPRESSION, StroomHeaderArguments.COMPRESSION_ZIP);

        final List<StreamTargetStroomStreamHandler> handlerList = StreamTargetStroomStreamHandler
                .buildSingleHandlerList(streamStore, feedDocCache, null, feed.getName(), feed.getStreamType());

        final StroomStreamProcessor stroomStreamProcessor = new StroomStreamProcessor(metaMap, handlerList, new byte[1000],
                "DefaultDataFeedRequest-");

        stroomStreamProcessor.process(Files.newInputStream(file), "test");
        stroomStreamProcessor.closeHandlers();
    }

    /**
     * Gets the next task to be processed.
     *
     * @return The next task or null if there are currently no more tasks.
     */
    private List<StreamProcessorTask> getTasks() {
        List<StreamProcessorTask> streamProcessorTasks = Collections.emptyList();

        List<ProcessorFilterTask> streamTasks = streamTaskCreator.assignStreamTasks(nodeCache.getDefaultNode(), 100);
        while (streamTasks.size() > 0) {
            streamProcessorTasks = new ArrayList<>(streamTasks.size());
            for (final ProcessorFilterTask streamTask : streamTasks) {
                streamProcessorTasks.add(new StreamProcessorTask(streamTask));
            }
            streamTasks = streamTaskCreator.assignStreamTasks(nodeCache.getDefaultNode(), 100);
        }

        return streamProcessorTasks;
    }

    protected void testSteppingTask(final String feedName, final Path dir) throws IOException {
        final List<Exception> exceptions = new ArrayList<>();

        // feedCriteria.setFeedType(FeedType.REFERENCE);
        final Optional<FeedDoc> feeds = feedDocCache.get(feedName);
        Assert.assertTrue("No feeds found", feeds.isPresent());
        final List<DocRef> pipelines = pipelineStore.findByName(feedName);
        Assert.assertTrue("No pipelines found", pipelines != null && pipelines.size() > 0);
        Assert.assertEquals("Expected 1 pipeline", 1, pipelines.size());

        final DocRef pipelineRef = pipelines.get(0);
        final FeedDoc feed = feeds.get();

        final ExpressionOperator expression = new ExpressionOperator.Builder(Op.AND)
                .addTerm(StreamDataSource.FEED, Condition.EQUALS, feedName)
                .addOperator(new ExpressionOperator.Builder(Op.OR)
                        .addTerm(StreamDataSource.STREAM_TYPE, Condition.EQUALS, StreamTypeNames.RAW_REFERENCE)
                        .addTerm(StreamDataSource.STREAM_TYPE, Condition.EQUALS, StreamTypeNames.RAW_EVENTS)
                        .build())
                .build();

        final FindStreamCriteria streamCriteria = new FindStreamCriteria();
        streamCriteria.setExpression(expression);
        streamCriteria.obtainSelectedIdSet().setMatchAll(Boolean.TRUE);

        final SteppingTask action = new SteppingTask(UserTokenUtil.INTERNAL_PROCESSING_USER_TOKEN);
        action.setPipeline(pipelineRef);
        action.setCriteria(streamCriteria);

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
            Assert.assertFalse("Translation stepping has output indicators.", elementData.getOutputIndicators() != null
                    && elementData.getOutputIndicators().getMaxSeverity() != null);
            Assert.assertFalse("Translation stepping has code indicators.", elementData.getCodeIndicators() != null
                    && elementData.getCodeIndicators().getMaxSeverity() != null);

            final String stem = feed.getName() + "~STEPPING~" + elementId;
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
            Assert.fail(exceptions.get(0).getMessage());
        }
    }

    private SteppingResult step(final StepType direction, final int steps, final SteppingTask request,
                                final SteppingResult existingResponse) {
        SteppingResult newResponse = existingResponse;

        for (int i = 0; i < steps; i++) {
            request.setStepType(direction);
            final SteppingResult stepResponse = taskManager.exec(request);

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
        final BaseResultList<Stream> list = streamMetaService.find(new FindStreamCriteria());
        if (list == null || list.size() == 0) {
            return 0;
        }
        list.sort(Comparator.comparing(Stream::getId));
        final Stream latest = list.get(list.size() - 1);
        return latest.getId();
    }

    private void copyStream(final Stream stream, final OutputStream outputStream) {
        final StreamSource streamSource = streamStore.openStreamSource(stream.getId());
        StreamUtil.streamToStream(streamSource.getInputStream(), outputStream, false);
        streamStore.closeStreamSource(streamSource);
    }

    private void compareFiles(final Path expectedFile, final Path actualFile, final List<Exception> exceptions) {
        try {
            ComparisonHelper.compare(expectedFile, actualFile, false, true);
        } catch (final RuntimeException e) {
            exceptions.add(e);
        }
    }
}
