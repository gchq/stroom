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

package stroom.security.identity.client.view;

import stroom.security.identity.client.presenter.EditAccountPresenter.EditAccountView;
import stroom.security.identity.client.presenter.EditAccountUiHandlers;
import stroom.widget.button.client.Button;
import stroom.widget.form.client.FormGroup;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class EditAccountViewImpl
        extends ViewWithUiHandlers<EditAccountUiHandlers>
        implements EditAccountView {

    private final Widget widget;

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
    FormGroup neverExpiresGroup;
    @UiField
    CustomCheckBox neverExpires;
    @UiField
    FormGroup enabledGroup;
    @UiField
    CustomCheckBox enabled;
    @UiField
    FormGroup inactiveGroup;
    @UiField
    CustomCheckBox inactive;
    @UiField
    FormGroup lockedGroup;
    @UiField
    CustomCheckBox locked;
    @UiField
    Button changePassword;

    @Inject
    public EditAccountViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public void focus() {
        userId.setFocus(true);
    }

    @Override
    public void setUserId(final String userId) {
        this.userId.setValue(userId);
    }

    @Override
    public String getUserId() {
        return userId.getValue();
    }

    @Override
    public void setUserIdFeedback(final String feedback) {

    }

    @Override
    public void setEmail(final String email) {
        this.email.setValue(email);
    }

    @Override
    public String getEmail() {
        return email.getValue();
    }

    @Override
    public void setEmailFeedback(final String feedback) {

    }

    @Override
    public void setFirstName(final String firstName) {
        this.firstName.setValue(firstName);
    }

    @Override
    public String getFirstName() {
        return firstName.getValue();
    }

    @Override
    public void setLastName(final String lastName) {
        this.lastName.setValue(lastName);
    }

    @Override
    public String getLastName() {
        return lastName.getValue();
    }

    @Override
    public void setComments(final String comments) {
        this.comments.setValue(comments);
    }

    @Override
    public String getComments() {
        return comments.getValue();
    }

    @Override
    public void setNeverExpires(final boolean neverExpires) {
        this.neverExpires.setValue(neverExpires);
    }

    @Override
    public boolean isNeverExpires() {
        return neverExpires.getValue();
    }

    @Override
    public void setNeverExpiresVisible(final boolean visible) {
        neverExpiresGroup.setVisible(visible);
    }

    @Override
    public void setEnabled(final boolean enabled) {
        this.enabled.setValue(enabled);
    }

    @Override
    public boolean isEnabled() {
        return enabled.getValue();
    }

    @Override
    public void setEnabledVisible(final boolean visible) {
        enabledGroup.setVisible(visible);
    }

    @Override
    public void setInactive(final boolean inactive) {
        this.inactive.setValue(inactive);
    }

    @Override
    public boolean isInactive() {
        return inactive.getValue();
    }

    @Override
    public void setInactiveVisible(final boolean visible) {
        inactiveGroup.setVisible(visible);
    }

    @Override
    public void setLocked(final boolean locked) {
        this.locked.setValue(locked);
    }

    @Override
    public boolean isLocked() {
        return locked.getValue();
    }

    @Override
    public void setLockedVisible(final boolean visible) {
        lockedGroup.setVisible(visible);
    }

    @Override
    public void setPasswordButtonText(final String text) {
        changePassword.setText(text);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @UiHandler("changePassword")
    public void onChangePassword(final ClickEvent e) {
        getUiHandlers().onChangePassword();
    }

    public interface Binder extends UiBinder<Widget, EditAccountViewImpl> {

    }
}
