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

package stroom.folder.client;

import stroom.data.client.presenter.MetaPresenter;
import stroom.data.client.presenter.ProcessorTaskPresenter;
import stroom.docref.DocRef;
import stroom.document.client.event.OpenDocumentEvent.CommonDocLinkTab;
import stroom.entity.client.component.TabbedController;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.explorer.shared.ExplorerConstants;
import stroom.processor.client.presenter.ProcessorPresenter;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.component.DocumentUserPermissionsTabController;
import stroom.security.shared.AppPermission;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

/// A folder's tab, or the System root's: the data in the folder, its processors and their active
/// tasks, and its permissions, as far as the user may see them. Nothing in it can be changed, so
/// every tab is read only.
///
/// Which tabs it has depends on what it shows (the System root always has Permissions; a folder
/// only for a user who may manage processors), so they are added when it is first read.
public class FolderTabs extends TabbedController<DocRef> {

    private static final TabData DATA = new TabDataImpl("Data");
    private static final TabData TASKS = new TabDataImpl("Active Tasks");
    private static final TabData PROCESSORS = new TabDataImpl("Processors");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    private final ClientSecurityContext securityContext;
    private final Provider<MetaPresenter> metaPresenterProvider;
    private final Provider<ProcessorPresenter> processorPresenterProvider;
    private final Provider<ProcessorTaskPresenter> processorTaskPresenterProvider;
    private final Provider<DocumentUserPermissionsTabController> permissionsProvider;
    private boolean tabsAdded;

    @Inject
    public FolderTabs(final EventBus eventBus,
                      final LinkTabPanelView view,
                      final ClientSecurityContext securityContext,
                      final Provider<MetaPresenter> metaPresenterProvider,
                      final Provider<ProcessorPresenter> processorPresenterProvider,
                      final Provider<ProcessorTaskPresenter> processorTaskPresenterProvider,
                      final Provider<DocumentUserPermissionsTabController> permissionsProvider) {
        super(eventBus, view);
        this.securityContext = securityContext;
        this.metaPresenterProvider = metaPresenterProvider;
        this.processorPresenterProvider = processorPresenterProvider;
        this.processorTaskPresenterProvider = processorTaskPresenterProvider;
        this.permissionsProvider = permissionsProvider;
    }

    /// @return Whether the user may see a folder's tab at all: they must be able to see its data
    /// or manage its processors.
    public static boolean canOpen(final ClientSecurityContext securityContext) {
        return securityContext.hasAppPermission(AppPermission.VIEW_DATA_PERMISSION)
               || securityContext.hasAppPermission(AppPermission.MANAGE_PROCESSORS_PERMISSION);
    }

    @Override
    protected void onRead(final DocRef docRef, final DocRef folder) {
        if (!tabsAdded) {
            tabsAdded = true;
            addTabs(ExplorerConstants.SYSTEM_DOC_REF.equals(docRef));
        }
    }

    private void addTabs(final boolean root) {
        TabData selectedTab = null;
        if (securityContext.hasAppPermission(AppPermission.VIEW_DATA_PERMISSION)) {
            addTab(DATA,
                    metaPresenterProvider::get,
                    (meta, docRef, folder) -> meta.read(docRef, null));
            selectedTab = DATA;
        }

        final boolean manageProcessors = securityContext.hasAppPermission(
                AppPermission.MANAGE_PROCESSORS_PERMISSION);
        if (manageProcessors) {
            addTab(PROCESSORS,
                    () -> {
                        final ProcessorPresenter processors = processorPresenterProvider.get();
                        final boolean isAdmin = securityContext.hasAppPermission(AppPermission.ADMINISTRATOR);
                        processors.setIsAdmin(isAdmin);
                        processors.setAllowUpdate(isAdmin);
                        return processors;
                    },
                    (processors, docRef, folder) -> processors.read(docRef, null));
            addTab(TASKS,
                    processorTaskPresenterProvider::get,
                    (tasks, docRef, folder) -> tasks.read(docRef, null));
            if (selectedTab == null) {
                selectedTab = PROCESSORS;
            }
        }

        if (root || manageProcessors) {
            addTab(PERMISSIONS,
                    permissionsProvider::get,
                    (permissions, docRef, folder) -> permissions.setDocRef(docRef));
            setCommonTab(CommonDocLinkTab.PERMISSIONS, PERMISSIONS);
        }

        if (selectedTab != null) {
            selectTab(selectedTab);
        }
    }
}
