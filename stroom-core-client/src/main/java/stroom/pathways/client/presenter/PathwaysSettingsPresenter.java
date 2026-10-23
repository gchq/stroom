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

package stroom.pathways.client.presenter;

import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocPresenter;
import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.feed.shared.FeedDoc;
import stroom.pathways.client.presenter.PathwaysSettingsPresenter.PathwaysSettingsView;
import stroom.pathways.shared.PathwaysDoc;
import stroom.planb.shared.SharedFileStoreSettings;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;
import stroom.util.shared.time.SimpleDuration;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

import java.util.List;

public class PathwaysSettingsPresenter extends DocPresenter<PathwaysSettingsView, PathwaysDoc>
        implements PathwaysSettingsUiHandlers {

    private final DocSelectionBoxPresenter feedPresenter;

    @Inject
    public PathwaysSettingsPresenter(final EventBus eventBus,
                                     final PathwaysSettingsView view,
                                     final DocSelectionBoxPresenter feedPresenter) {
        super(eventBus, view);
        this.feedPresenter = feedPresenter;
        view.setUiHandlers(this);

        feedPresenter.setIncludedTypes(FeedDoc.TYPE);
        feedPresenter.setRequiredPermissions(DocumentPermission.VIEW);
        view.setInfoFeedView(feedPresenter.getView());
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(feedPresenter.addDataSelectionHandler(e -> onChange()));
    }

    @Override
    protected void onRead(final DocRef docRef, final PathwaysDoc doc, final boolean readOnly) {
        getView().setTemporalOrderingTolerance(doc.getTemporalOrderingTolerance());
        getView().setIgnoredSpanNames(doc.getIgnoredSpanNames());
        getView().setIgnoredAttributes(doc.getIgnoredAttributes());
        getView().setNarrowingWindow(doc.getNarrowingWindow());
        feedPresenter.setSelectedEntityReference(doc.getInfoFeed(), true);
        getView().setSharedFileStore(doc.getSharedFileStore());
        // Where a pathway already lives is decided by these two, so once anything has been written
        // under them they stop being editable. The store refuses the change anyway; this is what stops
        // the user making it.
        getView().setSharedFileStoreLocked(doc.hasSharedFileStoreData());
    }

    @Override
    protected PathwaysDoc onWrite(final PathwaysDoc doc) {
        return doc
                .copy()
                .temporalOrderingTolerance(getView().getTemporalOrderingTolerance())
                .ignoredSpanNames(getView().getIgnoredSpanNames())
                .ignoredAttributes(getView().getIgnoredAttributes())
                .narrowingWindow(getView().getNarrowingWindow())
                .infoFeed(feedPresenter.getSelectedEntityReference())
                .sharedFileStore(configuredSharedFileStore())
                .build();
    }

    // The editor always hands back a settings object, filling an untouched form in as an empty path and
    // a shard count of one. Storing that would make every document look configured, so a blank path is
    // written as no shared file store at all, which is what the document's absent state means.
    private SharedFileStoreSettings configuredSharedFileStore() {
        final SharedFileStoreSettings settings = getView().getSharedFileStore();
        return settings == null || NullSafe.isBlankString(settings.getSharedPath())
                ? null
                : settings;
    }

    public interface PathwaysSettingsView
            extends View, ReadOnlyChangeHandler, HasUiHandlers<PathwaysSettingsUiHandlers> {

        void setInfoFeedView(View view);

        SimpleDuration getTemporalOrderingTolerance();

        void setTemporalOrderingTolerance(SimpleDuration temporalOrderingTolerance);

        List<String> getIgnoredSpanNames();

        void setIgnoredSpanNames(List<String> ignoredSpanNames);

        List<String> getIgnoredAttributes();

        SimpleDuration getNarrowingWindow();

        void setNarrowingWindow(SimpleDuration narrowingWindow);

        void setIgnoredAttributes(List<String> ignoredAttributes);









        SharedFileStoreSettings getSharedFileStore();

        void setSharedFileStore(SharedFileStoreSettings sharedFileStore);

        void setSharedFileStoreLocked(boolean locked);
    }
}
