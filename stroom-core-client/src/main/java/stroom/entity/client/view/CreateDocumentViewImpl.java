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

package stroom.entity.client.view;

import stroom.entity.client.presenter.CreateDocumentPresenter.CreateDocumentView;
import stroom.explorer.client.component.EntityTree;
import stroom.explorer.shared.PermissionInheritance;
import stroom.item.client.SelectionBox;
import stroom.widget.form.client.FormGroup;
import stroom.widget.form.client.TextInput;
import stroom.widget.popup.client.view.DialogAction;
import stroom.widget.popup.client.view.DialogActionUiHandlers;
import stroom.widget.presenter.client.ViewWithUiHandlers;

import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

public class CreateDocumentViewImpl extends ViewWithUiHandlers<DialogActionUiHandlers> implements CreateDocumentView {

    private final Widget widget;
    @UiField
    EntityTree folders;
    @UiField
    FormGroup nameGroup;
    @UiField
    TextInput name;
    @UiField
    SelectionBox<PermissionInheritance> permissionInheritance;

    @Inject
    public CreateDocumentViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        widget.addAttachHandler(event -> focus());

        permissionInheritance.addItem(PermissionInheritance.NONE);
        permissionInheritance.addItem(PermissionInheritance.DESTINATION);
        permissionInheritance.setValue(PermissionInheritance.DESTINATION);
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
    public EntityTree getFolders() {
        return folders;
    }

    @Override
    public PermissionInheritance getPermissionInheritance() {
        return permissionInheritance.getValue();
    }

    @Override
    public void setPermissionInheritance(final PermissionInheritance permissionInheritance) {
        this.permissionInheritance.setValue(permissionInheritance);
    }

    @Override
    public void setNameInvalid(final String message) {
        nameGroup.setFeedback(message);
        name.setFocus(true);
    }

    @Override
    public void setNameValid() {
        nameGroup.clearFeedback();
    }

    @UiHandler("name")
    void onKeyDown(final KeyDownEvent event) {
        if (event.getNativeKeyCode() == '\r') {
            getUiHandlers().onDialogAction(DialogAction.OK);
        }
    }

    @UiHandler("name")
    void onKeyUp(final KeyUpEvent event) {
        // Once a name has been typed the feedback no longer applies
        if (nameGroup.getFeedback() != null && !name.getText().trim().isEmpty()) {
            nameGroup.clearFeedback();
        }
    }

    @Override
    public void focus() {
        name.setFocus(true);
    }

    public interface Binder extends UiBinder<Widget, CreateDocumentViewImpl> {

    }
}
