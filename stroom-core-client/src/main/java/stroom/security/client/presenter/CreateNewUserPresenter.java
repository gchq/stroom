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

import stroom.security.client.presenter.CreateNewUserPresenter.CreateNewUserView;
import stroom.widget.popup.client.event.DialogEvent;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.view.DialogAction;
import stroom.widget.popup.client.view.DialogActionUiHandlers;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class CreateNewUserPresenter extends MyPresenterWidget<CreateNewUserView> implements DialogActionUiHandlers {

    @Inject
    public CreateNewUserPresenter(final EventBus eventBus,
                                  final CreateNewUserView view) {
        super(eventBus, view);
    }

    public void show(final HidePopupRequestEvent.Handler handler) {
        getView().setUiHandlers(this);
        getView().clear();

        final PopupSize popupSize = PopupSize.resizableX(600);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption("Add External Identity Provider User Identity")
                .onShow(e -> getView().focus())
                .onHideRequest(handler)
                .fire();
    }

    @Override
    public void onDialogAction(final DialogAction action) {
        DialogEvent.fire(this, this, action);
    }


    // --------------------------------------------------------------------------------


    public interface CreateNewUserView extends View, HasUiHandlers<DialogActionUiHandlers> {

        String getSubjectId();

        String getDisplayName();

        String getFullName();

        void focus();

        void clear();
    }
}
