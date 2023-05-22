/*
 * Copyright 2022 Crown Copyright
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

package stroom.analytics.client.presenter;

import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.ContentCallback;
import stroom.entity.client.presenter.DocumentEditTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

public class AnalyticRulePresenter extends DocumentEditTabPresenter<LinkTabPanelView, AnalyticRuleDoc> {

    private static final TabData QUERY_TAB = new TabDataImpl("Query");
    private static final TabData SETTINGS_TAB = new TabDataImpl("Settings");
    private static final TabData PROCESSING_TAB = new TabDataImpl("Processing");
    private final AnalyticQueryEditPresenter queryEditPresenter;
    private final AnalyticRuleSettingsPresenter settingsPresenter;
    private final AnalyticRuleProcessingPresenter processPresenter;

    @Inject
    public AnalyticRulePresenter(final EventBus eventBus,
                                 final LinkTabPanelView view,
                                 final AnalyticQueryEditPresenter queryEditPresenter,
                                 final AnalyticRuleSettingsPresenter settingsPresenter,
                                 final AnalyticRuleProcessingPresenter processPresenter) {
        super(eventBus, view);
        this.queryEditPresenter = queryEditPresenter;
        this.settingsPresenter = settingsPresenter;
        this.processPresenter = processPresenter;

        addTab(QUERY_TAB);
        addTab(PROCESSING_TAB);
        addTab(SETTINGS_TAB);
        selectTab(QUERY_TAB);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(queryEditPresenter.addDirtyHandler(event -> {
            if (event.isDirty()) {
                setDirty(true);
            }
        }));
        registerHandler(settingsPresenter.addDirtyHandler(event -> {
            if (event.isDirty()) {
                setDirty(true);
            }
        }));
        registerHandler(processPresenter.addDirtyHandler(event -> {
            if (event.isDirty()) {
                setDirty(true);
            }
        }));
    }

    @Override
    protected void getContent(final TabData tab, final ContentCallback callback) {
        if (QUERY_TAB.equals(tab)) {
            callback.onReady(queryEditPresenter);
        } else if (SETTINGS_TAB.equals(tab)) {
            callback.onReady(settingsPresenter);
        } else if (PROCESSING_TAB.equals(tab)) {
            callback.onReady(processPresenter);
        } else {
            callback.onReady(null);
        }
    }

    @Override
    public void onRead(final DocRef docRef, final AnalyticRuleDoc entity, final boolean readOnly) {
        super.onRead(docRef, entity, readOnly);
        queryEditPresenter.read(docRef, entity, readOnly);
        settingsPresenter.read(docRef, entity, readOnly);
        processPresenter.read(docRef, entity, readOnly);
    }

    @Override
    protected AnalyticRuleDoc onWrite(final AnalyticRuleDoc entity) {
        AnalyticRuleDoc modified = entity;
        modified = queryEditPresenter.write(modified);
        modified = settingsPresenter.write(modified);
        modified = processPresenter.write(modified);
        return modified;
    }

    @Override
    public String getType() {
        return AnalyticRuleDoc.DOCUMENT_TYPE;
    }
}
