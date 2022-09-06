package stroom.util.concurrent;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Execution(ExecutionMode.CONCURRENT)
class TestAtomicLoopedItemSequence {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestAtomicLoopedItemSequence.class);

    @TestFactory
    Stream<DynamicTest> testGetNextItem() {

        return Stream.of(2, 3, 5, 7, 10, 99, 100, 401)
                .map(cnt ->
                        DynamicTest.dynamicTest("Count:" + cnt, () -> {
                            final AtomicLoopedItemSequence sequence = new AtomicLoopedItemSequence();
                            final LongAdder hitCount = new LongAdder();

                            final List<Integer> list = IntStream.rangeClosed(1, cnt)
                                    .boxed()
                                    .collect(Collectors.toList());

                            // Go round a few times to make sure it is looping properly
                            for (int j = 0; j < 3; j++) {
                                for (int i = 1; i < cnt + 1; i++) {
                                    Assertions.assertThat(sequence.getNextItem(list).get())
                                            .isEqualTo(i);
                                    hitCount.increment();
                                }
                            }
                            LOGGER.info("Got {} items", hitCount.sum());
                        }));
    }

    @Test
    void testGetNextItem_empty() {
        final AtomicLoopedItemSequence sequence = new AtomicLoopedItemSequence();
        final List<Integer> list = Collections.emptyList();

        final Optional<Integer> optItem = sequence.getNextItem(list);
        Assertions.assertThat(optItem)
                .isEmpty();
    }

    @Test
    void testGetNextItem_null() {
        final AtomicLoopedItemSequence sequence = new AtomicLoopedItemSequence();
        final List<Integer> list = null;

        final Optional<Integer> optItem = sequence.getNextItem(list);
        Assertions.assertThat(optItem)
                .isEmpty();
    }

    @Test
    void testGetNextItem_single() {
        final AtomicLoopedItemSequence sequence = new AtomicLoopedItemSequence();
        final List<Integer> list = List.of(123);

        final Optional<Integer> optItem = sequence.getNextItem(list);
        Assertions.assertThat(optItem)
                .isPresent();
        Assertions.assertThat(optItem.get())
                .isEqualTo(123);
    }

    @Test
    void testGetNextItem_changingListSize() {
        final AtomicLoopedItemSequence sequence = new AtomicLoopedItemSequence();
        final List<Integer> list = new ArrayList<>(List.of(1, 2, 3));

        Optional<Integer> optItem;
        optItem = sequence.getNextItem(list);
        Assertions.assertThat(optItem.get())
                .isEqualTo(1);

        list.add(4);

        optItem = sequence.getNextItem(list);
        Assertions.assertThat(optItem.get())
                .isEqualTo(2);

        list.add(5);

        optItem = sequence.getNextItem(list);
        Assertions.assertThat(optItem.get())
                .isEqualTo(3);

        list.add(6);

        optItem = sequence.getNextItem(list);
        Assertions.assertThat(optItem.get())
                .isEqualTo(4);

        optItem = sequence.getNextItem(list);
        Assertions.assertThat(optItem.get())
                .isEqualTo(5);

        optItem = sequence.getNextItem(list);
        Assertions.assertThat(optItem.get())
                .isEqualTo(6);

        optItem = sequence.getNextItem(list);
        Assertions.assertThat(optItem.get())
                .isEqualTo(1);
    }
}
