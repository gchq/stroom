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
import stroom.alert.client.event.ConfirmEvent;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.security.client.CurrentUser;
import stroom.security.identity.client.presenter.CurrentPasswordPresenter.CurrentPasswordView;
import stroom.security.identity.shared.AuthenticationResource;
import stroom.security.identity.shared.ChangePasswordRequest;
import stroom.security.identity.shared.ConfirmPasswordRequest;
import stroom.security.shared.SessionResource;
import stroom.svg.shared.SvgImage;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.view.HideRequest;
import stroom.widget.popup.client.view.HideRequestUiHandlers;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class CurrentPasswordPresenter extends MyPresenterWidget<CurrentPasswordView> {

    private static final AuthenticationResource RESOURCE = GWT.create(AuthenticationResource.class);
    private static final SessionResource SESSION_RESOURCE = GWT.create(SessionResource.class);

    private final RestFactory restFactory;
    private final CurrentUser currentUser;

    private final Provider<ChangePasswordPresenter> changePasswordPresenterProvider;

    @Inject
    public CurrentPasswordPresenter(final EventBus eventBus,
                                    final CurrentPasswordView view,
                                    final RestFactory restFactory,
                                    final CurrentUser currentUser,
                                    final Provider<ChangePasswordPresenter> changePasswordPresenterProvider) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.currentUser = currentUser;
        this.changePasswordPresenterProvider = changePasswordPresenterProvider;
        view.setUiHandlers(new HideRequestUiHandlers() {
            @Override
            public void hideRequest(final HideRequest request) {
                HidePopupRequestEvent.builder(CurrentPasswordPresenter.this)
                        .action(request.getAction())
                        .fire();
            }
        });
    }

    public void show() {
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .icon(SvgImage.PASSWORD)
                .caption("Enter Your Current Password")
                .modal(true)
                .onShow((event) -> getView().focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        if (getView().validate()) {
                            confirmPassword(e);
                        } else {
                            e.reset();
                        }
                    } else {
                        e.hide();
                    }
                })
                .fire();
    }

    private void confirmPassword(final HidePopupRequestEvent event) {
        final ConfirmPasswordRequest request = new ConfirmPasswordRequest(getView().getPassword());
        restFactory
                .create(RESOURCE)
                .method(res -> res.confirmPassword(request))
                .onSuccess(r -> {
                    if (r.isValid()) {
                        final ChangePasswordPresenter changePasswordPresenter = changePasswordPresenterProvider.get();
                        changePasswordPresenter.show("Change Password", e -> {
                            if (e.isOk()) {
                                // Validate the change-password dialog (new password + confirmation), not
                                // the current-password view — so the dialog's feedback labels update and
                                // e.reset() keeps it open on failure while OK stays enabled.
                                if (changePasswordPresenter.validate()) {
                                    changePassword(e, changePasswordPresenter);
                                } else {
                                    e.reset();
                                }
                            } else {
                                e.hide();
                            }
                        });

                        event.hide();
                    } else {
                        AlertEvent.fireError(this, r.getMessage(), event::reset);
                    }
                })
                .onFailure(RestErrorHandler.forPopup(this, event))
                .taskMonitorFactory(this)
                .exec();
    }

    private void changePassword(final HidePopupRequestEvent event,
                                final ChangePasswordPresenter changePasswordPresenter) {
        final ChangePasswordRequest request = new ChangePasswordRequest(
                currentUser.getUserRef().getSubjectId(),
                getView().getPassword(),
                changePasswordPresenter.getPassword(),
                changePasswordPresenter.getConfirmPassword());
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
                                        terminateOtherSessions();
                                    }
                                });
                    } else {
                        AlertEvent.fireError(this, r.getMessage(), event::reset);
                    }
                })
                .onFailure(RestErrorHandler.forPopup(this, event))
                .taskMonitorFactory(this)
                .exec();
    }

    private void terminateOtherSessions() {
        restFactory
                .create(SESSION_RESOURCE)
                .method(SessionResource::terminateOtherSessions)
                .onSuccess(ok -> AlertEvent.fireInfo(this, "Signed out of your other sessions.", null))
                .onFailure(restError -> AlertEvent.fireErrorFromException(
                        this, restError.getException(), null))
                .taskMonitorFactory(this)
                .exec();
    }

    public interface CurrentPasswordView extends View, Focus, HasUiHandlers<HideRequestUiHandlers> {

        String getPassword();

        boolean validate();
    }
}
