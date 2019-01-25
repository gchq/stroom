package stroom.lifecycle.api;

public abstract class RunnableWrapper implements Runnable {
    private final Runnable runnable;

    public RunnableWrapper(final Runnable runnable) {
        this.runnable = runnable;
    }

    @Override
    public void run() {
        runnable.run();
    }
}