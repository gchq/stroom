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

package stroom.dataconnection.client.presenter;

import stroom.dataconnection.shared.DataConnectionDoc;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocTabPresenter;
import stroom.entity.client.presenter.DocTabProvider;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.MarkdownEditPresenter;
import stroom.entity.client.presenter.MarkdownTabProvider;
import stroom.security.client.presenter.DocumentUserPermissionsTabProvider;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Provider;

/// The editor of a Data Connection, with tabs for its settings, documentation and permissions.
public class DataConnectionPresenter extends DocTabPresenter<LinkTabPanelView, DataConnectionDoc> {

    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    /// @param eventBus                           The event bus.
    /// @param view                               The tab panel.
    /// @param settingsPresenterProvider          Makes the settings tab.
    /// @param markdownEditPresenterProvider      Makes the documentation tab.
    /// @param documentUserPermissionsTabProvider Makes the permissions tab.
    @Inject
    public DataConnectionPresenter(
            final EventBus eventBus,
            final LinkTabPanelView view,
            final Provider<DataConnectionSettingsPresenter> settingsPresenterProvider,
            final Provider<MarkdownEditPresenter> markdownEditPresenterProvider,
            final DocumentUserPermissionsTabProvider<DataConnectionDoc> documentUserPermissionsTabProvider) {
        super(eventBus, view);

        addTab(SETTINGS, new DocTabProvider<>(settingsPresenterProvider::get));
        addTab(DOCUMENTATION, new MarkdownTabProvider<DataConnectionDoc>(eventBus, markdownEditPresenterProvider) {
            @Override
            public void onRead(final MarkdownEditPresenter presenter,
                               final DocRef docRef,
                               final DataConnectionDoc document,
                               final boolean readOnly) {
                presenter.setText(document.getDescription());
                presenter.setReadOnly(readOnly);
            }

            @Override
            public DataConnectionDoc onWrite(final MarkdownEditPresenter presenter,
                                                 final DataConnectionDoc document) {
                return document.copy().description(presenter.getText()).build();
            }
        });
        addTab(PERMISSIONS, documentUserPermissionsTabProvider);
        selectTab(SETTINGS);
    }

    @Override
    public String getType() {
        return DataConnectionDoc.TYPE;
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
