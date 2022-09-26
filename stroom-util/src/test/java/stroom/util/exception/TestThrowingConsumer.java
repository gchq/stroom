package stroom.util.exception;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

class TestThrowingConsumer {

    final AtomicBoolean wasStuffDone = new AtomicBoolean(false);

    @Test
    void unchecked() {
        Stream.of(1)
                .forEach(ThrowingConsumer.unchecked(this::consumeStuff));
        Assertions.assertThat(wasStuffDone)
                .isTrue();
    }

    @Test
    void unchecked_throws() {
        Assertions.assertThatThrownBy(() -> {
                    Stream.of(0)
                            .forEach(ThrowingConsumer.unchecked(this::consumeStuff));
                })
                .isInstanceOf(RuntimeException.class)
                .getCause()
                .isInstanceOf(MyCheckedException.class);

        Assertions.assertThat(wasStuffDone)
                .isFalse();
    }

    private void consumeStuff(final int i) throws MyCheckedException {
        if (i == 0) {
            throw new MyCheckedException();
        }
        wasStuffDone.set(true);
    }

    private static class MyCheckedException extends Exception {

    }
}
