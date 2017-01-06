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

import stroom.search.server.ClusterSearchTask;
import stroom.search.server.Coprocessor;
import stroom.search.server.NodeResult;
import stroom.task.server.TaskCallback;
import stroom.task.server.ThreadPoolImpl;
import stroom.util.shared.ThreadPool;
import stroom.util.shared.VoidResult;
import stroom.util.task.ServerTask;

import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;

public class SenderTask extends ServerTask<VoidResult> {
    private static final ThreadPool THREAD_POOL = new ThreadPoolImpl("Stroom Result Sender", 5, 0, Integer.MAX_VALUE);

    private final ClusterSearchTask clusterSearchTask;
    private final Map<Integer, Coprocessor<?>> coprocessorMap;
    private final TaskCallback<NodeResult> callback;
    private final long frequency;
    private final AtomicBoolean sendingComplete;
    private final AtomicBoolean searchComplete;
    private final LinkedBlockingDeque<String> errors;

    public SenderTask(final ClusterSearchTask clusterSearchTask, final Map<Integer, Coprocessor<?>> coprocessorMap,
            final TaskCallback<NodeResult> callback, final long frequency, final AtomicBoolean sendingComplete,
            final AtomicBoolean searchComplete, final LinkedBlockingDeque<String> errors) {
        super(clusterSearchTask);
        this.clusterSearchTask = clusterSearchTask;
        this.coprocessorMap = coprocessorMap;
        this.callback = callback;
        this.frequency = frequency;
        this.sendingComplete = sendingComplete;
        this.searchComplete = searchComplete;
        this.errors = errors;
    }

    public ClusterSearchTask getClusterSearchTask() {
        return clusterSearchTask;
    }

    public Map<Integer, Coprocessor<?>> getCoprocessorMap() {
        return coprocessorMap;
    }

    public TaskCallback<NodeResult> getCallback() {
        return callback;
    }

    public long getFrequency() {
        return frequency;
    }

    public AtomicBoolean getSendingComplete() {
        return sendingComplete;
    }

    public AtomicBoolean getSearchComplete() {
        return searchComplete;
    }

    public LinkedBlockingDeque<String> getErrors() {
        return errors;
    }

    @Override
    public ThreadPool getThreadPool() {
        return THREAD_POOL;
    }
}
