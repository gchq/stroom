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

package stroom.dashboard.client.query;

import stroom.alert.client.event.AlertEvent;
import stroom.widget.popup.client.event.DialogEvent;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.view.DialogAction;
import stroom.widget.popup.client.view.DialogActionUiHandlers;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.function.BiConsumer;

public class NamePresenter
        extends MyPresenterWidget<NamePresenter.NameView>
        implements HidePopupRequestEvent.Handler,
        DialogActionUiHandlers {

    private BiConsumer<String, HidePopupRequestEvent> nameConsumer;

    @Inject
    public NamePresenter(final EventBus eventBus, final NameView view) {
        super(eventBus, view);
    }

    public void show(final String name,
                     final String caption,
                     final BiConsumer<String, HidePopupRequestEvent> nameConsumer) {
        this.nameConsumer = nameConsumer;
        getView().setName(name);
        getView().setUiHandlers(this);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .caption(caption)
                .onShow(e -> getView().focus())
                .onHideRequest(this)
                .fire();
    }

    @Override
    public void onHideRequest(final HidePopupRequestEvent e) {
        if (e.isOk()) {
            String entityName = getView().getName();
            if (entityName != null) {
                entityName = entityName.trim();
            }

            if (entityName == null || entityName.length() == 0) {
                AlertEvent.fireWarn(this,
                        "You must provide a name",
                        e::reset);

            } else {
                nameConsumer.accept(entityName, e);
            }
        } else {
            e.hide();
        }
    }

    @Override
    public void onDialogAction(final DialogAction action) {
        DialogEvent.fire(this, this, action);
    }

    public interface NameView extends View, Focus, HasUiHandlers<DialogActionUiHandlers> {

        String getName();

        void setName(String name);
    }
}
