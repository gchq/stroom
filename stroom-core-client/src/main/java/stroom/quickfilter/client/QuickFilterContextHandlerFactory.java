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

package stroom.quickfilter.client;

import stroom.dispatch.client.QuietTaskMonitorFactory;
import stroom.dispatch.client.RestFactory;
import stroom.quickfilter.shared.QuickFilterContext;
import stroom.quickfilter.shared.QuickFilterHistoryKey;
import stroom.quickfilter.shared.QuickFilterHistoryResource;
import stroom.quickfilter.shared.RecordQuickFilterUseRequest;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.dropdowntree.client.view.QuickFilter;
import stroom.widget.dropdowntree.client.view.QuickFilterContextHandler;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.InfoMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.ShowMenuEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupPosition.PopupLocation;
import stroom.widget.util.client.Rect;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.HasHandlers;
import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Connects a {@link QuickFilter} to its server-side history. One call per screen, next to
 * where it registers its tooltip:
 * <pre>
 * view.setQuickFilterContextHandler(
 *         handlerFactory.create(DependencyCriteria.QUICK_FILTER_CONTEXT, this, this));
 * </pre>
 * The widget itself cannot do this - it lives in a module with no REST layer and no event bus -
 * so it exposes {@link QuickFilterContextHandler} and this is the one implementation.
 */
public class QuickFilterContextHandlerFactory {

    private static final QuickFilterHistoryResource RESOURCE = GWT.create(QuickFilterHistoryResource.class);

    private final RestFactory restFactory;

    @Inject
    public QuickFilterContextHandlerFactory(final RestFactory restFactory) {
        this.restFactory = restFactory;
    }

    /**
     * @param context            which quick filter this is - see {@link QuickFilterContext}
     * @param hasHandlers        where to fire the drop-down menu from; normally the presenter
     * @param taskMonitorFactory what to show busy while the history loads; normally the presenter
     */
    public QuickFilterContextHandler create(final QuickFilterContext context,
                                            final HasHandlers hasHandlers,
                                            final TaskMonitorFactory taskMonitorFactory) {
        return new Handler(context.toHistoryKey(), hasHandlers, taskMonitorFactory);
    }


    // --------------------------------------------------------------------------------


    private class Handler implements QuickFilterContextHandler {

        private final QuickFilterHistoryKey key;
        private final HasHandlers hasHandlers;
        private final TaskMonitorFactory taskMonitorFactory;

        Handler(final QuickFilterHistoryKey key,
                final HasHandlers hasHandlers,
                final TaskMonitorFactory taskMonitorFactory) {
            this.key = key;
            this.hasHandlers = hasHandlers;
            this.taskMonitorFactory = taskMonitorFactory;
        }

        @Override
        public void showRecentFilters(final QuickFilter quickFilter, final Consumer<String> onSelect) {
            // Fetched on click rather than on load: a screen full of quick filters would
            // otherwise fire a request each for a list the user may never open, and the list
            // is stale the moment they use it anyway.
            restFactory
                    .create(RESOURCE)
                    .method(res -> res.fetch(key))
                    .onSuccess(filters -> showMenu(quickFilter, filters, onSelect))
                    .onFailure(error -> showMenu(quickFilter, null, onSelect))
                    .taskMonitorFactory(taskMonitorFactory)
                    .exec();
        }

        private void showMenu(final QuickFilter quickFilter,
                              final List<String> filters,
                              final Consumer<String> onSelect) {
            final List<Item> items = new ArrayList<>();
            if (filters == null) {
                items.add(InfoMenuItem.builder()
                        .text(SafeHtmlUtil.getSafeHtml("Could not load recent filters"))
                        .disabled()
                        .build());
            } else if (filters.isEmpty()) {
                items.add(InfoMenuItem.builder()
                        .text(SafeHtmlUtil.getSafeHtml("No recent filters"))
                        .disabled()
                        .build());
            } else {
                int priority = 0;
                for (final String filter : filters) {
                    items.add(new IconMenuItem.Builder()
                            .priority(priority++)
                            .text(filter)
                            .tooltip(filter)
                            .command(() -> onSelect.accept(filter))
                            .build());
                }
            }

            final Rect relativeRect = new Rect(quickFilter.getElement()).grow(3);
            ShowMenuEvent.builder()
                    .items(items)
                    .popupPosition(new PopupPosition(relativeRect, PopupLocation.BELOW))
                    .addAutoHidePartner(quickFilter.getElement())
                    .fire(hasHandlers);
        }

        @Override
        public void recordUse(final String filterText) {
            // Fire and forget. A history that fails to record is not something to interrupt the
            // user over, and the next accepted filter will try again.
            restFactory
                    .create(RESOURCE)
                    .call(res -> res.record(new RecordQuickFilterUseRequest(key, filterText)))
                    .onFailure(error -> {
                    })
                    .taskMonitorFactory(new QuietTaskMonitorFactory())
                    .exec();
        }
    }
}
