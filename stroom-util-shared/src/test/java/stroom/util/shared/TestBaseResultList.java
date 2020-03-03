package stroom.util.shared;

import org.junit.jupiter.api.Test;
import stroom.docref.SharedObject;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TestBaseResultList {


    @Test
    void testCollector1() {

        PageRequest pageRequest = PageRequest.createBoundedPageRequest(10,10);
        BaseResultList<WrappedInt> baseResultList = IntStream.rangeClosed(0, 99)
            .boxed()
            .map(WrappedInt::new)
            .collect(BaseResultList.collector(pageRequest));

        assertThat(baseResultList)
            .hasSize(pageRequest.getLength());
        assertThat(baseResultList.getFirst().getValue()).isEqualTo(10);
    }

    @Test
    void testCollector2() {

        BaseResultList<WrappedInt> baseResultList = IntStream.rangeClosed(0, 99)
            .boxed()
            .map(WrappedInt::new)
            .collect(BaseResultList.collector(null));

        assertThat(baseResultList)
            .hasSize(100);
        assertThat(baseResultList.getFirst().getValue()).isEqualTo(0);
    }

    private static class WrappedInt implements SharedObject {
        private final int value;

        WrappedInt(final int value) {
            this.value = value;
        }

        int getValue() {
            return value;
        }
    }
}