/*
 * Copyright 2016 Crown Copyright
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

package stroom.changepassword.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.changepassword.client.presenter.ChangePasswordPresenter.ChangePasswordView;
import stroom.dispatch.client.RestFactory;
import stroom.security.client.CurrentUser;
import stroom.security.shared.changepassword.ChangePasswordRequest;
import stroom.security.shared.changepassword.InternalIdpPasswordPolicyConfig;
import stroom.security.shared.changepassword.AuthenticationResource2;
import stroom.svg.shared.SvgImage;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.view.HideRequest;
import stroom.widget.popup.client.view.HideRequestUiHandlers;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.function.BiConsumer;

public class ChangePasswordPresenter extends MyPresenterWidget<ChangePasswordView> {

    private static final AuthenticationResource2 RESOURCE = GWT.create(AuthenticationResource2.class);

    private final RestFactory restFactory;
    private final CurrentUser currentUser;

    @Inject
    public ChangePasswordPresenter(final EventBus eventBus,
                                   final ChangePasswordView view,
                                   final RestFactory restFactory,
                                   final CurrentUser currentUser) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.currentUser = currentUser;

        view.setUiHandlers(new HideRequestUiHandlers() {
            @Override
            public void hideRequest(final HideRequest request) {
                HidePopupRequestEvent.builder(ChangePasswordPresenter.this).ok(request.isOk()).fire();
            }
        });
    }

    @Override
    protected void onBind() {
        super.onBind();
    }

    public void showChangePassword(final String currentPassword) {
        restFactory
                .create(RESOURCE)
                .method(AuthenticationResource2::fetchPasswordPolicy)
                .onSuccess(r -> {
                    getView().setPolicy(r);
                    ShowPopupEvent.builder(this)
                            .popupType(PopupType.OK_CANCEL_DIALOG)
                            .icon(SvgImage.PASSWORD)
                            .caption("Change Password")
                            .modal(true)
                            .onShow((event) -> getView().focus())
                            .onHideRequest(e -> {
                                if (e.isOk()) {
                                    if (getView().validate()) {
                                        changePassword(e, currentPassword);
                                    } else {
                                        e.reset();
                                    }
                                } else {
                                    e.hide();
                                }
                            })
                            .fire();
                })
                .taskListener(this)
                .exec();
    }

    public void showSetPassword(final String caption, final BiConsumer<String, String>  consumer) {
        restFactory
                .create(RESOURCE)
                .method(AuthenticationResource2::fetchPasswordPolicy)
                .onSuccess(r -> {
                    getView().setPolicy(r);
                    ShowPopupEvent.builder(this)
                            .popupType(PopupType.OK_CANCEL_DIALOG)
                            .icon(SvgImage.PASSWORD)
                            .caption(caption)
                            .modal(true)
                            .onShow((event) -> getView().focus())
                            .onHideRequest(e -> {
                                if (e.isOk()) {
                                    if (getView().validate()) {
                                        consumer.accept(getView().getPassword(), getView().getConfirmPassword());
                                        e.hide();
                                    } else {
                                        e.reset();
                                    }
                                } else {
                                    e.hide();
                                }
                            })
                            .fire();
                })
                .taskListener(this)
                .exec();
    }

    private void changePassword(final HidePopupRequestEvent event,
                                final String currentPassword) {
        final ChangePasswordRequest request = new ChangePasswordRequest(
                currentUser.getUserName().getSubjectId(),
                currentPassword,
                getView().getPassword(),
                getView().getConfirmPassword());
        restFactory
                .create(RESOURCE)
                .method(res -> res.changePassword(request))
                .onSuccess(r -> {
                    if (r.isChangeSucceeded()) {
                        AlertEvent.fireInfo(this, "Password changed", event::hide);
                    } else {
                        AlertEvent.fireError(this, r.getMessage(), event::reset);
                    }
                })
                .taskListener(this)
                .exec();


    }

    public interface ChangePasswordView extends View, Focus, HasUiHandlers<HideRequestUiHandlers> {

        void setPolicy(InternalIdpPasswordPolicyConfig passwordPolicyConfig);

        String getPassword();

        String getConfirmPassword();

        boolean validate();
    }
}
