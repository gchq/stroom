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

package stroom.credentials.client.component;

import stroom.ai.shared.KeyStoreType;
import stroom.alert.client.event.AlertEvent;
import stroom.credentials.shared.KeyStoreSecret;
import stroom.credentials.shared.Secret;
import stroom.importexport.client.ImportUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResourceKey;
import stroom.widget.component.client.Controller;
import stroom.widget.form.client.component.FileChooserController;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.UUID;
import java.util.function.Consumer;

/// The secret of a key store credential ([KeyStoreSecretForm]): the only secret with a file, which
/// it uploads when the form is submitted, the secret then holding the uploaded file's key.
public final class KeyStoreSecretFormController extends Controller implements SecretForm {

    private final KeyStoreSecretForm view;
    private final FileChooserController fileChooser;
    private ResourceKey resourceKey;

    /// Made by GIN, with its form.
    ///
    /// @param eventBus    The application's event bus, which an upload's failure is shown on.
    /// @param view        The form.
    /// @param fileChooser Uploads the form's file; this owns it.
    @Inject
    public KeyStoreSecretFormController(final EventBus eventBus,
                                        final KeyStoreSecretForm view,
                                        final FileChooserController fileChooser) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        this.fileChooser = own(fileChooser);
        fileChooser.attach(view.getFileChooser());
        fileChooser.setTaskMonitorFactory(taskMonitor(), "Uploading Data");
    }

    /// @param type The key store's type, e.g. that of the secret being replaced.
    public void setType(final KeyStoreType type) {
        view.setType(type);
    }

    @Override
    public Secret getSecret() {
        return new KeyStoreSecret(
                UUID.randomUUID().toString(),
                view.getType(),
                view.getPassword(),
                resourceKey);
    }

    @Override
    public String validate() {
        if (view.getType() == null) {
            return "You must choose a key store type, e.g. PKCS12";
        }
        if (NullSafe.isBlankString(fileChooser.getFilename())) {
            return "You must select a file to import";
        }
        return null;
    }

    @Override
    public void focus() {
        view.focus();
    }

    /// Uploads the key store's file; a failure is shown to the user before `onDone` is told.
    ///
    /// @param onDone Told whether the file was uploaded.
    @Override
    public void submit(final Consumer<Boolean> onDone) {
        fileChooser.upload(ImportUtil.getImportFileURL(),
                uploaded -> {
                    this.resourceKey = uploaded;
                    onDone.accept(true);
                },
                failure -> AlertEvent.fireError(this, failure, () -> onDone.accept(false)));
    }
}
