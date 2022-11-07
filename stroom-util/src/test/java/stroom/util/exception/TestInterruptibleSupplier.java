package stroom.util.exception;

import stroom.util.concurrent.AtomicSequence;
import stroom.util.concurrent.UncheckedInterruptedException;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Objects;

class TestInterruptibleSupplier {

    private final AtomicSequence atomicSequence = new AtomicSequence(2);

    @Test
    void unchecked() {
        final Integer val = Objects.requireNonNullElseGet(
                null,
                InterruptibleSupplier.unchecked(this::getNext));

        Assertions.assertThat(val)
                .isEqualTo(0);
    }

    @Test
    void unchecked_throws() {
        final Integer val = Objects.requireNonNullElseGet(
                null,
                InterruptibleSupplier.unchecked(this::getNext));

        Assertions.assertThat(val)
                .isEqualTo(0);

        Assertions.assertThatThrownBy(() -> {
                    //noinspection ResultOfMethodCallIgnored
                    Objects.requireNonNullElseGet(
                            null,
                            InterruptibleSupplier.unchecked(this::getNext));
                })
                .isInstanceOf(UncheckedInterruptedException.class)
                .getCause()
                .isInstanceOf(InterruptedException.class);
    }

    /**
     * Alternate between returning zero or throwing a {@link InterruptedException}
     */
    private int getNext() throws InterruptedException {
        final int i = atomicSequence.next();
        if (i == 1) {
            throw new InterruptedException();
        }
        return i;
    }
}
