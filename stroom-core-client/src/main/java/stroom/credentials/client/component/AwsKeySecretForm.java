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

import stroom.credentials.shared.AwsKeySecret;
import stroom.credentials.shared.Secret;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.PasswordBox;
import stroom.widget.form.client.TextBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// The secret of an AWS access key credential, e.g. for an Amazon Athena Data Connection: the access
/// key ID and the secret access key, both required.
public final class AwsKeySecretForm extends Component implements SecretForm {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    TextBox accessKeyId;
    @UiField
    PasswordBox secretAccessKey;

    /// Makes an empty form.
    public AwsKeySecretForm() {
        initWidget(BINDER.createAndBindUi(this));
    }

    @Override
    public Secret getSecret() {
        return new AwsKeySecret(accessKeyId.getValue().trim(), secretAccessKey.getValue().trim());
    }

    @Override
    public String validate() {
        if (accessKeyId.getValue().isBlank()) {
            return "Access key ID must not be empty";
        } else if (secretAccessKey.getValue().isBlank()) {
            return "Secret access key must not be empty";
        }
        return null;
    }

    @Override
    public void focus() {
        accessKeyId.setFocus(true);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, AwsKeySecretForm> {

    }
}
