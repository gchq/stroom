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

package stroom.search.solr.client.presenter;

import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocumentEditTabPresenter;
import stroom.entity.client.presenter.DocumentEditTabProvider;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.MarkdownEditPresenter;
import stroom.entity.client.presenter.MarkdownTabProvider;
import stroom.search.solr.shared.SolrIndexDoc;
import stroom.security.client.presenter.DocumentUserPermissionsTabProvider;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Provider;

public class SolrIndexPresenter extends DocumentEditTabPresenter<LinkTabPanelView, SolrIndexDoc> {

    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData FIELDS = new TabDataImpl("Fields");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    @Inject
    public SolrIndexPresenter(final EventBus eventBus,
                              final LinkTabPanelView view,
                              final Provider<SolrIndexSettingsPresenter> indexSettingsPresenterProvider,
                              final Provider<SolrIndexFieldListPresenter> indexFieldListPresenterProvider,
                              final Provider<MarkdownEditPresenter> markdownEditPresenterProvider,
                              final DocumentUserPermissionsTabProvider<SolrIndexDoc>
                                      documentUserPermissionsTabProvider) {
        super(eventBus, view);

        addTab(FIELDS, new DocumentEditTabProvider<>(indexFieldListPresenterProvider::get));
        addTab(SETTINGS, new DocumentEditTabProvider<>(indexSettingsPresenterProvider::get));
        addTab(DOCUMENTATION, new MarkdownTabProvider<SolrIndexDoc>(eventBus, markdownEditPresenterProvider) {
            @Override
            public void onRead(final MarkdownEditPresenter presenter,
                               final DocRef docRef,
                               final SolrIndexDoc document,
                               final boolean readOnly) {
                presenter.setText(document.getDescription());
                presenter.setReadOnly(readOnly);
            }

            @Override
            public SolrIndexDoc onWrite(final MarkdownEditPresenter presenter,
                                        final SolrIndexDoc document) {
                document.setDescription(presenter.getText());
                return document;
            }
        });
        addTab(PERMISSIONS, documentUserPermissionsTabProvider);
        selectTab(FIELDS);
    }

    @Override
    public String getType() {
        return SolrIndexDoc.TYPE;
    }

    @Override
    protected TabData getPermissionsTab() {
        return PERMISSIONS;
    }

    @Override
    protected TabData getDocumentationTab() {
        return DOCUMENTATION;
    }
}
