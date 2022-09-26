package stroom.util.exception;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

class TestInterruptibleRunnable {

    final AtomicBoolean shouldThrow = new AtomicBoolean(false);
    final AtomicBoolean wasStuffDone = new AtomicBoolean(false);

    @Test
    void unchecked() throws ExecutionException, InterruptedException {
        shouldThrow.set(false);
        CompletableFuture.runAsync(InterruptibleRunnable.unchecked(this::doStuff))
                .get();
    }

    @Test
    void unchecked_throws() {
        shouldThrow.set(true);
        Assertions.assertThatThrownBy(() ->
                        CompletableFuture.runAsync(InterruptibleRunnable.unchecked(this::doStuff))
                                .get())
                .isInstanceOf(ExecutionException.class)
                .getCause()
                .isInstanceOf(RuntimeException.class)
                .getCause()
                .isInstanceOf(InterruptedException.class);

        Assertions.assertThat(wasStuffDone)
                .isFalse();
    }


    private void doStuff() throws InterruptedException {
        if (shouldThrow.get()) {
            throw new InterruptedException();
        }
        wasStuffDone.set(true);
    }
}
