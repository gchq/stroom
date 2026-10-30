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
import stroom.security.client.UsersAndGroupsPlugin;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.UserRef;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// The User Groups screen ([UsersAndGroupsController]) in a main content tab of its own.
public class UsersAndGroupsTabController extends ContentTabController {

    public static final String TAB_TYPE = "UsersAndGroups";

    private final UsersAndGroupsController userAndGroups;

    /// Made by GIN.
    ///
    /// @param eventBus      The application's event bus.
    /// @param userAndGroups The screen.
    @Inject
    public UsersAndGroupsTabController(final EventBus eventBus,
                                       final UsersAndGroupsController userAndGroups) {
        super(eventBus);
        this.userAndGroups = own(userAndGroups);
        initWidget(userAndGroups.asWidget());
    }

    /// Fetches the users and groups again.
    public void refresh() {
        userAndGroups.refresh();
    }

    /// Filters the users and groups to one user.
    ///
    /// @param userRef The user.
    public void showUser(final UserRef userRef) {
        userAndGroups.showUser(userRef);
    }

    @Override
    public SvgImage getIcon() {
        return UsersAndGroupsPlugin.ICON;
    }

    @Override
    public String getLabel() {
        return UsersAndGroupsPlugin.SCREEN_NAME;
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }
}
