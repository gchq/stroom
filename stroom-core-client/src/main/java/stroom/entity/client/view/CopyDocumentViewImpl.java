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

package stroom.entity.client.view;

import stroom.entity.client.presenter.CopyDocumentPresenter.CopyDocumentView;
import stroom.explorer.shared.PermissionInheritance;
import stroom.item.client.SelectionBox;
import stroom.widget.form.client.FormGroup;
import stroom.widget.popup.client.view.HideRequestUiHandlers;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class CopyDocumentViewImpl extends ViewWithUiHandlers<HideRequestUiHandlers> implements CopyDocumentView {

    private final Widget widget;
    @UiField
    SimplePanel foldersInner;
    @UiField
    FormGroup nameFormGroup;
    @UiField
    TextBox name;
    @UiField
    SelectionBox<PermissionInheritance> permissionInheritance;

    @Inject
    public CopyDocumentViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        permissionInheritance.addItem(PermissionInheritance.NONE);
        permissionInheritance.addItem(PermissionInheritance.SOURCE);
        permissionInheritance.addItem(PermissionInheritance.DESTINATION);
        permissionInheritance.addItem(PermissionInheritance.COMBINED);
        permissionInheritance.setValue(PermissionInheritance.DESTINATION);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public String getName() {
        return this.name.getValue();
    }

    @Override
    public void setName(final String name) {
        this.name.setValue(name);
    }

    @Override
    public void setNameVisible(final boolean visible) {
        this.nameFormGroup.setVisible(visible);
    }

    @Override
    public void setFolderView(final View view) {
        view.asWidget().setWidth("100%");
        view.asWidget().setHeight("100%");
        foldersInner.setWidget(view.asWidget());
    }

    @Override
    public PermissionInheritance getPermissionInheritance() {
        return permissionInheritance.getValue();
    }

    @Override
    public void setPermissionInheritance(final PermissionInheritance permissionInheritance) {
        this.permissionInheritance.setValue(permissionInheritance);
    }

    public interface Binder extends UiBinder<Widget, CopyDocumentViewImpl> {

    }
}
