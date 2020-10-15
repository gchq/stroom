package stroom.util.shared;

public interface HasItems {

    /**
     * @return The name for the type of item being represented,
     * e.g. Record, Part, etc.
     */
    String getName();

    /**
     * Called when the navigator needs the item range
     * @return The range of items on the page, zero based.
     */
    OffsetRange<Long> getItemRange();

    /**
     * Called when the navigator needs the item from offset.
     * Inclusive
     * @return The first item number on the page, zero based.
     */
    default long getItemOffsetFrom() {
        return getItemRange().getOffset();
    }

    /**
     * Called when the navigator needs the item to offset.
     * Inclusive
     * @return The last item number on the page, zero based.
     */
    default long getItemOffsetTo() {
        return getItemRange().getOffset() + getItemRange().getLength() - 1;
    }

    /**
     * Called when the navigator sets the item no.
     * @param itemNo The first item number on the page, zero based.
     */
    void setItemNo(final long itemNo);

    RowCount<Long> getTotalItemsCount();

    boolean areNavigationControlsVisible();

    int getMaxItemsPerPage();

    default boolean hasMultipleItemsPerPage() {
        return getMaxItemsPerPage() > 1;
    }

    /**
     * Called when the user clicks the |< button
     */
    void firstPage();

    /**
     * Called when the user clicks the > button
     */
    void nextPage();

    /**
     * Called when the user clicks the < button
     */
    void previousPage();

    /**
     * Called when the user clicks the >| button
     */
    void lastPage();

    /**
     * Called when the user clicks refresh
     */
    void refresh();

    default boolean isFirstPage() {
        // Zero based
        return getItemRange().getOffset() == 0;
    }

    default boolean isLastPage() {
        // Zero based
        if (getTotalItemsCount().isExact()) {
            final long lastPossibleItemOffsetOnPage = getItemRange().getOffset() + getMaxItemsPerPage() - 1;
            return getTotalItemsCount().getCount() - 1 <= lastPossibleItemOffsetOnPage;
        } else {
            return false;
        }
    }
}
