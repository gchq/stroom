/*
 * Copyright 2026 Crown Copyright
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

package stroom.data.client.component;

import stroom.util.shared.HasItems;
import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.inject.Provider;

/// Moves the data viewer between the items of what it shows ([HasItems], e.g. a stream's records)
/// with its [ItemNavigator], and opens the item selection dialog when the user asks to choose one.
public class ItemNavigatorController extends Controller {

    private final ItemNavigator view;
    private final Provider<ItemSelectionController> itemSelectionProvider;
    private HasItems display;

    /// Made by GIN, with its navigator and what makes its selection dialog.
    ///
    /// @param view                  Its component.
    /// @param itemSelectionProvider Makes the item selection.
    @Inject
    public ItemNavigatorController(final ItemNavigator view,
                                   final Provider<ItemSelectionController> itemSelectionProvider) {
        this.view = view;
        this.itemSelectionProvider = itemSelectionProvider;
        initWidget(view);
        view.setOnAction(this::onAction);
    }

    /// @param display What the navigator moves between the items of, or null for nothing.
    public void setDisplay(final HasItems display) {
        this.display = display;
        refreshNavigator();
    }

    /// Shows the items now shown.
    public void refreshNavigator() {
        view.show(display);
    }

    /// @param refreshing Whether the items are being fetched, which the refresh button shows.
    public void setRefreshing(final boolean refreshing) {
        view.setRefreshing(refreshing);
    }

    private void onAction(final NavigatorAction action) {
        if (display == null) {
            return;
        }
        switch (action) {
            case FIRST:
                display.firstPage();
                break;
            case PREVIOUS:
                display.previousPage();
                break;
            case NEXT:
                display.nextPage();
                break;
            case LAST:
                display.lastPage();
                break;
            case REFRESH:
                display.refresh();
                break;
            case SELECT:
                selectItem();
                break;
        }
    }

    private void selectItem() {
        final HasItems items = display;
        final long offset = items.getItemRange().getOffset();
        itemSelectionProvider.get().edit(this, items.getName(), offset, items.getTotalItemsCount(), itemNo -> {
            if (itemNo != offset) {
                items.setItemNo(itemNo);
            }
        });
    }
}
