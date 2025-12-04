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

package stroom.folder.client;

import stroom.data.client.presenter.MetaPresenter;
import stroom.data.client.presenter.ProcessorTaskPresenter;
import stroom.docref.DocRef;
import stroom.document.client.DocumentTabData;
import stroom.entity.client.presenter.AbstractTabProvider;
import stroom.entity.client.presenter.ContentCallback;
import stroom.entity.client.presenter.LinkTabPanelPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.TabContentProvider;
import stroom.explorer.shared.ExplorerConstants;
import stroom.processor.client.presenter.ProcessorPresenter;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.presenter.DocumentUserPermissionsTabProvider;
import stroom.security.shared.AppPermission;
import stroom.svg.shared.SvgImage;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

public class FolderPresenter
        extends LinkTabPanelPresenter
        implements DocumentTabData {

    private static final TabData DATA = new TabDataImpl("Data");
    private static final TabData TASKS = new TabDataImpl("Active Tasks");
    private static final TabData PROCESSORS = new TabDataImpl("Processors");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    private final TabContentProvider<Object> tabContentProvider;
    private DocRef docRef;

    @Inject
    public FolderPresenter(final EventBus eventBus,
                           final ClientSecurityContext securityContext,
                           final LinkTabPanelView view,
                           final Provider<MetaPresenter> metaPresenterProvider,
                           final Provider<ProcessorPresenter> processorPresenterProvider,
                           final Provider<ProcessorTaskPresenter> processorTaskPresenterProvider,
                           final DocumentUserPermissionsTabProvider<Object> documentUserPermissionsTabProvider) {
        super(eventBus, view);
        this.tabContentProvider = new TabContentProvider<>(eventBus);

        TabData selectedTab = null;

        if (securityContext.hasAppPermission(AppPermission.VIEW_DATA_PERMISSION)) {
            addTab(DATA);
            tabContentProvider.add(DATA, new AbstractTabProvider<Object, MetaPresenter>(eventBus) {
                @Override
                protected MetaPresenter createPresenter() {
                    return metaPresenterProvider.get();
                }

                @Override
                public void onRead(final MetaPresenter presenter,
                                   final DocRef docRef,
                                   final Object document,
                                   final boolean readOnly) {
                    presenter.read(docRef, document, readOnly);
                }
            });
            selectedTab = DATA;
        }

        if (securityContext.hasAppPermission(AppPermission.MANAGE_PROCESSORS_PERMISSION)) {
            addTab(PROCESSORS);
            tabContentProvider.add(PROCESSORS, new AbstractTabProvider<Object, ProcessorPresenter>(eventBus) {
                @Override
                protected ProcessorPresenter createPresenter() {
                    final ProcessorPresenter processorPresenter = processorPresenterProvider.get();
                    final boolean isAdmin = securityContext.hasAppPermission(AppPermission.ADMINISTRATOR);
                    processorPresenter.setIsAdmin(isAdmin);
                    processorPresenter.setAllowUpdate(isAdmin);
                    return processorPresenter;
                }

                @Override
                public void onRead(final ProcessorPresenter presenter,
                                   final DocRef docRef,
                                   final Object document,
                                   final boolean readOnly) {
                    presenter.read(docRef, document, readOnly);
                }
            });
            addTab(TASKS);
            tabContentProvider.add(TASKS, new AbstractTabProvider<Object, ProcessorTaskPresenter>(eventBus) {
                @Override
                protected ProcessorTaskPresenter createPresenter() {
                    return processorTaskPresenterProvider.get();
                }

                @Override
                public void onRead(final ProcessorTaskPresenter presenter,
                                   final DocRef docRef,
                                   final Object document,
                                   final boolean readOnly) {
                    presenter.read(docRef, document, readOnly);
                }
            });
            addTab(PERMISSIONS);
            tabContentProvider.add(PERMISSIONS, documentUserPermissionsTabProvider);

            if (selectedTab == null) {
                selectedTab = PROCESSORS;
            }
        }

        selectTab(selectedTab);
    }

    @Override
    public void getContent(final TabData tab, final ContentCallback callback) {
        callback.onReady(tabContentProvider.getPresenter(tab, this));
    }

    public void read(final DocRef docRef) {
        this.docRef = docRef;
        tabContentProvider.read(docRef, null, true);
    }

    @Override
    public String getType() {
        return ExplorerConstants.FOLDER_TYPE;
    }

    @Override
    public boolean isCloseable() {
        return true;
    }

    @Override
    public String getLabel() {
        return docRef.getName();
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.FOLDER;
    }

    @Override
    public DocRef getDocRef() {
        return docRef;
    }

    @Override
    protected TabData getPermissionsTab() {
        return PERMISSIONS;
    }
}
