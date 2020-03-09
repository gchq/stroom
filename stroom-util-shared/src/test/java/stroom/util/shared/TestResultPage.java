package stroom.util.shared;

import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TestResultPage {
    @Test
    void testCollector1() {

        PageRequest pageRequest = new PageRequest(10L,10);
        ResultPage<WrappedInt> resultPage = IntStream.rangeClosed(0, 99)
            .boxed()
            .map(WrappedInt::new)
            .collect(ResultPage.collector(pageRequest));

        assertThat(resultPage.size())
            .isEqualTo(pageRequest.getLength());
        assertThat(resultPage.getFirst().getValue()).isEqualTo(10);
    }

    @Test
    void testCollector2() {

        ResultPage<WrappedInt> resultPage = IntStream.rangeClosed(0, 99)
            .boxed()
            .map(WrappedInt::new)
            .collect(ResultPage.collector(null));

        assertThat(resultPage.size())
            .isEqualTo(100);
        assertThat(resultPage.getFirst().getValue()).isEqualTo(0);
    }

    private static class WrappedInt {
        private final int value;

        WrappedInt(final int value) {
            this.value = value;
        }

        int getValue() {
            return value;
        }
    }

}