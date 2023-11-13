package stroom.util.resultpage;

import stroom.util.shared.PageRequest;
import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A page request constrained result page builder that will receive results and track the total results added.
 *
 * @param <T>
 */
public class ExactResultPageBuilder<T> implements ResultPageBuilder<T> {

    private final PageRequest pageRequest;
    private final List<T> values = new ArrayList<>();
    private long count;

    public ExactResultPageBuilder(final PageRequest pageRequest) {
        Objects.requireNonNull(pageRequest);
        Objects.requireNonNull(pageRequest.getOffset());
        Objects.requireNonNull(pageRequest.getLength());
        this.pageRequest = pageRequest;
    }

    @Override
    public boolean add(final T t) {
        if (count >= pageRequest.getOffset() && count < pageRequest.getOffset() + pageRequest.getLength()) {
            values.add(t);
        }
        count++;
        return true;
    }

    @Override
    public void skip(final int count) {
        this.count += count;
    }

    @Override
    public int size() {
        return values.size();
    }

    @Override
    public ResultPage<T> build() {
        final PageResponse response = new PageResponse(pageRequest.getOffset(), values.size(), count, true);
        return new ResultPage<>(values, response);
    }
}