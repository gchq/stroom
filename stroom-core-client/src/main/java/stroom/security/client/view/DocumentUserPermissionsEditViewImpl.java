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

import stroom.item.client.SelectionBox;
import stroom.security.client.presenter.ChangeUiHandlers;
import stroom.security.client.presenter.DocumentUserPermissionsEditPresenter.DocumentUserPermissionsEditView;
import stroom.security.shared.DocumentPermission;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public final class DocumentUserPermissionsEditViewImpl
        extends ViewWithUiHandlers<ChangeUiHandlers>
        implements DocumentUserPermissionsEditView {

    private static final String NONE_TITLE = "[ none ]";

    private final Widget widget;

    @UiField
    SelectionBox<DocumentPermission> permission;
    @UiField
    SimplePanel permissions;
    @UiField
    HTML details;

    @Inject
    public DocumentUserPermissionsEditViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        permission.addItems(DocumentPermission.LIST);
        permission.setNonSelectString(NONE_TITLE);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        permission.focus();
    }

    @Override
    public void setPermission(final DocumentPermission permission) {
        this.permission.setValue(permission);
    }

    @Override
    public DocumentPermission getPermission() {
        return permission.getValue();
    }

    @Override
    public void setDocumentTypeView(final View view) {
        permissions.setWidget(view.asWidget());
    }

    @Override
    public void setDetails(final SafeHtml details) {
        this.details.setHTML(details);
    }

    @UiHandler("permission")
    public void onPermission(final ValueChangeEvent<DocumentPermission> event) {
        getUiHandlers().onChange();
    }

    public interface Binder extends UiBinder<Widget, DocumentUserPermissionsEditViewImpl> {

    }
}
