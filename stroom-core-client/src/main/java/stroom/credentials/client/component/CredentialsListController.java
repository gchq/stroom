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

import stroom.alert.client.event.ConfirmEvent;
import stroom.credentials.client.CredentialClient;
import stroom.credentials.client.component.CredentialEditorController.CreationState;
import stroom.credentials.shared.Credential;
import stroom.credentials.shared.CredentialWithPerms;
import stroom.credentials.shared.FindCredentialRequest;
import stroom.dispatch.client.RestDataProvider;
import stroom.dispatch.client.RestErrorHandler;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.util.client.ExpiryFormatter;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.widget.component.client.Controller;

import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/// The credentials ([CredentialsList]) the user may see, a page at a time: New and Edit open a
/// credential's editor, Replace Secret its secret's, and Delete deletes the selected one once
/// confirmed. Without the permission to see credentials, none are fetched.
public class CredentialsListController extends Controller {

    private final CredentialClient credentialClient;
    private final Provider<CredentialEditorController> credentialEditProvider;
    private final Provider<ReplaceSecretController> replaceSecretProvider;
    private final ClientSecurityContext securityContext;
    private final ExpiryFormatter expiryFormatter;
    private CredentialsList view;
    private RestDataProvider<CredentialWithPerms, ResultPage<CredentialWithPerms>> dataProvider;

    /// Made by GIN; [#attach(CredentialsList)] gives it its list.
    ///
    /// @param eventBus                        The application's event bus.
    /// @param credentialClient                Finds, makes and deletes the credentials.
    /// @param credentialEditProvider          Makes a credential's editor.
    /// @param replaceSecretProvider           Makes the dialog that replaces a credential's secret.
    /// @param securityContext                 Tells whether the user may see credentials.
    /// @param expiryFormatter                 Formats when a credential expires.
    @Inject
    public CredentialsListController(final EventBus eventBus,
                                     final CredentialClient credentialClient,
                                     final Provider<CredentialEditorController> credentialEditProvider,
                                     final Provider<ReplaceSecretController> replaceSecretProvider,
                                     final ClientSecurityContext securityContext,
                                     final ExpiryFormatter expiryFormatter) {
        super(eventBus);
        this.credentialClient = credentialClient;
        this.credentialEditProvider = credentialEditProvider;
        this.replaceSecretProvider = replaceSecretProvider;
        this.securityContext = securityContext;
        this.expiryFormatter = expiryFormatter;
    }

    /// Attaches this to a list, e.g. one a template declares. The list is then this controller's
    /// widget, e.g. to show it as a screen of its own.
    ///
    /// @param view The list.
    /// @return This, for its owner to keep.
    public CredentialsListController attach(final CredentialsList view) {
        this.view = Objects.requireNonNull(view, "view");
        initWidget(view);
        view.addColumns(expiryFormatter::formatWithDuration);
        view.setOnAdd(this::handleAddButtonClick);
        view.setOnDelete(this::handleDeleteButtonClick);
        view.setOnEdit(this::handleEditButtonClick);
        view.setOnReplaceSecret(this::handleReplaceSecretButtonClick);

        // Avoid permissions issues if the user doesn't have the AppPermission to see the data
        if (securityContext.hasAppPermission(AppPermission.CREDENTIALS)) {
            dataProvider = createDataProvider(getEventBus());
            view.setDataProvider(dataProvider);
        }
        return this;
    }

