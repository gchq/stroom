/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.visualisation.client.presenter;

import stroom.dashboard.client.vis.ClearFunctionCacheEvent;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.ContentCallback;
import stroom.entity.client.presenter.DocumentEditTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.MarkdownEditPresenter;
import stroom.visualisation.shared.VisualisationDoc;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

public class VisualisationPresenter extends DocumentEditTabPresenter<LinkTabPanelView, VisualisationDoc> {

    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");

    private final VisualisationSettingsPresenter settingsPresenter;
    private final MarkdownEditPresenter markdownEditPresenter;

    private int loadCount;

    @Inject
    public VisualisationPresenter(final EventBus eventBus,
                                  final LinkTabPanelView view,
                                  final VisualisationSettingsPresenter settingsPresenter,
                                  final MarkdownEditPresenter markdownEditPresenter) {
        super(eventBus, view);
        this.settingsPresenter = settingsPresenter;
        this.markdownEditPresenter = markdownEditPresenter;

        addTab(SETTINGS);
        addTab(DOCUMENTATION);
        selectTab(SETTINGS);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(settingsPresenter.addDirtyHandler(event -> {
            if (event.isDirty()) {
                setDirty(true);
            }
        }));
        registerHandler(markdownEditPresenter.addDirtyHandler(event -> {
            if (event.isDirty()) {
                setDirty(true);
            }
        }));
    }

    @Override
    protected void getContent(final TabData tab, final ContentCallback callback) {
        if (SETTINGS.equals(tab)) {
            callback.onReady(settingsPresenter);
        } else if (DOCUMENTATION.equals(tab)) {
            callback.onReady(markdownEditPresenter);
        } else {
            callback.onReady(null);
        }
    }

    @Override
    public void onRead(final DocRef docRef, final VisualisationDoc doc, final boolean readOnly) {
        super.onRead(docRef, doc, readOnly);
        loadCount++;
        settingsPresenter.read(docRef, doc, readOnly);
        markdownEditPresenter.setText(doc.getDescription());
        markdownEditPresenter.setReadOnly(readOnly);

        if (loadCount > 1) {
            // Remove the visualisation function from the cache so dashboards
            // reload it.
            ClearFunctionCacheEvent.fire(this, docRef);
        }
    }

    @Override
    protected VisualisationDoc onWrite(VisualisationDoc doc) {
        doc = settingsPresenter.write(doc);
        doc.setDescription(markdownEditPresenter.getText());
        return doc;
    }

    @Override
    public String getType() {
        return VisualisationDoc.DOCUMENT_TYPE;
    }
}
