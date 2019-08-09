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

package stroom.receive.rules.client.presenter;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import stroom.docref.DocRef;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.entity.client.presenter.ContentCallback;
import stroom.entity.client.presenter.DocumentEditTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.TabContentProvider;
import stroom.receive.rules.shared.ReceiveDataRules;
import stroom.security.client.api.ClientSecurityContext;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import java.util.ArrayList;

public class RuleSetPresenter extends DocumentEditTabPresenter<LinkTabPanelView, ReceiveDataRules> implements HasDirtyHandlers {
    private static final TabData RULES = new TabDataImpl("Rules");
    private static final TabData FIELDS = new TabDataImpl("Fields");

    private final TabContentProvider<ReceiveDataRules> tabContentProvider = new TabContentProvider<>();

    @Inject
    public RuleSetPresenter(final EventBus eventBus,
                            final LinkTabPanelView view,
                            final ClientSecurityContext securityContext,
                            final Provider<RuleSetSettingsPresenter> settingsPresenterProvider,
                            final Provider<FieldListPresenter> fieldListPresenterProvider) {
        super(eventBus, view, securityContext);

        tabContentProvider.setDirtyHandler(event -> {
            if (event.isDirty()) {
                setDirty(true);
            }
        });

        addTab(RULES);
        tabContentProvider.add(RULES, settingsPresenterProvider);

        addTab(FIELDS);
        tabContentProvider.add(FIELDS, fieldListPresenterProvider);

        selectTab(RULES);
    }

    @Override
    protected void getContent(final TabData tab, final ContentCallback callback) {
        callback.onReady(tabContentProvider.getPresenter(tab));
    }

    @Override
    public void onRead(final DocRef docRef, final ReceiveDataRules dataReceiptPolicy) {
        super.onRead(docRef, dataReceiptPolicy);
        if (dataReceiptPolicy.getFields() == null) {
            dataReceiptPolicy.setFields(new ArrayList<>());
        }
        if (dataReceiptPolicy.getRules() == null) {
            dataReceiptPolicy.setRules(new ArrayList<>());
        }

        tabContentProvider.read(docRef, dataReceiptPolicy);
    }

    @Override
    protected void onWrite(final ReceiveDataRules dataReceiptPolicy) {
        tabContentProvider.write(dataReceiptPolicy);
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        super.onReadOnly(readOnly);
        tabContentProvider.onReadOnly(readOnly);
    }

    @Override
    public String getType() {
        return ReceiveDataRules.DOCUMENT_TYPE;
    }
}