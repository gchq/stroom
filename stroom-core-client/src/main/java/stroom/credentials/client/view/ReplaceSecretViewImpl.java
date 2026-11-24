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

package stroom.credentials.client.view;

import stroom.credentials.client.presenter.ReplaceSecretPresenter.ReplaceSecretView;
import stroom.widget.datepicker.client.DateTimeBox;
import stroom.widget.form.client.FormGroup;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;

import javax.inject.Inject;

/// Backs ReplaceSecretViewImpl.ui.xml.
public class ReplaceSecretViewImpl extends ViewImpl implements ReplaceSecretView {

    private final Widget widget;

    @UiField
    Label warning;
    @UiField
    SimplePanel secret;
    @UiField
    CustomCheckBox credsExpire;
    @UiField
    FormGroup expiryTimeFormGroup;
    @UiField
    DateTimeBox expiryTime;

    private View secretView;

    @Inject
    @SuppressWarnings("unused")
    public ReplaceSecretViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setCredentialName(final String name) {
        warning.setText("The current secret of '" + name + "' will be discarded and cannot be recovered. "
                        + "Everything that uses this credential will use the new secret straight away.");
    }

    @Override
    public void setSecretView(final View view) {
        this.secretView = view;
        secret.setWidget(view.asWidget());
    }

    @Override
    public boolean isCredExpire() {
        return credsExpire.getValue();
    }

    @Override
    public void setCredExpire(final boolean expire) {
        credsExpire.setValue(expire);
        updateState();
    }

    @Override
    public DateTimeBox getExpiryTime() {
        return expiryTime;
    }

    @Override
    public void focus() {
        if (secretView instanceof final Focus focus) {
            focus.focus();
        }
    }

    private void updateState() {
        // Expiry is shown either way but only enabled if the tick box is ticked, as in the
        // credential settings, so the dialog doesn't change shape
        final boolean expires = credsExpire.getValue();
        expiryTime.setEnabled(expires);
        expiryTimeFormGroup.setDisabled(!expires);
    }

    @UiHandler("credsExpire")
    public void onCredsExpire(final ValueChangeEvent<Boolean> e) {
        updateState();
    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, ReplaceSecretViewImpl> {

    }
}
