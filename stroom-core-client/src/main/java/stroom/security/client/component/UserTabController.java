/*
 * Copyright 2024 Crown Copyright
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

package stroom.security.client.component;

import stroom.content.client.component.ContentTabController;
import stroom.entity.client.component.LinkTabPanel;
import stroom.security.client.UserTabPlugin;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.svg.shared.SvgImage;
import stroom.task.client.SimpleTask;
import stroom.task.client.Task;
import stroom.task.client.TaskMonitor;
import stroom.util.shared.UserRef;
import stroom.util.shared.string.CaseType;
import stroom.widget.component.client.ControllerLayer;
import stroom.widget.presenter.client.Layer;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;
import stroom.widget.util.client.LazyValue;

import com.google.gwt.core.client.Scheduler;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/// A user's or group's tab: their identity, groups, permissions, dependencies and API keys, a tab
/// each.
public class UserTabController extends ContentTabController {

    public static final String TAB_TYPE = "User";

    private static final TabData INFO_TAB = TabDataImpl.builder()
            .withLabel("Info")
            .withTooltip("Basic identity information about the user/group.")
            .build();
    // Manage users only
    private static final TabData APP_PERMS_TAB = TabDataImpl.builder()
            .withLabel("Application Permissions")
            .withTooltip("Application level permissions for this user/group.")
            .build();
    private static final TabData DOC_PERMS_TAB = TabDataImpl.builder()
            .withLabel("Document Permissions")
            .withTooltip("Document level permissions for this user/group.")
            .build();
    private static final TabData USER_GROUPS_TAB = TabDataImpl.builder()
            .withLabel("User Groups")
            .withTooltip("Groups this user/group is a member of and members of this group (if applicable).")
            .build();
    private static final TabData DEPENDENCIES_TAB = TabDataImpl.builder()
            .withLabel("Dependencies")
            .withTooltip("Content items that depend on this user/group.")
            .build();
    private static final TabData API_KEYS_TAB = TabDataImpl.builder()
            .withLabel("API Keys")
            .withTooltip("API keys held by this user/group.")
            .build();

    private final UserInfoTabController userInfo;
    private final ControllerLayer userInfoLayer;
    private final LazyValue<UserPermissionReportController> userPermissionReportLazyValue;
    private ControllerLayer userPermissionReportLayer;
    private final LazyValue<UserDependenciesListController> userDependenciesListLazyValue;
    private ControllerLayer userDependenciesListLayer;
    private final LazyValue<ApiKeysController> apiKeysListPresenterLazyValue;
    private final LazyValue<AppPermissionsEditController> appPermissionsEditLazyValue;
    private ControllerLayer appPermissionsEditLayer;
    private final LazyValue<UserAndGroupsController> userAndGroupsLazyValue;
    private ControllerLayer userAndGroupsLayer;
    private final List<TabData> tabs = new ArrayList<>();

    private final LinkTabPanel tabPanel;
    private UserRef userRef;
    private String label;
    private Layer currentContent;

    /// Made by GIN, with its tabs, each made when it is first shown.
    @Inject
    public UserTabController(final EventBus eventBus,
                             final LinkTabPanel view,
                             final UserInfoTabController userInfo,
                             final ClientSecurityContext clientSecurityContext,
                             final Provider<UserPermissionReportController> userPermissionReportProvider,
                             final Provider<UserDependenciesListController> userDependenciesListProvider,
                             final Provider<ApiKeysController> apiKeysListPresenterProvider,
                             final Provider<AppPermissionsEditController> appPermissionsEditProvider,
                             final Provider<UserAndGroupsController> userAndGroupsProvider) {
        super(eventBus);
        this.tabPanel = view;
        initWidget(view);
        own(userInfo);
        this.userInfo = userInfo;
        this.userInfoLayer = new ControllerLayer(userInfo);
        this.userPermissionReportLazyValue = new LazyValue<UserPermissionReportController>(
                (Supplier<UserPermissionReportController>) () -> make(userPermissionReportProvider),
                userPermissionReport -> {
                    userPermissionReportLayer = new ControllerLayer(userPermissionReport);
                    userPermissionReport.setUserRef(userRef);
                });
        this.userDependenciesListLazyValue = new LazyValue<UserDependenciesListController>(
                (Supplier<UserDependenciesListController>) () -> make(userDependenciesListProvider),
                userDependenciesList -> {
                    userDependenciesListLayer = new ControllerLayer(userDependenciesList);
                    userDependenciesList.setUserRef(getUserRef());
                });
        this.apiKeysListPresenterLazyValue = new LazyValue<ApiKeysController>(
                (Supplier<ApiKeysController>) () -> make(apiKeysListPresenterProvider),
                apiKeysListPresenter -> {
                    apiKeysListPresenter.setOwner(getUserRef());
                });
        this.appPermissionsEditLazyValue = new LazyValue<AppPermissionsEditController>(
                (Supplier<AppPermissionsEditController>) () -> make(appPermissionsEditProvider),
                appPermissionsEdit -> {
                    appPermissionsEditLayer = new ControllerLayer(appPermissionsEdit);
                    appPermissionsEdit.setUserRef(getUserRef());
                });
        this.userAndGroupsLazyValue = new LazyValue<UserAndGroupsController>(
                (Supplier<UserAndGroupsController>) () -> make(userAndGroupsProvider),
                userAndGroups -> {
                    userAndGroupsLayer = new ControllerLayer(userAndGroups);
                    userAndGroups.setUserRef(userRef);
                });

        final boolean hasManagerUsersPerm = clientSecurityContext.hasAppPermission(
                AppPermission.MANAGE_USERS_PERMISSION);

        addTab(INFO_TAB);
        addTab(USER_GROUPS_TAB);
        // It was decided that a user should not see their own app perms.
        if (hasManagerUsersPerm) {
            addTab(APP_PERMS_TAB);
        }
        addTab(DOC_PERMS_TAB);
        addTab(DEPENDENCIES_TAB);
        if (clientSecurityContext.hasAppPermission(AppPermission.MANAGE_API_KEYS)) {
            addTab(API_KEYS_TAB);
        }

        selectTab(INFO_TAB);

        own(tabPanel.getTabBar().addSelectionHandler(event ->
                selectTab(event.getSelectedItem())));
        own(tabPanel.getTabBar().addShowMenuHandler(e ->
                getEventBus().fireEvent(e)));
    }

    /// Shows one of the tabs.
    ///
    /// @param tab The tab.
    public void selectTab(final TabData tab) {
        final TaskMonitor taskMonitor = createTaskMonitor();
        final Task task = new SimpleTask("Selecting tab");
        taskMonitor.onStart(task);
        Scheduler.get().scheduleDeferred(() -> {
            if (tab != null) {
                getContent(tab, content -> {
                    if (content != null) {
                        currentContent = content;
                        // Set the content.
                        tabPanel.getLayerContainer().show(currentContent);
                        // Update the selected tab.
                        tabPanel.getTabBar().selectTab(tab);
                    }
                });
            }
            taskMonitor.onEnd(task);
        });
    }

    private void getContent(final TabData tab, final Consumer<Layer> callback) {
        if (INFO_TAB.equals(tab)) {
            userInfo.setUserRef(userRef);
            callback.accept(userInfoLayer);
        } else if (USER_GROUPS_TAB.equals(tab)) {
            final UserAndGroupsController userAndGroups = userAndGroupsLazyValue.getValue();
            userAndGroups.setUserRef(userRef);
            callback.accept(userAndGroupsLayer);
        } else if (APP_PERMS_TAB.equals(tab)) {
            final AppPermissionsEditController appPermissionsEdit = appPermissionsEditLazyValue.getValue();
            appPermissionsEdit.setUserRef(userRef);
            callback.accept(appPermissionsEditLayer);
        } else if (DOC_PERMS_TAB.equals(tab)) {
            final UserPermissionReportController userPermissionReport = userPermissionReportLazyValue.getValue();
            userPermissionReport.setUserRef(userRef);
            callback.accept(userPermissionReportLayer);
        } else if (DEPENDENCIES_TAB.equals(tab)) {
            final UserDependenciesListController userDependenciesList = userDependenciesListLazyValue.getValue();
            userDependenciesList.setUserRef(userRef);
            callback.accept(userDependenciesListLayer);
        } else if (API_KEYS_TAB.equals(tab)) {
            callback.accept(apiKeysListPresenterLazyValue.getValue());
        } else {
            callback.accept(null);
        }
    }

    private void addTab(final TabData tab) {
        tabPanel.getTabBar().addTab(tab);
        tabs.add(tab);
    }

    @Override
    public SvgImage getIcon() {
        return userRef != null && userRef.isGroup()
                ? UserTabPlugin.GROUP_ICON
                : UserTabPlugin.USER_ICON;
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }

    private static String getLabel(final UserRef userRef) {
        // A user with nothing to identify it would otherwise be shown as '{null}'
        if (userRef == null
            || (userRef.getDisplayName() == null
                && userRef.getSubjectId() == null
                && userRef.getFullName() == null
                && userRef.getUuid() == null)) {
            return "Unknown User/Group";
        }
        return userRef.getType(CaseType.SENTENCE) + ": " + userRef.toDisplayString();
    }

    /// Shows a user or group.
    ///
    /// @param userRef The user or group.
    public void setUserRef(final UserRef userRef) {
        this.userRef = userRef;
        this.label = getLabel(userRef);
        userInfo.setUserRef(userRef);
        appPermissionsEditLazyValue.consumeIfInitialised(appPermissionsEdit ->
                appPermissionsEdit.setUserRef(userRef));
        userPermissionReportLazyValue.consumeIfInitialised(userPermissionReport ->
                userPermissionReport.setUserRef(userRef));
        userDependenciesListLazyValue.consumeIfInitialised(userDependenciesList ->
                userDependenciesList.setUserRef(userRef));
        apiKeysListPresenterLazyValue.consumeIfInitialised(apiKeysPresenter ->
                apiKeysPresenter.setOwner(userRef));
    }

    /// @return The user or group shown.
    public UserRef getUserRef() {
        return userRef;
    }
}
