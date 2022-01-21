package stroom.query.common.v2;

public interface Data {

    /**
     * Get root items from the data.
     *
     * @return Root items.
     */
    Items get();

    /**
     * Get child items from the data for the provided parent key.
     *
     * @param key The parent key to get child items for.
     * @return The child items for the parent key.
     */
    Items get(final Key key);
}
