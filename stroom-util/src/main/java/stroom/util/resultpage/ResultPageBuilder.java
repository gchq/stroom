package stroom.util.resultpage;

import stroom.util.shared.PageRequest;
import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A result page builder that will receive results until we reach the requested page size.
 * When we have received enough values the add method will return false to inform the adding process that it should
 * stop.
 *
 * @param <T>
 */
public class ResultPageBuilder<T> {

    private final PageRequest pageRequest;
    private final List<T> values = new ArrayList<>();
    private boolean exact = true;
    private long count;

    public ResultPageBuilder(final PageRequest pageRequest) {
        Objects.requireNonNull(pageRequest);
        Objects.requireNonNull(pageRequest.getOffset());
        Objects.requireNonNull(pageRequest.getLength());
        this.pageRequest = pageRequest;
    }

    public boolean add(final T t) {
        if (count >= pageRequest.getOffset()) {
            if (count < pageRequest.getOffset() + pageRequest.getLength()) {
                values.add(t);
            } else {
                exact = false;
                return false;
            }
        }
        count++;
        return true;
    }

    public void skip(final int count) {
        this.count += count;
    }

    public int size() {
        return values.size();
    }

    public ResultPage<T> build() {
        final PageResponse response = new PageResponse(pageRequest.getOffset(), values.size(), count, exact);
        return new ResultPage<>(values, response);
    }
}
