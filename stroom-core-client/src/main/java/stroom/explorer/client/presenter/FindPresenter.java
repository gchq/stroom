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

package stroom.explorer.client.presenter;

import stroom.explorer.client.event.ShowFindEvent;
import stroom.explorer.client.presenter.FindPresenter.FindListener;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerTreeFilter;
import stroom.quickfilter.client.QuickFilterContextHandlerFactory;
import stroom.ui.config.client.UiConfigCache;
import stroom.widget.dropdowntree.client.view.QuickFilterTooltipUtil;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

public class FindPresenter
        extends AbstractFindPresenter
        implements ShowFindEvent.Handler {

    // Read by a screen reader when the filter is focused; the syntax help has the full detail
    private static final String FILTER_DESCRIPTION = "Finds items whose name contains the text. "
                                                     + "Separate several terms with spaces. "
                                                     + "Use type:, uuid: or tag: to match other fields, "
                                                     + "e.g. type:Feed.";

    private boolean showing;

    @Inject
    public FindPresenter(final EventBus eventBus,
                         final FindView view,
                         final FindDocResultListPresenter findResultListPresenter,
                         final QuickFilterContextHandlerFactory quickFilterContextHandlerFactory,
                         final UiConfigCache uiConfigCache) {
        super(eventBus, view, findResultListPresenter, quickFilterContextHandlerFactory);

        view.setFilterGuidance("Find by name", FILTER_DESCRIPTION);
        // The same fields as the explorer tree's filter
        uiConfigCache.get(uiConfig -> {
            if (uiConfig != null) {
                view.registerPopupTextProvider(() -> QuickFilterTooltipUtil.createTooltip(
                        "Find Quick Filter",
                        ExplorerTreeFilter.FIELD_DEFINITIONS,
                        uiConfig.getHelpUrlQuickFilter()));
            }
        }, this);
    }

    @Override
    public void onShow(final ShowFindEvent event) {
        if (!showing) {
            showing = true;

            // Make sure we are set to focus text next time we show.
            getFindResultListPresenter().setFocusText(true);

            final ExplorerTreeFilter.Builder explorerTreeFilterBuilder =
                    getFindResultListPresenter().getExplorerTreeFilterBuilder();
            // Don't want favourites in the recent items as they are effectively duplicates
            explorerTreeFilterBuilder.includedRootTypes(ExplorerConstants.SYSTEM_TYPE);
            explorerTreeFilterBuilder.setNameFilter(
                    explorerTreeFilterBuilder.build().getNameFilter(),
                    true);

            // Refresh the results.
            refresh();

            final PopupSize popupSize = PopupSize.resizable(800, 600);
            ShowPopupEvent.builder(this)
                    .popupType(PopupType.CLOSE_DIALOG)
                    .popupSize(popupSize)
                    .caption("Find")
                    .onShow(e -> getView().focus())
                    .onHideRequest(HidePopupRequestEvent::hide)
                    .onHide(e -> showing = false)
                    .fire();
        }
    }

    /// Hears the app-wide events the screen handles, and makes the screen when it is first needed.
    public static final class FindListener {

        @Inject
        public FindListener(final EventBus eventBus, final Provider<FindPresenter> presenter) {
            eventBus.addHandler(ShowFindEvent.getType(), event -> presenter.get().onShow(event));
        }
    }
}
