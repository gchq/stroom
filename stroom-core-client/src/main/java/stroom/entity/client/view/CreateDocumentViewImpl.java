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
 *
 */

package stroom.entity.client.view;

import stroom.entity.client.presenter.CreateDocumentPresenter.CreateDocumentView;
import stroom.explorer.shared.PermissionInheritance;
import stroom.item.client.ItemListBox;
import stroom.widget.popup.client.view.HideRequest;
import stroom.widget.popup.client.view.HideRequestUiHandlers;

import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class CreateDocumentViewImpl extends ViewWithUiHandlers<HideRequestUiHandlers> implements CreateDocumentView {

    private final Widget widget;
    @UiField
    SimplePanel foldersInner;
    @UiField
    TextBox name;
    @UiField
    ItemListBox<PermissionInheritance> permissionInheritance;

    @Inject
    public CreateDocumentViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        widget.addAttachHandler(event -> focus());

        permissionInheritance.addItem(PermissionInheritance.NONE);
        permissionInheritance.addItem(PermissionInheritance.DESTINATION);
        permissionInheritance.setSelectedItem(PermissionInheritance.DESTINATION);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public String getName() {
        return name.getText();
    }

    @Override
    public void setName(final String name) {
        this.name.setText(name);
    }

    @Override
    public void setFolderView(final View view) {
        foldersInner.setWidget(view.asWidget());
    }

    @Override
    public void setFoldersVisible(final boolean visible) {
        foldersInner.setVisible(visible);
    }

    @Override
    public PermissionInheritance getPermissionInheritance() {
        return permissionInheritance.getSelectedItem();
    }

    @Override
    public void setPermissionInheritance(final PermissionInheritance permissionInheritance) {
        this.permissionInheritance.setSelectedItem(permissionInheritance);
    }

    @UiHandler("name")
    void onKeyDown(final KeyDownEvent event) {
        if (event.getNativeKeyCode() == '\r') {
            getUiHandlers().hideRequest(new HideRequest(false, true));
        }
    }

    @Override
    public void focus() {
        name.setFocus(true);
    }

    public interface Binder extends UiBinder<Widget, CreateDocumentViewImpl> {

    }
}
