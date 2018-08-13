package stroom.refdata.onheapstore;

import stroom.entity.shared.Range;
import stroom.refdata.offheapstore.MapDefinition;

import java.util.Comparator;

class RangeValueStoreKey implements Comparable<RangeValueStoreKey> {

    private static final Comparator<RangeValueStoreKey> RANGE_COMPARATOR = Comparator
            .comparingLong(rangeValueStoreKey -> rangeValueStoreKey.getRange().getFrom());

    private static final Comparator<RangeValueStoreKey> COMPARATOR = Comparator
            .comparing(RangeValueStoreKey::getMapDefinition)
            .thenComparing(RANGE_COMPARATOR);

    private final MapDefinition mapDefinition;
    private final Range<Long> range;

    private RangeValueStoreKey(final MapDefinition mapDefinition, final Range<Long> range) {
        this.mapDefinition = mapDefinition;
        this.range = range;
    }

    MapDefinition getMapDefinition() {
        return mapDefinition;
    }

    Range<Long> getRange() {
        return range;
    }

    @Override
    public int compareTo(final RangeValueStoreKey that) {
        return COMPARATOR.compare(this, that);
    }
}
