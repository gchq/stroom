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

import stroom.jobsystem.server.DistributedTask;
import stroom.streamtask.shared.StreamTask;
import stroom.util.shared.SimpleThreadPool;
import stroom.util.shared.ThreadPool;
import stroom.util.shared.VoidResult;
import stroom.util.task.ServerTask;

public class StreamProcessorTask extends ServerTask<VoidResult>implements DistributedTask<VoidResult> {
    private static final long serialVersionUID = 5719364078026952526L;

    public static final String JOB_NAME = "Stream Processor";
    private static final ThreadPool THREAD_POOL = new SimpleThreadPool("Stream Processor#", 1);

    private StreamTask streamTask;

    // Used for test code
    private transient StreamProcessorTaskExecutor streamProcessorTaskExecutor;

    public StreamProcessorTask(final StreamTask streamTask) {
        this.streamTask = streamTask;
    }

    public StreamTask getStreamTask() {
        return streamTask;
    }

    public StreamProcessorTaskExecutor getStreamProcessorTaskExecutor() {
        return streamProcessorTaskExecutor;
    }

    public void setStreamProcessorTaskExecutor(final StreamProcessorTaskExecutor streamProcessorTaskExecutor) {
        this.streamProcessorTaskExecutor = streamProcessorTaskExecutor;
    }

    @Override
    public ThreadPool getThreadPool() {
        return THREAD_POOL;
    }

    @Override
    public String getTraceString() {
        return String.valueOf(streamTask.getId());
    }
}
