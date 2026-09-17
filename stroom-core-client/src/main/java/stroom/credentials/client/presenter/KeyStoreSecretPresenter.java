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

import stroom.ai.shared.KeyStoreType;
import stroom.alert.client.event.AlertEvent;
import stroom.credentials.client.presenter.KeyStoreSecretPresenter.KeyStoreSecretView;
import stroom.credentials.shared.KeyStoreSecret;
import stroom.credentials.shared.Secret;
import stroom.importexport.client.presenter.ImportUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResourceKey;
import stroom.widget.form.client.CustomFileUpload;

import com.google.gwt.user.client.ui.Focus;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.UUID;
import java.util.function.Consumer;
import javax.inject.Inject;

public class KeyStoreSecretPresenter
        extends MyPresenterWidget<KeyStoreSecretView> {

    private ResourceKey resourceKey;
    private Consumer<Boolean> afterSubmitConsumer;

    @Inject
    public KeyStoreSecretPresenter(final EventBus eventBus,
                                   final KeyStoreSecretView view) {
        super(eventBus, view);

        view.getFileUpload().setAction(ImportUtil.getImportFileURL());

        view.getFileUpload()
                .onSuccess(resourceKey -> {
                    this.resourceKey = resourceKey;
                    afterSubmitConsumer.accept(true);
                })
                .onFailure(message -> AlertEvent.fireError(this, message, () ->
                        afterSubmitConsumer.accept(false)))
                .taskMonitorFactory(this, "Uploading Data");
    }

    public void setType(final KeyStoreType type) {
        getView().setType(type);
    }

    /**
     * Returns the secrets object held by this object.
     *
     * @return A new secrets object updated with any changes.
     */
    public Secret getSecret() {
        return new KeyStoreSecret(
                UUID.randomUUID().toString(),
                getView().getType(),
                getView().getPassword(),
                resourceKey);
    }

    public void onOk(final Consumer<Boolean> consumer) {
        final String message = validate();
        if (message != null) {
            afterSubmitConsumer = null;
            AlertEvent.fireError(this, message, () -> consumer.accept(false));
        } else {
            afterSubmitConsumer = consumer;
            getView().getFileUpload().submit();
        }
    }

    private String validate() {
        if (getView().getType() == null) {
            return "You must choose a key store type, e.g. PKCS12";
        }
        final String fileName = getView().getFileUpload().getFilename();
        if (NullSafe.isBlankString(fileName)) {
            return "You must select a file to import";
        }
        return null;
    }

    public interface KeyStoreSecretView extends View, Focus {

        void setType(final KeyStoreType type);

        KeyStoreType getType();

        String getPassword();

        void setPassword(String password);

        CustomFileUpload getFileUpload();
    }
}
