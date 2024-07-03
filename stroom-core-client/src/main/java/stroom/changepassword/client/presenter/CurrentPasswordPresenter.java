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
import stroom.changepassword.client.presenter.CurrentPasswordPresenter.CurrentPasswordView;
import stroom.dispatch.client.RestFactory;
import stroom.security.shared.changepassword.ConfirmPasswordRequest;
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
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class CurrentPasswordPresenter extends MyPresenterWidget<CurrentPasswordView> {

    private static final AuthenticationResource2 RESOURCE = GWT.create(AuthenticationResource2.class);

    private final RestFactory restFactory;

    private final Provider<ChangePasswordPresenter> changePasswordPresenterProvider;

    @Inject
    public CurrentPasswordPresenter(final EventBus eventBus,
                                    final CurrentPasswordView view,
                                    final RestFactory restFactory,
                                    final Provider<ChangePasswordPresenter> changePasswordPresenterProvider) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.changePasswordPresenterProvider = changePasswordPresenterProvider;
        view.setUiHandlers(new HideRequestUiHandlers() {
            @Override
            public void hideRequest(final HideRequest request) {
                HidePopupRequestEvent.builder(CurrentPasswordPresenter.this).ok(request.isOk()).fire();
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
                        changePasswordPresenterProvider.get().showChangePassword(getView().getPassword());
                        event.hide();
                    } else {
                        AlertEvent.fireError(this, r.getMessage(), event::reset);
                    }
                })
                .taskListener(this)
                .exec();
    }

    public interface CurrentPasswordView extends View, Focus, HasUiHandlers<HideRequestUiHandlers> {

        String getPassword();

        boolean validate();
    }
}
