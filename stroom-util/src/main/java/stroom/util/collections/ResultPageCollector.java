package stroom.util.collections;

import stroom.util.shared.PageRequest;
import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Collector.Characteristics;

public class ResultPageCollector<T> {
    private final int offset;
    private final int limit;
    private long count = 0;
    private final List<T> list = new ArrayList<>();

    public static <T> Collector<T, ?, ResultPageCollector<T>> create(final PageRequest pageRequest) {
        final int offset = getOffset(pageRequest);
        final int limit = getLimit(pageRequest);
        return create(offset, limit);
    }

    public static <T> Collector<T, ?, ResultPageCollector<T>> create(final int offset, final int limit) {
        return Collector.of(() -> new ResultPageCollector<>(offset, limit), ResultPageCollector::add,
                (left, right) -> {
                    left.combine(right);
                    return left;
                },
                Characteristics.IDENTITY_FINISH);
    }

    private static int getLimit(final PageRequest pageRequest) {
        if (pageRequest != null && pageRequest.getLength() != null) {
            return pageRequest.getLength();
        }
        return 1000000;
    }

    private static int getOffset(final PageRequest pageRequest) {
        if (pageRequest != null && pageRequest.getOffset() != null) {
            return pageRequest.getOffset().intValue();
        }
        return 0;
    }

    private ResultPageCollector(final int offset, final int limit) {
        this.offset = offset;
        this.limit = limit;
    }

    private void add(T item) {
        if (count >= offset && count < offset + limit) {
            list.add(item);
        }
        count++;
    }

    private void combine(ResultPageCollector<T> builder) {
        list.addAll(builder.list);
        count += builder.count;
    }

    public ResultPage<T> build() {
        return new ResultPage<>(list, new PageResponse(offset, list.size(), count, true));
    }
}
