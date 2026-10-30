/*
 * Copyright 2025 Crown Copyright
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

package stroom.contentstore.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.contentstore.shared.ContentStoreContentPack;
import stroom.contentstore.shared.ContentStoreContentPackStatus;
import stroom.contentstore.shared.ContentStoreContentPackWithDynamicState;
import stroom.contentstore.shared.ContentStoreResource;
import stroom.dispatch.client.RestDataProvider;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.widget.component.client.Controller;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.gwt.core.client.GWT;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/// The content packs at the top of the App Store tab ([ContentStoreContentPackList]): a page at a
/// time, then each installed pack on it asked, one after another, whether it can be upgraded. What
/// is learnt is kept, so paging back doesn't ask again.
public class ContentStoreContentPackListController extends Controller {

    private static final ContentStoreResource CONTENT_STORE_RESOURCE = GWT.create(ContentStoreResource.class);

    private ContentStoreContentPackList view;
    private final RestFactory restFactory;

    // Map of Content Packs -> status
    private final Map<ContentStoreContentPack, ContentStoreContentPackStatus> contentPackStatusCache
            = new HashMap<>();

    // Flag set when we request a new page; used to ignore unwanted LOADED events
    private boolean requestedNewPage = false;
    private Runnable onStateChange = () -> {
    };

    /// Made by GIN; [#attach(ContentStoreContentPackList)] gives it its list.
    ///
    /// @param eventBus    The application's event bus.
    /// @param restFactory Lists the packs and checks for upgrades.
    @Inject
    public ContentStoreContentPackListController(final EventBus eventBus,
                                                 final RestFactory restFactory) {
        super(eventBus);
        this.restFactory = restFactory;
    }

    /// Attaches this to a list, e.g. one a template declares. The list is then this controller's
    /// widget.
    ///
    /// @param view The list.
    /// @return This, for its owner to keep.
    public ContentStoreContentPackListController attach(final ContentStoreContentPackList view) {
        this.view = Objects.requireNonNull(view, "view");
        initWidget(view);

        view.setDataProvider(createDataProvider(getEventBus()));
        view.setOnLoaded(() -> {
            // We get a LOADED event on every redraw, so we
            // use a flag to detect the new page requests
            if (requestedNewPage) {
                requestedNewPage = false;
                loadStateFromCache();
                doUpgradeCheck();
            }
        });
        return this;
    }

    /// @param onStateChange Run when a pack's status changes, e.g. an upgrade is found, so the page
    ///                      shows it.
    public void setOnStateChange(final Runnable onStateChange) {
        this.onStateChange = onStateChange;
    }

    /// @return The selection, one pack at most.
    public MultiSelectionModel<ContentStoreContentPackWithDynamicState> getSelectionModel() {
        return view.getSelectionModel();
    }

    /// Redraws this list so any state changes show up.
    public void redraw() {
        view.redraw();
    }

    // Called when the page has loaded to load up all the state we've found so far into the rows.
    // Having a cache means we don't need to redo work if we page backwards.
    private void loadStateFromCache() {
        boolean dirty = false;
        for (int iCp = 0; iCp < view.getVisibleItemCount(); ++iCp) {
            final ContentStoreContentPackWithDynamicState cpws = view.getVisibleItem(iCp);
            if (cpws != null) {
                final ContentStoreContentPackStatus status = contentPackStatusCache.get(cpws.getContentPack());

                // If the status exists and doesn't match the stored status
                // then change it
                if (status != null
                    && !status.equals(cpws.getInstallationStatus())) {

                    cpws.setInstallationStatus(status);
                    dirty = true;
                }
            }
        }

        if (dirty) {
            onStateChange.run();
        }
    }

    private RestDataProvider<ContentStoreContentPackWithDynamicState,
            ResultPage<ContentStoreContentPackWithDynamicState>> createDataProvider(final EventBus eventBus) {

        return new RestDataProvider<>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<ContentStoreContentPackWithDynamicState>> dataConsumer,
                                final RestErrorHandler restErrorHandler) {
                final PageRequest pageRequest = new PageRequest(range.getStart(), range.getLength());
                restFactory
                        .create(CONTENT_STORE_RESOURCE)
                        .method((r) -> {
                            // Set flag so load event isn't ignored
                            requestedNewPage = true;
                            return r.list(pageRequest);
                        })
                        .onSuccess(dataConsumer)
                        .onFailure(restErrorHandler)
                        .owner(ContentStoreContentPackListController.this)
                        .taskMonitorFactory(view.getTaskMonitor())
                        .exec();
            }
        };
    }

    // Called once everything has loaded into the list
    private void doUpgradeCheck() {
        if (view.getVisibleItemCount() > 0) {
            doUpgradeCheckOn(0);
        }
    }

    // Called asynchronously to check a visible row; a row past the end is ignored
    private void doUpgradeCheckOn(final int rowIndex) {
        if (rowIndex < view.getVisibleItemCount()) {
            final ContentStoreContentPackWithDynamicState cpws = view.getVisibleItem(rowIndex);
            if (cpws != null) {

                // Only check items with status INSTALLED
                // and that we haven't checked before
                // Checking other items would waste processor power
                final ContentStoreContentPackStatus status = cpws.getInstallationStatus();
                if (cpws.getContentPack().getGitCommit().isEmpty()
                    && status.equals(ContentStoreContentPackStatus.INSTALLED)
                    && !contentPackStatusCache.containsKey(cpws.getContentPack())) {

                    restFactory
                            .create(CONTENT_STORE_RESOURCE)
                            .method(res -> res.checkContentUpgradeAvailable(cpws.getContentPack()))
                            .onSuccess(upgradeAvailable -> {
                                if (upgradeAvailable.isOk()) {
                                    if (upgradeAvailable.getValue() != null && upgradeAvailable.getValue()) {
                                        cpws.setInstallationStatus(ContentStoreContentPackStatus.CONTENT_UPGRADABLE);
                                        contentPackStatusCache.put(cpws.getContentPack(), cpws.getInstallationStatus());
                                        onStateChange.run();
                                    } else {
                                        contentPackStatusCache.put(cpws.getContentPack(), cpws.getInstallationStatus());
                                    }
                                } else {
                                    cpws.setInstallationStatus(ContentStoreContentPackStatus.ERROR);
                                    contentPackStatusCache.put(cpws.getContentPack(), cpws.getInstallationStatus());
                                }
                                // Check the next item
                                doUpgradeCheckOn(rowIndex + 1);
                            })
                            .onFailure(restError -> AlertEvent.fireError(this,
                                    "Check for updated content failed",
                                    restError.getMessage(),
                                    null))
                            .owner(this)
                            .taskMonitorFactory(view.getTaskMonitor())
                            .exec();
                } else {
                    // Try the next content pack
                    doUpgradeCheckOn(rowIndex + 1);
                }
            } else {
                // Try the next row
                doUpgradeCheckOn(rowIndex + 1);
            }
        }
    }
}
