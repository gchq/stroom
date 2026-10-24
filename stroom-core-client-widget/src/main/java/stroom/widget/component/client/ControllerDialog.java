/*
 * Copyright 2026 Crown Copyright
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

package stroom.widget.component.client;

import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.presenter.client.MyPresenterWidget;

import com.google.gwt.core.client.Scheduler;

import java.util.Objects;
import java.util.function.Consumer;

/// Shows a controller as a dialog, for one use: the dialog is owned by its opener while it is shown,
/// so it sees the opener's context (e.g. [Contexts#READ_ONLY]), and disposed once it is hidden, or
/// with its opener if that is disposed first.
///
/// ```java
/// final FieldEditor editor = fieldEditorProvider.get();
/// editor.read(field, otherNames);
/// ControllerDialog.showOnce(this, editor)
///         .popupType(PopupType.OK_CANCEL_DIALOG)
///         .caption("Edit Field")
///         .onShow(e -> editor.focus())
///         .onHideRequest(e -> ...)
///         .fire();
/// ```
///
/// The popup manager shows presenters, so the controller is shown through a small presenter that
/// owns it; the hide request handler's event (`e.hide()`, `e.reset()`) works as for any dialog.
public final class ControllerDialog {

    // Runs something once the event being handled is done (replaced in JVM tests, which have no
    // GWT scheduler)
    static Consumer<Runnable> deferred = runnable -> Scheduler.get().scheduleDeferred(runnable::run);

    private ControllerDialog() {
        // Static utility
    }

    /// Starts showing a controller as a dialog for one use.
    ///
    /// @param opener What shows it, which owns it until it is hidden.
    /// @param dialog The controller, just made and not owned by anything else. It must have been made
    ///               with the event bus, which the popup manager listens to.
    /// @return The popup's builder: set its type, caption and handlers, then `fire()` it.
    /// @throws IllegalStateException If the controller was made without an event bus.
    public static ShowPopupEvent.Builder showOnce(final HasControllerNode opener, final Controller dialog) {
        Objects.requireNonNull(opener, "opener");
        Objects.requireNonNull(dialog, "dialog");
        if (dialog.getEventBus() == null) {
            throw new IllegalStateException(dialog.getControllerNode().getName()
                                            + " can't be shown as a dialog: it was made without an event bus");
        }
        final DialogHost host = new DialogHost(dialog);
        opener.getControllerNode().adopt(host.getControllerNode());
        disposeWhenHidden(host);
        return ShowPopupEvent.builder(host);
    }

    // Disposes a presenter shown as a popup for one use once it is hidden, after the popup manager
    // has hidden it (see Controller.makeDialog and AppRoot.makeDialog)
    static <P extends MyPresenterWidget<?>> P disposeWhenHidden(final P dialog) {
        final ControllerNode dialogNode = dialog.getControllerNode();
        // Null only for a mock (e.g. Mockito's, in a test)
        if (dialogNode != null) {
            // A registration of the dialog's, so it goes when the dialog is disposed
            dialogNode.add(dialog.getEventBus().addHandlerToSource(HidePopupEvent.getType(), dialog, event ->
                    deferred.accept(dialogNode::dispose)));
        }
        return dialog;
    }


    // --------------------------------------------------------------------------------


    // The presenter the popup manager shows, which owns the controller, so disposing it (once
    // hidden) disposes the controller
    static final class DialogHost extends MyPresenterWidget<ControllerView> {

        private DialogHost(final Controller content) {
            super(content.getEventBus(), new ControllerView(content));
            own(content);
        }

        Controller getContent() {
            return getView().getContent();
        }
    }
}
