package stroom.util.shared;

public interface HasItems {

    /**
     * @return The name for the type of item being represented,
     * e.g. Record, Part, etc.
     */
    String getName();

    /**
     * @return The first item number on the page, zero based.
     */
    long getItemNo();

    /**
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
        return getItemNo() == 0;
    }

    default boolean isLastPage() {
        // Zero based
        final long lastPossibleItemNoOnPage = getItemNo() + getMaxItemsPerPage() - 1;
        return getTotalItemsCount().isExact()
                && getTotalItemsCount().getCount() - 1 < lastPossibleItemNoOnPage;
    }
}
