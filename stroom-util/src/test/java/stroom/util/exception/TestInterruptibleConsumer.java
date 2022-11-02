package stroom.util.exception;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

class TestInterruptibleConsumer {

    final AtomicBoolean wasStuffDone = new AtomicBoolean(false);

    @Test
    void unchecked() {

        Stream.of(1)
                .forEach(InterruptibleConsumer.unchecked(this::consumeStuff));
        Assertions.assertThat(wasStuffDone)
                .isTrue();
    }

    @Test
    void unchecked_throws() {
        Assertions.assertThatThrownBy(() ->
                        Stream.of(0)
                                .forEach(InterruptibleConsumer.unchecked(this::consumeStuff)))
                .isInstanceOf(RuntimeException.class)
                .getCause()
                .isInstanceOf(InterruptedException.class);

        Assertions.assertThat(wasStuffDone)
                .isFalse();
    }

    private void consumeStuff(final int i) throws InterruptedException {
        if (i == 0) {
            throw new InterruptedException();
        }
        wasStuffDone.set(true);
    }
}
