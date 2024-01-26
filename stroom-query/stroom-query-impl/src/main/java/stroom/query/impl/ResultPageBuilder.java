package stroom.query.impl;

import stroom.util.shared.PageRequest;
import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ResultPageBuilder<T> {

    private List<T> list = new ArrayList<>();
    private final int maxSize;
    private final int trimSize;
    private final PageRequest pageRequest;
    private final Comparator<T> comparator;

    public ResultPageBuilder(final PageRequest pageRequest,
                             final Comparator<T> comparator) {
        this.pageRequest = pageRequest;
        this.comparator = comparator;

        maxSize = pageRequest.getOffset() + pageRequest.getLength();
        trimSize = maxSize + 1000;
    }

    public ResultPage<T> build() {
        list.sort(comparator);
        if (list.size() > pageRequest.getOffset()) {
            int endIndex = pageRequest.getOffset() + pageRequest.getLength();
            endIndex = Math.min(endIndex, list.size());
            final List<T> sublist = list.subList(pageRequest.getOffset(), endIndex);
            return new ResultPage<>(sublist,
                    new PageResponse(
                            pageRequest.getOffset(),
                            endIndex,
                            (long) endIndex,
                            false));
        }
        return new ResultPage<>(Collections.emptyList(),
                new PageResponse(
                        pageRequest.getOffset(),
                        0,
                        0L,
                        false));
    }

    public void add(final T t) {
        list.add(t);
        if (list.size() > trimSize) {
            list.sort(comparator);
            list = list.subList(0, maxSize);
        }
    }
}
