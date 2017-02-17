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

package stroom.streamtask.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.node.server.NodeCache;
import stroom.pipeline.shared.PipelineEntity;
import stroom.streamstore.server.StreamSource;
import stroom.streamstore.server.StreamStore;
import stroom.streamstore.shared.Stream;
import stroom.streamtask.shared.StreamProcessor;
import stroom.streamtask.shared.StreamProcessorFilter;
import stroom.streamtask.shared.StreamProcessorFilterService;
import stroom.streamtask.shared.StreamProcessorService;
import stroom.streamtask.shared.StreamTask;
import stroom.streamtask.shared.TaskStatus;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.date.DateUtil;
import stroom.util.shared.VoidResult;
import stroom.util.spring.StroomBeanStore;
import stroom.util.spring.StroomScope;
import stroom.util.task.TaskMonitor;
import org.springframework.context.annotation.Scope;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@TaskHandlerBean(task = StreamProcessorTask.class)
@Scope(value = StroomScope.TASK)
public class StreamProcessorTaskHandler extends AbstractTaskHandler<StreamProcessorTask, VoidResult> {
    @Resource
    private StroomBeanStore beanStore;

    private static final Logger LOGGER = LoggerFactory.getLogger(StreamProcessorTaskHandler.class);

    @Resource(name = "cachedStreamProcessorService")
    private StreamProcessorService streamProcessorService;
    @Resource(name = "cachedStreamProcessorFilterService")
    private StreamProcessorFilterService streamProcessorFilterService;
    @Resource
    private StreamTaskHelper streamTaskHelper;
    @Resource
    private StreamStore streamStore;
    @Resource
    private NodeCache nodeCache;
    @Resource
    private TaskMonitor taskMonitor;

    private static final Set<String> FETCH_SET = new HashSet<>(
            Arrays.asList(StreamProcessor.ENTITY_TYPE, StreamProcessorFilter.ENTITY_TYPE, PipelineEntity.ENTITY_TYPE));

    @Override
    public VoidResult exec(final StreamProcessorTask task) {
        boolean complete = false;
        final long startTime = System.currentTimeMillis();
        StreamTask streamTask = task.getStreamTask();
        LOGGER.trace("Executing stream task: {}", streamTask.getId());

        StreamSource streamSource = null;
        try {
            // Open the stream source.
            streamSource = streamStore.openStreamSource(streamTask.getStream().getId());
            if (streamSource != null) {
                final Stream stream = streamSource.getStream();

                // Load lazy stuff
                // stream.setStreamType(streamTypeService.load(stream.getStreamType()));
                final StreamProcessor sourceStreamProcessor = streamProcessorService.load(stream.getStreamProcessor());

                StreamProcessor destStreamProcessor = null;
                StreamProcessorFilter destStreamProcessorFilter = null;
                if (streamTask.getStreamProcessorFilter() != null) {
                    destStreamProcessorFilter = streamProcessorFilterService.load(streamTask.getStreamProcessorFilter(),
                            FETCH_SET);
                    if (destStreamProcessorFilter != null) {
                        destStreamProcessor = streamProcessorService
                                .load(destStreamProcessorFilter.getStreamProcessor(), FETCH_SET);
                    }
                }
                if (destStreamProcessorFilter == null || destStreamProcessor == null) {
                    throw new RuntimeException("No dest processor has been loaded.");
                }

                if (destStreamProcessor.getPipeline() != null) {
                    taskMonitor.info("Stream {} {} {} {}", stream.getId(),
                            DateUtil.createNormalDateTimeString(stream.getCreateMs()),
                            destStreamProcessor.getTaskType(), destStreamProcessor.getPipeline().getName());
                } else {
                    taskMonitor.info("Stream {} {} {}", stream.getId(),
                            DateUtil.createNormalDateTimeString(stream.getCreateMs()),
                            destStreamProcessor.getTaskType());
                }

                // Don't process any streams that we have already created
                if (sourceStreamProcessor != null && sourceStreamProcessor.equals(destStreamProcessor)) {
                    complete = true;
                    LOGGER.warn("Skipping stream that we seem to have created (avoid processing forever) {} {}", stream,
                            sourceStreamProcessor);

                } else {
                    // Change the task status.... and save
                    streamTask = streamTaskHelper.changeTaskStatus(streamTask, nodeCache.getDefaultNode(),
                            TaskStatus.PROCESSING, startTime, null);
                    // Avoid having to do another fetch
                    streamTask.setStreamProcessorFilter(destStreamProcessorFilter);

                    final String taskType = destStreamProcessor.getTaskType();

                    final StreamProcessorTaskExecutor streamProcessorTaskExecutor = (StreamProcessorTaskExecutor) beanStore
                            .getBean(taskType);

                    // Used as a hook for the test code
                    task.setStreamProcessorTaskExecutor(streamProcessorTaskExecutor);

                    try {
                        streamProcessorTaskExecutor.exec(destStreamProcessor, destStreamProcessorFilter, streamTask,
                                streamSource);
                        // Only record completion for this task if it was not
                        // terminated.
                        if (!task.isTerminated()) {
                            complete = true;
                        }
                    } catch (final Exception ex) {
                        LOGGER.error("Task failed {} {}", new Object[] {destStreamProcessor, stream}, ex);
                    }
                }
            }
        } catch (final Throwable t) {
            LOGGER.error(t.getMessage(), t);
        } finally {
            // Close the stream source.
            if (streamSource != null) {
                try {
                    streamStore.closeStreamSource(streamSource);
                } catch (final Exception e) {
                    LOGGER.error(e.getMessage(), e);
                }
                streamSource = null;
            }

            if (complete) {
                streamTaskHelper.changeTaskStatus(streamTask, nodeCache.getDefaultNode(), TaskStatus.COMPLETE,
                        startTime, System.currentTimeMillis());
            } else {
                streamTaskHelper.changeTaskStatus(streamTask, nodeCache.getDefaultNode(), TaskStatus.FAILED, startTime,
                        System.currentTimeMillis());
            }
        }

        return new VoidResult();
    }
}
