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

package stroom.credentials.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.credentials.client.presenter.AwsKeySecretPresenter.AwsKeySecretView;
import stroom.credentials.shared.AwsKeySecret;
import stroom.credentials.shared.Secret;

import com.google.gwt.user.client.ui.Focus;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.function.Consumer;
import javax.inject.Inject;

/// Edits the secret of an AWS access key credential: the access key ID and secret access key.
public final class AwsKeySecretPresenter
        extends MyPresenterWidget<AwsKeySecretView> implements SecretPresenter<AwsKeySecretView> {

    /// @param eventBus The event bus.
    /// @param view     The view.
    @Inject
    public AwsKeySecretPresenter(final EventBus eventBus,
                                 final AwsKeySecretView view) {
        super(eventBus, view);
    }

    @Override
    public Secret getSecret() {
        return new AwsKeySecret(getView().getAccessKeyId().trim(), getView().getSecretAccessKey().trim());
    }

    @Override
    public void onOk(final Consumer<Boolean> consumer) {
        final String message = validate();
        if (message != null) {
            AlertEvent.fireError(this, message, () -> consumer.accept(false));
        } else {
            consumer.accept(true);
        }
    }

    private String validate() {
        if (getView().getAccessKeyId().isBlank()) {
            return "Access key ID must not be empty";
        } else if (getView().getSecretAccessKey().isBlank()) {
            return "Secret access key must not be empty";
        }
        return null;
    }


    // --------------------------------------------------------------------------------


    /// The view of an AWS access key.
    public interface AwsKeySecretView extends View, Focus {

        /// @return The access key ID as entered.
        String getAccessKeyId();

        /// @param accessKeyId The access key ID to show.
        void setAccessKeyId(String accessKeyId);

        /// @return The secret access key as entered.
        String getSecretAccessKey();

        /// @param secretAccessKey The secret access key to show.
        void setSecretAccessKey(String secretAccessKey);
    }
}
