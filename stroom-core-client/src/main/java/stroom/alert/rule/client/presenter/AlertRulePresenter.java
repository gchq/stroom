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

package stroom.alert.rule.client.presenter;

import stroom.alert.rule.shared.AlertRuleDoc;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.ContentCallback;
import stroom.entity.client.presenter.DocumentEditTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.security.client.api.ClientSecurityContext;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

public class AlertRulePresenter extends DocumentEditTabPresenter<LinkTabPanelView, AlertRuleDoc> {

    private static final TabData SETTINGS_TAB = new TabDataImpl("Settings");
    private final AlertRuleSettingsPresenter settingsPresenter;

    @Inject
    public AlertRulePresenter(final EventBus eventBus,
                              final LinkTabPanelView view,
                              final AlertRuleSettingsPresenter settingsPresenter,
                              final ClientSecurityContext securityContext) {
        super(eventBus, view, securityContext);
        this.settingsPresenter = settingsPresenter;

        settingsPresenter.addDirtyHandler(event -> {
            if (event.isDirty()) {
                setDirty(true);
            }
        });

        addTab(SETTINGS_TAB);
        selectTab(SETTINGS_TAB);
    }

    @Override
    protected void getContent(final TabData tab, final ContentCallback callback) {
        if (SETTINGS_TAB.equals(tab)) {
            callback.onReady(settingsPresenter);
        } else {
            callback.onReady(null);
        }
    }

    @Override
    public void onRead(final DocRef docRef, final AlertRuleDoc entity) {
        super.onRead(docRef, entity);
        settingsPresenter.read(docRef, entity);
    }

    @Override
    protected void onWrite(final AlertRuleDoc entity) {
        settingsPresenter.write(entity);
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        super.onReadOnly(readOnly);
        settingsPresenter.onReadOnly(readOnly);
    }

    @Override
    public String getType() {
        return AlertRuleDoc.DOCUMENT_TYPE;
    }
}
