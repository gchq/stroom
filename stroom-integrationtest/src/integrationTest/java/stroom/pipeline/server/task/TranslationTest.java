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

package stroom.pipeline.server.task;

import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.DocRefUtil;
import stroom.importexport.shared.ImportState.ImportMode;
import stroom.feed.MetaMap;
import stroom.feed.StroomHeaderArguments;
import stroom.feed.server.FeedService;
import stroom.feed.shared.Feed;
import stroom.feed.shared.FindFeedCriteria;
import stroom.importexport.server.ImportExportSerializer;
import stroom.node.server.NodeCache;
import stroom.pipeline.server.PipelineService;
import stroom.pipeline.shared.FindPipelineEntityCriteria;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.SharedElementData;
import stroom.pipeline.shared.SharedStepData;
import stroom.pipeline.shared.StepType;
import stroom.pipeline.shared.SteppingResult;
import stroom.proxy.repo.StroomStreamProcessor;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.security.UserTokenUtil;
import stroom.streamstore.server.StreamSource;
import stroom.streamstore.server.StreamStore;
import stroom.streamstore.server.StreamTarget;
import stroom.streamstore.server.StreamTypeService;
import stroom.streamstore.server.fs.serializable.RASegmentOutputStream;
import stroom.streamstore.server.fs.serializable.RawInputSegmentWriter;
import stroom.streamstore.shared.*;
import stroom.streamtask.server.StreamProcessorFilterService;
import stroom.streamtask.server.StreamProcessorService;
import stroom.streamtask.server.StreamProcessorTask;
import stroom.streamtask.server.StreamTargetStroomStreamHandler;
import stroom.streamtask.server.StreamTaskCreator;
import stroom.streamtask.shared.StreamProcessor;
import stroom.streamtask.shared.StreamTask;
import stroom.task.server.TaskManager;
import stroom.task.server.TaskMonitorImpl;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.ComparisonHelper;
import stroom.test.ContentImportService;
import stroom.test.StroomCoreServerTestFileUtil;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.shared.Indicators;

