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

import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.docref.StringMatch;
import stroom.security.shared.DocPermissionResource;
import stroom.security.shared.FetchDocumentUsersRequest;
import stroom.security.shared.User;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.ResultPage;

import com.google.gwt.core.client.GWT;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.function.Consumer;

public class DocumentUserListPresenter extends AbstractUserListPresenter {

    private static final DocPermissionResource DOC_PERMISSION_RESOURCE = GWT.create(DocPermissionResource.class);

    private final FetchDocumentUsersRequest criteria;

    @Inject
    public DocumentUserListPresenter(final EventBus eventBus,
                                     final UserListView userListView,
                                     final PagerView pagerView,
                                     final RestFactory restFactory,
                                     final UiConfigCache uiConfigCache) {
        super(eventBus, userListView, pagerView, uiConfigCache);

        criteria = new FetchDocumentUsersRequest();
        RestDataProvider<User, ResultPage<User>> dataProvider = new RestDataProvider<User, ResultPage<User>>(
                eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<User>> dataConsumer,
                                final RestErrorHandler errorHandler) {
                CriteriaUtil.setRange(criteria, range);
                restFactory
                        .create(DOC_PERMISSION_RESOURCE)
                        .method(res -> res.fetchDocumentUsers(criteria))
                        .onSuccess(dataConsumer)
                        .onFailure(errorHandler)
                        .taskListener(pagerView)
                        .exec();
            }
        };
        dataProvider.addDataDisplay(getDataGrid());
    }

    @Override
    public void changeNameFilter(String name) {
        if (name != null) {
            name = name.trim();
            if (name.isEmpty()) {
                name = null;
            }
        }

        StringMatch stringMatch = criteria.getStringMatch();
        if (stringMatch == null) {
            stringMatch = StringMatch.contains(name);
            criteria.setStringMatch(stringMatch);
            refresh();
        } else if (!Objects.equals(name, stringMatch.getPattern())) {
            stringMatch = StringMatch.contains(name);
            criteria.setStringMatch(stringMatch);
            refresh();
        }
    }

    public void setDocRef(final DocRef docRef) {
        criteria.setDocRef(docRef);
        refresh();
    }
//
//    public void refresh() {
//        super.refresh();
//        if (filter != null && !filter.isEmpty()) {
//            filterUsers(userList);
//            // async update of the grid
//        } else {
//            final List<UserRef> users = userList != null
//                    ? new ArrayList<>(userList)
//                    : Collections.emptyList();
//            updateGrid(users);
//        }
//    }
//
//    private void updateGrid(final List<UserRef> users) {
//        final UserRef selected = getSelectionModel().getSelected();
//        getSelectionModel().clear();
//
//        users.sort(Comparator.comparing(UserRef::getSubjectId));
//
//        getDataGrid().setRowData(0, users);
//        getDataGrid().setRowCount(users.size());
//
//        if (selected != null && users.contains(selected)) {
//            getSelectionModel().setSelected(selected);
//        }
//    }
//
//    private void filterUsers(final List<UserRef> users) {
//        // Convert our users to a simpler object to avoid sending a lot of rich
//        // objects over the network when all we need is to filter on the username
//        final List<UserRef> allSimpleUsers = users != null
//                ? users
//                : Collections.emptyList();
//
//        if (!allSimpleUsers.isEmpty()) {
////            restFactory
////                    .create(DOC_PERMISSION_RESOURCE)
////                    .method(res -> res.filterUsers(new FilterUsersRequest(allSimpleUsers, filter)))
////                    .onSuccess(filteredSimpleUsers -> {
////                        // Map the users back again
////                        final List<User> filteredUsers = filteredSimpleUsers.stream()
////                                .map(simpleUser -> uuidToUserMap.get(simpleUser.getUuid()))
////                                .collect(Collectors.toList());
////                        updateGrid(filteredUsers);
////                    })
////                    .taskListener(pagerView)
////                    .exec();
//        } else {
//            updateGrid(Collections.emptyList());
//        }
//    }
}
