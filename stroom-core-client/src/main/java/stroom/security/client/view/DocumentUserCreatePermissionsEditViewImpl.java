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

import stroom.docref.DocRef;
import stroom.security.client.presenter.DocumentUserCreatePermissionsEditPresenter.DocumentUserCreatePermissionsEditView;
import stroom.security.client.presenter.DocumentUserCreatePermissionsEditUiHandler;
import stroom.security.client.presenter.DocumentUserPermissionsEditUiHandler;
import stroom.util.shared.UserRef;
import stroom.widget.button.client.Button;
import stroom.widget.form.client.FormGroup;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public final class DocumentUserCreatePermissionsEditViewImpl
        extends ViewWithUiHandlers<DocumentUserCreatePermissionsEditUiHandler>
        implements DocumentUserCreatePermissionsEditView {

    private final Widget widget;

    @UiField
    FormGroup userFormGroup;
    @UiField
    Label document;
    @UiField
    Label user;
    @UiField
    SimplePanel createPermissions;
    @UiField
    Button applyPermissionToDescendants;

    @Inject
    public DocumentUserCreatePermissionsEditViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
//        createPermissions.setFocus(true);
    }

    @Override
    public void setDocument(final DocRef docRef) {
        this.document.setText(docRef.getName());
    }

    @Override
    public void setUser(final UserRef userRef) {
        if (userRef.isGroup()) {
            userFormGroup.setLabel("User Group");
        } else {
            userFormGroup.setLabel("User");
        }
        this.user.setText(userRef.getDisplayName());
    }

    @Override
    public void setDocumentTypeView(final View view) {
        createPermissions.setWidget(view.asWidget());
    }

    @UiHandler("applyPermissionToDescendants")
    void onApplyPermissionToDescendants(final ClickEvent e) {
        getUiHandlers().onApplyToDescendants(applyPermissionToDescendants);
    }

    public interface Binder extends UiBinder<Widget, DocumentUserCreatePermissionsEditViewImpl> {

    }
}