import javax.annotation.Resource;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public abstract class TranslationTest extends AbstractCoreIntegrationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TranslationTest.class);

    private static final int OLD_YEAR = 2006;

    @Resource
    private NodeCache nodeCache;
    @Resource
    private StreamTaskCreator streamTaskCreator;
    @Resource
    private TaskManager taskManager;
    @Resource
    private FeedService feedService;
    @Resource
    private PipelineService pipelineService;
    @Resource
    private StreamProcessorService streamProcessorService;
    @Resource
    private StreamProcessorFilterService streamProcessorFilterService;
    @Resource
    private StreamStore streamStore;
    @Resource
    private StreamTypeService streamTypeService;
    @Resource
    private ImportExportSerializer importExportSerializer;
    @Resource
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
        final BaseResultList<PipelineEntity> pipelines = pipelineService.find(new FindPipelineEntityCriteria());
        for (final PipelineEntity pipelineEntity : pipelines) {
            final Feed feed = feedService.loadByName(pipelineEntity.getName());

            if (feed != null && feed.isReference() == reference) {
                StreamProcessor streamProcessor = new StreamProcessor();
                streamProcessor.setPipeline(pipelineEntity);
                streamProcessor.setEnabled(true);
                streamProcessor = streamProcessorService.save(streamProcessor);

                int priority = 1;
                if (feed.isReference()) {
                    priority++;
                }

                final String streamType = feed.isReference() ?
                        StreamType.RAW_REFERENCE.getName() : StreamType.RAW_EVENTS.getName();
                final QueryData findStreamQueryData = new QueryData.Builder()
                        .dataSource(QueryData.STREAM_STORE_DOC_REF)
                        .expression(new ExpressionOperator.Builder(ExpressionOperator.Op.AND)
                            .addOperator(new ExpressionOperator.Builder(ExpressionOperator.Op.OR)
                                .addTerm(FindStreamDataSource.FEED, ExpressionTerm.Condition.EQUALS, feed.getName())
                                .build())
                            .addOperator(new ExpressionOperator.Builder(ExpressionOperator.Op.OR)
                                .addTerm(FindStreamDataSource.STREAM_TYPE, ExpressionTerm.Condition.EQUALS, streamType)
                                .build())
                            .build())
                        .build();

                streamProcessorFilterService.addFindStreamCriteria(streamProcessor, priority, findStreamQueryData);

                // Add data.
                try (final java.util.stream.Stream<Path> stream = Files.list(inputDir)) {
                    final List<Path> files = stream
                            .filter(p -> {
                                final String fileName = p.getFileName().toString();
                                return fileName.startsWith(feed.getName()) && (fileName.endsWith(".in") || fileName.endsWith(".zip"));
                            })
                            .sorted(Comparator.naturalOrder())         // The order these files are added in is important for the stepping tests.
                            .collect(Collectors.toList());

                    files.forEach(p -> {
                        // Add and test each file.
                        final String fileName = p.getFileName().toString();
                        final int index = fileName.lastIndexOf(".");
                        final String stem = fileName.substring(0, index);

                        try {
                            test(p, feed, outputDir, stem, compareOutput, exceptions);
                        } catch (final Exception e) {
                            Assert.fail(e.getMessage());
                        }
                    });
                } catch (final IOException e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
            }
        }
    }

    private void test(final Path inputFile, final Feed feed, final Path outputDir, final String stem,
                      final boolean compareOutput, final List<Exception> exceptions) throws Exception {
        LOGGER.info("Testing input {}, feed {}, output {}, stem {}",
                inputFile.getFileName().toString(), feed.getName(), outputDir.getFileName().toString(), stem);

        addStream(inputFile, feed);

        streamTaskCreator.createTasks(new TaskMonitorImpl());

        List<StreamProcessorTask> tasks = getTasks();
        Assert.assertTrue("There should be one task here", tasks.size() == 1);

        for (final StreamProcessorTask task : tasks) {
            final long startStreamId = getLatestStreamId();
            taskManager.exec(task);
            final long endStreamId = getLatestStreamId();

            if (compareOutput) {
                final List<Stream> processedStreams = new ArrayList<>();

                for (long streamId = startStreamId + 1; streamId <= endStreamId; streamId++) {
                    final Stream stream = streamStore.loadStreamById(streamId);
                    final StreamType streamType = streamTypeService.load(stream.getStreamType());
                    if (streamType.isStreamTypeProcessed()) {
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

    private void addStream(final Path file, final Feed feed) throws IOException {
        if (file.getFileName().toString().endsWith(".zip")) {
            loadZipData(file, feed);

        } else {
            // Add the associated data to the stream store.
            StreamType streamType = null;
            if (feed.isReference()) {
                streamType = StreamType.RAW_REFERENCE;
            } else {
                streamType = StreamType.RAW_EVENTS;
            }

            // We need to ensure the reference data is older then the earliest
            // event we are going to see. In the case of these component tests
            // we have some events from 2007.
            ZonedDateTime dateTime = ZonedDateTime.now(ZoneOffset.UTC);
            dateTime = dateTime.withYear(OLD_YEAR);
            long millis = dateTime.toInstant().toEpochMilli();

            // Create the stream.
            final Stream stream = Stream.createStreamForTesting(streamType, feed, millis,
                    millis);
            final StreamTarget target = streamStore.openStreamTarget(stream);

            final InputStream inputStream = new BufferedInputStream(Files.newInputStream(file));
            final RASegmentOutputStream outputStream = new RASegmentOutputStream(target);

            final RawInputSegmentWriter writer = new RawInputSegmentWriter();
            writer.write(inputStream, outputStream);

            streamStore.closeStreamTarget(target);

            // Check that what was written to the store is the same as the
            // contents of the file.
            final StreamSource checkSource = streamStore.openStreamSource(stream.getId());
            final byte[] original = Files.readAllBytes(file);
            final byte[] stored = StreamUtil.streamToBytes(checkSource.getInputStream());
            streamStore.closeStreamSource(checkSource);
            Assert.assertTrue(Arrays.equals(original, stored));
        }
    }

    private void loadZipData(final Path file, final Feed feed) throws IOException {
        final MetaMap metaMap = new MetaMap();
        metaMap.put(StroomHeaderArguments.COMPRESSION, StroomHeaderArguments.COMPRESSION_ZIP);

        final List<StreamTargetStroomStreamHandler> handlerList = StreamTargetStroomStreamHandler
                .buildSingleHandlerList(streamStore, feedService, null, feed, feed.getStreamType());

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

        List<StreamTask> streamTasks = streamTaskCreator.assignStreamTasks(nodeCache.getDefaultNode(), 100);
        while (streamTasks.size() > 0) {
            streamProcessorTasks = new ArrayList<>(streamTasks.size());
            for (final StreamTask streamTask : streamTasks) {
                streamProcessorTasks.add(new StreamProcessorTask(streamTask));
            }
            streamTasks = streamTaskCreator.assignStreamTasks(nodeCache.getDefaultNode(), 100);
        }

        return streamProcessorTasks;
    }

    protected void testSteppingTask(final String feedName, final Path dir) throws IOException {
        final List<Exception> exceptions = new ArrayList<>();

        // We first need to get all of the feeds from the DB.
        final FindFeedCriteria feedCriteria = new FindFeedCriteria(feedName);

        // feedCriteria.setFeedType(FeedType.REFERENCE);
        final BaseResultList<Feed> feeds = feedService.find(feedCriteria);
        Assert.assertTrue("No feeds found", feeds != null && feeds.size() > 0);
        Assert.assertEquals("Expected 1 feed", 1, feeds.size());
        final BaseResultList<PipelineEntity> pipelines = pipelineService
                .find(new FindPipelineEntityCriteria(feedName));
        Assert.assertTrue("No pipelines found", pipelines != null && pipelines.size() > 0);
        Assert.assertEquals("Expected 1 pipeline", 1, pipelines.size());

        final PipelineEntity pipelineEntity = pipelines.getFirst();
        final Feed feed = feeds.getFirst();
        final FindStreamCriteria streamCriteria = new FindStreamCriteria();
        streamCriteria.obtainFeeds().obtainInclude().add(feed);
        streamCriteria.obtainStreamIdSet().setMatchAll(Boolean.TRUE);
        streamCriteria.obtainStreamTypeIdSet().add(StreamType.RAW_REFERENCE);
        streamCriteria.obtainStreamTypeIdSet().add(StreamType.RAW_EVENTS);
        final SteppingTask action = new SteppingTask(UserTokenUtil.INTERNAL_PROCESSING_USER_TOKEN);
        action.setPipeline(DocRefUtil.create(pipelineEntity));
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
        final BaseResultList<Stream> list = streamStore.find(new FindStreamCriteria());
        if (list == null || list.size() == 0) {
            return 0;
        }
        Collections.sort(list);
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
        } catch (final Exception e) {
            exceptions.add(e);
        }
    }
}
