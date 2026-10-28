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

import stroom.dispatch.client.DefaultErrorHandler;
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

/// Asks for a new password ([ChangePassword]) in a dialog made for one use, once the password
/// policy has been fetched. Shared by signing in, resetting a password, changing one's own and an
/// account's.
public class ChangePasswordController extends Controller {

    private static final AuthenticationResource RESOURCE = GWT.create(AuthenticationResource.class);

    private final ChangePassword view;
    private final RestFactory restFactory;

    /// Made by GIN, with its form.
    ///
    /// @param eventBus    The application's event bus.
    /// @param view        The form.
    /// @param restFactory Fetches the password policy.
    @Inject
    public ChangePasswordController(final EventBus eventBus,
                                    final ChangePassword view,
                                    final RestFactory restFactory) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        initWidget(view);
        view.setOnEnter(() -> ControllerDialog.pressAction(this, DialogAction.OK));
    }

    /// Fetches the password policy, then asks for a new password in a dialog, owned by `opener`
    /// until it is closed. OK checks the password against the policy and its confirmation, and
    /// gives them to `onOk` when they are valid. If the policy can't be fetched, the error is shown
    /// and nothing more happens.
    ///
    /// @param opener   What shows it.
    /// @param caption  The dialog's caption, e.g. "Change Password".
    /// @param onOk     Given the valid password, its confirmation and the hide request, which it
    ///                 hides once the password is saved, or resets to keep the dialog open.
    /// @param onCancel Run once the dialog is cancelled and hidden.
    public void show(final HasControllerNode opener,
                     final String caption,
                     final OnNewPassword onOk,
                     final Runnable onCancel) {
        restFactory
                .create(RESOURCE)
                .method(AuthenticationResource::fetchPasswordPolicy)
                .onSuccess(policy -> {
                    if (opener.getControllerNode().isDisposed()) {
                        // The opener went before the policy came, so there is nothing to show it on
                        dispose();
                        return;
                    }
                    view.setPolicy(policy);
                    ControllerDialog.showOnce(opener, this)
                            .popupType(PopupType.OK_CANCEL_DIALOG)
                            .icon(SvgImage.PASSWORD)
                            .caption(caption)
                            .modal(true)
                            .onShow(e -> view.focus())
                            .onHideRequest(e -> {
                                if (!e.isOk()) {
                                    e.hide();
                                    onCancel.run();
                                } else if (view.validate()) {
                                    onOk.accept(view.getPassword(), view.getConfirmPassword(), e);
                                } else {
                                    e.reset();
                                }
                            })
                            .fire();
                })
                // Not shown, so nothing owns it: it goes once the error has been seen
                .onFailure(new DefaultErrorHandler(this, this::dispose))
                .exec();
    }


    // --------------------------------------------------------------------------------


    /// What is done with a valid new password.
    @FunctionalInterface
    public interface OnNewPassword {

        /// @param password        The new password.
        /// @param confirmPassword The new password typed again.
        /// @param event           The dialog's hide request.
        void accept(String password, String confirmPassword, HidePopupRequestEvent event);
    }
}
