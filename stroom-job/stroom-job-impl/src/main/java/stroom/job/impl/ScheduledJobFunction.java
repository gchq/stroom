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

package stroom.job.impl;

import stroom.job.api.ScheduledJob;

import java.util.concurrent.atomic.AtomicBoolean;

class ScheduledJobFunction implements Runnable {

    private final AtomicBoolean running;
    private final ScheduledJob scheduledJob;
    private final Runnable runnable;

    public ScheduledJobFunction(final ScheduledJob scheduledJob,
                                final Runnable runnable,
                                final AtomicBoolean running) {
        this.scheduledJob = scheduledJob;
        this.running = running;
        this.runnable = runnable;
    }

    public AtomicBoolean getRunning() {
        return running;
    }

    @Override
    public void run() {
        runnable.run();
    }

    @Override
    public String toString() {
        return scheduledJob.toString();
    }
}
