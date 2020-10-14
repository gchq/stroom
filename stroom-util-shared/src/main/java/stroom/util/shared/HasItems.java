package stroom.util.shared;

public interface HasItems {

    /**
     * @return The name for the type of item being represented,
     * e.g. Record, Part, etc.
     */
    String getName();

    /**
     * @return The item number, zero based.
     */
    long getItemNo();

    /**
     * @param itemNo The item number, zero based.
     */
    void setItemNo(final long itemNo);

    RowCount<Long> getTotalItemsCount();

    boolean areNavigationControlsVisible();

    /**
     * Called when the user clicks the |< button
     */
    void firstItem();

    /**
     * Called when the user clicks the > button
     */
    void nextItem();

    /**
     * Called when the user clicks the < button
     */
    void previousItem();

    /**
     * Called when the user clicks the >| button
     */
    void lastItem();

    /**
     * Called when the user clicks refresh
     */
    void refresh();

    default boolean isFirstItem() {
        // Zero based
        return getItemNo() == 0;
    }

    default boolean isLastItem() {
        // Zero based
        return getTotalItemsCount().isExact()
                && getTotalItemsCount().getCount() - 1 == getItemNo();
    }
}
