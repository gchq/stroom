/*
 * Copyright 2025 Crown Copyright
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
import stroom.credentials.client.presenter.SshKeySecretPresenter.SshKeySecretView;
import stroom.credentials.shared.Secret;
import stroom.credentials.shared.SshKeySecret;

import com.google.gwt.user.client.ui.Focus;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.function.Consumer;
import javax.inject.Inject;

public class SshKeySecretPresenter
        extends MyPresenterWidget<SshKeySecretView> {

    @Inject
    public SshKeySecretPresenter(final EventBus eventBus,
                                 final SshKeySecretView view) {
        super(eventBus, view);
    }

    /**
     * Returns the secrets object held by this object.
     *
     * @return A new secrets object updated with any changes.
     */
    public Secret getSecret() {
        return new SshKeySecret(
                getView().getPassPhrase(),
                getView().getPrivateKey(),
                getView().isVerifyHosts(),
                getView().getKnownHosts());
    }

    public void onOk(final Consumer<Boolean> consumer) {
        final String message = validate();
        if (message != null) {
            AlertEvent.fireError(this, message, () -> consumer.accept(false));
        } else {
            consumer.accept(true);
        }
    }

    private String validate() {
        if (getView().getPrivateKey().isBlank()) {
            return "Private key must not be empty";
        }
        return null;
    }

    public interface SshKeySecretView extends View, Focus {

        String getPassPhrase();

        void setPassPhrase(String passPhrase);

        String getPrivateKey();

        void setPrivateKey(String privateKey);

        boolean isVerifyHosts();

        void setVerifyHosts(boolean verifyHosts);

        String getKnownHosts();

        void setKnownHosts(String knownHosts);
    }
}
