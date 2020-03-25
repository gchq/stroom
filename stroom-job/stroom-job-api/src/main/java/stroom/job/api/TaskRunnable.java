package stroom.job.api;

public abstract class TaskRunnable implements Runnable {
    private final Runnable runnable;

    public TaskRunnable(final Runnable runnable) {
        this.runnable = runnable;
    }

    @Override
    public void run() {
        runnable.run();
    }
}