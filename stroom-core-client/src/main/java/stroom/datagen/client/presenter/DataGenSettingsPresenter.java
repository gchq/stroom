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
import stroom.editor.client.presenter.EditorPresenter;
import stroom.entity.client.presenter.DocPresenter;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.feed.shared.FeedDoc;
import stroom.security.shared.DocumentPermission;
import stroom.ui.config.client.UiConfigCache;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

import javax.inject.Provider;

public class DataGenSettingsPresenter
        extends DocPresenter<DataGenSettingsView, DataGenDoc> {

    final DocSelectionBoxPresenter destinationFeedPresenter;
    private final UiConfigCache uiConfigCache;
    private final EditorPresenter templatePresenter;

    @Inject
    public DataGenSettingsPresenter(final EventBus eventBus,
                                    final DataGenSettingsView view,
                                    final DocSelectionBoxPresenter destinationFeedPresenter,
                                    final UiConfigCache uiConfigcache,
                                    final Provider<EditorPresenter> editorPresenterProvider) {
        super(eventBus, view);
        this.destinationFeedPresenter = destinationFeedPresenter;
        this.uiConfigCache = uiConfigcache;
        this.templatePresenter = editorPresenterProvider.get();

        view.setTemplateEditor(templatePresenter.getView());
        destinationFeedPresenter.setIncludedTypes(FeedDoc.TYPE);
        destinationFeedPresenter.setRequiredPermissions(DocumentPermission.VIEW);
        view.setDestinationFeed(destinationFeedPresenter.getView());
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(destinationFeedPresenter.addDataSelectionHandler(e -> onChange()));
        registerHandler(templatePresenter.addValueChangeHandler(e -> onChange()));
    }

    @Override
    protected void onRead(final DocRef docRef, final DataGenDoc dataGenDoc, final boolean readOnly) {
        uiConfigCache.get(extendedUiConfig -> {
            if (extendedUiConfig != null) {
                final DocRef selectedDocRef = dataGenDoc.getFeed();

                if (selectedDocRef != null) {
                    destinationFeedPresenter.setSelectedEntityReference(selectedDocRef, true);
                }
                templatePresenter.setText(dataGenDoc.getTemplate());
                templatePresenter.setReadOnly(false);
                templatePresenter.getFormatAction().setAvailable(!readOnly);
            }
        }, this);
    }

    @Override
    protected DataGenDoc onWrite(final DataGenDoc doc) {
        return doc
                .copy()
                .template(templatePresenter.getText())
                .feed(destinationFeedPresenter.getSelectedEntityReference())
                .build();
    }

    public interface DataGenSettingsView extends View {

        void setDestinationFeed(View view);

        void setTemplateEditor(View view);
    }
}
