/*
 * Copyright 2016 Crown Copyright
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

import stroom.widget.tab.client.presenter.SlideTabLayoutView;
import stroom.widget.tab.client.presenter.TabData;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Provider;

public class UsersAndGroupsPresenter extends SlideTabPresenter {
    @Inject
    public UsersAndGroupsPresenter(final EventBus eventBus, final SlideTabLayoutView view,
                                   final Provider<UsersAndGroupsTabPresenter> usersAndGroupsTabPresenterProvider) {
        super(eventBus, view);

        final UsersAndGroupsTabPresenter usersPresenter = usersAndGroupsTabPresenterProvider.get();
        final UsersAndGroupsTabPresenter groupsPresenter = usersAndGroupsTabPresenterProvider.get();

        usersPresenter.setGroup(false);
        groupsPresenter.setGroup(true);

        final TabData users = addTab("Users", usersPresenter);
        final TabData groups = addTab("Groups", groupsPresenter);

        changeSelectedTab(users);
    }
}
