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

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.dispatch.client.RestFactory;
import stroom.security.client.ApiKeysPlugin;
import stroom.security.client.AppPermissionsPlugin;
import stroom.security.client.UserTabPlugin;
import stroom.security.client.UsersAndGroupsPlugin;
import stroom.security.client.UsersPlugin;
import stroom.security.client.event.OpenApiKeysScreenEvent;
import stroom.security.client.event.OpenAppPermissionsScreenEvent;
import stroom.security.client.event.OpenUserEvent;
import stroom.security.client.event.OpenUsersAndGroupsScreenEvent;
import stroom.security.client.event.OpenUsersScreenEvent;
import stroom.security.identity.client.AccountsPlugin;
import stroom.security.identity.client.event.OpenAccountEvent;
import stroom.security.shared.HasUserRef;
import stroom.security.shared.User;
import stroom.security.shared.UserFields;
import stroom.security.shared.UserResource;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;
import stroom.util.shared.string.CaseType;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.MenuBuilder;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.TableRowElement;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.cellview.client.DataGrid;
import com.google.gwt.user.client.ui.FocusUtil;
import com.google.gwt.view.client.SelectionModel;
import com.google.inject.Provider;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

public class UserAndGroupHelper {

    private static final UserResource USER_RESOURCE = GWT.create(UserResource.class);

    public static final String COL_NAME_DISPLAY_NAME = "Display Name";
    public static final String COL_NAME_FULL_NAME = "Full Name";
    public static final String COL_NAME_UNIQUE_USER_ID = "Unique User ID";
    public static final String COL_NAME_ENABLED = "Enabled";

    private UserAndGroupHelper() {
    }

    public static SafeHtml buildSingleIconHeader(final boolean isGroup) {
        final String iconClassName = "svgCell-icon";
        final Preset preset = isGroup
                ? SvgPresets.USER_GROUP.title("")
                : SvgPresets.USER.title("");

        return HtmlBuilder.builder()
                .div(
                        divBuilder -> {
                            divBuilder.append(SvgImageUtil.toSafeHtml(
                                    preset.getTitle(),
                                    preset.getSvgImage(),
                                    iconClassName));
                        },
                        Attribute.className("single-icon-column-header"))
                .toSafeHtml();
    }

    public static SafeHtml buildUserAndGroupIconHeader() {
        final String iconClassName = "svgCell-icon";
        final Preset userPreset = SvgPresets.USER.title("");
        final Preset groupPreset = SvgPresets.USER_GROUP.title("");
        return HtmlBuilder.builder()
                .div(
                        divBuilder -> {
                            divBuilder.append(SvgImageUtil.toSafeHtml(
                                    userPreset.getTitle(),
                                    userPreset.getSvgImage(),
                                    iconClassName));
                            divBuilder.append("/");
                            divBuilder.append(SvgImageUtil.toSafeHtml(
                                    groupPreset.getTitle(),
                                    groupPreset.getSvgImage(),
                                    iconClassName));
                        },
                        Attribute.className("two-icon-column-header"))
                .toSafeHtml();
    }

    public static Preset mapUserRefTypeToIcon(final UserRef userRef) {
        return NullSafe.get(userRef,
                userRef2 -> mapUserTypeToIcon(userRef2.isGroup(), userRef.isEnabled()));
    }

    public static Preset mapUserTypeToIcon(final User user) {
        return NullSafe.get(user,
                usr -> mapUserTypeToIcon(usr.isGroup(), usr.isEnabled()));
    }

    public static Preset mapUserTypeToIcon(final boolean isGroup,
                                           final boolean isEnabled) {
        final Preset preset = isGroup
                ? SvgPresets.USER_GROUP
                : SvgPresets.USER;
        return preset.enabled(isEnabled);
    }

