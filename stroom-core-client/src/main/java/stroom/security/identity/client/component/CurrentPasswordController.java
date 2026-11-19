/*
 * Copyright 2024 Crown Copyright
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

package stroom.security.identity.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.security.client.CurrentUser;
import stroom.security.identity.shared.AuthenticationResource;
import stroom.security.identity.shared.ChangePasswordRequest;
import stroom.security.identity.shared.ConfirmPasswordRequest;
import stroom.security.shared.SessionResource;
import stroom.svg.shared.SvgImage;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.DialogAction;
import stroom.widget.popup.client.PopupType;
import stroom.widget.popup.client.event.HidePopupRequestEvent;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

/// Changes the user's own password: asks for their current password ([CurrentPassword]) in a
/// dialog made for one use, and once the server confirms it, closes and asks for the new one
/// ([ChangePasswordController]).
public class CurrentPasswordController extends Controller {

    private static final AuthenticationResource RESOURCE = GWT.create(AuthenticationResource.class);
    private static final SessionResource SESSION_RESOURCE = GWT.create(SessionResource.class);

    private final CurrentPassword view;
    private final RestFactory restFactory;
    private final CurrentUser currentUser;
    private final Provider<ChangePasswordController> changePasswordProvider;

    /// Made by GIN, with its form.
    ///
    /// @param eventBus               The application's event bus.
    /// @param view                   The form.
    /// @param restFactory            Checks the current password and changes it.
    /// @param currentUser            The user whose password it is.
    /// @param changePasswordProvider Makes the dialog that asks for the new password.
    @Inject
    public CurrentPasswordController(final EventBus eventBus,
                                     final CurrentPassword view,
                                     final RestFactory restFactory,
                                     final CurrentUser currentUser,
                                     final Provider<ChangePasswordController> changePasswordProvider) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        this.currentUser = currentUser;
        this.changePasswordProvider = changePasswordProvider;
        initWidget(view);
        view.setOnEnter(() -> ControllerDialog.pressAction(this, DialogAction.OK));
    }

    /// Asks for the user's current password in a dialog, owned by `opener` until it is closed, then
    /// for the new one in another, also owned by `opener`.
    ///
    /// @param opener What shows it, which outlives both dialogs (e.g. the app's root).
    public void show(final HasControllerNode opener) {
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .icon(SvgImage.PASSWORD)
                .caption("Enter Your Current Password")
                .modal(true)
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    if (!e.isOk()) {
                        e.hide();
                    } else if (view.validate()) {
                        confirmPassword(opener, e);
                    } else {
                        e.reset();
                    }
                })
                .fire();
    }

    private void confirmPassword(final HasControllerNode opener, final HidePopupRequestEvent event) {
        final String currentPassword = view.getPassword();
        final ConfirmPasswordRequest request = new ConfirmPasswordRequest(currentPassword);
        restFactory
                .create(RESOURCE)
                .method(res -> res.confirmPassword(request))
                .onSuccess(r -> {
                    if (r.isValid()) {
                        // This dialog goes once hidden, so the new password's is the opener's
                        changePasswordProvider.get().show(opener, "Change Password",
                                (password, confirmPassword, e) ->
                                        changePassword(opener, currentPassword, password, confirmPassword, e),
                                () -> {
                                });
                        event.hide();
                    } else {
                        AlertEvent.fireError(this, r.getMessage(), event::reset);
                    }
                })
                .onFailure(RestErrorHandler.forPopup(this, event))
                .owner(this)
                .exec();
    }

    // Run once this dialog has gone, so its replies are the opener's
    private void changePassword(final HasControllerNode opener,
                                final String currentPassword,
                                final String password,
                                final String confirmPassword,
                                final HidePopupRequestEvent event) {
        final ChangePasswordRequest request = new ChangePasswordRequest(
                currentUser.getUserRef().getSubjectId(),
                currentPassword,
                password,
                confirmPassword);
        restFactory
                .create(RESOURCE)
                .method(res -> res.changePassword(request))
                .onSuccess(r -> {
                    if (r.isChangeSucceeded()) {
                        // A password change does not sign you out elsewhere by itself; offer it opt-in.
                        event.hide();
                        ConfirmEvent.fire(this,
                                "Your password has been changed. Do you also want to sign out of all " +
                                "your other sessions (on other browsers and devices)?",
                                result -> {
                                    if (result) {
                                        terminateOtherSessions(opener);
                                    }
                                });
                    } else {
                        AlertEvent.fireError(this, r.getMessage(), event::reset);
                    }
                })
                .onFailure(RestErrorHandler.forPopup(this, event))
                .owner(opener)
                .exec();
    }

    private void terminateOtherSessions(final HasControllerNode opener) {
        restFactory
                .create(SESSION_RESOURCE)
                .method(SessionResource::terminateOtherSessions)
                .onSuccess(ok -> AlertEvent.fireInfo(this, "Signed out of your other sessions.", null))
                .onFailure(restError -> AlertEvent.fireErrorFromException(
                        this, restError.getException(), null))
                .owner(opener)
                .exec();
    }
}
