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

package stroom.security.client.component;

import stroom.widget.component.client.Component;
import stroom.widget.form.client.FormGroup;
import stroom.widget.form.client.TextBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.Objects;

/// One identity provider user to add: their unique identity, and optionally their display and
/// full names.
public class CreateExternalUser extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    FormGroup subjectIdGroup;
    @UiField
    TextBox subjectId;
    @UiField
    TextBox displayName;
    @UiField
    TextBox fullName;

    private Runnable onEnter = () -> {
    };

    /// Makes an empty form.
    public CreateExternalUser() {
        initWidget(BINDER.createAndBindUi(this));
        subjectId.addKeyDownHandler(this::onUserIdentityKeyDown);
        subjectId.addKeyUpHandler(this::onUserIdentityKeyUp);
        displayName.addKeyDownHandler(this::onDisplayNameKeyDown);
        fullName.addKeyDownHandler(this::onFullNameKeyDown);
    }

    /// Focuses the unique identity, once the event being handled is done.
    public void focus() {
        Scheduler.get().scheduleDeferred(() -> subjectId.setFocus(true));
    }

    /// @param onEnter What Enter in any field does, e.g. press OK on the dialog it is shown in.
    public void setOnEnter(final Runnable onEnter) {
        this.onEnter = Objects.requireNonNull(onEnter, "onEnter");
    }

    /// @return The user's unique identity, as entered.
    public String getSubjectId() {
        return subjectId.getText();
    }

    /// @return The user's display name, as entered.
    public String getDisplayName() {
        return displayName.getText();
    }

    /// @return The user's full name, as entered.
    public String getFullName() {
        return fullName.getText();
    }

    /// Shows why the unique identity is invalid, under its field, and focuses the field.
    ///
    /// @param message What is wrong with the unique identity.
    public void setSubjectIdInvalid(final String message) {
        subjectIdGroup.setFeedback(message);
        subjectId.setFocus(true);
    }

    private void onUserIdentityKeyDown(final KeyDownEvent event) {
        handleKeyDown(event);
    }

    private void onUserIdentityKeyUp(final KeyUpEvent event) {
        // Once an identity has been typed the feedback no longer applies
        if (subjectIdGroup.getFeedback() != null && !subjectId.getText().trim().isEmpty()) {
            subjectIdGroup.clearFeedback();
        }
    }

    private void onDisplayNameKeyDown(final KeyDownEvent event) {
        handleKeyDown(event);
    }

    private void onFullNameKeyDown(final KeyDownEvent event) {
        handleKeyDown(event);
    }

    private void handleKeyDown(final KeyDownEvent event) {
        if (event.getNativeKeyCode() == '\r') {
            onEnter.run();
        }
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, CreateExternalUser> {

    }
}
