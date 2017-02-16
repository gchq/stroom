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

import javax.annotation.Resource;
import javax.persistence.EntityNotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import stroom.node.shared.Node;
import stroom.streamtask.shared.StreamTask;
import stroom.streamtask.shared.StreamTaskService;
import stroom.streamtask.shared.TaskStatus;

@Component
@Transactional(propagation = Propagation.NEVER)
public class StreamTaskHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamTaskHelper.class);

    @Resource
    private StreamTaskService streamTaskService;

    public StreamTask changeTaskStatus(final StreamTask streamTask, final Node node, final TaskStatus status,
            final Long startTime, final Long endTime) {
        LOGGER.debug("changeTaskStatus() - Changing task status of %s to node=%s, status=%s", streamTask, node, status);
        final long now = System.currentTimeMillis();

        StreamTask result = null;

        try {
            modify(streamTask, node, status, now, startTime, endTime);
            result = streamTaskService.save(streamTask);
        } catch (final EntityNotFoundException e) {
            LOGGER.warn("changeTaskStatus() - Task cannot be found %s", streamTask);
        } catch (final Throwable t) {
            try {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.warn("changeTaskStatus() - %s - Task has changed, attempting reload %s", t.getMessage(),
                            streamTask, t);
                } else {
                    LOGGER.warn("changeTaskStatus() - Task has changed, attempting reload %s", streamTask);
                }

                final StreamTask loaded = streamTaskService.load(streamTask);
                if (loaded == null) {
                    LOGGER.warn("changeTaskStatus() - Failed to reload task %s", streamTask);
                } else if (TaskStatus.DELETED.equals(loaded.getStatus())) {
                    LOGGER.warn("changeTaskStatus() - Task has been deleted %s", streamTask);
                } else {
                    LOGGER.warn("changeTaskStatus() - Loaded stream task %s", loaded);
                    modify(loaded, node, status, now, startTime, endTime);
                    result = streamTaskService.save(loaded);
                }
            } catch (final EntityNotFoundException e) {
                LOGGER.warn("changeTaskStatus() - Failed to reload task as it cannot be found %s", streamTask);
            } catch (final Throwable t2) {
                LOGGER.error("changeTaskStatus() - %s - Task has changed, attempting reload %s", t2.getMessage(),
                        streamTask, t2);
            }
        }

        return result;
    }

    private void modify(final StreamTask streamTask, final Node node, final TaskStatus status, final Long statusMs,
            final Long startTimeMs, final Long endTimeMs) {
        streamTask.setNode(node);
        streamTask.setStatus(status);
        streamTask.setStatusMs(statusMs);
        streamTask.setStartTimeMs(startTimeMs);
        streamTask.setEndTimeMs(endTimeMs);
    }
}
