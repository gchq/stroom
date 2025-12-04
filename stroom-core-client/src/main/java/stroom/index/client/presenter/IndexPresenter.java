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

package stroom.index.client.presenter;

import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocumentEditTabPresenter;
import stroom.entity.client.presenter.DocumentEditTabProvider;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.MarkdownEditPresenter;
import stroom.entity.client.presenter.MarkdownTabProvider;
import stroom.index.shared.LuceneIndexDoc;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.presenter.DocumentUserPermissionsTabProvider;
import stroom.security.shared.AppPermission;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Provider;

public class IndexPresenter extends DocumentEditTabPresenter<LinkTabPanelView, LuceneIndexDoc> {

    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData FIELDS = new TabDataImpl("Fields");
    private static final TabData SHARDS = new TabDataImpl("Shards");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    @Inject
    public IndexPresenter(final EventBus eventBus,
                          final LinkTabPanelView view,
                          final Provider<IndexSettingsPresenter> indexSettingsPresenterProvider,
                          final Provider<IndexFieldListPresenter> indexFieldListPresenterProvider,
                          final Provider<IndexShardPresenter> indexShardPresenterProvider,
                          final Provider<MarkdownEditPresenter> markdownEditPresenterProvider,
                          final DocumentUserPermissionsTabProvider<LuceneIndexDoc> documentUserPermissionsTabProvider,
                          final ClientSecurityContext securityContext) {
        super(eventBus, view);

        TabData selectFirst = FIELDS;

        if (securityContext.hasAppPermission(AppPermission.MANAGE_INDEX_SHARDS_PERMISSION)) {
            selectFirst = SHARDS;
            addTab(SHARDS, new DocumentEditTabProvider<LuceneIndexDoc>(indexShardPresenterProvider::get));
        }

        addTab(FIELDS, new DocumentEditTabProvider<LuceneIndexDoc>(indexFieldListPresenterProvider::get));
        addTab(SETTINGS, new DocumentEditTabProvider<LuceneIndexDoc>(indexSettingsPresenterProvider::get));
        addTab(DOCUMENTATION, new MarkdownTabProvider<LuceneIndexDoc>(eventBus, markdownEditPresenterProvider) {
            @Override
            public void onRead(final MarkdownEditPresenter presenter,
                               final DocRef docRef,
                               final LuceneIndexDoc document,
                               final boolean readOnly) {
                presenter.setText(document.getDescription());
                presenter.setReadOnly(readOnly);
            }

            @Override
            public LuceneIndexDoc onWrite(final MarkdownEditPresenter presenter,
                                          final LuceneIndexDoc document) {
                document.setDescription(presenter.getText());
                return document;
            }
        });
        addTab(PERMISSIONS, documentUserPermissionsTabProvider);
        selectTab(selectFirst);
    }

    @Override
    public String getType() {
        return LuceneIndexDoc.TYPE;
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
