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

package stroom.security.client.component;

import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.FormGroup;
import stroom.widget.form.client.TextBox;
import stroom.widget.tickbox.client.TickBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Consumer;

/// The Info tab of a user's or group's screen: its names and unique identifier, read only, and
/// for a user whether they may sign in.
public class UserInfoTab extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

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
    TickBox isEnabledTickBox;
    @UiField
    FormGroup enabledFormGroup;

    /// Makes the tab, showing no user.
    public UserInfoTab() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// Shows a user's or group's details; a group has no names or enabled state, and its
    /// identifier is its name.
    ///
    /// @param userRef The user or group, or `null` for an unknown one, shown as a user with no
    ///                details.
    public void setUserRef(final UserRef userRef) {
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

    /// @param onEnabledChange Given whether the Enabled box is now ticked, when the user presses it.
    public void setOnEnabledChange(final Consumer<Boolean> onEnabledChange) {
        isEnabledTickBox.setOnChange(onEnabledChange);
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        // The names and identifier are always read only
        isEnabledTickBox.setEnabled(!readOnly);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, UserInfoTab> {

    }
}
