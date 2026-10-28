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

package stroom.security.client;

import stroom.cell.info.client.CellIcon;
import stroom.security.client.component.UserListController;
import stroom.security.client.event.OpenApiKeysScreenEvent;
import stroom.security.client.event.OpenAppPermissionsScreenEvent;
import stroom.security.client.event.OpenUserEvent;
import stroom.security.client.event.OpenUsersAndGroupsScreenEvent;
import stroom.security.client.event.OpenUsersScreenEvent;
import stroom.security.identity.client.AccountsPlugin;
import stroom.security.identity.client.event.OpenAccountEvent;
import stroom.security.shared.User;
import stroom.security.shared.UserFields;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;
import stroom.util.shared.string.CaseType;
import stroom.widget.menu.client.Item;
import stroom.widget.menu.client.MenuBuilder;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.safehtml.shared.SafeHtml;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class UserAndGroupHelper {

    public static final String COL_NAME_DISPLAY_NAME = "Display Name";
    public static final String COL_NAME_FULL_NAME = "Full Name";
    public static final String COL_NAME_UNIQUE_USER_ID = "Unique User ID";
    public static final String COL_NAME_ENABLED = "Enabled";

    private UserAndGroupHelper() {
    }

    public static SafeHtml buildSingleIconHeader(final boolean isGroup) {
        final String iconClassName = "icon-cell";
        final SvgImage icon = isGroup
                ? SvgImage.USERS
                : SvgImage.USER;

        return HtmlBuilder.builder()
                .div(
                        divBuilder -> {
                            divBuilder.append(SvgImageUtil.toSafeHtml(
                                    "",
                                    icon,
                                    iconClassName));
                        },
                        Attribute.className("single-icon-column-header"))
                .toSafeHtml();
    }

    public static SafeHtml buildUserAndGroupIconHeader() {
        final String iconClassName = "icon-cell";
        return HtmlBuilder.builder()
                .div(
                        divBuilder -> {
                            divBuilder.append(SvgImageUtil.toSafeHtml(
                                    "",
                                    SvgImage.USER,
                                    iconClassName));
                            divBuilder.append("/");
                            divBuilder.append(SvgImageUtil.toSafeHtml(
                                    "",
                                    SvgImage.USERS,
                                    iconClassName));
                        },
                        Attribute.className("two-icon-column-header"))
                .toSafeHtml();
    }

    public static CellIcon mapUserRefTypeToIcon(final UserRef userRef) {
        return NullSafe.get(userRef,
                userRef2 -> mapUserTypeToIcon(userRef2.isGroup(), userRef.isEnabled()));
    }

    public static CellIcon mapUserTypeToIcon(final User user) {
        return NullSafe.get(user,
                usr -> mapUserTypeToIcon(usr.isGroup(), usr.isEnabled()));
    }

    public static CellIcon mapUserTypeToIcon(final boolean isGroup,
                                           final boolean isEnabled) {
        return isGroup
                ? CellIcon.of(SvgImage.USERS, "User Group", isEnabled)
                : CellIcon.of(SvgImage.USER, "User", isEnabled);
    }

    public static Consumer<User> createAfterChangeConsumer(final UserListController userList) {
        return user -> {
            userList.getSelectionModel()
                    .clear(true);
            userList.refresh();
        };
    }

    public static List<Item> buildUserActionMenu(final UserRef userRef,
                                                 final boolean isExternalIdp,
                                                 final Set<UserScreen> userScreens,
                                                 final HasHandlers hasHandlers,
                                                 final Consumer<UserRef> onCopyPermissions) {
        final Set<UserScreen> screens = NullSafe.set(userScreens);
        if (userRef == null) {
            return Collections.emptyList();
        } else {
            // The menu is named by buildActionsForMsg, so has no heading item
            final MenuBuilder builder = MenuBuilder.builder();
            if (screens.contains(UserScreen.USER)) {
                builder.withIconMenuItem(itemBuilder -> itemBuilder
                        .icon(userRef.isGroup()
                                ? UserTabPlugin.GROUP_ICON
                                : UserTabPlugin.USER_ICON)
                        // The menu name gives the user/group name
                        .text("Open " + userRef.getType(CaseType.SENTENCE))
                        .command(() ->
                                OpenUserEvent.fire(hasHandlers, userRef)));
            }
            if (userRef.isUser() && screens.contains(UserScreen.USERS)) {
                builder.withIconMenuItem(itemBuilder -> itemBuilder
                        .icon(UsersPlugin.ICON)
                        .text(buildMenuItemText(UsersPlugin.SCREEN_NAME))
                        .command(() ->
                                OpenUsersScreenEvent.fire(hasHandlers, userRef)));
            }
            if (userRef.isUser() && !isExternalIdp && screens.contains(UserScreen.ACCOUNTS)) {
                builder.withIconMenuItem(itemBuilder -> itemBuilder
                        .icon(AccountsPlugin.ICON)
                        .text(buildMenuItemText(AccountsPlugin.SCREEN_NAME))
                        .command(() ->
                                OpenAccountEvent.fire(hasHandlers, userRef.getSubjectId())));
            }
            if (screens.contains(UserScreen.USER_GROUPS)) {
                builder.withIconMenuItem(itemBuilder -> itemBuilder
                        .icon(UsersAndGroupsPlugin.ICON)
                        .text(buildMenuItemText(UsersAndGroupsPlugin.SCREEN_NAME))
                        .command(() ->
                                OpenUsersAndGroupsScreenEvent.fire(hasHandlers, userRef)));
            }
            if (screens.contains(UserScreen.APP_PERMISSIONS)) {
                builder.withIconMenuItem(itemBuilder -> itemBuilder
                        .icon(AppPermissionsPlugin.ICON)
                        .text(buildMenuItemText(AppPermissionsPlugin.SCREEN_NAME))
                        .command(() ->
                                OpenAppPermissionsScreenEvent.fire(hasHandlers, userRef)));
            }
            if (userRef.isUser() && screens.contains(UserScreen.API_KEYS)) {
                builder.withIconMenuItem(itemBuilder -> itemBuilder
                        .icon(ApiKeysPlugin.ICON)
                        .text(buildMenuItemText(ApiKeysPlugin.SCREEN_NAME))
                        .command(() ->
                                OpenApiKeysScreenEvent.fire(hasHandlers, userRef)));
            }
            if (userRef.isUser() && onCopyPermissions != null) {
                builder.withSeparatorIf(builder.hasItems());

                builder.withIconMenuItem(itemBuilder -> itemBuilder
                        .icon(UserTabPlugin.USER_ICON)
                        .text("Copy User Groups and Permissions from...")
                        .command(() -> onCopyPermissions.accept(userRef)));
            }

            return builder.build();
        }
    }

    private static String buildMenuItemText(final String screenName) {
        // The menu name gives the user/group, so only the screen is needed
        return "Show in " + screenName;
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
            // Quoted, as a display name may contain spaces
            return UserFields.FIELD_DISPLAY_NAME + ":" + quoteFilterValue(userRef.getDisplayName());
        } else {
            return "";
        }
    }

    // Quotes a quick filter value so that it is read as one value, whatever it contains, escaping
    // any backslashes and double quotes, as QuickFilterPrinter does.
    private static String quoteFilterValue(final String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

}
