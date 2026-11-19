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

package stroom.alert.client.component;

import stroom.alert.client.event.CommonAlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;
import stroom.widget.popup.client.Size;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// An alert or confirmation ([CommonAlert]) in a dialog, made for one use.
public class CommonAlertController extends Controller {

    private final CommonAlert view;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     The content.
    @Inject
    public CommonAlertController(final EventBus eventBus, final CommonAlert view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
    }

    /// Shows an alert, or a confirmation, in a dialog, owned by `opener` until it is closed.
    ///
    /// @param opener   What shows it.
    /// @param event    The alert or confirmation.
    /// @param onHidden Told, once it is closed, whether it was closed with OK.
    public void show(final HasControllerNode opener,
                     final CommonAlertEvent<?> event,
                     final Consumer<Boolean> onHidden) {
        // One with no level is an error, as it always was
        if (event.getLevel() == null) {
            view.setError(event.getMessage());
        } else {
            switch (event.getLevel()) {
                case INFO -> view.setInfo(event.getMessage());
                case QUESTION -> view.setQuestion(event.getMessage());
                case WARN -> view.setWarn(event.getMessage());
                default -> view.setError(event.getMessage());
            }
        }
        view.setDetail(event.getDetail());

        final PopupSize popupSize = PopupSize.builder()
                .width(Size.builder()
                        .initial(600)
                        .min(200)
                        .resizable(true)
                        .build())
                .height(Size.builder()
                        .resizable(true)
                        .build())
                .build();
        final boolean confirm = event instanceof ConfirmEvent;
        ControllerDialog.showOnce(opener, this)
                .popupType(confirm
                        ? PopupType.OK_CANCEL_DIALOG
                        : PopupType.CLOSE_DIALOG)
                .caption(confirm
                        ? "Confirm"
                        : "Alert")
                .modal(true)
                .popupSize(popupSize)
                .onHide(e -> onHidden.accept(e.isOk()))
                .fire();
    }
}
