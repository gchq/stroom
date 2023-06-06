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

package stroom.statistics.impl.hbase.client.presenter;

import stroom.docref.DocRef;
import stroom.entity.client.presenter.ContentCallback;
import stroom.entity.client.presenter.DocumentEditTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.MarkdownEditPresenter;
import stroom.statistics.impl.hbase.shared.StroomStatsStoreDoc;
import stroom.statistics.impl.hbase.shared.StroomStatsStoreEntityData;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

public class StroomStatsStorePresenter extends DocumentEditTabPresenter<LinkTabPanelView, StroomStatsStoreDoc> {

    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData FIELDS = new TabDataImpl("Fields");
    private static final TabData CUSTOM_ROLLUPS = new TabDataImpl("Custom Roll-ups");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");

    private final StroomStatsStoreSettingsPresenter stroomStatsStoreSettingsPresenter;
    private final StroomStatsStoreFieldListPresenter stroomStatsStoreFieldListPresenter;
    private final StroomStatsStoreCustomMaskListPresenter stroomStatsStoreCustomMaskListPresenter;
    private final MarkdownEditPresenter markdownEditPresenter;

    @Inject
    public StroomStatsStorePresenter(
            final EventBus eventBus,
            final LinkTabPanelView view,
            final StroomStatsStoreSettingsPresenter stroomStatsStoreSettingsPresenter,
            final StroomStatsStoreFieldListPresenter stroomStatsStoreFieldListPresenter,
            final StroomStatsStoreCustomMaskListPresenter stroomStatsStoreCustomMaskListPresenter,
            final MarkdownEditPresenter markdownEditPresenter) {
        super(eventBus, view);
        this.stroomStatsStoreSettingsPresenter = stroomStatsStoreSettingsPresenter;
        this.stroomStatsStoreFieldListPresenter = stroomStatsStoreFieldListPresenter;
        this.stroomStatsStoreCustomMaskListPresenter = stroomStatsStoreCustomMaskListPresenter;
        this.markdownEditPresenter = markdownEditPresenter;

        addTab(SETTINGS);
        addTab(FIELDS);
        addTab(CUSTOM_ROLLUPS);
        addTab(DOCUMENTATION);

        selectTab(SETTINGS);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(stroomStatsStoreSettingsPresenter.addDirtyHandler(event -> {
            if (event.isDirty()) {
                setDirty(true);
            }
        }));
        registerHandler(stroomStatsStoreFieldListPresenter.addDirtyHandler(event -> {
            if (event.isDirty()) {
                setDirty(true);
            }
        }));
        registerHandler(stroomStatsStoreCustomMaskListPresenter.addDirtyHandler(event -> {
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
            callback.onReady(stroomStatsStoreSettingsPresenter);
        } else if (FIELDS.equals(tab)) {
            callback.onReady(stroomStatsStoreFieldListPresenter);
        } else if (CUSTOM_ROLLUPS.equals(tab)) {
            callback.onReady(stroomStatsStoreCustomMaskListPresenter);
        } else if (DOCUMENTATION.equals(tab)) {
            callback.onReady(markdownEditPresenter);
        } else {
            callback.onReady(null);
        }
    }

    @Override
    public void onRead(final DocRef docRef, final StroomStatsStoreDoc doc, final boolean readOnly) {
        super.onRead(docRef, doc, readOnly);
        if (doc.getConfig() == null) {
            doc.setConfig(new StroomStatsStoreEntityData());
        }

        stroomStatsStoreSettingsPresenter.read(docRef, doc, readOnly);
        stroomStatsStoreFieldListPresenter.read(docRef, doc, readOnly);
        stroomStatsStoreCustomMaskListPresenter.read(docRef, doc, readOnly);
        markdownEditPresenter.setText(doc.getDescription());
        markdownEditPresenter.setReadOnly(readOnly);

        // the field and rollup presenters need to know about each other as
        // changes in one affect the other
        stroomStatsStoreFieldListPresenter.setCustomMaskListPresenter(stroomStatsStoreCustomMaskListPresenter);
    }

    @Override
    protected StroomStatsStoreDoc onWrite(StroomStatsStoreDoc doc) {
        doc = stroomStatsStoreSettingsPresenter.write(doc);
        doc = stroomStatsStoreFieldListPresenter.write(doc);
        doc = stroomStatsStoreCustomMaskListPresenter.write(doc);
        doc.setDescription(markdownEditPresenter.getText());
        return doc;
    }

    @Override
    public String getType() {
        return StroomStatsStoreDoc.DOCUMENT_TYPE;
    }
}
