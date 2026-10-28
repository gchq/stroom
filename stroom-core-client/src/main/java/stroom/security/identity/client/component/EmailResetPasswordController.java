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
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.security.identity.shared.AuthenticationResource;
import stroom.svg.shared.SvgImage;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.DialogAction;
import stroom.widget.popup.client.PopupType;
import stroom.widget.popup.client.event.HidePopupRequestEvent;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// Reset Your Password ([EmailResetPassword]), from the sign in page's 'Forgot password?' link: a
/// dialog made for one use that emails a password reset link to the address entered.
public class EmailResetPasswordController extends Controller {

    private static final AuthenticationResource RESOURCE = GWT.create(AuthenticationResource.class);

    private final EmailResetPassword view;
    private final RestFactory restFactory;

    /// Made by GIN, with its form.
    ///
    /// @param eventBus    The application's event bus.
    /// @param view        The form.
    /// @param restFactory Sends the email.
    @Inject
    public EmailResetPasswordController(final EventBus eventBus,
                                        final EmailResetPassword view,
                                        final RestFactory restFactory) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        initWidget(view);
        view.setOnEnter(() -> ControllerDialog.pressAction(this, DialogAction.OK));
    }

    /// Shows the dialog, owned by `opener` until it is closed.
    ///
    /// @param opener What shows it.
    public void show(final HasControllerNode opener) {
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .icon(SvgImage.PASSWORD)
                .caption("Reset Your Password")
                .modal(true)
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    if (!e.isOk()) {
                        e.hide();
                    } else if (view.validate()) {
                        resetPassword(e);
                    } else {
                        e.reset();
                    }
                })
                .fire();
    }

    private void resetPassword(final HidePopupRequestEvent event) {
        final String email = view.getEmail();
        restFactory
                .create(RESOURCE)
                .method(resource -> resource.resetEmail(email))
                .onSuccess(r -> {
                    if (r) {
                        AlertEvent.fireInfo(
                                this,
                                "Password Reset",
                                "Please check your email.\n" +
                                "\n" +
                                "If the email address is registered you should shortly\n" +
                                "receive a message with a link that will let you change your password.\n",
                                event::hide);
                    } else {
                        AlertEvent.fireError(this, "Error",
                                "Unable to reset password", event::reset);
                    }
                })
                .onFailure(RestErrorHandler.forPopup(this, event))
                .owner(this)
                .exec();
    }
}
