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

package stroom.security.client.view;

import stroom.explorer.shared.DocumentType;
import stroom.item.client.SelectionBox;
import stroom.security.client.presenter.DocumentPermissionsEditPresenter.DocumentPermissionsEditView;
import stroom.security.client.presenter.DocumentPermissionsEditUiHandlers;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.DocumentPermissionChange;
import stroom.widget.button.client.Button;
import stroom.widget.form.client.FormGroup;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.List;

public final class DocumentPermissionsEditViewImpl
        extends ViewWithUiHandlers<DocumentPermissionsEditUiHandlers>
        implements DocumentPermissionsEditView {

    private static final String NONE_TITLE = "[ none ]";

    private final Widget widget;

    @UiField
    SimplePanel docList;
    @UiField
    SelectionBox<DocumentPermissionChange> documentPermissionChange;

    @UiField
    FormGroup userRefLabel;
    @UiField
    FormGroup docRefLabel;
    @UiField
    FormGroup permissionLabel;
    @UiField
    FormGroup docTypeLabel;

    @UiField
    SimplePanel userRef;
    @UiField
    SimplePanel docRef;
    @UiField
    SelectionBox<DocumentPermission> permission;
    @UiField
    SelectionBox<DocumentType> docType;
    @UiField
    Button apply;

    @Inject
    public DocumentPermissionsEditViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        permission.addItems(DocumentPermission.LIST);
        documentPermissionChange.addItems(DocumentPermissionChange.LIST);
        documentPermissionChange.setValue(DocumentPermissionChange.SET_PERMSSION);
        update();
        apply.setEnabled(false);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        documentPermissionChange.focus();
    }

    @Override
    public void setDocList(final View view) {
        docList.setWidget(view.asWidget());
    }

    @Override
    public DocumentPermissionChange getChange() {
        return documentPermissionChange.getValue();
    }

    @Override
    public void setUserRefSelection(final View view) {
        userRef.setWidget(view.asWidget());
    }

    @Override
    public void setDocRefSelection(final View view) {
        docRef.setWidget(view.asWidget());
    }

    @Override
    public void setDocTypes(final List<DocumentType> docTypes) {
        docType.addItems(docTypes);
    }

    @Override
    public DocumentType getDocType() {
        return docType.getValue();
    }

    @Override
    public DocumentPermission getPermission() {
        return permission.getValue();
    }

    private void update() {
        userRefLabel.setVisible(false);
        userRef.setVisible(false);
        docRefLabel.setVisible(false);
        docRef.setVisible(false);
        permissionLabel.setVisible(false);
        permission.setVisible(false);
        docTypeLabel.setVisible(false);
        docType.setVisible(false);

        switch (documentPermissionChange.getValue()) {
            case SET_PERMSSION: {
                userRefLabel.setVisible(true);
                userRef.setVisible(true);
                permissionLabel.setVisible(true);
                permission.setVisible(true);
                break;
            }
            case REMOVE_PERMISSION: {
                userRefLabel.setVisible(true);
                userRef.setVisible(true);
                break;
            }

            case ADD_DOCUMENT_CREATE_PERMSSION: {
                userRefLabel.setVisible(true);
                userRef.setVisible(true);
                docTypeLabel.setVisible(true);
                docType.setVisible(true);
                break;
            }
            case REMOVE_DOCUMENT_CREATE_PERMSSION: {
                userRefLabel.setVisible(true);
                userRef.setVisible(true);
                docTypeLabel.setVisible(true);
                docType.setVisible(true);
                break;
            }
            case ADD_ALL_DOCUMENT_CREATE_PERMSSIONS: {
                userRefLabel.setVisible(true);
                userRef.setVisible(true);
                break;
            }
            case REMOVE_ALL_DOCUMENT_CREATE_PERMSSIONS: {
                userRefLabel.setVisible(true);
                userRef.setVisible(true);
                break;
            }

            case ADD_ALL_PERMSSIONS_FROM: {
                docRefLabel.setVisible(true);
                docRef.setVisible(true);
                break;
            }
            case SET_ALL_PERMSSIONS_FROM: {
                docRefLabel.setVisible(true);
                docRef.setVisible(true);
                break;
            }

            case REMOVE_ALL_PERMISSIONS: {
                break;
            }
        }
    }

    @Override
    public void setApplyEnabled(final boolean enabled) {
        apply.setEnabled(enabled);
    }

    @UiHandler("documentPermissionChange")
    public void onDocumentPermissionChange(final ValueChangeEvent<DocumentPermissionChange> e) {
        update();
    }

    @UiHandler("permission")
    public void onPermissionChange(final ValueChangeEvent<DocumentPermission> e) {
        getUiHandlers().validate();
    }

    @UiHandler("docType")
    public void onDocTypeChange(final ValueChangeEvent<DocumentType> e) {
        getUiHandlers().validate();
    }

    @UiHandler("apply")
    public void onApply(final ClickEvent e) {
        getUiHandlers().apply(apply);
    }

    public interface Binder extends UiBinder<Widget, DocumentPermissionsEditViewImpl> {

    }
}
