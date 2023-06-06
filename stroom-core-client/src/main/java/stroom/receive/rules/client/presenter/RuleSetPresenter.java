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

import stroom.docref.DocRef;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.entity.client.presenter.ContentCallback;
import stroom.entity.client.presenter.DocumentEditTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.MarkdownEditPresenter;
import stroom.receive.rules.shared.ReceiveDataRules;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;

public class RuleSetPresenter extends DocumentEditTabPresenter<LinkTabPanelView, ReceiveDataRules>
        implements HasDirtyHandlers {

    private static final TabData RULES = new TabDataImpl("Rules");
    private static final TabData FIELDS = new TabDataImpl("Fields");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");

    private final RuleSetSettingsPresenter settingsPresenter;
    private final FieldListPresenter fieldListPresenter;
    private final MarkdownEditPresenter markdownEditPresenter;

    @Inject
    public RuleSetPresenter(final EventBus eventBus,
                            final LinkTabPanelView view,
                            final RuleSetSettingsPresenter settingsPresenter,
                            final FieldListPresenter fieldListPresenter,
                            final MarkdownEditPresenter markdownEditPresenter) {
        super(eventBus, view);
        this.settingsPresenter = settingsPresenter;
        this.fieldListPresenter = fieldListPresenter;
        this.markdownEditPresenter = markdownEditPresenter;

        addTab(RULES);
        addTab(FIELDS);
        addTab(DOCUMENTATION);
        selectTab(RULES);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(settingsPresenter.addDirtyHandler(event -> {
            if (event.isDirty()) {
                setDirty(true);
            }
        }));
        registerHandler(fieldListPresenter.addDirtyHandler(event -> {
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
        if (RULES.equals(tab)) {
            callback.onReady(settingsPresenter);
        } else if (FIELDS.equals(tab)) {
            callback.onReady(fieldListPresenter);
        } else if (DOCUMENTATION.equals(tab)) {
            callback.onReady(markdownEditPresenter);
        } else {
            callback.onReady(null);
        }
    }

    @Override
    public void onRead(final DocRef docRef, final ReceiveDataRules doc, final boolean readOnly) {
        super.onRead(docRef, doc, readOnly);
        if (doc.getFields() == null) {
            doc.setFields(new ArrayList<>());
        }
        if (doc.getRules() == null) {
            doc.setRules(new ArrayList<>());
        }

        settingsPresenter.read(docRef, doc, readOnly);
        fieldListPresenter.read(docRef, doc, readOnly);
        markdownEditPresenter.setText(doc.getDescription());
        markdownEditPresenter.setReadOnly(readOnly);
    }

    @Override
    protected ReceiveDataRules onWrite(ReceiveDataRules dataReceiptPolicy) {
        dataReceiptPolicy = settingsPresenter.write(dataReceiptPolicy);
        dataReceiptPolicy = fieldListPresenter.write(dataReceiptPolicy);
        dataReceiptPolicy.setDescription(markdownEditPresenter.getText());
        return dataReceiptPolicy;
    }

    @Override
    public String getType() {
        return ReceiveDataRules.DOCUMENT_TYPE;
    }
}
