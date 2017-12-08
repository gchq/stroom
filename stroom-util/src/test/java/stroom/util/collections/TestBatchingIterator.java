package stroom.util.collections;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class TestBatchingIterator {

    @Test
    public void batchedStreamOf() throws Exception {

        Stream<Integer> sourceStream = IntStream.rangeClosed(1, 10)
                .boxed();

        List<List<Integer>> batches = BatchingIterator.batchedStreamOf(sourceStream, 3)
                .collect(Collectors.toList());

        Assert.assertEquals(4, batches.size());
        Assert.assertEquals(Arrays.asList(1,2,3), batches.get(0));
        Assert.assertEquals(Arrays.asList(4,5,6), batches.get(1));
        Assert.assertEquals(Arrays.asList(7,8,9), batches.get(2));
        Assert.assertEquals(Arrays.asList(10), batches.get(3));
    }

    @Test
    public void batchedStreamOf_empty() throws Exception {

        Stream<Integer> sourceStream = Stream.empty();

        List<List<Integer>> batches = BatchingIterator.batchedStreamOf(sourceStream, 3)
                .collect(Collectors.toList());

        Assert.assertNotNull(batches);
        Assert.assertEquals(0, batches.size());
    }

}