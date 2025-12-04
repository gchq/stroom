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
import stroom.security.client.presenter.UserPermissionReportPresenter.UserPermissionReportView;
import stroom.security.shared.PermissionShowLevel;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;

public final class UserPermissionReportViewImpl
        extends ViewImpl
        implements UserPermissionReportView {

    private final Widget widget;

    @UiField
    SelectionBox<PermissionShowLevel> permissionVisibility;
    @UiField
    SimplePanel permissionsList;

    @Inject
    public UserPermissionReportViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
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
    public void setPermissionListView(final View view) {
        permissionsList.setWidget(view.asWidget());
    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, UserPermissionReportViewImpl> {

    }
}