    public static void onDelete(final UserListPresenter userListPresenter,
                                final RestFactory restFactory,
                                final HasHandlers hasHandlers) {

        final User user = userListPresenter.getSelectionModel().getSelected();
        if (user != null) {
            final String userDescription = getDescription(user);
            final String msg = "Are you sure you want to permanently delete " + userDescription + "?"
                               + "\n\nThis will also remove them from any groups that they are currently " +
                               "a member of and delete any API keys they held. " +
                               "Any documents that are solely owned by them will then only be " +
                               "accessible by an administrator."
                               + "\nYou will not be permitted to delete them if any content has a " +
                               "dependency on them.";
            ConfirmEvent.fire(hasHandlers, msg, ok -> {
                if (ok) {
                    restFactory
                            .create(USER_RESOURCE)
                            .method(resource -> resource.delete(user.getUuid()))
                            .onSuccess(didDelete -> {
                                if (didDelete) {
                                    userListPresenter.getSelectionModel().clear(true);
                                    userListPresenter.refresh();
                                }
                            })
                            .onFailure(error ->
                                    AlertEvent.fireError(
                                            hasHandlers,
                                            "Error deleting " + userDescription,
                                            error.getMessage(),
                                            null))
                            .taskMonitorFactory(userListPresenter.getPagerView())
                            .exec();
                }
            });
        }
    }

    public static void onEditUserOrGroup(final UserListPresenter userListPresenter,
                                         final Provider<CreateNewGroupPresenter> createNewGroupPresenterProvider,
                                         final TaskMonitorFactory taskMonitorFactory) {
        final User user = userListPresenter.getSelectionModel().getSelected();
        if (user != null && user.isGroup()) {
            final CreateNewGroupPresenter createNewGroupPresenter = createNewGroupPresenterProvider.get();
            createNewGroupPresenter.getView().setName(user.getSubjectId());
            final PopupSize popupSize = PopupSize.resizable(600, 200);
            ShowPopupEvent.builder(createNewGroupPresenter)
                    .popupType(PopupType.OK_CANCEL_DIALOG)
                    .popupSize(popupSize)
                    .caption("Edit User Group")
                    .onShow(e -> createNewGroupPresenter.getView().focus())
                    .onHideRequest(e -> {
                        if (e.isOk()) {
                            createNewGroupPresenter.editGroupName(
                                    user,
                                    createAfterChangeConsumer(userListPresenter),
                                    e,
                                    taskMonitorFactory);
                        } else {
                            e.hide();
                        }
                    })
                    .fire();
        }
    }

    public static Consumer<User> createAfterChangeConsumer(final UserListPresenter userListPresenter) {
        return user -> {
            userListPresenter.getSelectionModel()
                    .clear(true);
            userListPresenter.refresh();
        };
    }

    private static String getDescription(final User user) {
        return user.getType(CaseType.LOWER)
               + " '" + user.asRef().toDisplayString() + "'";
    }

    public static <T extends HasUserRef> boolean selectUserIfShown(final UserRef userRef,
                                                                   final ResultPage<T> currentData,
                                                                   final SelectionModel<T> selectionModel,
                                                                   final DataGrid<T> dataGrid) {
        if (currentData != null) {
            final AtomicInteger idx = new AtomicInteger(-1);
            GWT.log("Attempting to show user " + userRef);
            final boolean found = currentData.stream()
                    .peek(appUserPermissions -> idx.incrementAndGet())
                    .filter(Objects::nonNull)
                    .filter(hasUserRef ->
                            Objects.equals(hasUserRef.getUserRef(), userRef))
                    .findAny()
                    .map(hasUserRef -> {
                        GWT.log("Found " + userRef);
                        selectionModel.setSelected(hasUserRef, true);
                        dataGrid.flush();
                        dataGrid.setKeyboardSelectedRow(idx.get());
                        Scheduler.get().scheduleDeferred(() -> {
                            try {
                                final TableRowElement rowElement = dataGrid.getRowElement(idx.get());
                                NullSafe.consume(rowElement, FocusUtil::focusRow);
                            } catch (final IndexOutOfBoundsException e) {
                                GWT.log(idx.get() + " is out of bounds");
                            }
                        });
                        return true;
                    })
                    .orElse(false);
            return found;
        } else {
            return false;
        }
    }

