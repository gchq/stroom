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

package stroom.sqlstore.client.presenter;

import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocTabPresenter;
import stroom.entity.client.presenter.DocTabProvider;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.MarkdownEditPresenter;
import stroom.entity.client.presenter.MarkdownTabProvider;
import stroom.security.client.presenter.DocumentUserPermissionsTabProvider;
import stroom.sqlstore.shared.SqlTemporalStoreDoc;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Provider;

public class SqlTemporalStorePresenter extends DocTabPresenter<LinkTabPanelView, SqlTemporalStoreDoc> {

    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData DATA = new TabDataImpl("Data");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    @Inject
    public SqlTemporalStorePresenter(
            final EventBus eventBus,
            final LinkTabPanelView view,
            final Provider<SqlTemporalStoreDataPresenter> sqlTemporalStoreDataPresenterProvider,
            final Provider<SqlTemporalStoreSettingsPresenter> sqlStoreSettingsPresenterProvider,
            final Provider<MarkdownEditPresenter> markdownEditPresenterProvider,
            final DocumentUserPermissionsTabProvider<SqlTemporalStoreDoc> documentUserPermissionsTabProvider) {
        super(eventBus, view);

        addTab(SETTINGS, new DocTabProvider<>(sqlStoreSettingsPresenterProvider::get));
        addTab(DATA, new DocTabProvider<>(sqlTemporalStoreDataPresenterProvider::get));
        addTab(DOCUMENTATION, new MarkdownTabProvider<>(eventBus, markdownEditPresenterProvider) {
            @Override
            public void onRead(final MarkdownEditPresenter presenter,
                               final DocRef docRef,
                               final SqlTemporalStoreDoc document,
                               final boolean readOnly) {
                presenter.setText(document.getDescription());
                presenter.setReadOnly(readOnly);
            }

            @Override
            public SqlTemporalStoreDoc onWrite(final MarkdownEditPresenter presenter,
                                    final SqlTemporalStoreDoc document) {
                return document.copy().description(presenter.getText()).build();
            }
        });
        addTab(PERMISSIONS, documentUserPermissionsTabProvider);
        selectTab(SETTINGS);
    }

    @Override
    public String getType() {
        return SqlTemporalStoreDoc.TYPE;
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
