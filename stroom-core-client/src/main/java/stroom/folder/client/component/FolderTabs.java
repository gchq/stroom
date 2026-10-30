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

package stroom.folder.client.component;

import stroom.data.client.component.MetaBrowserController;
import stroom.data.client.component.ProcessorTasksTabController;
import stroom.docref.DocRef;
import stroom.document.client.editor.LinkTabPanel;
import stroom.document.client.editor.TabbedController;
import stroom.document.client.event.OpenDocumentEvent.CommonDocLinkTab;
import stroom.processor.client.component.ProcessorsController;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.component.DocumentUserPermissionsTabController;
import stroom.security.shared.AppPermission;
import stroom.widget.tab.client.TabData;
import stroom.widget.tab.client.TabDataImpl;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

/// A folder's tab, or the System root's: the data in the folder, its processors and their active
/// tasks, and its permissions, as far as the user may see them. Nothing in it can be changed, so
/// every tab is read only.
///
/// Which tabs it has depends on the user's permissions (Processors and Active Tasks only for a user
/// who may manage processors); they are added when it is first read.
public class FolderTabs extends TabbedController<DocRef> {

    private static final TabData DATA = new TabDataImpl("Data");
    private static final TabData TASKS = new TabDataImpl("Active Tasks");
    private static final TabData PROCESSORS = new TabDataImpl("Processors");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    private final ClientSecurityContext securityContext;
    private final Provider<MetaBrowserController> metaControllerProvider;
    private final Provider<ProcessorsController> processorsProvider;
    private final Provider<ProcessorTasksTabController> processorTaskControllerProvider;
    private final Provider<DocumentUserPermissionsTabController> permissionsProvider;
    private boolean tabsAdded;

    @Inject
    public FolderTabs(final EventBus eventBus,
                      final LinkTabPanel view,
                      final ClientSecurityContext securityContext,
                      final Provider<MetaBrowserController> metaControllerProvider,
                      final Provider<ProcessorsController> processorsProvider,
                      final Provider<ProcessorTasksTabController> processorTaskControllerProvider,
                      final Provider<DocumentUserPermissionsTabController> permissionsProvider) {
        super(eventBus, view);
        this.securityContext = securityContext;
        this.metaControllerProvider = metaControllerProvider;
        this.processorsProvider = processorsProvider;
        this.processorTaskControllerProvider = processorTaskControllerProvider;
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
            addTabs();
        }
    }

    private void addTabs() {
        TabData selectedTab = null;
        if (securityContext.hasAppPermission(AppPermission.VIEW_DATA_PERMISSION)) {
            addTab(DATA,
                    metaControllerProvider::get,
                    (meta, docRef, folder) -> meta.read(docRef, null));
            selectedTab = DATA;
        }

        final boolean manageProcessors = securityContext.hasAppPermission(
                AppPermission.MANAGE_PROCESSORS_PERMISSION);
        if (manageProcessors) {
            addTab(PROCESSORS,
                    () -> {
                        final ProcessorsController processors = processorsProvider.get();
                        final boolean isAdmin = securityContext.hasAppPermission(AppPermission.ADMINISTRATOR);
                        processors.setIsAdmin(isAdmin);
                        processors.setAllowUpdate(isAdmin);
                        return processors;
                    },
                    (processors, docRef, folder) -> processors.read(docRef, null));
            addTab(TASKS,
                    processorTaskControllerProvider::get,
                    (tasks, docRef, folder) -> tasks.read(docRef, null));
            if (selectedTab == null) {
                selectedTab = PROCESSORS;
            }
        }

        // Every folder has Permissions, as the System root does: the tab shows only what the user
        // may see and change
        addTab(PERMISSIONS,
                permissionsProvider::get,
                (permissions, docRef, folder) -> permissions.setDocRef(docRef));
        setCommonTab(CommonDocLinkTab.PERMISSIONS, PERMISSIONS);

        if (selectedTab != null) {
            selectTab(selectedTab);
        }
    }
}
