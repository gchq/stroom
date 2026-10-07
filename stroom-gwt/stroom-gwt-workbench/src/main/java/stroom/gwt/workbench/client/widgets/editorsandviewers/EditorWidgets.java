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

import stroom.data.client.presenter.TextPresenter;
import stroom.data.client.view.TextViewImpl;
import stroom.editor.client.presenter.CurrentPreferences;
import stroom.editor.client.presenter.DelegatingAceCompleter;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.editor.client.view.EditorMenuPresenter;
import stroom.editor.client.view.EditorViewImpl;
import stroom.gwt.workbench.client.StroomThemeDecorator;
import stroom.ui.config.shared.UserPreferences.Toggle;
import stroom.widget.util.client.GlobalKeyHandler;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.EventBus;

/// Widgets shared by the `Widgets/Editors & Viewers/*` stories.
final class EditorWidgets {

    /// The style of React's `EditorFrame`/`Frame` border.
    static final String FRAME_BORDER = "1px solid var(--code-editor__border-color, #444)";

    // Shared by every rendering: it is a singleton in Stroom, and creating one adds a completion
    // provider to Ace that can't be removed.
    private static DelegatingAceCompleter delegatingAceCompleter;

    private EditorWidgets() {
        // Static utility
    }

    /// Creates Stroom's real Ace editor presenter and view, as GIN would.
    ///
    /// @param eventBus The event bus, on which the editor's context menu is shown.
    /// @return The presenter.
    static EditorPresenter editorPresenter(final EventBus eventBus) {
        return new EditorPresenter(
                eventBus,
                new EditorViewImpl(GWT.create(EditorViewImpl.Binder.class)),
                new EditorMenuPresenter(),
                getDelegatingAceCompleter(),
                currentPreferences(),
                new NoOpGlobalKeyHandler());
    }

    /// Creates Stroom's data [TextPresenter] (the editor used to show stream data), as GIN would.
    ///
    /// @param eventBus The event bus.
    /// @return The presenter.
    static TextPresenter textPresenter(final EventBus eventBus) {
        return new TextPresenter(
                eventBus,
                new TextViewImpl(GWT.create(TextViewImpl.Binder.class)),
                editorPresenter(eventBus));
    }

    /// Equivalent of React's `EditorFrame`/`Frame`, a `<div>` with a fixed size and a
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
