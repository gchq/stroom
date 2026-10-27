/*
 * Copyright 2026 Crown Copyright
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
import stroom.entity.client.component.MarkdownEditController;
import stroom.entity.client.presenter.ControllerTabProvider;
import stroom.entity.client.presenter.DocTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.MarkdownTabProvider;
import stroom.security.client.presenter.DocumentUserPermissionsTabProvider;
import stroom.sqlstore.client.component.SqlTemporalStoreDataController;
import stroom.sqlstore.client.component.SqlTemporalStoreSettingsController;
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
            final Provider<SqlTemporalStoreDataController> sqlTemporalStoreDataPresenterProvider,
            final Provider<SqlTemporalStoreSettingsController> sqlStoreSettingsProvider,
            final Provider<MarkdownEditController> markdownEditProvider,
            final DocumentUserPermissionsTabProvider<SqlTemporalStoreDoc> documentUserPermissionsTabProvider) {
        super(eventBus, view);

        addTab(SETTINGS, new ControllerTabProvider<>(eventBus, sqlStoreSettingsProvider::get,
                SqlTemporalStoreSettingsController::read));
        addTab(DATA, new ControllerTabProvider<>(eventBus, sqlTemporalStoreDataPresenterProvider::get,
                SqlTemporalStoreDataController::read));
        addTab(DOCUMENTATION, new MarkdownTabProvider<>(getEventBus(), markdownEditProvider::get) {
            @Override
            public void onRead(final MarkdownEditController markdownEdit,
                               final DocRef docRef,
                               final SqlTemporalStoreDoc document) {
                markdownEdit.setText(document.getDescription());
                markdownEdit.setReadOnly(isReadOnly());
            }

            @Override
            public SqlTemporalStoreDoc onWrite(final MarkdownEditController markdownEdit,
                                    final SqlTemporalStoreDoc document) {
                return document.copy().description(markdownEdit.getText()).build();
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
