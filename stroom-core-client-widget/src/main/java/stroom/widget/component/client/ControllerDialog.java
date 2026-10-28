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

import stroom.widget.popup.client.DialogAction;
import stroom.widget.popup.client.event.DisablePopupEvent;
import stroom.widget.popup.client.event.EnablePopupEvent;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.RenamePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;

import com.google.gwt.core.client.Scheduler;

import java.util.Objects;
import java.util.function.Consumer;

/// Shows a controller as a dialog. Shown for one use ([#showOnce]), the dialog is owned by its
/// opener while it is shown, so it sees the opener's context (e.g. [Contexts#READ_ONLY]), and
/// disposed once it is hidden, or with its opener if that is disposed first. Shown again and again
/// ([#show]), it stays with what owns it, and isn't disposed when it is hidden.
///
/// ```java
/// // In FieldEditorController, whose widget is its component, the dialog's content
/// view.setName(field.getFldName());
/// ControllerDialog.showOnce(opener, this)
///         .popupType(PopupType.OK_CANCEL_DIALOG)
///         .caption("Edit Field")
///         .onShow(e -> view.focus())
///         .onHideRequest(e -> ...)
///         .fire();
/// ```
///
/// The popup manager shows the controller's widget; the hide request handler's event (`e.hide()`,
/// `e.reset()`) works as for any dialog, and while it is shown the dialog's spinner is the
/// controller's task monitor ([Contexts#TASK_MONITOR]), unless it provides one of its own.
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
        checkCanShow(dialog);
        opener.getControllerNode().adopt(dialog.getControllerNode());
        disposeWhenHidden(dialog);
        return ShowPopupEvent.builder(dialog);
    }

    /// Starts showing a controller as a dialog or popup that is shown again, e.g. settings kept
    /// between showings: it stays owned by what owns it, and isn't disposed when it is hidden.
    /// Showing it while it is shown hides it, as the popup manager does for any popup asked to show
    /// twice (a second click on the button that opens it).
    ///
    /// ```java
    /// // In the timeline's controller, which owns its settings' controller
    /// ControllerDialog.show(settings).popupType(PopupType.POPUP).caption("Timeline Settings").fire();
    /// ```
    ///
    /// @param dialog The controller, owned by what shows it. It must have been made with the event
    ///               bus, which the popup manager listens to.
    /// @return The popup's builder: set its type, caption and handlers, then `fire()` it.
    /// @throws IllegalStateException If the controller was made without an event bus.
    public static ShowPopupEvent.Builder show(final Controller dialog) {
        checkCanShow(dialog);
        return ShowPopupEvent.builder(dialog);
    }

    /// Enables or disables one of a dialog's buttons, e.g. OK until something is chosen.
    ///
    /// @param dialog  The controller shown with [#showOnce] or [#show]; nothing happens once a
    ///                dialog shown once is hidden.
    /// @param action  The button's action, e.g. `DialogAction.OK`.
    /// @param enabled Whether the button can be pressed.
    public static void setActionEnabled(final Controller dialog, final DialogAction action, final boolean enabled) {
        if (!dialog.isDisposed()) {
            if (enabled) {
                EnablePopupEvent.builder(dialog).action(action).fire();
            } else {
                DisablePopupEvent.builder(dialog).action(action).fire();
            }
        }
    }

    /// Presses one of a dialog's buttons as the user would, e.g. OK on a double click on what is
    /// chosen: its hide request handler is told, as for any dialog.
    ///
    /// @param dialog The controller shown with [#showOnce] or [#show]; nothing happens once a
    ///               dialog shown once is hidden.
    /// @param action The button's action, e.g. `DialogAction.OK`.
    public static void pressAction(final Controller dialog, final DialogAction action) {
        if (!dialog.isDisposed()) {
            HidePopupRequestEvent.builder(dialog).action(action).fire();
        }
    }

    /// Changes a dialog's caption while it is shown, e.g. to a page's title once it has loaded.
    ///
    /// @param dialog  The controller shown with [#showOnce] or [#show]; nothing happens once a
    ///                dialog shown once is hidden.
    /// @param caption The new caption.
    public static void rename(final Controller dialog, final String caption) {
        if (!dialog.isDisposed()) {
            RenamePopupEvent.builder(dialog).caption(caption).fire();
        }
    }

    /// Hides a dialog at once, without asking its hide request handler, e.g. a sub menu closed with
    /// its menu. One shown with [#showOnce] is then disposed.
    ///
    /// @param dialog    The controller shown with [#showOnce] or [#show]. Hiding it again does
    ///                  nothing (the popup manager no longer has it), and once a dialog shown once is
    ///                  disposed nothing is fired.
    /// @param autoClose Whether it is hidden by a click outside it, as its hide handler is told.
    /// @param ok        Whether it is hidden by its OK action, as its hide handler is told.
    public static void hide(final Controller dialog, final boolean autoClose, final boolean ok) {
        if (!dialog.isDisposed()) {
            HidePopupEvent.builder(dialog).autoClose(autoClose).ok(ok).fire();
        }
    }

    // Disposes a controller shown as a popup for one use once it is hidden, after the popup manager
    // has hidden it (see showOnce, Controller.makeDialog and AppRoot.makeDialog)
    static <C extends Controller> C disposeWhenHidden(final C dialog) {
        final ControllerNode dialogNode = dialog.getControllerNode();
        // Null only for a mock (e.g. Mockito's, in a test)
        if (dialogNode != null) {
            // A registration of the dialog's, so it goes when the dialog is disposed
            dialogNode.add(dialog.getEventBus().addHandlerToSource(HidePopupEvent.getType(), dialog, event ->
                    deferred.accept(dialogNode::dispose)));
        }
        return dialog;
    }

    private static void checkCanShow(final Controller dialog) {
        Objects.requireNonNull(dialog, "dialog");
        if (dialog.getEventBus() == null) {
            throw new IllegalStateException(dialog.getControllerNode().getName()
                                            + " can't be shown as a dialog: it was made without an event bus");
        }
    }
}
