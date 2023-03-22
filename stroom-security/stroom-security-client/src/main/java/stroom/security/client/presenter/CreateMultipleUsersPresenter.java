/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.security.client.presenter;

import stroom.security.client.presenter.CreateMultipleUsersPresenter.CreateMultipleUsersView;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class CreateMultipleUsersPresenter extends MyPresenterWidget<CreateMultipleUsersView> {

    @Inject
    public CreateMultipleUsersPresenter(final EventBus eventBus,
                                        final CreateMultipleUsersView view) {
        super(eventBus, view);
    }

    public void show(final PopupUiHandlers popupUiHandlers) {
        getView().setUiHandlers(popupUiHandlers);

        final PopupSize popupSize = PopupSize.resizable(600, 600);
        ShowPopupEvent.fire(this,
                this,
                PopupType.OK_CANCEL_DIALOG,
                popupSize,
                "Add Multiple External Identity Provider Users",
                popupUiHandlers);
        getView().clear();
        getView().focus();
    }

    public void hide() {
        HidePopupEvent.fire(this, this);
    }

    public String getUsersCsvData() {
        return getView().getUsersCsvData();
    }

    public interface CreateMultipleUsersView extends View, HasUiHandlers<PopupUiHandlers> {

        String getUsersCsvData();

        void focus();

        void clear();
    }
}
