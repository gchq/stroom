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
import stroom.dispatch.client.RestFactory;
import stroom.security.shared.DocPermissionResource;
import stroom.security.shared.FilterUsersRequest;
import stroom.security.shared.User;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.UserName;

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
    private boolean isGroup = true;

    @Inject
    public DocumentUserListPresenter(final EventBus eventBus,
                                     final UserListView userListView,
                                     final PagerView pagerView,
                                     final RestFactory restFactory,
                                     final UiConfigCache uiConfigCache) {
        super(eventBus, userListView, pagerView, uiConfigCache);
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

    public void setDocumentPermissions(final List<User> userList, final boolean isGroup) {

//        GWT.log("Setting "
//                + (isGroup
//                ? "group"
//                : "user") + " list:"
//                + userList.stream().map(User::getSubjectId).collect(Collectors.joining(", ")));
//
        this.isGroup = isGroup;
        this.uuidToUserMap = userList == null
                ? Collections.emptyMap()
                : userList.stream()
                        .collect(Collectors.toMap(User::getUuid, Function.identity()));

        this.userList = userList;
        refresh();
    }

    @Override
    public boolean includeAdditionalUserInfo() {
        return !isGroup;
    }

    public void refresh() {
        super.refresh();
        if (filter != null && !filter.isEmpty()) {
            filterUsers(userList);
            // async update of the grid
        } else {
            final List<User> users = userList != null
                    ? new ArrayList<>(userList)
                    : Collections.emptyList();
            updateGrid(users);
        }
    }

    private void updateGrid(final List<User> users) {
        final User selected = getSelectionModel().getSelected();
        getSelectionModel().clear();

        users.sort(Comparator.comparing(User::getSubjectId));

        getDataGrid().setRowData(0, users);
        getDataGrid().setRowCount(users.size());

        if (selected != null && users.contains(selected)) {
            getSelectionModel().setSelected(selected);
        }
    }

    private void filterUsers(final List<User> users) {
        // Convert our users to a simpler object to avoid sending a lot of rich
        // objects over the network when all we need is to filter on the username
        final List<UserName> allSimpleUsers = users != null
                ? users.stream()
                .map(User::asUserName)
                .collect(Collectors.toList())
                : Collections.emptyList();

        if (!allSimpleUsers.isEmpty()) {
            restFactory
                    .create(DOC_PERMISSION_RESOURCE)
                    .method(res -> res.filterUsers(new FilterUsersRequest(allSimpleUsers, filter)))
                    .onSuccess(filteredSimpleUsers -> {
                        // Map the users back again
                        final List<User> filteredUsers = filteredSimpleUsers.stream()
                                .map(simpleUser -> uuidToUserMap.get(simpleUser.getUuid()))
                                .collect(Collectors.toList());
                        updateGrid(filteredUsers);
                    })
                    .taskHandlerFactory(pagerView)
                    .exec();
        } else {
            updateGrid(Collections.emptyList());
        }
    }
}
