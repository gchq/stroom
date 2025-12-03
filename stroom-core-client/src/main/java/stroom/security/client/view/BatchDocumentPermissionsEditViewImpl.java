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

import stroom.docstore.shared.DocumentType;
import stroom.item.client.SelectionBox;
import stroom.security.client.presenter.BatchDocumentPermissionsEditPresenter.BatchDocumentPermissionsEditView;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.DocumentPermissionChange;
import stroom.widget.form.client.FormGroup;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;

import java.util.List;

public final class BatchDocumentPermissionsEditViewImpl
        extends ViewImpl
        implements BatchDocumentPermissionsEditView {

    private static final String NONE_TITLE = "[ none ]";

    private final Widget widget;

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

    @Inject
    public BatchDocumentPermissionsEditViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        permission.addItems(DocumentPermission.LIST);
        permission.setNonSelectString(NONE_TITLE);
        documentPermissionChange.addItems(DocumentPermissionChange.LIST);
        documentPermissionChange.setValue(DocumentPermissionChange.SET_PERMISSION);
        update();
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
            case SET_PERMISSION: {
                userRefLabel.setVisible(true);
                userRef.setVisible(true);
                permissionLabel.setVisible(true);
                permission.setVisible(true);
                break;
            }
//            case REMOVE_PERMISSION: {
//                userRefLabel.setVisible(true);
//                userRef.setVisible(true);
//                break;
//            }
            case ADD_DOCUMENT_CREATE_PERMISSION: {
                userRefLabel.setVisible(true);
                userRef.setVisible(true);
                docTypeLabel.setVisible(true);
                docType.setVisible(true);
                break;
            }
            case REMOVE_DOCUMENT_CREATE_PERMISSION: {
                userRefLabel.setVisible(true);
                userRef.setVisible(true);
                docTypeLabel.setVisible(true);
                docType.setVisible(true);
                break;
            }
            case ADD_ALL_DOCUMENT_CREATE_PERMISSIONS: {
                userRefLabel.setVisible(true);
                userRef.setVisible(true);
                break;
            }
            case REMOVE_ALL_DOCUMENT_CREATE_PERMISSIONS: {
                userRefLabel.setVisible(true);
                userRef.setVisible(true);
                break;
            }
            case ADD_ALL_PERMISSIONS_FROM: {
                docRefLabel.setVisible(true);
                docRef.setVisible(true);
                break;
            }
            case SET_ALL_PERMISSIONS_FROM: {
                docRefLabel.setVisible(true);
                docRef.setVisible(true);
                break;
            }
            case REMOVE_ALL_PERMISSIONS: {
                break;
            }
        }
    }

    @UiHandler("documentPermissionChange")
    public void onDocumentPermissionChange(final ValueChangeEvent<DocumentPermissionChange> e) {
        update();
    }


    public interface Binder extends UiBinder<Widget, BatchDocumentPermissionsEditViewImpl> {

    }
}
