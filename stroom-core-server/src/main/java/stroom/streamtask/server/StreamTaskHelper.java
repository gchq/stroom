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

import stroom.util.logging.StroomLogger;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import stroom.node.shared.Node;
import stroom.streamtask.shared.StreamTask;
import stroom.streamtask.shared.StreamTaskService;
import stroom.streamtask.shared.TaskStatus;
import stroom.util.thread.ThreadUtil;

@Component
@Transactional(propagation = Propagation.NEVER)
public class StreamTaskHelper {
    protected static final StroomLogger LOGGER = StroomLogger.getLogger(StreamTaskHelper.class);

    @Resource
    private StreamTaskService streamTaskService;

    StreamTask changeTaskStatus(final StreamTask streamTask, final Node node, final TaskStatus status,
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
            // Try this operation a few times.
            boolean success = false;
            Throwable lastError = null;

            // Try and do this up to 100 times.
            for (int tries = 0; tries < 100 && !success; tries++) {
                success = true;

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
                    success = false;
                    lastError = t2;
                    // Wait before trying this operation again.
                    ThreadUtil.sleep(1000);
                }
            }

            if (!success) {
                LOGGER.error("Error changing task status for task '%s': %s", streamTask, lastError.getMessage(), lastError);
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
