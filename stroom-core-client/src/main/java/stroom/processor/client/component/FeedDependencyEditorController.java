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

package stroom.processor.client.component;

import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.explorer.client.component.DocSelectionController;
import stroom.feed.client.FeedClient;
import stroom.feed.shared.FeedDoc;
import stroom.meta.shared.MetaResource;
import stroom.processor.shared.FeedDependency;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.BiConsumer;

/// The New and Edit Feed Dependency dialog ([FeedDependencyEditor]) of a processor filter, made for
/// one use: a feed, chosen with a [DocSelectionController], and a stream type.
public class FeedDependencyEditorController extends Controller {

    private static final MetaResource META_RESOURCE = GWT.create(MetaResource.class);

    private final FeedDependencyEditor view;
    private final DocSelectionController feed;
    private final RestFactory restFactory;
    private final FeedClient feedClient;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus             The application's event bus.
    /// @param view                 The content.
    /// @param docSelectionProvider Makes the feed picker's controller.
    /// @param restFactory          Fetches the stream types.
    /// @param feedClient           Finds the feed of a feed name.
    @Inject
    public FeedDependencyEditorController(final EventBus eventBus,
                                          final FeedDependencyEditor view,
                                          final Provider<DocSelectionController> docSelectionProvider,
                                          final RestFactory restFactory,
                                          final FeedClient feedClient) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        this.feedClient = feedClient;
        initWidget(view);
        feed = make(docSelectionProvider);
        feed.attach(view.getFeedBox());
        feed.setIncludedTypes(FeedDoc.TYPE);
        feed.setRequiredPermissions(DocumentPermission.USE);
    }

    /// Edits a feed dependency in a dialog, owned by `opener` until it is closed.
    ///
    /// @param opener         What shows it.
    /// @param caption        The dialog's caption, e.g. "New Feed Dependency".
    /// @param feedDependency The dependency.
    /// @param onOk           Given the dependency entered and the dialog's OK, which it hides or
    ///                       resets.
    public void edit(final HasControllerNode opener,
                     final String caption,
                     final FeedDependency feedDependency,
                     final BiConsumer<FeedDependency, HidePopupRequestEvent> onOk) {
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizableX())
                .caption(caption)
                .onShow(e -> {
                    read(feedDependency);
                    feed.focus();
                })
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        onOk.accept(new FeedDependency(feedDependency.getUuid(),
                                NullSafe.get(feed.getSelectedEntityReference(), DocRef::getName),
                                view.getTypeBox().getValue()), e);
                    } else {
                        e.hide();
                    }
                })
                .fire();
    }

    private void read(final FeedDependency feedDependency) {
        // Fetch the doc ref for the supplied feed name.
        feedClient.getDocRefForName(feedDependency.getFeedName(), docRef ->
                feed.setSelectedEntityReference(docRef, true), taskMonitor());

        view.getTypeBox().clear();
        restFactory
                .create(META_RESOURCE)
                .method(MetaResource::getTypes)
                .onSuccess(result -> {
                    if (result != null) {
                        view.getTypeBox().addItems(result);
                    }
                    if (feedDependency.getStreamType() != null) {
                        view.getTypeBox().setValue(feedDependency.getStreamType());
                    }
                })
                .owner(this)
                .taskMonitorFactory(taskMonitor())
                .exec();
    }

}
