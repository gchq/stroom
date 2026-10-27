/*
 * Copyright 2016 Crown Copyright
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

package stroom.feed.client.presenter;

import stroom.data.client.component.MetaBrowserController;
import stroom.data.client.presenter.ProcessorTaskPresenter;
import stroom.docref.DocRef;
import stroom.entity.client.component.MarkdownEditController;
import stroom.entity.client.presenter.AbstractTabProvider;
import stroom.entity.client.presenter.ControllerTabProvider;
import stroom.entity.client.presenter.DocTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.MarkdownTabProvider;
import stroom.feed.client.component.FeedSettingsController;
import stroom.feed.shared.FeedDoc;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.presenter.DocumentUserPermissionsTabProvider;
import stroom.security.shared.AppPermission;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Provider;

public class FeedPresenter extends DocTabPresenter<LinkTabPanelView, FeedDoc> {

    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData DATA = new TabDataImpl("Data");
    private static final TabData TASKS = new TabDataImpl("Active Tasks");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    @Inject
    public FeedPresenter(final EventBus eventBus,
                         final LinkTabPanelView view,
                         final ClientSecurityContext securityContext,
                         final Provider<FeedSettingsController> settingsProvider,
                         final Provider<MetaBrowserController> metaPresenterProvider,
                         final Provider<ProcessorTaskPresenter> taskPresenterProvider,
                         final Provider<MarkdownEditController> markdownEditProvider,
                         final DocumentUserPermissionsTabProvider<FeedDoc> documentUserPermissionsTabProvider) {
        super(eventBus, view);

        TabData selectedTab = SETTINGS;

        if (securityContext.hasAppPermission(AppPermission.VIEW_DATA_PERMISSION)) {
            addTab(DATA, new ControllerTabProvider<>(eventBus, metaPresenterProvider::get,
                    (presenter, docRef, document) -> {
                        presenter.read(docRef, document);
                        // Refresh any displayed data so it reflects the (now-saved) feed settings, e.g. a
                        // changed encoding. onRead runs on initial load and after each save (with the saved
                        // doc, so the server has the new encoding); refreshData() is a no-op unless a stream
                        // is currently displayed. This was previously in onWrite, which ran on every dirty
                        // check - many times per edit, for all settings fields, and before the change was
                        // persisted (so the re-fetch used the pre-save encoding).
                        presenter.refreshData();
                    }));
            selectedTab = DATA;
        }

        if (securityContext.hasAppPermission(AppPermission.MANAGE_PROCESSORS_PERMISSION)) {
            addTab(TASKS, new AbstractTabProvider<FeedDoc, ProcessorTaskPresenter>() {
                @Override
                protected ProcessorTaskPresenter createPresenter() {
                    return taskPresenterProvider.get();
                }

                @Override
                public void onRead(final ProcessorTaskPresenter presenter,
                                   final DocRef docRef,
                                   final FeedDoc document) {
                    presenter.read(docRef, document);
                }
            });
        }

        addTab(SETTINGS, new ControllerTabProvider<>(eventBus, settingsProvider::get,
                FeedSettingsController::read,
                FeedSettingsController::write));
        addTab(DOCUMENTATION, new MarkdownTabProvider<FeedDoc>(getEventBus(), markdownEditProvider::get) {
            @Override
            public void onRead(final MarkdownEditController markdownEdit,
                               final DocRef docRef,
                               final FeedDoc document) {
                markdownEdit.setText(document.getDescription());
                markdownEdit.setReadOnly(isReadOnly());
            }

            @Override
            public FeedDoc onWrite(final MarkdownEditController markdownEdit,
                                   final FeedDoc document) {
                return document.copy().description(markdownEdit.getText()).build();
            }
        });
        addTab(PERMISSIONS, documentUserPermissionsTabProvider);

        selectTab(selectedTab);
    }

    @Override
    public String getType() {
        return FeedDoc.TYPE;
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
