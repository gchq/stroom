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

package stroom.processor.impl;

import stroom.job.api.DistributedTask;
import stroom.processor.api.DataProcessorTaskExecutor;
import stroom.processor.shared.ProcessorTask;
import stroom.task.api.ServerTask;
import stroom.task.api.SimpleThreadPool;
import stroom.task.shared.ThreadPool;
import stroom.task.api.VoidResult;

public class DataProcessorTask extends ServerTask<VoidResult> implements DistributedTask<VoidResult> {
    private static final long serialVersionUID = 5719364078026952526L;
    private static final ThreadPool THREAD_POOL = new SimpleThreadPool("Stream Processor#", 1);

    private ProcessorTask processorTask;

    // Used for test code
    private transient DataProcessorTaskExecutor dataProcessorTaskExecutor;

    public DataProcessorTask(final ProcessorTask processorTask) {
        this.processorTask = processorTask;
    }

    public ProcessorTask getProcessorTask() {
        return processorTask;
    }

    public DataProcessorTaskExecutor getDataProcessorTaskExecutor() {
        return dataProcessorTaskExecutor;
    }

    void setDataProcessorTaskExecutor(final DataProcessorTaskExecutor dataProcessorTaskExecutor) {
        this.dataProcessorTaskExecutor = dataProcessorTaskExecutor;
    }

    @Override
    public ThreadPool getThreadPool() {
        return THREAD_POOL;
    }

    @Override
    public String getTraceString() {
        return String.valueOf(processorTask.getId());
    }
}
