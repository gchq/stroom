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

package stroom.annotation.client;

import com.google.gwt.event.dom.client.HasKeyDownHandlers;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.user.client.ui.HasText;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.alert.client.event.AlertEvent;
import stroom.annotation.client.AddEventLinkPresenter.AddEventLinkView;
import stroom.annotation.shared.EventId;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import java.util.function.Consumer;

public class AddEventLinkPresenter extends MyPresenterWidget<AddEventLinkView> implements PopupUiHandlers {
    @Inject
    public AddEventLinkPresenter(final EventBus eventBus, final AddEventLinkView view) {
        super(eventBus, view);
    }

    private Consumer<EventId> consumer;

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(getView().getNameBox().addKeyDownHandler(event -> {
            if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
                onHideRequest(false, true);
            }
        }));
    }

    public void show(final Consumer<EventId> consumer) {
        this.consumer = consumer;
        getView().getName().setText("");

        final PopupSize popupSize = new PopupSize(250, 78, 250, 78, 1000, 78, true);
        ShowPopupEvent.fire(this, this, PopupType.OK_CANCEL_DIALOG, popupSize, "Link An Event", this);
        getView().focus();
    }

    @Override
    public void onHideRequest(final boolean autoClose, final boolean ok) {
        if (ok) {
            final String name = getView().getName().getText();
            if (name != null) {
                final String[] parts = name.split(":");
                if (parts.length != 2) {
                    AlertEvent.fireError(this, "Invalid event id '" + name + "'", null);
                } else {
                    try {
                        final EventId eventId = new EventId(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
                        consumer.accept(eventId);
                        HidePopupEvent.fire(this, this);
                    } catch (final NumberFormatException e) {
                        AlertEvent.fireError(this, "Invalid event id '" + name + "'", null);
                    }
                }
            }
        } else {
            HidePopupEvent.fire(this, this);
        }
    }

    @Override
    public void onHide(final boolean autoClose, final boolean ok) {
    }

    public String getName() {
        return getView().getName().getText();
    }

    public interface AddEventLinkView extends View {
        HasText getName();

        HasKeyDownHandlers getNameBox();

        void focus();
    }
}
