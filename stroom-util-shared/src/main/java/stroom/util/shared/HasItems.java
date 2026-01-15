/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.util.shared;

public interface HasItems {

    /**
     * @return The name for the type of item being represented,
     * e.g. Record, Part, etc.
     */
    String getName();

    /**
     * Called when the navigator needs the item range
     *
     * @return The range of items on the page, zero based.
     */
    OffsetRange getItemRange();

    /**
     * Called when the navigator needs the item from offset.
     * Inclusive
     *
     * @return The first item number on the page, zero based.
     */
    default long getItemOffsetFrom() {
        return getItemRange().getOffset();
    }

    /**
     * Called when the navigator needs the item to offset.
     * Inclusive
     *
     * @return The last item number on the page, zero based.
     */
    default long getItemOffsetTo() {
        return getItemRange().getOffset() + getItemRange().getLength() - 1;
    }

    /**
     * Called when the navigator sets the item no.
     *
     * @param itemNo The first item number on the page, zero based.
     */
    void setItemNo(final long itemNo);

    Count<Long> getTotalItemsCount();

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
