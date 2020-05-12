package stroom.util;

import java.util.Objects;

/**
 * Wraps a {@link Runnable} with the purpose that the wrapped {@link Runnable}
 * can be lazily injected by Guice.
 */
public abstract class RunnableWrapper implements Runnable {
    private final Runnable runnable;

    public RunnableWrapper(final Runnable runnable) {
        this.runnable = Objects.requireNonNull(runnable);
    }

    @Override
    public void run() {
        runnable.run();
    }
}