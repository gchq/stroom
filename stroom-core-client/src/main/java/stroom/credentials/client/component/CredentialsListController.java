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
import stroom.credentials.client.presenter.CredentialClient;
import stroom.credentials.client.presenter.CredentialEditPresenter;
import stroom.credentials.client.presenter.CredentialEditPresenter.CreationState;
import stroom.credentials.client.presenter.ReplaceSecretPresenter;
import stroom.credentials.shared.Credential;
import stroom.credentials.shared.CredentialWithPerms;
import stroom.credentials.shared.FindCredentialRequest;
import stroom.data.client.presenter.RestDataProvider;
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

import java.util.function.Consumer;

/// The credentials ([CredentialsList]) the user may see, a page at a time: New and Edit open a
/// credential's editor, Replace Secret its secret's, and Delete deletes the selected one once
/// confirmed. Without the permission to see credentials, none are fetched.
public class CredentialsListController extends Controller {

    private final CredentialsList view;
    private final CredentialClient credentialClient;
    private final Provider<CredentialEditPresenter> credentialEditPresenterProvider;
    private final Provider<ReplaceSecretPresenter> replaceSecretPresenterProvider;
    private RestDataProvider<CredentialWithPerms, ResultPage<CredentialWithPerms>> dataProvider;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                        The application's event bus.
    /// @param view                            The content.
    /// @param credentialClient                Finds, makes and deletes the credentials.
    /// @param credentialEditPresenterProvider Makes a credential's editor.
    /// @param replaceSecretPresenterProvider  Makes the dialog that replaces a credential's secret.
    /// @param securityContext                 Tells whether the user may see credentials.
    /// @param expiryFormatter                 Formats when a credential expires.
    @Inject
    public CredentialsListController(final EventBus eventBus,
                                     final CredentialsList view,
                                     final CredentialClient credentialClient,
                                     final Provider<CredentialEditPresenter> credentialEditPresenterProvider,
                                     final Provider<ReplaceSecretPresenter> replaceSecretPresenterProvider,
                                     final ClientSecurityContext securityContext,
                                     final ExpiryFormatter expiryFormatter) {
        super(eventBus);
        this.view = view;
        this.credentialClient = credentialClient;
        this.credentialEditPresenterProvider = credentialEditPresenterProvider;
        this.replaceSecretPresenterProvider = replaceSecretPresenterProvider;
        initWidget(view);
        view.addColumns(expiryFormatter::formatWithDuration);
        view.setOnAdd(this::handleAddButtonClick);
        view.setOnDelete(this::handleDeleteButtonClick);
        view.setOnEdit(this::handleEditButtonClick);
        view.setOnReplaceSecret(this::handleReplaceSecretButtonClick);

        // Avoid permissions issues if the user doesn't have the AppPermission to see the data
        if (securityContext.hasAppPermission(AppPermission.CREDENTIALS)) {
            dataProvider = createDataProvider(eventBus);
            view.setDataProvider(dataProvider);
        }
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

    /// Lets clients turn off the selection of the first credential once they have loaded, if none is
    /// selected. Unless this is called, the first is selected.
    ///
    /// @param defaultSelection True if the first is to be selected if none is, false if none is to
    ///                         be selected then.
    public void setDefaultSelection(final boolean defaultSelection) {
        view.setSelectFirstWhenLoaded(defaultSelection);
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

    // Shows a credential as selected, or none
    private void setSelectedCredential(final Credential credential) {
        view.getSelectionModel().setSelected(new CredentialWithPerms(credential, true, true));
    }

    /// @return The uuid of the selected credential, or `null` if none is selected.
    public String getSelectedCredentialsId() {
        final CredentialWithPerms selectedCwp = view.getSelectionModel().getSelected();
        if (selectedCwp != null) {
            return selectedCwp.getCredential().getUuid();
        } else {
            return null;
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
            makeDialog(credentialEditPresenterProvider)
                    .show(docRef, null, CreationState.NEW_CREDENTIALS, this::afterEdit);
        }, view.getTaskMonitor());
    }

    private void afterEdit(final Credential credential) {
        // Reload the list & select it
        dataProvider.refresh();
        setSelectedCredential(credential);
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

    // Deletes the credentials, then selects the first
    private void deleteCredentials(final CredentialWithPerms selectedCwp) {
        if (selectedCwp != null) {
            credentialClient.deleteCredentials(selectedCwp.getCredential().getUuid(), result -> {
                // Reload the list & select the first item
                dataProvider.refresh();
                view.selectFirst();
            }, view.getTaskMonitor());
        }
    }

    private void handleEditButtonClick() {
        final CredentialWithPerms cwp = view.getSelectionModel().getSelected();

        // Check that the user can edit these credentials
        if (cwp.isEdit()) {
            makeDialog(credentialEditPresenterProvider).show(cwp.getCredential().asDocRef(),
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
            makeDialog(replaceSecretPresenterProvider).show(cwp.getCredential(), this::afterEdit);
        }
    }
}
