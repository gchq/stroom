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
import stroom.widget.form.client.TextArea;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// Several identity provider users to add, one per line, as CSV: unique identity, optional display
/// name and optional full name.
public class CreateMultipleUsers extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    FormGroup userBatchGroup;
    @UiField
    TextArea userBatch;

    /// Makes an empty form.
    public CreateMultipleUsers() {
        initWidget(BINDER.createAndBindUi(this));
        userBatch.addKeyUpHandler(this::onUserBatchKeyUp);
    }

    /// Focuses the users, once the event being handled is done.
    public void focus() {
        Scheduler.get().scheduleDeferred(() -> userBatch.setFocus(true));
    }

    /// Shows why the users are invalid, under their field, and focuses the field.
    ///
    /// @param message What is wrong with the users.
    public void setUsersCsvDataInvalid(final String message) {
        userBatchGroup.setFeedback(message);
        userBatch.setFocus(true);
    }

    /// @return The users, as entered.
    public String getUsersCsvData() {
        return userBatch.getText();
    }

    private void onUserBatchKeyUp(final KeyUpEvent event) {
        // Once users have been entered the feedback no longer applies
        if (userBatchGroup.getFeedback() != null && !userBatch.getText().trim().isEmpty()) {
            userBatchGroup.clearFeedback();
        }
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, CreateMultipleUsers> {

    }
}
