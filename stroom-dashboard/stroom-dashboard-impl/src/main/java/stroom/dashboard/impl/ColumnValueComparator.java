package stroom.dashboard.impl;

import stroom.dashboard.shared.ColumnValue;

import java.util.Comparator;

public class ColumnValueComparator implements Comparator<ColumnValue> {

    private final GenericComparator genericComparator = new GenericComparator();

    @Override
    public int compare(final ColumnValue o1, final ColumnValue o2) {
        return genericComparator.compare(o1.getValue(), o2.getValue());
    }
}
