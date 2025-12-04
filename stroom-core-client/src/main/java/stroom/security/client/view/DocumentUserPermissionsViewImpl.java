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
import stroom.security.client.presenter.DocumentUserPermissionsPresenter.DocumentUserPermissionsView;
import stroom.security.shared.PermissionShowLevel;
import stroom.util.shared.UserRef;
import stroom.util.shared.string.CaseType;
import stroom.widget.form.client.FormGroup;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;

public final class DocumentUserPermissionsViewImpl
        extends ViewImpl
        implements DocumentUserPermissionsView {

    private static final String DOC_PERM_DETAILS_BASE_LABEL = "Document Permission Details";

    private final Widget widget;

    @UiField
    SelectionBox<PermissionShowLevel> permissionVisibility;
    @UiField
    SimplePanel docUserPermissionsList;
    @UiField
    FormGroup detailsFormGroup;
    @UiField
    HTML details;

    @Inject
    public DocumentUserPermissionsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setDocUserPermissionListView(final View view) {
        docUserPermissionsList.setWidget(view.asWidget());
    }

    @Override
    public void setDetails(final SafeHtml details) {
        this.details.setHTML(details);
    }

    @Override
    public void setUserRef(final UserRef userRef) {
        if (userRef == null) {
            detailsFormGroup.setLabel(DOC_PERM_DETAILS_BASE_LABEL + ":");
        } else {
            final String suffix = " for " + userRef.getType(CaseType.LOWER)
                                  + " \"" + userRef.getDisplayName() + "\":";
            detailsFormGroup.setLabel(DOC_PERM_DETAILS_BASE_LABEL + suffix);
        }
    }

    @Override
    public SelectionBox<PermissionShowLevel> getPermissionVisibility() {
        return permissionVisibility;
    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, DocumentUserPermissionsViewImpl> {

    }
}
