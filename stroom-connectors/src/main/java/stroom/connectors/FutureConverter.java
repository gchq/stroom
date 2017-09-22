package stroom.connectors;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class FutureConverter <IN, OUT> implements Future<OUT> {
    private final Future<IN> in;

    private FutureConverter(final Future<IN> in) {
        this.in = in;
    }

    public static <I, O> FutureConverter<I, O> build(final Future<I> in) {
        return new FutureConverter<>(in);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public OUT get() throws InterruptedException, ExecutionException {
        return null;
    }

    @Override
    public OUT get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return null;
    }
}