    // Sets up the data provider for the list of credentials
    private RestDataProvider<CredentialWithPerms, ResultPage<CredentialWithPerms>> createDataProvider(
            final EventBus eventBus) {
        return new RestDataProvider<>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<CredentialWithPerms>> dataConsumer,
                                final RestErrorHandler restErrorHandler) {
                final PageRequest pageRequest = new PageRequest(range.getStart(), range.getLength());
                final FindCredentialRequest request = new FindCredentialRequest(pageRequest,
                        null,
                        null,
                        null,
                        DocumentPermission.VIEW);
                credentialClient.findCredentialsWithPermissions(request, dataConsumer, restErrorHandler,
                        view.getTaskMonitor());
            }
        };
    }

    /// @param picker Whether the list is a picker, whose credential chosen stays chosen while another
    ///               page is shown (see [CredentialsList#setPicker(boolean)]).
    public void setPicker(final boolean picker) {
        view.setPicker(picker);
    }

    /// Finds the credentials again and selects one.
    ///
    /// @param uuid The credential to select, or `null` for none.
    public void setSelectedCredentialsId(final String uuid) {
        dataProvider.refresh();
        if (uuid != null) {
            credentialClient.getCredentialByUuid(uuid, this::setSelectedCredential, view.getTaskMonitor());
        } else {
            setSelectedCredential(null);
        }
    }

    // Shows a credential as selected, or none. If it isn't shown yet (the list is still being fetched),
    // it is selected with no permissions until its row arrives, which the selection then follows
    private void setSelectedCredential(final Credential credential) {
        if (credential == null) {
            view.getSelectionModel().clear();
        } else {
            final CredentialWithPerms shown = view.getShown(credential.getUuid());
            view.getSelectionModel().setSelected(shown != null
                    ? shown
                    : new CredentialWithPerms(credential, false, false));
        }
    }

    /// @return The name of the selected credential, or `null` if none is selected.
    public String getSelectedCredentialsName() {
        final CredentialWithPerms selectedCwp = view.getSelectionModel().getSelected();
        if (selectedCwp != null) {
            return selectedCwp.getCredential().getName();
        } else {
            return null;
        }
    }

    private void handleAddButtonClick() {
        credentialClient.createDocRef(docRef -> {
            credentialEditProvider.get()
                    .show(this, docRef, null, CreationState.NEW_CREDENTIALS, this::afterEdit);
        }, view.getTaskMonitor());
    }

    private void afterEdit(final Credential credential) {
        // The credential, once the page fetched again shows it (UI-GUIDE.md, "What a list selects")
        view.getSelectionModel().selectWhenShown(row -> row.getCredential() != null
                                                       && credential.getUuid().equals(row.getCredential().getUuid()));
        dataProvider.refresh();
    }

    private void handleDeleteButtonClick() {
        final CredentialWithPerms selectedCwp = view.getSelectionModel().getSelected();
        ConfirmEvent.fire(this,
                "Are you sure you want to delete the credentials '" +
                selectedCwp.getCredential().getName() +
                "' ?",
                ok -> {
                    if (ok) {
                        deleteCredentials(selectedCwp);
                    }
                });
    }

    // Deletes the credentials, then selects the credential in their place
    private void deleteCredentials(final CredentialWithPerms selectedCwp) {
        if (selectedCwp != null) {
            credentialClient.deleteCredentials(selectedCwp.getCredential().getUuid(), result -> {
                view.getSelectionModel().selectNextAfterRemove(List.of(selectedCwp));
                dataProvider.refresh();
            }, view.getTaskMonitor());
        }
    }

    private void handleEditButtonClick() {
        final CredentialWithPerms cwp = view.getSelectionModel().getSelected();

        // Check that the user can edit these credentials
        if (cwp.isEdit()) {
            credentialEditProvider.get().show(this,
                    cwp.getCredential().asDocRef(),
                    cwp,
                    CreationState.OLD_CREDENTIALS,
                    this::afterEdit);
        }
    }

    // Opens the dialog to replace the secret of the selected credentials
    private void handleReplaceSecretButtonClick() {
        final CredentialWithPerms cwp = view.getSelectionModel().getSelected();

        // Check that the user can edit these credentials
        if (cwp != null && cwp.isEdit()) {
            replaceSecretProvider.get().show(this, cwp.getCredential(), this::afterEdit);
        }
    }
}
