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

package stroom.annotation.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.annotation.shared.EventId;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.DialogAction;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// The Link An Event dialog ([AddEventLink]) of an annotation, made for one use: asks for an event's
/// id, as `streamId:eventId`.
public class AddEventLinkController extends Controller {

    private final AddEventLink view;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     The content.
    @Inject
    public AddEventLinkController(final EventBus eventBus, final AddEventLink view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        view.setOnEnter(() -> ControllerDialog.pressAction(this, DialogAction.OK));
    }

    /// Asks for an event's id in a dialog, owned by `opener` until it is closed.
    ///
    /// @param opener What shows it.
    /// @param onOk   Given the event's id when OK is pressed with a valid one.
    public void show(final HasControllerNode opener, final Consumer<EventId> onOk) {
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizableX(300))
                .caption("Link An Event")
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final String text = view.getText();
                        final EventId eventId = parse(text);
                        if (eventId == null) {
                            AlertEvent.fireError(this, "Invalid event id '" + text + "'", e::reset);
                        } else {
                            onOk.accept(eventId);
                            e.hide();
                        }
                    } else {
                        e.hide();
                    }
                })
                .fire();
    }

    /// @param text An event's id as typed, `streamId:eventId`.
    /// @return The id, or null if the text isn't one.
    static EventId parse(final String text) {
        if (text == null) {
            return null;
        }
        final String[] parts = text.split(":");
        if (parts.length != 2) {
            return null;
        }
        try {
            return new EventId(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
        } catch (final NumberFormatException e) {
            return null;
        }
    }
}
