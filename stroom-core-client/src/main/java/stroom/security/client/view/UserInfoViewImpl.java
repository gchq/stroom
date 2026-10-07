/*
 * Copyright 2024 Crown Copyright
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

import stroom.security.client.presenter.UserInfoPresenter.UserInfoView;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;
import stroom.widget.form.client.FormGroup;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

public class UserInfoViewImpl extends ViewImpl implements UserInfoView {

    private final Widget widget;

    @UiField
    FormGroup displayNameFormGroup;
    @UiField
    TextBox displayName;
    @UiField
    FormGroup subjectIdFormGroup;
    @UiField
    TextBox subjectId;
    @UiField
    FormGroup fullNameFormGroup;
    @UiField
    TextBox fullName;
    @UiField
    CustomCheckBox isEnabledTickBox;
    @UiField
    FormGroup enabledFormGroup;

    private UserRef userRef;

    @Inject
    public UserInfoViewImpl(final Binder binder) {
        this.widget = binder.createAndBindUi(this);

        // All for display only
        displayName.setReadOnly(true);
        subjectId.setReadOnly(true);
        fullName.setReadOnly(true);
    }

    @Override
    public void setUserRef(final UserRef userRef) {
        this.userRef = userRef;
        // No user (e.g. an unknown one) is shown as a user with no details
        final boolean isUser = userRef == null || userRef.isUser();

        enabledFormGroup.setVisible(isUser);
        fullNameFormGroup.setVisible(isUser);
        displayNameFormGroup.setVisible(isUser);

        subjectIdFormGroup.setLabel(isUser
                ? "Unique Identifier"
                : "Group Name");
        subjectIdFormGroup.setHelpText(isUser
                ? null
                // helpHTML element in ui.xml will be used instead
                : "The name of the group."); // Overrides the helpHTML element
        subjectIdFormGroup.setScreenReaderText(isUser
                ? "Read only. The user's ID in the identity provider."
                : "Read only.");
        subjectId.setText(NullSafe.get(userRef, UserRef::getSubjectId));
        displayName.setText(NullSafe.get(userRef, UserRef::getDisplayName));
        fullName.setText(NullSafe.get(userRef, UserRef::getFullName));
        isEnabledTickBox.setValue(userRef != null && userRef.isEnabled());
    }

    @Override
    public void setReadOnly(final boolean isReadOnly) {
        isEnabledTickBox.setEnabled(isReadOnly);
//        displayName.setReadOnly(isReadOnly);
//        subjectId.setReadOnly(isReadOnly);
//        fullName.setReadOnly(isReadOnly);
    }

    @Override
    public CustomCheckBox getIsEnabledTickBox() {
        return isEnabledTickBox;
    }

    @Override
    public Widget asWidget() {
        return widget;
    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, UserInfoViewImpl> {

    }
}
