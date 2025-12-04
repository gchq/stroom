/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.security.client.view;

import stroom.item.client.SelectionBox;
import stroom.security.client.presenter.AppPermissionsPresenter.AppPermissionsView;
import stroom.security.shared.PermissionShowLevel;
import stroom.util.shared.UserRef;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;

public final class AppPermissionsViewImpl
        extends ViewImpl
        implements AppPermissionsView {

    private static final String APP_PERM_BASE_LABEL = "Application Permissions";
    private static final String APP_PERM_DETAILS_BASE_LABEL = "Application Permission Details";
    private final Widget widget;

    @UiField
    SelectionBox<PermissionShowLevel> permissionVisibility;
    @UiField
    SimplePanel appUserPermissionsList;

    @UiField
    SimplePanel appPermissionsEdit;

//    @UiField
//    FormGroup detailsFormGroup;
//    @UiField
//    HTML details;

    @Inject
    public AppPermissionsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        setUserRef(null);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public SelectionBox<PermissionShowLevel> getPermissionVisibility() {
        return permissionVisibility;
    }

    @Override
    public void setAppUserPermissionListView(final View view) {
        appUserPermissionsList.setWidget(view.asWidget());
    }

    @Override
    public void setAppPermissionsEditView(final View view) {
        appPermissionsEdit.setWidget(view.asWidget());
    }

//    @Override
//    public void setDetails(final SafeHtml details) {
//        appPermissionsEdit.serDetails(details);
//    }

    public void setUserRef(final UserRef userRef) {

    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, AppPermissionsViewImpl> {

    }
}
