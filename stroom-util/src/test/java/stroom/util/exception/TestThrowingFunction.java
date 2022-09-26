package stroom.util.exception;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class TestThrowingFunction {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestThrowingFunction.class);

    @Test
    void unchecked() {
        final List<Integer> list = Stream.of(1)
                .map(ThrowingFunction.unchecked(this::addTen))
                .collect(Collectors.toList());

        Assertions.assertThat(list)
                .containsExactly(11);
    }

    @Test
    void unchecked_throws() {
        Assertions.assertThatThrownBy(() -> {
                    final List<Integer> list = Stream.of(0)
                            .map(ThrowingFunction.unchecked(this::addTen))
                            .collect(Collectors.toList());
        })
                .isInstanceOf(RuntimeException.class);
    }

    private int addTen(final int i) throws MyCheckedException {
        if (i == 0) {
            throw new MyCheckedException();
        }
        return i + 10;
    }

    private static class MyCheckedException extends Exception {
    }
}
