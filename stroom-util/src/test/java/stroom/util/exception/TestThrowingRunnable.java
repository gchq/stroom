package stroom.util.exception;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

class TestThrowingRunnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestThrowingRunnable.class);

    final AtomicBoolean shouldThrow = new AtomicBoolean(false);
    final AtomicBoolean wasStuffDone = new AtomicBoolean(false);


    @Test
    void unchecked() throws ExecutionException, InterruptedException {

        shouldThrow.set(false);
        CompletableFuture.runAsync(ThrowingRunnable.unchecked(this::doStuff))
                .get();
        Assertions.assertThat(wasStuffDone)
                .isTrue();
    }

    @Test
    void unchecked_throws() {

        shouldThrow.set(true);
        Assertions.assertThatThrownBy(() ->
                        CompletableFuture.runAsync(ThrowingRunnable.unchecked(this::doStuff))
                                .get())
                .isInstanceOf(ExecutionException.class)
                .getCause()
                .isInstanceOf(RuntimeException.class)
                .getCause()
                .isInstanceOf(MyCheckedException.class);

        Assertions.assertThat(wasStuffDone)
                .isFalse();
    }

    private void doStuff() throws MyCheckedException {
        if (shouldThrow.get()) {
            throw new MyCheckedException();
        }
        wasStuffDone.set(true);
    }

    private static class MyCheckedException extends Exception {

    }
}
