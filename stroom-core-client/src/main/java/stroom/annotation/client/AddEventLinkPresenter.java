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

import stroom.alert.client.event.AlertEvent;
import stroom.annotation.client.AddEventLinkPresenter.AddEventLinkView;
import stroom.annotation.shared.EventId;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.DefaultPopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import com.google.gwt.event.dom.client.HasKeyDownHandlers;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.user.client.ui.HasText;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.function.Consumer;

public class AddEventLinkPresenter extends MyPresenterWidget<AddEventLinkView> {

    private final PopupUiHandlers popupUiHandlers;
    private Consumer<EventId> consumer;

    @Inject
    public AddEventLinkPresenter(final EventBus eventBus, final AddEventLinkView view) {
        super(eventBus, view);

        popupUiHandlers = new DefaultPopupUiHandlers(this) {
            @Override
            public void onShow() {
                super.onShow();
                getView().focus();
            }

            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                if (ok) {
                    final String name = getView().getName().getText();
                    if (name != null) {
                        final String[] parts = name.split(":");
                        if (parts.length != 2) {
                            AlertEvent.fireError(
                                    AddEventLinkPresenter.this,
                                    "Invalid event id '" + name + "'",
                                    null);
                        } else {
                            try {
                                final EventId eventId = new EventId(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
                                consumer.accept(eventId);
                                hide(autoClose, ok);
                            } catch (final NumberFormatException e) {
                                AlertEvent.fireError(
                                        AddEventLinkPresenter.this,
                                        "Invalid event id '" + name + "'",
                                        null);
                            }
                        }
                    }
                } else {
                    hide(autoClose, ok);
                }
            }
        };
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(getView().getNameBox().addKeyDownHandler(event -> {
            if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
                popupUiHandlers.onHideRequest(false, true);
            }
        }));
    }

    public void show(final Consumer<EventId> consumer) {
        this.consumer = consumer;
        getView().getName().setText("");

        final PopupSize popupSize = PopupSize.resizableX();
        ShowPopupEvent.fire(this,
                this,
                PopupType.OK_CANCEL_DIALOG,
                popupSize,
                "Link An Event",
                popupUiHandlers);
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
