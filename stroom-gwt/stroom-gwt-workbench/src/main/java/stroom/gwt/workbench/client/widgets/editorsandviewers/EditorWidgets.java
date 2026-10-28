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

package stroom.gwt.workbench.client.widgets.editorsandviewers;

import stroom.data.client.component.DataTextPane;
import stroom.data.client.component.DataTextPaneController;
import stroom.editor.client.component.AceEditorController;
import stroom.editor.client.component.CodeEditor;
import stroom.editor.client.component.CodeEditorController;
import stroom.editor.client.presenter.CurrentPreferences;
import stroom.editor.client.presenter.DelegatingAceCompleter;
import stroom.gwt.workbench.client.StroomThemeDecorator;
import stroom.ui.config.shared.UserPreferences.Toggle;
import stroom.widget.util.client.GlobalKeyHandler;

import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.EventBus;

/// Widgets shared by the `Widgets/Editors & Viewers/*` stories (and the view states sheet).
public final class EditorWidgets {

    /// The style of an editor frame's border.
    static final String FRAME_BORDER = "1px solid var(--code-editor__border-color, #444)";

    // Shared by every rendering: it is a singleton in Stroom, and creating one adds a completion
    // provider to Ace that can't be removed.
    private static DelegatingAceCompleter delegatingAceCompleter;

    private EditorWidgets() {
        // Static utility
    }

    /// Creates Stroom's real code editor, a controller attached to its component, as Stroom's
    /// screens make one.
    ///
    /// @param eventBus The event bus, on which the editor's context menu is shown.
    /// @return The editor's controller, whose widget is the editor.
    public static CodeEditorController codeEditor(final EventBus eventBus) {
        return codeEditorController(eventBus).attach(new CodeEditor());
    }

    /// Creates Stroom's code editor controller, as GIN would; it is given its editor when attached.
    ///
    /// @param eventBus The event bus, on which the editor's context menu is shown.
    /// @return The controller.
    public static CodeEditorController codeEditorController(final EventBus eventBus) {
        return new CodeEditorController(
                eventBus,
                new AceEditorController(
                        eventBus,
                        getDelegatingAceCompleter(),
                        currentPreferences(),
                        new NoOpGlobalKeyHandler()));
    }

    /// Creates Stroom's data text pane (the editor used to show stream data), as GIN would.
    ///
    /// @param eventBus The event bus.
    /// @return The pane's controller.
    static DataTextPaneController dataTextPane(final EventBus eventBus) {
        return new DataTextPaneController(
                eventBus,
                new DataTextPane(),
                () -> codeEditorController(eventBus));
    }

    /// An editor frame, a `<div>` with a fixed size and a
    /// [#FRAME_BORDER], as Ace needs a laid-out box.
    ///
    /// @param widget The widget to frame.
    /// @param width  The CSS width, e.g. `720px`.
    /// @param height The CSS height, e.g. `380px`.
    /// @return The frame holding the widget.
    static FlowPanel frame(final Widget widget, final String width, final String height) {
        final FlowPanel frame = new FlowPanel();
        frame.getElement().getStyle().setProperty("width", width);
        frame.getElement().getStyle().setProperty("height", height);
        frame.getElement().getStyle().setProperty("border", FRAME_BORDER);
        frame.getElement().getStyle().setProperty("position", "relative");
        frame.getElement().getStyle().setProperty("display", "flex");
        frame.getElement().getStyle().setProperty("flexDirection", "column");
        widget.getElement().getStyle().setProperty("flex", "1 1 auto");
        widget.getElement().getStyle().setProperty("minHeight", "0");
        frame.add(widget);
        return frame;
    }

    private static DelegatingAceCompleter getDelegatingAceCompleter() {
        if (delegatingAceCompleter == null) {
            delegatingAceCompleter = new DelegatingAceCompleter();
        }
        return delegatingAceCompleter;
    }

    /// The editor preferences of the workbench's Stroom theme, with Stroom's defaults.
    private static CurrentPreferences currentPreferences() {
        final CurrentPreferences currentPreferences = new CurrentPreferences();
        currentPreferences.setTheme(StroomThemeDecorator.getStroomThemeName());
        currentPreferences.setEditorKeyBindings("STANDARD");
        currentPreferences.setEditorLiveAutoCompletion(Toggle.OFF);
        return currentPreferences;
    }


    // --------------------------------------------------------------------------------


    /// Stroom's global key handler runs its keyboard shortcuts, which the workbench doesn't have.
    private static class NoOpGlobalKeyHandler implements GlobalKeyHandler {

        @Override
        public void onKeyDown(final KeyDownEvent event) {
            // No shortcuts
        }

        @Override
        public void onKeyUp(final KeyUpEvent event) {
            // No shortcuts
        }
    }
}
