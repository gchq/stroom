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

package stroom.security.client.presenter;

import stroom.content.client.presenter.ContentTabPresenter;
import stroom.entity.client.presenter.ContentCallback;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.security.client.UserTabPlugin;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.svg.shared.SvgImage;
import stroom.task.client.SimpleTask;
import stroom.task.client.Task;
import stroom.task.client.TaskMonitor;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;
import stroom.util.shared.string.CaseType;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;
import stroom.widget.util.client.LazyValue;

import com.google.gwt.core.client.Scheduler;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.Layer;
import com.gwtplatform.mvp.client.PresenterWidget;

import java.util.ArrayList;
import java.util.List;

public class UserTabPresenter
        extends ContentTabPresenter<LinkTabPanelView> {

    private static final String TAB_TYPE = "User";

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

    private final UserInfoPresenter userInfoPresenter;
    private final LazyValue<UserPermissionReportPresenter> userPermsReportPresenterLazyValue;
    private final LazyValue<UserDependenciesListPresenter> userDependenciesListPresenterLazyValue;
    private final LazyValue<ApiKeysPresenter> apiKeysListPresenterLazyValue;
    private final LazyValue<AppPermissionsEditPresenter> appPermissionsEditPresenterLazyValue;
    private final LazyValue<UserAndGroupsPresenter> userAndGroupsPresenterLazyValue;
    private final List<TabData> tabs = new ArrayList<>();

    private UserRef userRef;
    private String label;
    private PresenterWidget<?> currentContent;

    @Inject
    public UserTabPresenter(final EventBus eventBus,
                            final LinkTabPanelView view,
                            final UserInfoPresenter userInfoPresenter,
                            final ClientSecurityContext clientSecurityContext,
                            final Provider<UserPermissionReportPresenter> userPermsReportPresenterProvider,
                            final Provider<UserDependenciesListPresenter> userDependenciesListPresenterProvider,
                            final Provider<ApiKeysPresenter> apiKeysListPresenterProvider,
                            final Provider<AppPermissionsEditPresenter> appPermissionsEditPresenterProvider,
                            final Provider<UserAndGroupsPresenter> userAndGroupsPresenterProvider) {
        super(eventBus, view);
        this.userInfoPresenter = userInfoPresenter;
        this.userPermsReportPresenterLazyValue = new LazyValue<>(
                userPermsReportPresenterProvider,
                userPermissionReportPresenter -> {
                    userPermissionReportPresenter.setUserRef(userRef);
                });
        this.userDependenciesListPresenterLazyValue = new LazyValue<>(
                userDependenciesListPresenterProvider,
                userDependenciesListPresenter -> {
                    userDependenciesListPresenter.setUserRef(getUserRef());
                });
        this.apiKeysListPresenterLazyValue = new LazyValue<>(
                apiKeysListPresenterProvider,
                apiKeysListPresenter -> {
                    apiKeysListPresenter.setOwner(getUserRef());
                });
        this.appPermissionsEditPresenterLazyValue = new LazyValue<>(
                appPermissionsEditPresenterProvider,
                appPermissionsEditPresenter -> {
                    appPermissionsEditPresenter.setUserRef(getUserRef());
                });
        this.userAndGroupsPresenterLazyValue = new LazyValue<>(
                userAndGroupsPresenterProvider,
                userAndGroupsPresenter -> {
                    userAndGroupsPresenter.setUserRef(userRef);
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
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(getView().getTabBar().addSelectionHandler(event ->
                selectTab(event.getSelectedItem())));
        registerHandler(getView().getTabBar().addShowMenuHandler(e ->
                getEventBus().fireEvent(e)));
    }

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
                        getView().getLayerContainer().show((Layer) currentContent);
                        // Update the selected tab.
                        getView().getTabBar().selectTab(tab);
                        afterSelectTab(content);
                    }
                });
            }
            taskMonitor.onEnd(task);
        });
    }

    protected void getContent(final TabData tab, final ContentCallback callback) {
        if (INFO_TAB.equals(tab)) {
            userInfoPresenter.setUserRef(userRef);
            callback.onReady(userInfoPresenter);
        } else if (USER_GROUPS_TAB.equals(tab)) {
            final UserAndGroupsPresenter userAndGroupsPresenter
                    = userAndGroupsPresenterLazyValue.getValue();
            userAndGroupsPresenter.setUserRef(userRef);
            callback.onReady(userAndGroupsPresenter);
        } else if (APP_PERMS_TAB.equals(tab)) {
            final AppPermissionsEditPresenter appPermissionsEditPresenter
                    = appPermissionsEditPresenterLazyValue.getValue();
            appPermissionsEditPresenter.setUserRef(userRef);
            callback.onReady(appPermissionsEditPresenter);
        } else if (DOC_PERMS_TAB.equals(tab)) {
            final UserPermissionReportPresenter userPermsReportPresenter = userPermsReportPresenterLazyValue.getValue();
            userPermsReportPresenter.setUserRef(userRef);
            callback.onReady(userPermsReportPresenter);
        } else if (DEPENDENCIES_TAB.equals(tab)) {
            final UserDependenciesListPresenter userDependenciesListPresenter
                    = userDependenciesListPresenterLazyValue.getValue();
            userDependenciesListPresenter.setUserRef(userRef);
            callback.onReady(userDependenciesListPresenter);
        } else if (API_KEYS_TAB.equals(tab)) {
            callback.onReady(apiKeysListPresenterLazyValue.getValue());
        } else {
            callback.onReady(null);
        }
    }

    protected void afterSelectTab(final PresenterWidget<?> content) {
    }

    private void addTab(final TabData tab) {
        getView().getTabBar().addTab(tab);
        tabs.add(tab);
    }

    @Override
    public SvgImage getIcon() {
        return userRef != null && userRef.isGroup()
                ? UserTabPlugin.GROUP_ICON.getSvgImage()
                : UserTabPlugin.USER_ICON.getSvgImage();
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }

    public void setUserRef(final UserRef userRef) {
        this.userRef = userRef;
        this.label = NullSafe.getOrElse(
                userRef,
                ref -> ref.getType(CaseType.SENTENCE) + ": " + ref.toDisplayString(),
                "Unknown User/Group");
        userInfoPresenter.setUserRef(userRef);
        appPermissionsEditPresenterLazyValue.consumeIfInitialised(appPermissionsEditPresenter ->
                appPermissionsEditPresenter.setUserRef(userRef));
        userPermsReportPresenterLazyValue.consumeIfInitialised(userPermissionReportPresenter ->
                userPermissionReportPresenter.setUserRef(userRef));
        userDependenciesListPresenterLazyValue.consumeIfInitialised(userDependenciesListPresenter ->
                userDependenciesListPresenter.setUserRef(userRef));
        apiKeysListPresenterLazyValue.consumeIfInitialised(apiKeysPresenter ->
                apiKeysPresenter.setOwner(userRef));
    }

    public UserRef getUserRef() {
        return userRef;
    }
}
