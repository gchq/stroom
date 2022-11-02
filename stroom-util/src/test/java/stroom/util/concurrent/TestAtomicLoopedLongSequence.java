package stroom.util.concurrent;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TestAtomicLoopedLongSequence {

    @Test
    void getNext1() {
        AtomicLoopedLongSequence sequence = new AtomicLoopedLongSequence(5);
        for (int j = 0; j < 2; j++) {
            for (int i = 0; i < 5; i++) {
                Assertions.assertThat(sequence.getNext())
                        .isEqualTo(i);
            }
        }
    }

    @Test
    void getNext2() {
        AtomicLoopedLongSequence sequence = new AtomicLoopedLongSequence(1, 6);
        for (int j = 0; j < 2; j++) {
            for (int i = 1; i < 6; i++) {
                Assertions.assertThat(sequence.getNext())
                        .isEqualTo(i);
            }
        }
    }
}
