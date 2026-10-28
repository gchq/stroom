/*
 * Copyright 2026 Crown Copyright
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

package stroom.credentials.client.component;

import stroom.widget.component.client.Component;
import stroom.widget.datepicker.client.DateTimeBox;
import stroom.widget.form.client.FormGroup;
import stroom.widget.tickbox.client.TickBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/// A credential's new secret, under a warning that the old one is lost, and whether and when the
/// new one expires.
public class ReplaceSecret extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    Label warning;
    @UiField
    SimplePanel secret;
    @UiField
    TickBox credsExpire;
    @UiField
    FormGroup expiryTimeFormGroup;
    @UiField
    DateTimeBox expiryTime;

    /// Makes an empty form, with no secret form until [#setSecretForm(IsWidget)].
    public ReplaceSecret() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @param name The name of the credential, shown in the warning.
    public void setCredentialName(final String name) {
        warning.setText("The current secret of '" + name + "' will be discarded and cannot be recovered. "
                        + "Everything that uses this credential will use the new secret straight away.");
    }

    /// @param form The form for the secret of the credential's type.
    public void setSecretForm(final IsWidget form) {
        secret.setWidget(form);
    }

    /// @return True if the new secret expires.
    public boolean isCredExpire() {
        return credsExpire.getValue();
    }

    /// @param expire True if the new secret expires.
    public void setCredExpire(final boolean expire) {
        credsExpire.setValue(expire);
        updateState();
    }

    /// @return The expiry date's box, for its controller to attach to.
    public DateTimeBox getExpiryTimeBox() {
        return expiryTime;
    }

    /// @param expiryTime When the new secret expires, in milliseconds since the epoch.
    public void setExpiryTime(final Long expiryTime) {
        this.expiryTime.setValue(expiryTime);
    }

    /// @return When the new secret expires, in milliseconds since the epoch.
    public Long getExpiryTime() {
        return expiryTime.getValue();
    }

    /// @return Whether the expiry is a valid date and time.
    public boolean isExpiryTimeValid() {
        return expiryTime.isValid();
    }

    // Expiry is shown either way but only enabled if the tick box is ticked, as in the credential
    // settings, so the dialog doesn't change shape
    private void updateState() {
        final boolean expires = credsExpire.getValue();
        expiryTime.setEnabled(expires);
        expiryTimeFormGroup.setDisabled(!expires);
    }

    @UiHandler("credsExpire")
    void onCredsExpire(final ValueChangeEvent<Boolean> e) {
        updateState();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ReplaceSecret> {

    }
}
