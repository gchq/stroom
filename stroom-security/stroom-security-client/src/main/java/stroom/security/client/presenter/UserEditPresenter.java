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
import stroom.security.shared.UserRef;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView;

public class UserEditPresenter extends MyPresenterWidget<UserEditPresenter.UserEditView>
        implements UserEditUiHandlers {
    private final UserEditAddRemoveUsersPresenter userListAddRemovePresenter;
    private final AppPermissionsPresenter appPermissionsPresenter;

    @Inject
    public UserEditPresenter(final EventBus eventBus, final UserEditView view, final UserEditAddRemoveUsersPresenter userListAddRemovePresenter, final AppPermissionsPresenter appPermissionsPresenter) {
        super(eventBus, view);
        this.userListAddRemovePresenter = userListAddRemovePresenter;
        this.appPermissionsPresenter = appPermissionsPresenter;
        view.setUiHandlers(this);

        view.setUserGroupsView(userListAddRemovePresenter.getView());
        view.setAppPermissionsView(appPermissionsPresenter.getView());
    }

    @Override
    protected void onBind() {
        super.onBind();
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
        userListAddRemovePresenter.setUser(userRef);
        appPermissionsPresenter.setUser(userRef);
    }

    public interface UserEditView extends View, HasUiHandlers<UserEditUiHandlers> {
        void setUserGroupsView(View view);

        void setAppPermissionsView(View view);
    }
}
