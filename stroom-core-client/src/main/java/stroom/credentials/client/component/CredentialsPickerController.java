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

import stroom.alert.client.event.AlertEvent;
import stroom.document.client.MarkdownConverter;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// Picks one of the user's credentials ([CredentialsPicker]), in a dialog made for one use.
public class CredentialsPickerController extends Controller {

    private static final int DIALOG_WIDTH = 700;
    private static final int DIALOG_HEIGHT = 700;

    private final CredentialsPicker view;
    private final MarkdownConverter markdownConverter;
    private final CredentialsListController credentialsList;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus          The application's event bus.
    /// @param view              The content.
    /// @param markdownConverter Renders the note on where to get credentials.
    /// @param credentialsList   The credentials.
    @Inject
    public CredentialsPickerController(final EventBus eventBus,
                                       final CredentialsPicker view,
                                       final MarkdownConverter markdownConverter,
                                       final CredentialsListController credentialsList) {
        super(eventBus);
        this.view = view;
        this.markdownConverter = markdownConverter;
        initWidget(view);
        this.credentialsList = own(credentialsList).attach(view.getCredentialsList());
        credentialsList.setDefaultSelection(false);
    }

    /// Shows the dialog, owned by `opener` until it is closed. OK with no credentials chosen says
    /// so and leaves the dialog open.
    ///
    /// @param opener            What shows it.
    /// @param markdownLabel     A note on the credentials, e.g. where to get them, or `null`.
    /// @param credentialsId     The UUID of the credentials chosen to start with, or `null`.
    /// @param noneChosenMessage What OK with nothing chosen says.
    /// @param onPicked          Given the name of the credentials chosen on OK.
    public void show(final HasControllerNode opener,
                     final String markdownLabel,
                     final String credentialsId,
                     final String noneChosenMessage,
                     final Consumer<String> onPicked) {
        view.setNote(markdownConverter.convertMarkdownToHtml(markdownLabel));
        credentialsList.setSelectedCredentialsId(credentialsId);

        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(DIALOG_WIDTH, DIALOG_HEIGHT))
                .caption("Credentials")
                .modal(true)
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final String credentialsName = credentialsList.getSelectedCredentialsName();
                        if (credentialsName != null) {
                            e.hide();
                            onPicked.accept(credentialsName);
                        } else {
                            AlertEvent.fireWarn(this, noneChosenMessage, e::reset);
                        }
                    } else {
                        e.hide();
                    }
                })
                .fire();
    }
}
