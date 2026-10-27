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
import stroom.docref.DocRef;
import stroom.security.client.component.DocumentUserPermissionsTabController;
import stroom.svg.shared.SvgImage;
import stroom.widget.datagrid.client.WrapperView;

import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Inject;

/// A document's permissions ([DocumentUserPermissionsTabController]) in a main content tab of its
/// own, until content tabs can show controllers.
public class DocumentUserPermissionsPresenter extends ContentTabPresenter<WrapperView> {

    private final DocumentUserPermissionsTabController permissions;
    private DocRef docRef;

    /// Made by GIN.
    ///
    /// @param eventBus    The application's event bus.
    /// @param view        Shows the permissions.
    /// @param permissions The permissions.
    @Inject
    public DocumentUserPermissionsPresenter(final EventBus eventBus,
                                            final WrapperView view,
                                            final DocumentUserPermissionsTabController permissions) {
        super(eventBus, view);
        this.permissions = own(permissions);
        view.setView(permissions);
    }

    /// Lists the permissions on a document.
    ///
    /// @param docRef The document.
    public void setDocRef(final DocRef docRef) {
        this.docRef = docRef;
        permissions.setDocRef(docRef);
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.LOCKED;
    }

    @Override
    public String getLabel() {
        return "Permissions For '" + docRef.getDisplayValue() + "'";
    }

    @Override
    public String getType() {
        return "DocumentPermissions";
    }
}
