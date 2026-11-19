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

import stroom.credentials.client.CredentialClient;
import stroom.credentials.shared.Credential;
import stroom.credentials.shared.CredentialWithPerms;
import stroom.credentials.shared.PutCredentialRequest;
import stroom.dispatch.client.DefaultErrorHandler;
import stroom.docref.DocRef;
import stroom.security.client.component.DocumentUserPermissionsTabController;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.ControllerLayer;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;
import stroom.widget.tab.client.TabData;
import stroom.widget.tab.client.TabDataImpl;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// Adds or edits a credential ([CredentialEditor]) in a dialog made for one use: its Settings, saved
/// on OK, and its Permissions.
public class CredentialEditorController extends Controller {

    // Wide enough for the Permissions tab
    private static final int DIALOG_WIDTH = 750;
    private static final int DIALOG_HEIGHT = 750;
    private static final TabData SETTINGS = new TabDataImpl("Settings", false);
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions", false);

    private final CredentialEditor view;
    private final CredentialClient credentialClient;
    private final CredentialSettingsController credentialSettings;
    private final ControllerLayer credentialSettingsLayer;
    private final DocumentUserPermissionsTabController permissions;
    private final ControllerLayer permissionsLayer;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus             The application's event bus.
    /// @param view                 The tabs.
    /// @param credentialSettings   The Settings tab.
    /// @param permissions          The Permissions tab.
    /// @param credentialClient     Saves the credential.
    @Inject
    public CredentialEditorController(final EventBus eventBus,
                                      final CredentialEditor view,
                                      final CredentialSettingsController credentialSettings,
                                      final DocumentUserPermissionsTabController permissions,
                                      final CredentialClient credentialClient) {
        super(eventBus);
        this.view = view;
        this.credentialSettings = own(credentialSettings);
        credentialSettingsLayer = new ControllerLayer(credentialSettings);
        this.permissions = own(permissions);
        permissionsLayer = new ControllerLayer(permissions);
        this.credentialClient = credentialClient;
        initWidget(view);

        view.addTab(SETTINGS);
        view.addTab(PERMISSIONS);
        switchTab(SETTINGS);
        view.setOnTabSelected(this::switchTab);
        view.setOnShowMenu(getEventBus()::fireEvent);
    }

    /// Shows a credential in a dialog, owned by `opener` until it is closed.
    ///
    /// @param opener        What shows it.
    /// @param docRef        The credential's reference.
    /// @param cwp           The credential, with the user's permissions on it.
    /// @param creationState Whether it is new or saved.
    /// @param consumer      Given the saved credential once OK has saved it.
    public void show(final HasControllerNode opener,
                     final DocRef docRef,
                     final CredentialWithPerms cwp,
                     final CreationState creationState,
                     final Consumer<Credential> consumer) {
        credentialSettings.read(docRef, cwp);
        permissions.setDocRef(docRef);

        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(DIALOG_WIDTH, DIALOG_HEIGHT))
                .caption(CreationState.NEW_CREDENTIALS.equals(creationState)
                        ? "New Credentials"
                        : "Edit Credentials")
                .modal(true)
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        credentialSettings.onOk(valid -> {
                            if (valid) {
                                // Store or update the secret
                                final PutCredentialRequest request = credentialSettings.write();
                                credentialClient.storeCredential(request,
                                        guard(result -> {
                                            consumer.accept(result);
                                            e.hide();
                                        }), new DefaultErrorHandler(this, e::reset),
                                        taskMonitor());
                            } else {
                                e.reset();
                            }
                        });
                    } else {
                        e.hide();
                    }
                })
                .fire();
    }

    private void switchTab(final TabData tab) {
        if (SETTINGS.equals(tab)) {
            view.showTab(tab, credentialSettingsLayer);
        } else if (PERMISSIONS.equals(tab)) {
            view.showTab(tab, permissionsLayer);
        }
    }



    // --------------------------------------------------------------------------------


    /// Whether the credential is new, to be created, or saved, to be updated.
    public enum CreationState {
        NEW_CREDENTIALS,
        OLD_CREDENTIALS
    }
}
