/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.security.client.presenter;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import stroom.query.api.DocRef;
import stroom.security.shared.DocumentPermissions;
import stroom.security.shared.UserRef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class DocumentUserListPresenter extends AbstractUserListPresenter {
    private DocumentPermissions documentPermissions;
    private boolean group;

    private String filter;

    @Inject
    public DocumentUserListPresenter(final EventBus eventBus, final UserListView userListView) {
        super(eventBus, userListView);
    }

//    @Override
//    protected void onBind() {
//        super.onBind();
//        registerHandler(getDataGridView().addDoubleClickHandler(new DoubleClickEvent.Handler() {
//            @Override
//            public void onDoubleClick(DoubleClickEvent event) {
//                if (findUserCriteria != null && findUserCriteria.getRelatedUser() == null) {
//                    HidePopupEvent.fire(DocumentUserListPresenter.this, DocumentUserListPresenter.this, false, true);
//                }
//            }
//        }));
//    }

    @Override
    public void changeNameFilter(String name) {
        if (name != null) {
            name = name.trim();
            if (name.length() == 0) {
                name = null;
            }
        }

        if (name != filter) {
            filter = name;
            refresh();
        }
    }

    public void setDocumentPermissions(final DocumentPermissions documentPermissions, final boolean group) {
        this.documentPermissions = documentPermissions;
        this.group = group;
        refresh();
    }

    public void refresh() {
        final List<UserRef> users = new ArrayList<UserRef>();
        for (final UserRef user : documentPermissions.getUserPermissions().keySet()) {
            if (user.isGroup() == group) {
                if (filter == null) {
                    users.add(user);
                } else if (user.getName().startsWith(filter)) {
                    users.add(user);
                }
            }
        }

        Collections.sort(users, Comparator.comparing(DocRef::getName));

        getDataGridView().setRowData(0, users);
        getDataGridView().setRowCount(users.size());
    }
}
