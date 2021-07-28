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

import stroom.security.client.presenter.UserEditPresenter.UserEditView;
import stroom.security.shared.User;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.DefaultPopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView;
import stroom.widget.popup.client.presenter.Size;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class UserEditPresenter extends MyPresenterWidget<UserEditView>
        implements UserEditUiHandlers {

    private final UserEditAddRemoveUsersPresenter userListAddRemovePresenter;
    private final AppPermissionsPresenter appPermissionsPresenter;

    @Inject
    public UserEditPresenter(final EventBus eventBus,
                             final UserEditView view,
                             final UserEditAddRemoveUsersPresenter userListAddRemovePresenter,
                             final AppPermissionsPresenter appPermissionsPresenter) {
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

    public void show(final User userRef, final Runnable closeRunnable) {
        read(userRef);

        final PopupUiHandlers popupUiHandlers = new DefaultPopupUiHandlers(this) {
            @Override
            public void onHide(final boolean autoClose, final boolean ok) {
                closeRunnable.run();
            }
        };
        final PopupSize popupSize = PopupSize.builder()
                .width(Size
                        .builder()
                        .initial(500)
                        .min(500)
                        .resizable(true)
                        .build())
                .height(Size
                        .builder()
                        .initial(600)
                        .min(600)
                        .resizable(true)
                        .build())
                .build();
        final String caption = "User - " + userRef.getName();
        ShowPopupEvent.fire(
                UserEditPresenter.this,
                UserEditPresenter.this,
                PopupView.PopupType.CLOSE_DIALOG,
                popupSize,
                caption,
                popupUiHandlers);
    }

    private void read(User userRef) {
        userListAddRemovePresenter.setUser(userRef);
        appPermissionsPresenter.setUser(userRef);
    }

    public interface UserEditView extends View, HasUiHandlers<UserEditUiHandlers> {

        void setUserGroupsView(View view);

        void setAppPermissionsView(View view);
    }
}
