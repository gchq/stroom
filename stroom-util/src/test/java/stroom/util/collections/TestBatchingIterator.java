package stroom.util.collections;


import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestBatchingIterator {

    @Test
    void batchedStreamOf() {

        final Stream<Integer> sourceStream = IntStream.rangeClosed(1, 10)
                .boxed();

        final List<List<Integer>> batches = BatchingIterator.batchedStreamOf(sourceStream, 3)
                .collect(Collectors.toList());

        assertThat(batches).hasSize(4);
        assertThat(batches.get(0)).containsExactly(1, 2, 3);
        assertThat(batches.get(1)).containsExactly(4, 5, 6);
        assertThat(batches.get(2)).containsExactly(7, 8, 9);
        assertThat(batches.get(3)).containsExactly(10);
    }

    @Test
    void batchedStreamOf_exact() {

        final Stream<Integer> sourceStream = IntStream.rangeClosed(1, 10)
                .boxed();

        final List<List<Integer>> batches = BatchingIterator.batchedStreamOf(sourceStream, 5)
                .collect(Collectors.toList());

        assertThat(batches).hasSize(2);
        assertThat(batches.get(0)).containsExactly(1, 2, 3, 4, 5);
        assertThat(batches.get(1)).containsExactly(6, 7, 8, 9, 10);
    }

    @Test
    void batchedStreamOf_empty() {

        final Stream<Integer> sourceStream = Stream.empty();

        final List<List<Integer>> batches = BatchingIterator.batchedStreamOf(sourceStream, 3)
                .collect(Collectors.toList());

        assertThat(batches).isNotNull();
        assertThat(batches).isEmpty();
    }

}
