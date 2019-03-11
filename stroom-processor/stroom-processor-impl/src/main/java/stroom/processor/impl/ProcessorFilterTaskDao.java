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

package stroom.processor.impl;


import stroom.meta.shared.Meta;
import stroom.processor.api.InclusiveRanges;
import stroom.processor.shared.FindProcessorFilterTaskCriteria;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterTask;
import stroom.processor.shared.ProcessorFilterTracker;
import stroom.processor.shared.TaskStatus;
import stroom.util.shared.BaseResultList;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Class used to do the transactional aspects of stream task creation
 */
public interface ProcessorFilterTaskDao {
    /**
     * Anything that we owned release
     */
    void releaseOwnedTasks();


    /**
     * Create new tasks for the specified filter and add them to the queue.
     *
     * @param filter          The fitter to create tasks for
     * @param tracker         The tracker that tracks the task creation progress for the
     *                        filter.
     * @param streamQueryTime The time that we queried for streams that match the stream
     *                        processor filter.
     * @param streams         The map of streams and optional event ranges to create stream
     *                        tasks for.
     * @param thisNodeName    This node, the node that will own the created tasks.
     * @param reachedLimit    For search based stream task creation this indicates if we
     *                        have reached the limit of stream tasks created for a single
     *                        search. This limit is imposed to stop search based task
     *                        creation running forever.
     * @return A list of tasks that we have created and that are owned by this
     * node and available to be handed to workers (i.e. their associated
     * streams are not locked).
     */
    void createNewTasks(final ProcessorFilter filter,
                        final ProcessorFilterTracker tracker,
                        final long streamQueryTime,
                        final Map<Meta, InclusiveRanges> streams,
                        final String thisNodeName,
                        final Long maxMetaId,
                        final boolean reachedLimit,
                        final Consumer<CreatedTasks> consumer);

    ProcessorFilterTask changeTaskStatus(ProcessorFilterTask processorFilterTask,
                                         String nodeName,
                                         TaskStatus status,
                                         Long startTime,
                                         Long endTime);

    BaseResultList<ProcessorFilterTask> find(FindProcessorFilterTaskCriteria criteria);
}
