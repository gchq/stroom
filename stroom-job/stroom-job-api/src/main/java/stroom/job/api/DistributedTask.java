/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.job.api;

import stroom.task.shared.ThreadPool;

public class DistributedTask {

    private final String jobName;
    private final Runnable runnable;
    private final ThreadPool threadPool;
    private final String traceString;

    public DistributedTask(final String jobName,
                           final Runnable runnable,
                           final ThreadPool threadPool,
                           final String traceString) {
        this.jobName = jobName;
        this.runnable = runnable;
        this.threadPool = threadPool;
        this.traceString = traceString;
    }

    public String getJobName() {
        return jobName;
    }

    public Runnable getRunnable() {
        return runnable;
    }

    public ThreadPool getThreadPool() {
        return threadPool;
    }

    public String getTraceString() {
        return traceString;
    }
}