    public static List<Item> buildUserActionMenu(final UserRef userRef,
                                                 final boolean isExternalIdp,
                                                 final Set<UserScreen> userScreens,
                                                 final HasHandlers hasHandlers,
                                                 final Function<UserRef, UserRefPopupPresenter>
                                                         copyPermissionsPopupFunction) {
        final Set<UserScreen> screens = NullSafe.set(userScreens);
        if (userRef == null) {
            return Collections.emptyList();
        } else {
            final MenuBuilder builder = MenuBuilder.builder()
                    .withSimpleMenuItem(itemBuilder ->
                            itemBuilder.text(buildActionsForMsg(userRef) + ":")
                                    .build())
                    .withSeparator();
            if (screens.contains(UserScreen.USER)) {
                builder.withIconMenuItem(itemBuilder -> itemBuilder
                        .icon(userRef.isGroup()
                                ? UserTabPlugin.GROUP_ICON
                                : UserTabPlugin.USER_ICON)
                        .text("Open " + userRef.getType(CaseType.LOWER)
                              + " '" + userRef.toDisplayString() + "'")
                        .command(() ->
                                OpenUserEvent.fire(hasHandlers, userRef)));
            }
            if (userRef.isUser() && screens.contains(UserScreen.USERS)) {
                builder.withIconMenuItem(itemBuilder -> itemBuilder
                        .icon(UsersPlugin.ICON)
                        .text(buildMenuItemText(userRef, UsersPlugin.SCREEN_NAME))
                        .command(() ->
                                OpenUsersScreenEvent.fire(hasHandlers, userRef)));
            }
            if (userRef.isUser() && !isExternalIdp && screens.contains(UserScreen.ACCOUNTS)) {
                builder.withIconMenuItem(itemBuilder -> itemBuilder
                        .icon(AccountsPlugin.ICON)
                        .text(buildMenuItemText(userRef, AccountsPlugin.SCREEN_NAME))
                        .command(() ->
                                OpenAccountEvent.fire(hasHandlers, userRef.getSubjectId())));
            }
            if (screens.contains(UserScreen.USER_GROUPS)) {
                builder.withIconMenuItem(itemBuilder -> itemBuilder
                        .icon(UsersAndGroupsPlugin.ICON)
                        .text(buildMenuItemText(userRef, UsersAndGroupsPlugin.SCREEN_NAME))
                        .command(() ->
                                OpenUsersAndGroupsScreenEvent.fire(hasHandlers, userRef)));
            }
            if (screens.contains(UserScreen.APP_PERMISSIONS)) {
                builder.withIconMenuItem(itemBuilder -> itemBuilder
                        .icon(AppPermissionsPlugin.ICON)
                        .text(buildMenuItemText(userRef, AppPermissionsPlugin.SCREEN_NAME))
                        .command(() ->
                                OpenAppPermissionsScreenEvent.fire(hasHandlers, userRef)));
            }
            if (userRef.isUser() && screens.contains(UserScreen.API_KEYS)) {
                builder.withIconMenuItem(itemBuilder -> itemBuilder
                        .icon(ApiKeysPlugin.ICON)
                        .text(buildMenuItemText(userRef, ApiKeysPlugin.SCREEN_NAME))
                        .command(() ->
                                OpenApiKeysScreenEvent.fire(hasHandlers, userRef)));
            }
            if (userRef.isUser() && copyPermissionsPopupFunction != null) {
                builder.withSeparatorIf(builder.hasItems());

                builder.withIconMenuItem(itemBuilder -> itemBuilder
                        .icon(UserTabPlugin.USER_ICON)
                        .text("Copy user groups and permissions from...")
                        .command(() -> {
                            copyPermissionsPopupFunction.apply(userRef).show("Select User");
                        }));
            }

            return builder.build();
        }
    }

    private static String buildMenuItemText(final UserRef userRef, final String screenName) {
        return "Show " + userRef.getType(CaseType.LOWER) + " on the " + screenName + " screen";
    }

    public static String buildActionsForMsg(final User user) {
        if (user == null) {
            return "";
        } else {
            return buildActionsForMsg(user.asRef());
        }
    }

    public static String buildActionsForMsg(final UserRef userRef) {
        if (userRef == null) {
            return "";
        } else {
            return "Actions for "
                   + userRef.getType(CaseType.LOWER)
                   + " '" + userRef.getDisplayName() + "'";
        }
    }

    public static String buildDisplayNameFilterInput(final UserRef userRef) {
        if (userRef == null) {
            return "";
        } else if (userRef.getDisplayName() != null) {
            return UserFields.FIELD_DISPLAY_NAME + ":" + userRef.getDisplayName();
        } else {
            return "";
        }
    }

}
