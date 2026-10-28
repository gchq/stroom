/*
 * Copyright 2023 Crown Copyright
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

package stroom.explorer.client.component;

import stroom.explorer.client.RecentItems;
import stroom.explorer.shared.ExplorerTreeFilter;
import stroom.quickfilter.client.QuickFilterContextHandlerFactory;
import stroom.widget.popup.client.PopupSize;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// The Recent Items dialog ([FindPanel]): the documents the user opened last, filtered by name,
/// and opens the one chosen. Made once by the app and shown again, it keeps the last filter.
public class RecentItemsController extends FindController {

    private final RecentItems recentItems;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                         The application's event bus.
    /// @param view                             The content.
    /// @param findResultList                   Finds the documents, and lists them.
    /// @param recentItems                      The documents the user opened last.
    /// @param quickFilterContextHandlerFactory Makes the filter's recent filters.
    @Inject
    public RecentItemsController(final EventBus eventBus,
                                 final FindPanel view,
                                 final FindDocResultListController findResultList,
                                 final RecentItems recentItems,
                                 final QuickFilterContextHandlerFactory quickFilterContextHandlerFactory) {
        super(eventBus, view, findResultList, quickFilterContextHandlerFactory);
        this.recentItems = recentItems;
        view.setFilterGuidance("Filter recent items by name", null);
    }

    @Override
    protected void setUpFilter(final ExplorerTreeFilter.Builder explorerTreeFilterBuilder) {
        explorerTreeFilterBuilder.recentItems(recentItems.getRecentItems());
        super.setUpFilter(explorerTreeFilterBuilder);
    }

    @Override
    protected String getCaption() {
        return "Recent Items";
    }

    @Override
    protected PopupSize getPopupSize() {
        return PopupSize.resizable(800, 800);
    }
}
