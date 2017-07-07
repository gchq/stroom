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

package stroom.security.client.presenter;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.alert.client.event.AlertEvent;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.security.client.event.ResetPasswordEvent;
import stroom.security.shared.CanEmailPasswordResetAction;
import stroom.security.shared.EmailPasswordResetForUserNameAction;
import stroom.security.shared.LoadUserPropertiesAction;
import stroom.security.shared.SaveUserPropertiesAction;
import stroom.security.shared.UserProperties;
import stroom.security.shared.UserRef;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView;
import stroom.widget.tickbox.client.view.TickBox;

public class UserEditPresenter extends MyPresenterWidget<UserEditPresenter.UserEditView>
        implements UserEditUiHandlers {
    private final ClientDispatchAsync dispatcher;
    private final UserEditAddRemoveUsersPresenter userListAddRemovePresenter;
    private final AppPermissionsPresenter appPermissionsPresenter;

    private UserRef userRef;

    @Inject
    public UserEditPresenter(final EventBus eventBus, final UserEditView view,
                             final ClientDispatchAsync dispatcher, final UserEditAddRemoveUsersPresenter userListAddRemovePresenter, final AppPermissionsPresenter appPermissionsPresenter) {
        super(eventBus, view);
        this.dispatcher = dispatcher;
        this.userListAddRemovePresenter = userListAddRemovePresenter;
        this.appPermissionsPresenter = appPermissionsPresenter;
        view.setUiHandlers(this);

        view.setUserGroupsView(userListAddRemovePresenter.getView());
        view.setAppPermissionsView(appPermissionsPresenter.getView());
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(getView().getLoginNeverExpires().addValueChangeHandler(event -> {
            final UserProperties userProperties = new UserProperties(null, !getView().getLoginNeverExpires().getBooleanValue());
            dispatcher.exec(new SaveUserPropertiesAction(userRef, userProperties)).onSuccess(up -> getView().getLoginNeverExpires().setBooleanValue(!up.getLoginExpires()));
        }));
        registerHandler(getView().getStatusNotEnabled().addValueChangeHandler(event -> {
            final UserProperties userProperties = new UserProperties(!getView().getStatusNotEnabled().getBooleanValue(), null);
            dispatcher.exec(new SaveUserPropertiesAction(userRef, userProperties)).onSuccess(up -> getView().getStatusNotEnabled().setBooleanValue(!up.getStatus()));
        }));
    }

    public void show(final UserRef userRef, final PopupUiHandlers popupUiHandlers) {
        read(userRef);

        final PopupUiHandlers internalPopupUiHandlers = new PopupUiHandlers() {
            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                HidePopupEvent.fire(UserEditPresenter.this, UserEditPresenter.this);
                popupUiHandlers.onHideRequest(autoClose, ok);
            }

            @Override
            public void onHide(final boolean autoClose, final boolean ok) {
                popupUiHandlers.onHide(autoClose, ok);
            }
        };
        final PopupSize popupSize = new PopupSize(500, 600, 500, 600, true);
        final String caption = "User - " + userRef.getName();
        ShowPopupEvent.fire(UserEditPresenter.this, UserEditPresenter.this, PopupView.PopupType.CLOSE_DIALOG,
                popupSize, caption, internalPopupUiHandlers);
    }

    private void read(UserRef userRef) {
        this.userRef = userRef;

        dispatcher.exec(new LoadUserPropertiesAction(userRef)).onSuccess(up -> {
            if (up.getStatus()) {
                getView().getStatusNotEnabled().setValue(TickBoxState.UNTICK);
            } else {
                getView().getStatusNotEnabled().setValue(TickBoxState.TICK);
            }

            getView().getLoginNeverExpires().setBooleanValue(!up.getLoginExpires());
        });

        userListAddRemovePresenter.setUser(userRef);
        appPermissionsPresenter.setUser(userRef);
    }

    @Override
    public void resetPassword() {
        doResetPassword(false);
    }

    @Override
    public void emailResetPassword() {
        doResetPassword(true);
    }

    private void doResetPassword(final boolean email) {
        if (email) {
            dispatcher.exec(new CanEmailPasswordResetAction()).onSuccess(result -> {
                if (result.getBoolean()) {
                    dispatcher.exec(new EmailPasswordResetForUserNameAction(userRef.getName())).onSuccess(user ->
                            AlertEvent.fireInfo(UserEditPresenter.this,
                                    "The password has been reset. An email with the new password has been sent to the users email account.",
                                    null)
                    );
                } else {
                    AlertEvent.fireError(UserEditPresenter.this, "System is not set up to email passwords!",
                            null);
                }
            });
        } else {
            ResetPasswordEvent.fire(UserEditPresenter.this, userRef);
        }
    }

    public interface UserEditView extends View, HasUiHandlers<UserEditUiHandlers> {
        TickBox getStatusNotEnabled();

        TickBox getLoginNeverExpires();

        void setUserGroupsView(View view);

        void setAppPermissionsView(View view);
    }
}
