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

package stroom.preferences.client.component;

import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;
import stroom.widget.util.client.KeyBinding;
import stroom.widget.util.client.KeyBinding.Action;
import stroom.widget.util.client.KeyShortcut;
import stroom.widget.util.client.KeyStroke;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/// A dialog ([KeyShortcutRecorder]), made for one use, in which the user presses the keys of a new
/// keyboard shortcut for an action, as in the keyboard shortcut editors of e.g. VS Code. It says, as
/// the user presses keys, whether the shortcut can be used, and whether it is already used by
/// another action (in which case choosing OK moves it from that action).
public final class KeyShortcutRecorderController extends Controller {

    private final KeyShortcutRecorder view;

    private Action action;
    private Map<Action, List<KeyShortcut>> bindings;
    private boolean characterKeyShortcutsEnabled;
    private KeyShortcut recorded;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     The content.
    @Inject
    public KeyShortcutRecorderController(final EventBus eventBus, final KeyShortcutRecorder view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        view.setOnKeyStroke(this::onKeyStroke);
    }

    /// Shows the dialog, owned by `opener` until it is closed.
    ///
    /// @param opener                       What shows it.
    /// @param action                       The action to record a shortcut for.
    /// @param existing                     The shortcut being changed, or null to add one.
    /// @param bindings                     The shortcuts of the configurable actions, to check
    ///                                     the new shortcut against.
    /// @param characterKeyShortcutsEnabled Whether the user has character key shortcuts turned
    ///                                     on.
    /// @param consumer                     Called if the user chooses OK, with the new shortcut
    ///                                     and the shortcuts of other actions that clash with it,
    ///                                     which should be removed from them.
    public void show(final HasControllerNode opener,
                     final Action action,
                     final KeyShortcut existing,
                     final Map<Action, List<KeyShortcut>> bindings,
                     final boolean characterKeyShortcutsEnabled,
                     final BiConsumer<KeyShortcut, Map<Action, List<KeyShortcut>>> consumer) {
        this.action = action;
        this.bindings = bindings;
        this.characterKeyShortcutsEnabled = characterKeyShortcutsEnabled;
        this.recorded = existing;
        view.setActionName(action.getDisplayName());
        update();

        final String caption = existing == null
                ? "Add Keyboard Shortcut"
                : "Change Keyboard Shortcut";
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizableX(450))
                .caption(caption)
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        if (recorded == null || KeyBinding.findFixedClash(recorded) != null) {
                            // The view says what is wrong, so let the user try again
                            view.focus();
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

    private void onKeyStroke(final KeyStroke keyStroke) {
        recorded = KeyShortcut.record(recorded, keyStroke);
        update();
    }

    private void update() {
        view.setShortcut(recorded == null
                ? null
                : recorded.toString());
        view.setError(getError());
        view.setMessage(getMessage());
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

}
