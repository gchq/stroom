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

import stroom.dispatch.client.RestFactory;
import stroom.security.identity.client.presenter.ChangePasswordPresenter.ChangePasswordView;
import stroom.security.identity.shared.AuthenticationResource;
import stroom.security.identity.shared.InternalIdpPasswordPolicyConfig;
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

import java.util.function.Consumer;

public class ChangePasswordPresenter extends MyPresenterWidget<ChangePasswordView> {

    private static final AuthenticationResource RESOURCE = GWT.create(AuthenticationResource.class);

    private final RestFactory restFactory;

    @Inject
    public ChangePasswordPresenter(final EventBus eventBus,
                                   final ChangePasswordView view,
                                   final RestFactory restFactory) {
        super(eventBus, view);
        this.restFactory = restFactory;

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

    public void show(final String caption, final Consumer<HidePopupRequestEvent> consumer) {
        restFactory
                .create(RESOURCE)
                .method(AuthenticationResource::fetchPasswordPolicy)
                .onSuccess(r -> {
                    getView().setPolicy(r);
                    ShowPopupEvent.builder(this)
                            .popupType(PopupType.OK_CANCEL_DIALOG)
                            .icon(SvgImage.PASSWORD)
                            .caption(caption)
                            .modal(true)
                            .onShow((event) -> getView().focus())
                            .onHideRequest(consumer::accept)
                            .fire();
                })
                .taskMonitorFactory(this)
                .exec();
    }

    public String getPassword() {
        return getView().getPassword();
    }

    public String getConfirmPassword() {
        return getView().getConfirmPassword();
    }

    public boolean validate() {
        return getView().validate();
    }

    public interface ChangePasswordView extends View, Focus, HasUiHandlers<HideRequestUiHandlers> {

        void setPolicy(InternalIdpPasswordPolicyConfig passwordPolicyConfig);

        String getPassword();

        String getConfirmPassword();

        boolean validate();
    }
}
