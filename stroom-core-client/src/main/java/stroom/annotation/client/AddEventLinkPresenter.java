/*
 * Copyright 2016-2025 Crown Copyright
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
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.event.dom.client.HasKeyDownHandlers;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.HasText;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.function.Consumer;

public class AddEventLinkPresenter extends MyPresenterWidget<AddEventLinkView> {

    @Inject
    public AddEventLinkPresenter(final EventBus eventBus, final AddEventLinkView view) {
        super(eventBus, view);
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(getView().getNameBox().addKeyDownHandler(event -> {
            if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
                hide();
            }
        }));
    }

    public void show(final Consumer<EventId> consumer) {
        getView().getName().setText("");
        final PopupSize popupSize = PopupSize.resizableX(300);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption("Link An Event")
                .onShow((event) -> getView().focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final String name = getView().getName().getText();
                        if (name != null) {
                            final String[] parts = name.split(":");
                            if (parts.length != 2) {
                                AlertEvent.fireError(
                                        AddEventLinkPresenter.this,
                                        "Invalid event id '" + name + "'",
                                        e::reset);
                            } else {
                                try {
                                    final EventId eventId = new EventId(Long.parseLong(parts[0]),
                                            Long.parseLong(parts[1]));
                                    consumer.accept(eventId);
                                    e.hide();
                                } catch (final NumberFormatException ex) {
                                    AlertEvent.fireError(
                                            AddEventLinkPresenter.this,
                                            "Invalid event id '" + name + "'",
                                            e::reset);
                                }
                            }
                        }
                    } else {
                        e.hide();
                    }
                })
                .fire();
    }

    private void hide() {
        HidePopupRequestEvent.builder(this).fire();
    }

    public String getName() {
        return getView().getName().getText();
    }

    public interface AddEventLinkView extends View, Focus {

        HasText getName();

        HasKeyDownHandlers getNameBox();
    }
}
