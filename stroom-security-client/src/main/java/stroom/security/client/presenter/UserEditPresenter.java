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

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.alert.client.event.AlertEvent;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.dispatch.client.AsyncCallbackAdaptor;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.client.event.ReloadEntityEvent;
import stroom.entity.shared.EntityServiceSaveAction;
import stroom.security.client.event.ResetPasswordEvent;
import stroom.security.shared.CanEmailPasswordResetAction;
import stroom.security.shared.EmailPasswordResetForUserAction;
import stroom.security.shared.User;
import stroom.security.shared.UserRef;
import stroom.util.shared.SharedBoolean;
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

    private User user;

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
        registerHandler(getView().getLoginNeverExpires().addValueChangeHandler(new ValueChangeHandler<TickBoxState>() {
            @Override
            public void onValueChange(ValueChangeEvent<TickBoxState> event) {
                user.setLoginExpiry(!getView().getLoginNeverExpires().getBooleanValue());
                save();
            }
        }));
        registerHandler(getView().getStatusNotEnabled().addValueChangeHandler(new ValueChangeHandler<TickBoxState>() {
            @Override
            public void onValueChange(ValueChangeEvent<TickBoxState> event) {
                user.setStatusEnabled(!getView().getStatusNotEnabled().getBooleanValue());
                save();
            }
        }));
        registerHandler(getEventBus().addHandler(ReloadEntityEvent.getType(), new ReloadEntityEvent.Handler() {
            @Override
            public void onReload(ReloadEntityEvent event) {
                if (event.getEntity().equals(user)) {
                    read((User) event.getEntity());
                }
            }
        }));
    }

    public void show(final User user, final PopupUiHandlers popupUiHandlers) {
        read(user);

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
        final String caption = "User - " + user.getName();
        ShowPopupEvent.fire(UserEditPresenter.this, UserEditPresenter.this, PopupView.PopupType.CLOSE_DIALOG,
                popupSize, caption, internalPopupUiHandlers);
    }

    private void read(User user) {
        this.user = user;

        // Set the status.
        if (!user.isStatusEnabled()) {
            getView().getStatusNotEnabled().setValue(TickBoxState.TICK);
        } else {
            getView().getStatusNotEnabled().setValue(TickBoxState.UNTICK);
        }

        getView().getLoginNeverExpires().setBooleanValue(!user.isLoginExpiry());

        userListAddRemovePresenter.setUser(UserRef.create(user));
        appPermissionsPresenter.setUser(UserRef.create(user));
    }

    private void save() {
        dispatcher.execute(new EntityServiceSaveAction<>(user), new AsyncCallbackAdaptor<User>() {
            @Override
            public void onSuccess(final User result) {
                read(result);
            }
        });
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
            dispatcher.execute(new CanEmailPasswordResetAction(), new AsyncCallbackAdaptor<SharedBoolean>() {
                @Override
                public void onSuccess(final SharedBoolean result) {
                    if (result.getBoolean()) {
                        dispatcher.execute(new EmailPasswordResetForUserAction(user), new AsyncCallbackAdaptor<User>() {
                            @Override
                            public void onSuccess(final User user) {
                                read(user);
                                AlertEvent.fireInfo(UserEditPresenter.this,
                                        "The password has been reset. An email with the new password has been sent to the users email account.",
                                        null);
                            }
                        });
                    } else {
                        AlertEvent.fireError(UserEditPresenter.this, "System is not set up to email passwords!",
                                null);
                    }
                }
            });
        } else {
            ResetPasswordEvent.fire(UserEditPresenter.this, user);
        }
    }

    public interface UserEditView extends View, HasUiHandlers<UserEditUiHandlers> {
        TickBox getStatusNotEnabled();

        TickBox getLoginNeverExpires();

        void setUserGroupsView(View view);

        void setAppPermissionsView(View view);
    }
}
