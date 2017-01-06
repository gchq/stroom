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

package stroom.search.server.sender;

import org.springframework.context.annotation.Scope;
import stroom.query.Payload;
import stroom.search.server.Coprocessor;
import stroom.search.server.NodeResult;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.task.server.TaskManager;
import stroom.util.logging.StroomLogger;
import stroom.util.shared.VoidResult;
import stroom.util.spring.StroomScope;
import stroom.util.task.TaskMonitor;
import stroom.util.thread.ThreadUtil;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

@TaskHandlerBean(task = SenderTask.class)
@Scope(value = StroomScope.TASK)
public class SenderTaskHandler extends AbstractTaskHandler<SenderTask, VoidResult> {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(SenderTaskHandler.class);

    private final TaskManager taskManager;
    private final TaskMonitor taskMonitor;

    @Inject
    public SenderTaskHandler(final TaskManager taskManager, final TaskMonitor taskMonitor) {
        this.taskManager = taskManager;
        this.taskMonitor = taskMonitor;
    }

    @Override
    public VoidResult exec(final SenderTask task) {
        if (!taskMonitor.isTerminated()) {
            taskMonitor.info("Creating search result");

            final long now = System.currentTimeMillis();

            // Find out if we are complete.
            final boolean searchComplete = task.getSearchComplete().get();

            // Produce payloads for each coprocessor.
            Map<Integer, Payload> payloadMap = null;
            if (task.getCoprocessorMap() != null && task.getCoprocessorMap().size() > 0) {
                for (final Entry<Integer, Coprocessor<?>> entry : task.getCoprocessorMap().entrySet()) {
                    final Payload payload = entry.getValue().createPayload();
                    if (payload != null) {
                        if (payloadMap == null) {
                            payloadMap = new HashMap<>();
                        }

                        payloadMap.put(entry.getKey(), payload);
                    }
                }
            }

            // Drain all current errors to a list.
            List<String> errorsSnapshot = new ArrayList<>();
            task.getErrors().drainTo(errorsSnapshot);
            if (errorsSnapshot.size() == 0) {
                errorsSnapshot = null;
            }

            // Only send a result if we have something new to send.
            if (payloadMap != null || errorsSnapshot != null || searchComplete) {
                // Form a result to send back to the requesting node.
                final NodeResult result = new NodeResult(payloadMap, errorsSnapshot, searchComplete);

                try {
                    // Give the result to the callback.
                    taskMonitor.info("Sending search result");
                    task.getCallback().onSuccess(result);
                } catch (final Throwable t) {
                    // If we failed to send the result or the source node
                    // rejected the result because the source task has been
                    // terminated then terminate the task.
                    LOGGER.info("Terminating search because we were unable to send result");
                    task.terminate();
                    task.getClusterSearchTask().terminate();
                }
            }

            if (searchComplete) {
                // We have sent the last data we were expected to so tell the
                // parent cluster search that we have finished sending data.
                task.getSendingComplete().set(true);

            } else {
                // If we aren't complete then send more using the supplied
                // sending frequency.
                final long duration = System.currentTimeMillis() - now;
                if (duration < task.getFrequency()) {
                    ThreadUtil.sleep(task.getFrequency() - duration);
                }

                // Make sure we don't continue to execute this task if it should
                // have terminated.
                if (!task.isTerminated() && !task.getClusterSearchTask().isTerminated()) {
                    final SenderTask senderTask = new SenderTask(task.getClusterSearchTask(), task.getCoprocessorMap(),
                            task.getCallback(), task.getFrequency(), task.getSendingComplete(),
                            task.getSearchComplete(), task.getErrors());
                    taskManager.execAsync(senderTask);
                }
            }
        }

        return VoidResult.INSTANCE;
    }
}
