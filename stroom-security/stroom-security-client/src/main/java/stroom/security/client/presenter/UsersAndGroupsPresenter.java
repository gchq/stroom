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

import stroom.widget.tab.client.presenter.LinkTabsLayoutView;
import stroom.widget.tab.client.presenter.LinkTabsPresenter;
import stroom.widget.tab.client.presenter.TabData;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Provider;

public class UsersAndGroupsPresenter extends LinkTabsPresenter {

    private final UsersAndGroupsTabPresenter usersPresenter;
    private final UsersAndGroupsTabPresenter groupsPresenter;
    private final TabData users;
    private final TabData groups;

    @Inject
    public UsersAndGroupsPresenter(final EventBus eventBus, final LinkTabsLayoutView view,
                                   final Provider<UsersAndGroupsTabPresenter> usersAndGroupsTabPresenterProvider) {
        super(eventBus, view);

        getWidget().getElement().addClassName("default-min-sizes");

        usersPresenter = usersAndGroupsTabPresenterProvider.get();
        groupsPresenter = usersAndGroupsTabPresenterProvider.get();

        usersPresenter.setGroup(false);
        groupsPresenter.setGroup(true);

        users = addTab("Users", usersPresenter);
        groups = addTab("Groups", groupsPresenter);

        changeSelectedTab(users);
    }

    public void focus() {
        changeSelectedTab(users);
        usersPresenter.getView().focus();
    }
}
