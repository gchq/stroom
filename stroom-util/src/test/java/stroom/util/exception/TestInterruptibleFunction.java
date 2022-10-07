package stroom.util.exception;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class TestInterruptibleFunction {

    @Test
    void unchecked() {
        final List<Integer> list = Stream.of(1)
                .map(InterruptibleFunction.unchecked(this::addTen))
                .collect(Collectors.toList());

        Assertions.assertThat(list)
                .containsExactly(11);
    }

    @Test
    void unchecked_throws() {
        Assertions.assertThatThrownBy(() ->
                        Stream.of(0)
                                .map(ThrowingFunction.unchecked(this::addTen))
                                .collect(Collectors.toList()))
                .isInstanceOf(RuntimeException.class)
                .getCause()
                .isInstanceOf(InterruptedException.class);
    }

    private int addTen(final int i) throws InterruptedException {
        if (i == 0) {
            throw new InterruptedException();
        }
        return i + 10;
    }
}
