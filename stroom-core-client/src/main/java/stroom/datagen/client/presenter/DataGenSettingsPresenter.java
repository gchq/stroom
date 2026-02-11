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

package stroom.datagen.client.presenter;

import stroom.datagen.client.presenter.DataGenSettingsPresenter.DataGenSettingsView;
import stroom.datagen.shared.DataGenDoc;
import stroom.docref.DocRef;
import stroom.document.client.event.DirtyUiHandlers;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.feed.shared.FeedDoc;
import stroom.security.shared.DocumentPermission;
import stroom.ui.config.client.UiConfigCache;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

public class DataGenSettingsPresenter
        extends DocumentEditPresenter<DataGenSettingsView, DataGenDoc>
        implements DirtyUiHandlers {

    final DocSelectionBoxPresenter destinationFeedPresenter;
    private final UiConfigCache uiConfigCache;

    @Inject
    public DataGenSettingsPresenter(final EventBus eventBus,
                                    final DataGenSettingsView view,
                                    final DocSelectionBoxPresenter destinationFeedPresenter,
                                    final UiConfigCache uiConfigcache) {
        super(eventBus, view);
        this.destinationFeedPresenter = destinationFeedPresenter;
        this.uiConfigCache = uiConfigcache;
        view.setUiHandlers(this);

        destinationFeedPresenter.setIncludedTypes(FeedDoc.TYPE);
        destinationFeedPresenter.setRequiredPermissions(DocumentPermission.VIEW);
        getView().setDestinationFeed(destinationFeedPresenter.getView());
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(destinationFeedPresenter.addDataSelectionHandler(e -> onDirty()));
    }

    @Override
    protected void onRead(final DocRef docRef, final DataGenDoc dataGenDoc, final boolean readOnly) {
        uiConfigCache.get(extendedUiConfig -> {
            if (extendedUiConfig != null) {
                final DocRef selectedDocRef = dataGenDoc.getFeed();

                if (selectedDocRef != null) {
                    destinationFeedPresenter.setSelectedEntityReference(selectedDocRef, true);
                }
                getView().setTemplate(dataGenDoc.getTemplate());
            }
        }, this);
    }

    @Override
    public void onDirty() {
        setDirty(true);
    }

    @Override
    protected DataGenDoc onWrite(final DataGenDoc doc) {
        return doc
                .copy()
                .template(getView().getTemplate())
                .feed(destinationFeedPresenter.getSelectedEntityReference())
                .build();
    }

    public interface DataGenSettingsView extends View, HasUiHandlers<DirtyUiHandlers> {

        void setDestinationFeed(View view);

        String getTemplate();

        void setTemplate(String template);
    }
}
