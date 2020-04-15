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
