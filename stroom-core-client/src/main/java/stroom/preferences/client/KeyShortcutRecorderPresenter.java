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

package stroom.preferences.client;

import stroom.preferences.client.KeyShortcutRecorderPresenter.KeyShortcutRecorderView;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.KeyBinding;
import stroom.widget.util.client.KeyBinding.Action;
import stroom.widget.util.client.KeyShortcut;
import stroom.widget.util.client.KeyStroke;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/// A dialog in which the user presses the keys of a new keyboard shortcut for an action, as in
/// the keyboard shortcut editors of e.g. VS Code. It says, as the user presses keys, whether the
/// shortcut can be used, and whether it is already used by another action (in which case
/// choosing OK moves it from that action).
public final class KeyShortcutRecorderPresenter
        extends MyPresenterWidget<KeyShortcutRecorderView>
        implements KeyShortcutRecorderUiHandlers {

    private Action action;
    private Map<Action, List<KeyShortcut>> bindings;
    private boolean characterKeyShortcutsEnabled;
    private KeyShortcut recorded;

    @Inject
    public KeyShortcutRecorderPresenter(final EventBus eventBus,
                                        final KeyShortcutRecorderView view) {
        super(eventBus, view);
        view.setUiHandlers(this);
    }

    /// Shows the dialog.
    ///
    /// @param action                       The action to record a shortcut for.
    /// @param existing                     The shortcut being changed, or null to add one.
    /// @param bindings                     The shortcuts of the configurable actions, to check
    ///                                     the new shortcut against.
    /// @param characterKeyShortcutsEnabled Whether the user has character key shortcuts turned
    ///                                     on.
    /// @param consumer                     Called if the user chooses OK, with the new shortcut
    ///                                     and the shortcuts of other actions that clash with it,
    ///                                     which should be removed from them.
    public void show(final Action action,
                     final KeyShortcut existing,
                     final Map<Action, List<KeyShortcut>> bindings,
                     final boolean characterKeyShortcutsEnabled,
                     final BiConsumer<KeyShortcut, Map<Action, List<KeyShortcut>>> consumer) {
        this.action = action;
        this.bindings = bindings;
        this.characterKeyShortcutsEnabled = characterKeyShortcutsEnabled;
        this.recorded = existing;
        getView().setActionName(action.getDisplayName());
        update();

        final String caption = existing == null
                ? "Add Keyboard Shortcut"
                : "Change Keyboard Shortcut";
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizableX(450))
                .caption(caption)
                .onShow(e -> getView().focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        if (recorded == null || KeyBinding.findFixedClash(recorded) != null) {
                            // The view says what is wrong, so let the user try again
                            getView().focus();
                        } else {
                            consumer.accept(recorded, KeyBinding.findClashes(bindings, action, recorded));
                            e.hide();
                        }
                    } else {
                        e.hide();
                    }
                })
                .onHide(e -> KeyBinding.setRecording(false))
                .fire();
    }

    @Override
    public void onKeyStroke(final KeyStroke keyStroke) {
        recorded = KeyShortcut.record(recorded, keyStroke);
        update();
    }

    private void update() {
        getView().setShortcut(recorded == null
                ? null
                : recorded.toString());
        getView().setError(getError());
        getView().setMessage(getMessage());
    }

    private String getError() {
        if (recorded == null) {
            return null;
        }
        final Action fixedAction = KeyBinding.findFixedClash(recorded);
        if (fixedAction != null) {
            return recorded + " is used for '" + fixedAction.getDisplayName()
                   + "', which can't be changed. Press other keys.";
        }
        return null;
    }

    private String getMessage() {
        if (recorded == null) {
            return "Press the keys for the shortcut.";
        } else if (KeyBinding.findFixedClash(recorded) != null) {
            return null;
        }
        final StringBuilder sb = new StringBuilder();
        final Map<Action, List<KeyShortcut>> clashes = KeyBinding.findClashes(bindings, action, recorded);
        if (!clashes.isEmpty()) {
            sb.append(recorded)
                    .append(" clashes with the shortcut for ")
                    .append(clashes.entrySet()
                            .stream()
                            .map(entry -> "'" + entry.getKey().getDisplayName() + "' ("
                                          + entry.getValue()
                                                  .stream()
                                                  .map(KeyShortcut::toString)
                                                  .collect(Collectors.joining(", "))
                                          + ")")
                            .collect(Collectors.joining(", ")))
                    .append(". Choosing OK removes it from there. ");
        }
        if (recorded.isCharacterKeyShortcut() && !characterKeyShortcutsEnabled) {
            sb.append("Character key shortcuts are turned off, so this shortcut won't work until you turn "
                      + "them on. ");
        } else if (!recorded.getFirst().hasCommandModifier()) {
            sb.append("Shortcuts without Ctrl, Alt or Meta don't work while you are typing in a text field. ");
        }
        sb.append("Press Enter to use ").append(recorded).append('.');
        return sb.toString();
    }


    // --------------------------------------------------------------------------------


    public interface KeyShortcutRecorderView
            extends View, Focus, HasUiHandlers<KeyShortcutRecorderUiHandlers> {

        /// @param actionName The name of the action the shortcut is for.
        void setActionName(String actionName);

        /// @param shortcut The text form of the shortcut recorded so far, or null if none.
        void setShortcut(String shortcut);

        /// @param error Why the shortcut can't be used, or null if it can.
        void setError(String error);

        /// @param message What the user can do next, and anything they should know about the
        ///                shortcut, or null for nothing.
        void setMessage(String message);
    }
}
