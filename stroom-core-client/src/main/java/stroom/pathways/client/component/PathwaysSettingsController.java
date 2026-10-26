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

package stroom.pathways.client.component;

import stroom.docref.DocRef;
import stroom.explorer.client.component.DocSelectionController;
import stroom.feed.shared.FeedDoc;
import stroom.pathways.shared.PathwaysDoc;
import stroom.pathways.shared.TracesDoc;
import stroom.security.shared.DocumentPermission;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

/// A pathways document's Settings tab ([PathwaysSettings]), with its trace store's and info feed's
/// pickers.
public class PathwaysSettingsController extends Controller {

    private final PathwaysSettings view;
    private final DocSelectionController traceStore;
    private final DocSelectionController infoFeed;

    /// Made by GIN, with its tab.
    ///
    /// @param eventBus             The application's event bus.
    /// @param view                 The tab.
    /// @param docSelectionProvider Makes the pickers' controllers.
    @Inject
    public PathwaysSettingsController(final EventBus eventBus,
                                      final PathwaysSettings view,
                                      final Provider<DocSelectionController> docSelectionProvider) {
        super(eventBus);
        this.view = view;
        initWidget(view);

        traceStore = make(docSelectionProvider);
        traceStore.attach(view.getTraceStoreBox());
        traceStore.setIncludedTypes(TracesDoc.TYPE);
        traceStore.setRequiredPermissions(DocumentPermission.VIEW);
        infoFeed = make(docSelectionProvider);
        infoFeed.attach(view.getInfoFeedBox());
        infoFeed.setIncludedTypes(FeedDoc.TYPE);
        infoFeed.setRequiredPermissions(DocumentPermission.VIEW);

        view.setOnChange(this::markChanged);
        own(traceStore.addSelectionListener(e -> markChanged()));
        own(infoFeed.addSelectionListener(e -> markChanged()));
        watch(Contexts.READ_ONLY, this::setReadOnly);
    }

    /// Shows a pathways document's settings.
    ///
    /// @param docRef The document.
    /// @param doc    Its settings.
    public void read(final DocRef docRef, final PathwaysDoc doc) {
        view.read(doc);
        traceStore.setSelectedEntityReference(doc.getTracesDocRef(), true);
        infoFeed.setSelectedEntityReference(doc.getInfoFeed(), true);
    }

    /// Writes the settings shown into a copy of a pathways document.
    ///
    /// @param doc The document.
    /// @return A copy of it with the settings shown.
    public PathwaysDoc write(final PathwaysDoc doc) {
        return view.write(doc.copy())
                .tracesDocRef(traceStore.getSelectedEntityReference())
                .infoFeed(infoFeed.getSelectedEntityReference())
                .build();
    }

    // The pickers too: they could change a read-only document's trace store and info feed
    private void setReadOnly(final boolean readOnly) {
        view.setReadOnly(readOnly);
        traceStore.setReadOnly(readOnly);
        infoFeed.setReadOnly(readOnly);
    }
}
