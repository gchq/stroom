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
import stroom.entity.client.presenter.DocumentEditTabPresenter;
import stroom.entity.client.presenter.DocumentEditTabProvider;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.MarkdownEditPresenter;
import stroom.entity.client.presenter.MarkdownTabProvider;
import stroom.receive.rules.shared.ReceiveDataRules;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import javax.inject.Provider;

public class RuleSetPresenter extends DocumentEditTabPresenter<LinkTabPanelView, ReceiveDataRules>
        implements HasDirtyHandlers {

    private static final TabData RULES = new TabDataImpl("Rules");
    private static final TabData FIELDS = new TabDataImpl("Fields");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");

    @Inject
    public RuleSetPresenter(final EventBus eventBus,
                            final LinkTabPanelView view,
                            final Provider<RuleSetSettingsPresenter> settingsPresenterProvider,
                            final Provider<FieldListPresenter> fieldListPresenterProvider,
                            final Provider<MarkdownEditPresenter> markdownEditPresenterProvider) {
        super(eventBus, view);

        addTab(RULES, new DocumentEditTabProvider<>(settingsPresenterProvider::get));
        addTab(FIELDS, new DocumentEditTabProvider<>(fieldListPresenterProvider::get));
        addTab(DOCUMENTATION, new MarkdownTabProvider<ReceiveDataRules>(eventBus, markdownEditPresenterProvider) {
            @Override
            public void onRead(final MarkdownEditPresenter presenter,
                               final DocRef docRef,
                               final ReceiveDataRules document,
                               final boolean readOnly) {
                presenter.setText(document.getDescription());
                presenter.setReadOnly(readOnly);
            }

            @Override
            public ReceiveDataRules onWrite(final MarkdownEditPresenter presenter,
                                            final ReceiveDataRules document) {
                document.setDescription(presenter.getText());
                return document;
            }
        });
        selectTab(RULES);
    }

    @Override
    public void onRead(final DocRef docRef, final ReceiveDataRules doc, final boolean readOnly) {
        if (doc.getFields() == null) {
            doc.setFields(new ArrayList<>());
        }
        if (doc.getRules() == null) {
            doc.setRules(new ArrayList<>());
        }
        super.onRead(docRef, doc, readOnly);
    }

    @Override
    public String getType() {
        return ReceiveDataRules.DOCUMENT_TYPE;
    }
}
