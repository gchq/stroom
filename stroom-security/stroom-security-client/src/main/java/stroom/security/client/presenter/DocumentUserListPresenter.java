/*
 * Copyright 2017 Crown Copyright
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

import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.security.shared.DocPermissionResource;
import stroom.security.shared.FilterUsersRequest;
import stroom.security.shared.SimpleUser;
import stroom.security.shared.User;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DocumentUserListPresenter extends AbstractUserListPresenter {

    private static final DocPermissionResource DOC_PERMISSION_RESOURCE = GWT.create(DocPermissionResource.class);

    private List<User> userList;
    private Map<String, User> uuidToUserMap;

    private String filter;
    private final RestFactory restFactory;

    @Inject
    public DocumentUserListPresenter(final EventBus eventBus,
                                     final UserListView userListView,
                                     final PagerView pagerView,
                                     final RestFactory restFactory) {
        super(eventBus, userListView, pagerView);
        this.restFactory = restFactory;
    }

    @Override
    public void changeNameFilter(String name) {
        if (name != null) {
            name = name.trim();
            if (name.length() == 0) {
                name = null;
            }
        }

        if (!Objects.equals(name, filter)) {
            filter = name;
            refresh();
        }
    }

    public void setDocumentPermissions(final List<User> userList) {

        this.uuidToUserMap = userList == null
                ? Collections.emptyMap()
                : userList.stream()
                        .collect(Collectors.toMap(User::getUuid, Function.identity()));

        this.userList = userList;
        refresh();
    }

    public void refresh() {
        if (filter != null && !filter.isEmpty()) {
            filterUsers(userList);
            // async update of the grid
        } else {
            final List<User> users = new ArrayList<>(userList);
            updateGrid(users);
        }
    }

    private void updateGrid(final List<User> users) {
        final User selected = getSelectionModel().getSelected();
        getSelectionModel().clear();

        users.sort(Comparator.comparing(User::getName));

        getDataGrid().setRowData(0, users);
        getDataGrid().setRowCount(users.size());

        if (selected != null && users.contains(selected)) {
            getSelectionModel().setSelected(selected);
        }
    }

    private void filterUsers(final List<User> users) {
        final Rest<List<SimpleUser>> rest = restFactory.create();

        // Convert our users to a simpler object to avoid sending a lot of rich
        // objects over the network when all we need is to filter on the user name
        final List<SimpleUser> allSimpleUsers = users.stream()
                .map(SimpleUser::new)
                .collect(Collectors.toList());

        rest
                .onSuccess(filteredSimpleUsers -> {
                    // Map the users back again
                    final List<User> filteredUsers = filteredSimpleUsers.stream()
                            .map(simpleUser -> uuidToUserMap.get(simpleUser.getUuid()))
                            .collect(Collectors.toList());
                    updateGrid(filteredUsers);
                })
                .call(DOC_PERMISSION_RESOURCE)
                .filterUsers(new FilterUsersRequest(allSimpleUsers, filter));
    }

}
