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

package stroom.security.client.presenter;

import stroom.content.client.presenter.ContentTabPresenter;
import stroom.security.client.UsersAndGroupsPlugin;
import stroom.security.client.component.UserAndGroupsController;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.UserRef;
import stroom.widget.datagrid.client.WrapperView;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// The User Groups screen ([UserAndGroupsController]) in a main content tab of its own, until
/// content tabs can show controllers.
public class UserAndGroupsPresenter extends ContentTabPresenter<WrapperView> {

    public static final String TAB_TYPE = "UsersAndGroups";

    private final UserAndGroupsController userAndGroups;

    /// Made by GIN.
    ///
    /// @param eventBus      The application's event bus.
    /// @param view          Shows the screen.
    /// @param userAndGroups The screen.
    @Inject
    public UserAndGroupsPresenter(final EventBus eventBus,
                                  final WrapperView view,
                                  final UserAndGroupsController userAndGroups) {
        super(eventBus, view);
        this.userAndGroups = own(userAndGroups);
        view.setView(userAndGroups);
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
