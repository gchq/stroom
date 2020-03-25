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

import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;
import stroom.security.shared.Changes;
import stroom.security.shared.DocumentPermissions;
import stroom.security.shared.User;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FolderPermissionsTabPresenter
        extends DocumentPermissionsTabPresenter {
    private final PermissionsListPresenter createPermissionsListPresenter;

    @Inject
    public FolderPermissionsTabPresenter(final EventBus eventBus, final FolderPermissionsTabView view,
                                         final DocumentUserListPresenter userListPresenter, final PermissionsListPresenter permissionsListPresenter, final PermissionsListPresenter createPermissionsListPresenter,
                                         final Provider<AdvancedUserListPresenter> selectUserPresenterProvider) {
        super(eventBus, view, userListPresenter, permissionsListPresenter, selectUserPresenterProvider);
        this.createPermissionsListPresenter = createPermissionsListPresenter;
        view.setCreatePermissionsView(createPermissionsListPresenter.getView());
    }

    @Override
    protected void setCurrentUser(final User user) {
        super.setCurrentUser(user);

        createPermissionsListPresenter.setCurrentUser(user);
    }

    @Override
    public void setDocumentPermissions(final List<String> allPermissions, final DocumentPermissions documentPermissions, final boolean group, final Changes changes) {
        super.setDocumentPermissions(allPermissions, documentPermissions, group, changes);

        final List<String> permissions = new ArrayList<>();
        for (final String permission : allPermissions) {
            if (permission.startsWith("Create")) {
                permissions.add(permission);
            }
        }

        createPermissionsListPresenter.setDocumentPermissions(documentPermissions, permissions, changes);
    }

    public interface FolderPermissionsTabView extends DocumentPermissionsTabView {
        void setCreatePermissionsView(View view);
    }
}
