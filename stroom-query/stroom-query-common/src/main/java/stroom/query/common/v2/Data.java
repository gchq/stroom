package stroom.query.common.v2;

import stroom.query.api.v2.TimeFilter;

public interface Data {

    /**
     * Get child items from the data for the provided parent key and time filter.
     *
     * @param key        The parent key to get child items for.
     * @param timeFilter The time filter to use to limit the data returned.
     * @return The filtered child items for the parent key.
     */
    Items get(Key key, TimeFilter timeFilter);
}
