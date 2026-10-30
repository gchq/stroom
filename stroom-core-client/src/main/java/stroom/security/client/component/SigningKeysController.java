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

package stroom.security.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.content.client.component.ContentTabController;
import stroom.data.table.client.HasRefresh;
import stroom.dispatch.client.RestFactory;
import stroom.preferences.client.DateTimeFormatter;
import stroom.security.identity.shared.SigningKeyResource;
import stroom.security.identity.shared.SigningKeyRow;
import stroom.security.identity.shared.SigningKeyStatus;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.IsButton;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// Shows the internal identity provider's signing keys, and revokes them.
///
/// It exists so that a key believed to have been exposed can be withdrawn, which otherwise means
/// editing the database. Withdrawing a key is not reversible: the reason to do it is that the key is
/// not trustworthy, and a way to restore trust in a key somebody has already decided about would be a
/// mistake waiting to happen.
public class SigningKeysController extends ContentTabController implements HasRefresh {

    private static final SigningKeyResource SIGNING_KEY_RESOURCE = GWT.create(SigningKeyResource.class);
    public static final String TAB_TYPE = "SigningKeys";

    private final SigningKeyListController signingKeyList;
    private final RestFactory restFactory;
    private final DateTimeFormatter dateTimeFormatter;
    private final IsButton revokeButton;
    private final IsButton revokeAllButton;

    /// Made by GIN, with its screen and its parts.
    ///
    /// @param eventBus          The application's event bus.
    /// @param signingKeyList    The signing key list.
    /// @param restFactory       Makes REST requests.
    /// @param dateTimeFormatter Formats dates and times.
    @Inject
    public SigningKeysController(final EventBus eventBus,
                                 final SigningKeyListController signingKeyList,
                                 final RestFactory restFactory,
                                 final DateTimeFormatter dateTimeFormatter) {
        super(eventBus);
        own(signingKeyList);
        this.signingKeyList = signingKeyList;
        this.restFactory = restFactory;
        this.dateTimeFormatter = dateTimeFormatter;

        // A single list. Unlike User Access there is no detail pane, because there is nothing further
        // about a signing key that is safe to show: the stored key includes its private material, so
        // the list's columns are the whole of what leaves the server.
        initWidget(signingKeyList.asWidget());

        signingKeyList.getToolbar().setName("Signing key actions");
        revokeButton = signingKeyList.getToolbar().button(SvgImage.DELETE, "Revoke Selected Signing Key",
                this::revokeSelected);
        revokeButton.setEnabled(false);
        // Always enabled: it acts on every key rather than the selected one, so setButtonStates() —
        // which is driven by the selection — has nothing to say about it.
        revokeAllButton = signingKeyList.getToolbar().button(SvgImage.CLEAR, "Revoke All Signing Keys Still in Use",
                this::revokeAll);

        own(getSelectionModel().addSelectionHandler(e -> setButtonStates()));
        setButtonStates();
    }

    private MultiSelectionModelImpl<SigningKeyRow> getSelectionModel() {
        return signingKeyList.getSelectionModel();
    }

    private void setButtonStates() {
        final SigningKeyRow selected = getSelectionModel().getSelected();
        revokeButton.setEnabled(selected != null && selected.getStatus() != SigningKeyStatus.REVOKED);
    }

    private void revokeSelected() {
        final SigningKeyRow row = getSelectionModel().getSelected();
        if (row == null || row.getStatus() == SigningKeyStatus.REVOKED) {
            return;
        }
        ConfirmEvent.fire(this, buildConfirmMessage(row), ok -> {
            if (ok) {
                restFactory
                        .create(SIGNING_KEY_RESOURCE)
                        .method(res -> res.revoke(row.getId()))
                        .onSuccess(revoked -> afterRevoke())
                        .onFailure(restError -> {
                            AlertEvent.fireError(this, restError.getMessage(), null);
                            refresh();
                        })
                        .taskMonitorFactory(this)
                        .exec();
            }
        });
    }

    private void revokeAll() {
        ConfirmEvent.fire(this, buildRevokeAllMessage(), ok -> {
            if (ok) {
                restFactory
                        .create(SIGNING_KEY_RESOURCE)
                        .method(SigningKeyResource::revokeAll)
                        .onSuccess(revoked -> afterRevoke())
                        .onFailure(restError -> {
                            AlertEvent.fireError(this, restError.getMessage(), null);
                            refresh();
                        })
                        .taskMonitorFactory(this)
                        .exec();
            }
        });
    }

    private void afterRevoke() {
        getSelectionModel().clear();
        refresh();
    }

    // What this costs depends entirely on which key it is, and that is not obvious from looking at the row.
    // Withdrawing the key that signs new tokens signs out everybody; withdrawing one already on its way out
    // usually affects a handful of people; withdrawing an expired one affects nobody at all. Saying which is
    // most of the point of asking.
    // The row cannot say when a key stopped signing. It carries when the key was issued and when it stops
    // being trusted, and retirement is neither of those, so the wording keeps to what is known.
    private String buildConfirmMessage(final SigningKeyRow row) {
        if (row.getStatus() == SigningKeyStatus.ACTIVE) {
            return "Are you sure you want to revoke the signing key currently in use?"
                   + "\n\nThis will sign out everyone using the internal identity provider. A replacement "
                   + "signing key is created immediately and people will be signed back in as soon as they "
                   + "authenticate again."
                   + "\n\nNodes and stroom-proxy hold their own tokens signed with this key and only replace "
                   + "them as they expire, so parts of the cluster may be unable to talk to each other for "
                   + "up to ten minutes. This recovers on its own and needs no intervention."
                   + "\n\nThis cannot be undone. Revoke it only if you believe the key may have been exposed.";
        }
        if (row.getStatus() == SigningKeyStatus.EXPIRED) {
            return "Are you sure you want to revoke this signing key?"
                   + "\n\nIt is no longer trusted, so revoking it will not sign anyone out and nothing will "
                   + "change. It is removed automatically once its retention has elapsed."
                   + "\n\nThis cannot be undone.";
        }
        return "Are you sure you want to revoke this signing key?"
               + "\n\nIt is no longer used to sign new tokens, and would stop being trusted on "
               + dateTimeFormatter.format(row.getExpiresMs())
               + " in any case, so this will sign out only those still holding a token signed with it, and "
               + "they will be signed straight back in."
               + "\n\nThis cannot be undone.";
    }

    private String buildRevokeAllMessage() {
        return "Are you sure you want to revoke every signing key?"
               + "\n\nThis will sign out everyone using the internal identity provider. A replacement signing "
               + "key is created immediately and people will be signed back in as soon as they authenticate "
               + "again."
               + "\n\nNodes and stroom-proxy hold their own tokens signed with these keys and only replace "
               + "them as they expire, so parts of the cluster may be unable to talk to each other for up to "
               + "ten minutes. This recovers on its own and needs no intervention."
               + "\n\nThis is what to use if you believe a key has been exposed but do not know which."
               + "\n\nThis cannot be undone.";
    }

    @Override
    public void refresh() {
        signingKeyList.refresh();
        setButtonStates();
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.KEY;
    }

    @Override
    public String getLabel() {
        return "Signing Keys";
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }
}
