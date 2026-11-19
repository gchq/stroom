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

package stroom.security.identity.client.component;

import stroom.widget.button.client.Button;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.FormGroup;
import stroom.widget.form.client.TextArea;
import stroom.widget.form.client.TextBox;
import stroom.widget.text.client.Label;
import stroom.widget.tickbox.client.TickBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.Objects;

/// An internal identity provider account: its user id, email, names and comments, whether it
/// expires and is enabled, its sign-in lock and activity, and a button to set its password.
public class AccountEditor extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    TextBox userId;
    @UiField
    TextBox email;
    @UiField
    TextBox firstName;
    @UiField
    TextBox lastName;
    @UiField
    TextArea comments;
    @UiField
    TickBox neverExpires;
    @UiField
    FormGroup enabledGroup;
    @UiField
    TickBox enabled;
    @UiField
    FormGroup inactiveGroup;
    @UiField
    Label activityState;
    @UiField
    Button reactivate;
    @UiField
    FormGroup lockedGroup;
    @UiField
    Label lockState;
    @UiField
    Button unlock;
    @UiField
    Button changePassword;

    private Runnable onChangePassword = () -> {
    };
    private Runnable onUnlock = () -> {
    };
    private Runnable onReactivate = () -> {
    };

    /// Makes an empty account.
    public AccountEditor() {
        initWidget(BINDER.createAndBindUi(this));
        changePassword.setOnClick(this::onChangePassword);
        unlock.setOnClick(this::onUnlock);
        reactivate.setOnClick(this::onReactivate);
    }

    /// Focuses the user id.
    public void focus() {
        userId.setFocus(true);
    }

    /// @param userId The name the user signs in with.
    public void setUserId(final String userId) {
        this.userId.setValue(userId);
    }

    /// @return The name the user signs in with, as entered.
    public String getUserId() {
        return userId.getValue();
    }

    /// @param email The account's email address.
    public void setEmail(final String email) {
        this.email.setValue(email);
    }

    /// @return The account's email address, as entered.
    public String getEmail() {
        return email.getValue();
    }

    /// @param firstName The user's first name.
    public void setFirstName(final String firstName) {
        this.firstName.setValue(firstName);
    }

    /// @return The user's first name, as entered.
    public String getFirstName() {
        return firstName.getValue();
    }

    /// @param lastName The user's last name.
    public void setLastName(final String lastName) {
        this.lastName.setValue(lastName);
    }

    /// @return The user's last name, as entered.
    public String getLastName() {
        return lastName.getValue();
    }

    /// @param comments Notes about the account, for administrators.
    public void setComments(final String comments) {
        this.comments.setValue(comments);
    }

    /// @return Notes about the account, as entered.
    public String getComments() {
        return comments.getValue();
    }

    /// @param neverExpires Whether the account is never made inactive when unused.
    public void setNeverExpires(final boolean neverExpires) {
        this.neverExpires.setValue(neverExpires);
    }

    /// @return Whether the account is never made inactive when unused, as ticked.
    public boolean isNeverExpires() {
        return neverExpires.getValue();
    }

    /// @param enabled Whether the user can sign in.
    public void setEnabled(final boolean enabled) {
        this.enabled.setValue(enabled);
    }

    /// @return Whether the user can sign in, as ticked.
    public boolean isEnabled() {
        return enabled.getValue();
    }

    /// Shows or hides what only an account that exists has: whether it is enabled, its sign-in
    /// lock and its activity.
    ///
    /// @param visible Whether they are shown.
    public void setStateVisible(final boolean visible) {
        enabledGroup.setVisible(visible);
        lockedGroup.setVisible(visible);
        inactiveGroup.setVisible(visible);
    }

    /// Shows what the lockout has done and whether there is anything to undo. Read only: an
    /// administrator does not lock an account, repeated wrong passwords do.
    ///
    /// @param description What the lockout has done.
    /// @param canUnlock   Whether Unlock can be pressed.
    public void setLockState(final String description, final boolean canUnlock) {
        lockState.setText(description);
        unlock.setEnabled(canUnlock);
    }

    /// Shows whether the account has gone unused. Read only, as for [#setLockState]: inactivity is
    /// applied by the account maintenance job, not by an administrator.
    ///
    /// @param description Whether it has, and since when.
    /// @param canReactivate Whether Reactivate can be pressed.
    public void setActivityState(final String description, final boolean canReactivate) {
        activityState.setText(description);
        reactivate.setEnabled(canReactivate);
    }

    /// @param text The password button's text, e.g. "Set Password" for a new account.
    public void setPasswordButtonText(final String text) {
        changePassword.setText(text);
    }

    /// @param onChangePassword What the password button does.
    public void setOnChangePassword(final Runnable onChangePassword) {
        this.onChangePassword = Objects.requireNonNull(onChangePassword, "onChangePassword");
    }

    /// @param onUnlock What Unlock does.
    public void setOnUnlock(final Runnable onUnlock) {
        this.onUnlock = Objects.requireNonNull(onUnlock, "onUnlock");
    }

    /// @param onReactivate What Reactivate does.
    public void setOnReactivate(final Runnable onReactivate) {
        this.onReactivate = Objects.requireNonNull(onReactivate, "onReactivate");
    }

    private void onChangePassword() {
        onChangePassword.run();
    }

    private void onUnlock() {
        onUnlock.run();
    }

    private void onReactivate() {
        onReactivate.run();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, AccountEditor> {

    }
}
