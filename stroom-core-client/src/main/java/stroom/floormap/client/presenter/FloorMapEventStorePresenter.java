/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.floormap.client.presenter;

import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocTabPresenter;
import stroom.entity.client.presenter.DocTabProvider;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.MarkdownEditPresenter;
import stroom.entity.client.presenter.MarkdownTabProvider;
import stroom.floormap.shared.FloorMapEventStoreDoc;
import stroom.security.client.presenter.DocumentUserPermissionsTabProvider;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Provider;

/// The editor for a [FloorMapEventStoreDoc].
public class FloorMapEventStorePresenter
        extends DocTabPresenter<LinkTabPanelView, FloorMapEventStoreDoc> {

    public static final String TAB_TYPE = "FloorMapEventStore";

    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData DATA = new TabDataImpl("Data");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    @Inject
    public FloorMapEventStorePresenter(
            final EventBus eventBus,
            final LinkTabPanelView view,
            final Provider<FloorMapEventStoreSettingsPresenter> settingsPresenterProvider,
            final Provider<FloorMapEventStoreDataPresenter> dataPresenterProvider,
            final Provider<MarkdownEditPresenter> markdownEditPresenterProvider,
            final DocumentUserPermissionsTabProvider<FloorMapEventStoreDoc> permissionsTabProvider) {
        super(eventBus, view);

        addTab(SETTINGS, new DocTabProvider<>(settingsPresenterProvider::get));
        addTab(DATA, new DocTabProvider<>(dataPresenterProvider::get));
        addTab(DOCUMENTATION,
                new MarkdownTabProvider<>(eventBus, markdownEditPresenterProvider) {
                    @Override
                    public void onRead(final MarkdownEditPresenter presenter,
                                       final DocRef docRef,
                                       final FloorMapEventStoreDoc document,
                                       final boolean readOnly) {
                        presenter.setText(document.getDescription());
                        presenter.setReadOnly(readOnly);
                    }

                    @Override
                    public FloorMapEventStoreDoc onWrite(final MarkdownEditPresenter presenter,
                                                         final FloorMapEventStoreDoc document) {
                        return document.copyEventStore().description(presenter.getText()).build();
                    }
                });
        addTab(PERMISSIONS, permissionsTabProvider);
        selectTab(SETTINGS);
    }

    @Override
    public String getType() {
        return FloorMapEventStoreDoc.TYPE;
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
