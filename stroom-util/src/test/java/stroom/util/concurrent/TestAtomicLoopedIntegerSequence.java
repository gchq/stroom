package stroom.util.concurrent;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TestAtomicLoopedIntegerSequence {

    @Test
    void getNext1() {
        final AtomicLoopedIntegerSequence sequence = new AtomicLoopedIntegerSequence(5);
        for (int j = 0; j < 2; j++) {
            for (int i = 0; i < 5; i++) {
                Assertions.assertThat(sequence.getNext())
                        .isEqualTo(i);
            }
        }
    }

    @Test
    void getNext2() {
        final AtomicLoopedIntegerSequence sequence = new AtomicLoopedIntegerSequence(1, 6);
        for (int j = 0; j < 2; j++) {
            for (int i = 1; i < 6; i++) {
                Assertions.assertThat(sequence.getNext())
                        .isEqualTo(i);
            }
        }
    }

}
