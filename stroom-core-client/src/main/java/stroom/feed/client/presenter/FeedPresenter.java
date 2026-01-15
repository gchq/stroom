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

package stroom.feed.client.presenter;

import stroom.data.client.presenter.MetaPresenter;
import stroom.data.client.presenter.ProcessorTaskPresenter;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.AbstractTabProvider;
import stroom.entity.client.presenter.DocumentEditTabPresenter;
import stroom.entity.client.presenter.DocumentEditTabProvider;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.MarkdownEditPresenter;
import stroom.entity.client.presenter.MarkdownTabProvider;
import stroom.feed.shared.FeedDoc;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.presenter.DocumentUserPermissionsTabProvider;
import stroom.security.shared.AppPermission;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Provider;

public class FeedPresenter extends DocumentEditTabPresenter<LinkTabPanelView, FeedDoc> {

    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData DATA = new TabDataImpl("Data");
    private static final TabData TASKS = new TabDataImpl("Active Tasks");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    private MetaPresenter metaPresenter;

    @Inject
    public FeedPresenter(final EventBus eventBus,
                         final LinkTabPanelView view,
                         final ClientSecurityContext securityContext,
                         final Provider<FeedSettingsPresenter> settingsPresenterProvider,
                         final Provider<MetaPresenter> metaPresenterProvider,
                         final Provider<ProcessorTaskPresenter> taskPresenterProvider,
                         final Provider<MarkdownEditPresenter> markdownEditPresenterProvider,
                         final DocumentUserPermissionsTabProvider<FeedDoc> documentUserPermissionsTabProvider) {
        super(eventBus, view);

        TabData selectedTab = SETTINGS;

        if (securityContext.hasAppPermission(AppPermission.VIEW_DATA_PERMISSION)) {
            addTab(DATA, new AbstractTabProvider<FeedDoc, MetaPresenter>(eventBus) {
                @Override
                protected MetaPresenter createPresenter() {
                    metaPresenter = metaPresenterProvider.get();
                    return metaPresenter;
                }

                @Override
                public void onRead(final MetaPresenter presenter,
                                   final DocRef docRef,
                                   final FeedDoc document,
                                   final boolean readOnly) {
                    presenter.read(docRef, document, readOnly);
                }
            });
            selectedTab = DATA;
        }

        if (securityContext.hasAppPermission(AppPermission.MANAGE_PROCESSORS_PERMISSION)) {
            addTab(TASKS, new AbstractTabProvider<FeedDoc, ProcessorTaskPresenter>(eventBus) {
                @Override
                protected ProcessorTaskPresenter createPresenter() {
                    return taskPresenterProvider.get();
                }

                @Override
                public void onRead(final ProcessorTaskPresenter presenter,
                                   final DocRef docRef,
                                   final FeedDoc document,
                                   final boolean readOnly) {
                    presenter.read(docRef, document, readOnly);
                }
            });
        }

        addTab(SETTINGS, new DocumentEditTabProvider<>(settingsPresenterProvider::get));
        addTab(DOCUMENTATION, new MarkdownTabProvider<FeedDoc>(eventBus, markdownEditPresenterProvider) {
            @Override
            public void onRead(final MarkdownEditPresenter presenter,
                               final DocRef docRef,
                               final FeedDoc document,
                               final boolean readOnly) {
                presenter.setText(document.getDescription());
                presenter.setReadOnly(readOnly);
            }

            @Override
            public FeedDoc onWrite(final MarkdownEditPresenter presenter,
                                   final FeedDoc document) {
                document.setDescription(presenter.getText());
                return document;
            }
        });
        addTab(PERMISSIONS, documentUserPermissionsTabProvider);

        selectTab(selectedTab);
    }

    @Override
    protected FeedDoc onWrite(final FeedDoc doc) {
        final FeedDoc modified = super.onWrite(doc);

        // Something has changed, e.g. the encoding so refresh the meta presenter to reflect it
        if (metaPresenter != null) {
            metaPresenter.refreshData();
        }

        return modified;
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

    //    @Override
//    public boolean handleKeyAction(final Action action) {
//        if (Action.DOCUMENTATION == action) {
//            selectTab(DOCUMENTATION);
//            return true;
//        } else if (Action.SETTINGS == action) {
//            selectTab(SETTINGS);
//            return true;
//        }
//        return false;
//    }
}
