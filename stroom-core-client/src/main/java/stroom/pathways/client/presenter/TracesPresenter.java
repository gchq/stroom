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

package stroom.pathways.client.presenter;

import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocTabPresenter;
import stroom.entity.client.presenter.DocTabProvider;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.MarkdownEditPresenter;
import stroom.entity.client.presenter.MarkdownTabProvider;
import stroom.pathways.shared.TracesDoc;
import stroom.pathways.shared.pathway.Pathway;
import stroom.security.client.presenter.DocumentUserPermissionsTabProvider;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Provider;

public class TracesPresenter extends DocTabPresenter<LinkTabPanelView, TracesDoc> {

    public static final String TAB_TYPE = "Traces";

    private static final TabData TRACES = new TabDataImpl("Traces");
    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    private final TracesListTabPresenter tracesListTabPresenter;

    @Inject
    public TracesPresenter(final EventBus eventBus,
                           final LinkTabPanelView view,
                           final TracesListTabPresenter tracesListTabPresenter,
                           final Provider<TracesSettingsPresenter> tracesSettingsPresenterProvider,
                           final Provider<MarkdownEditPresenter> markdownEditPresenterProvider,
                           final DocumentUserPermissionsTabProvider<TracesDoc> documentUserPermissionsTabProvider) {
        super(eventBus, view);
        this.tracesListTabPresenter = tracesListTabPresenter;

        addTab(TRACES, new DocTabProvider<>(() -> tracesListTabPresenter));
        addTab(SETTINGS, new DocTabProvider<>(tracesSettingsPresenterProvider::get));
        addTab(DOCUMENTATION, new MarkdownTabProvider<TracesDoc>(eventBus, markdownEditPresenterProvider) {
            @Override
            public void onRead(final MarkdownEditPresenter presenter,
                               final DocRef docRef,
                               final TracesDoc document,
                               final boolean readOnly) {
                presenter.setText(document.getDescription());
                presenter.setReadOnly(readOnly);
            }

            @Override
            public TracesDoc onWrite(final MarkdownEditPresenter presenter,
                                     final TracesDoc document) {
                return document.copyTraces().description(presenter.getText()).build();
            }
        });
        addTab(PERMISSIONS, documentUserPermissionsTabProvider);
        selectTab(TRACES);
    }

    @Override
    public String getType() {
        return TracesDoc.TYPE;
    }

    @Override
    protected TabData getPermissionsTab() {
        return PERMISSIONS;
    }

    @Override
    protected TabData getDocumentationTab() {
        return DOCUMENTATION;
    }

    @Override
    public void refresh() {
        // Do nothing to prevent auto-refreshing from the global tab refresh event.
    }

    public void forceRefresh() {
        tracesListTabPresenter.refresh();
    }

    public void setDataSourceRef(final DocRef dataSourceRef) {
        tracesListTabPresenter.setDataSourceRef(dataSourceRef);
    }

    public void setFilter(final String filter) {
        tracesListTabPresenter.setFilter(filter);
    }

    public void setPathway(final Pathway pathway) {
        tracesListTabPresenter.setPathway(pathway);
    }
}
