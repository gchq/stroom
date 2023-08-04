package stroom.query.common.v2;

import stroom.query.api.v2.OffsetRange;
import stroom.query.api.v2.TimeFilter;

import java.util.Set;

public interface Data {

    /**
     * Get child items from the data for the provided parent key and time filter.
     *
     * @param key        The parent key to get child items for.
     * @param timeFilter The time filter to use to limit the data returned.
     * @return The filtered child items for the parent key.
     */
    <R> Items<R> get(OffsetRange range,
                     Set<Key> openGroups,
                     TimeFilter timeFilter,
                     ItemMapper<R> mapper);
}
