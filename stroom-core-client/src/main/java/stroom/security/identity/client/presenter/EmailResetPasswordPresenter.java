/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.security.identity.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.security.identity.client.presenter.EmailResetPasswordPresenter.EmailResetPasswordView;
import stroom.security.identity.shared.AuthenticationResource;
import stroom.svg.shared.SvgImage;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.view.DialogAction;
import stroom.widget.popup.client.view.HideRequest;
import stroom.widget.popup.client.view.HideRequestUiHandlers;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class EmailResetPasswordPresenter extends MyPresenterWidget<EmailResetPasswordView> {

    private static final AuthenticationResource RESOURCE = GWT.create(AuthenticationResource.class);

    private final RestFactory restFactory;

    @Inject
    public EmailResetPasswordPresenter(final EventBus eventBus,
                                       final EmailResetPasswordView view,
                                       final RestFactory restFactory) {
        super(eventBus, view);
        this.restFactory = restFactory;
        view.setUiHandlers(new HideRequestUiHandlers() {
            @Override
            public void hideRequest(final HideRequest request) {
                HidePopupRequestEvent
                        .builder(EmailResetPasswordPresenter.this)
                        .action(request.getAction())
                        .fire();
            }
        });
    }

    @Override
    protected void onBind() {
        super.onBind();
    }

    public void show() {
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .icon(SvgImage.PASSWORD)
                .caption("Reset Your Password")
                .modal(true)
                .onShow((event) -> getView().focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        if (getView().validate()) {
                            resetPassword(e);
                        } else {
                            e.reset();
                        }
                    } else {
                        e.hide();
                    }
                })
                .fire();
    }

    private void resetPassword(final HidePopupRequestEvent event) {
        restFactory
                .create(RESOURCE)
                .method(resource ->
                        resource.resetEmail(getView().getEmail()))
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
                .taskMonitorFactory(this)
                .exec();
    }


    // --------------------------------------------------------------------------------


    public interface EmailResetPasswordView extends View, Focus, HasUiHandlers<HideRequestUiHandlers> {

        String getEmail();

        boolean validate();
    }
}
