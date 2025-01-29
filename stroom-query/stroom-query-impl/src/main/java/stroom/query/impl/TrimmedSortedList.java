package stroom.query.impl;

import stroom.util.shared.PageRequest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TrimmedSortedList<T> {

    private List<T> list = new ArrayList<>();
    private final int maxSize;
    private final int trimSize;
    private final Comparator<T> comparator;

    public TrimmedSortedList(final PageRequest pageRequest,
                             final Comparator<T> comparator) {
        this.comparator = comparator;

        maxSize = Math.max(0, pageRequest.getOffset() + pageRequest.getLength() + 1);
        trimSize = maxSize + 1000;
    }

    public void add(final T t) {
        list.add(t);
        if (list.size() > trimSize) {
            list.sort(comparator);
            list = list.subList(0, maxSize);
        }
    }

    public List<T> getList() {
        list.sort(comparator);
        if (list.size() > maxSize) {
            list = list.subList(0, maxSize);
        }
        return list;
    }
}
