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
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.view.DefaultHideRequestUiHandlers;
import stroom.widget.popup.client.view.HideRequestUiHandlers;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.function.Consumer;

public class NamePresenter
        extends MyPresenterWidget<NamePresenter.NameView>
        implements HidePopupRequestEvent.Handler {

    private Consumer<String> nameConsumer;

    @Inject
    public NamePresenter(final EventBus eventBus, final NameView view) {
        super(eventBus, view);
    }

    public void show(final String name,
                     final String caption,
                     final Consumer<String> nameConsumer) {
        this.nameConsumer = nameConsumer;
        getView().setName(name);
        getView().setUiHandlers(new DefaultHideRequestUiHandlers(this));
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
                        null);

            } else {
                nameConsumer.accept(entityName);
            }
        } else {
            e.hide();
        }
    }

    public void hide() {
        HidePopupEvent.builder(this).fire();
    }

    public interface NameView extends View, Focus, HasUiHandlers<HideRequestUiHandlers> {

        String getName();

        void setName(String name);
    }
}
