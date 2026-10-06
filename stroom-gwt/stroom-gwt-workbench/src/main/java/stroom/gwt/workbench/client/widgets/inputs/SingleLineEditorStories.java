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

package stroom.gwt.workbench.client.widgets.inputs;

import stroom.editor.client.presenter.CurrentPreferences;
import stroom.editor.client.presenter.DelegatingAceCompleter;
import stroom.editor.client.presenter.SingleLineEditorPresenter;
import stroom.editor.client.view.SingleLineEditorViewImpl;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.ui.config.shared.UserPreferences.Toggle;
import stroom.widget.util.client.GlobalKeyHandler;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.web.bindery.event.shared.SimpleEventBus;

/// Stories for [SingleLineEditorPresenter], matching `Widgets/Inputs/SingleLineEditor` in the
/// React Storybook.
///
/// The real presenter and view are used: a one-line Ace editor. The React port is a plain text
/// input styled to look like it.
public final class SingleLineEditorStories {

    // Shared by every rendering: it is a singleton in Stroom, and creating one adds a completion
    // provider to Ace that can't be removed.
    private static DelegatingAceCompleter delegatingAceCompleter;

    private SingleLineEditorStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Inputs/SingleLineEditor", SingleLineEditorStories.class)
                .layout(StoryLayout.PADDED)
                // A single-line, code-styled editor input
                .story("Basic", context -> {
                    // Differs from React: the Ace based editor has no placeholder (React's
                    // "Enter an expression"), which only shows when the value is empty anyway.
                    final SingleLineEditorPresenter presenter = singleLineEditor(context, "${feed}");
                    return presenter.getWidget();
                })
                // Read-only - value cannot be edited
                .story("ReadOnly", context -> {
                    final SingleLineEditorPresenter presenter = singleLineEditor(context, "read-only value");
                    presenter.setReadOnly(true);
                    return presenter.getWidget();
                });
    }

    private static SingleLineEditorPresenter singleLineEditor(final StoryContext context, final String text) {
        final SingleLineEditorViewImpl view = new SingleLineEditorViewImpl(
                GWT.create(SingleLineEditorViewImpl.Binder.class));
        final SingleLineEditorPresenter presenter = new SingleLineEditorPresenter(
                new SimpleEventBus(),
                view,
                getDelegatingAceCompleter(),
                currentPreferences(),
                new NoOpGlobalKeyHandler());
        presenter.setText(text);
        final Spy onChange = context.fn(InputWidgets.ON_CHANGE);
        presenter.addValueChangeHandler(event -> onChange.call(event.getValue()));
        return presenter;
    }

    private static DelegatingAceCompleter getDelegatingAceCompleter() {
        if (delegatingAceCompleter == null) {
            delegatingAceCompleter = new DelegatingAceCompleter();
        }
        return delegatingAceCompleter;
    }

    /// The editor preferences of the workbench's (dark) Stroom theme, with Stroom's defaults.
    private static CurrentPreferences currentPreferences() {
        final CurrentPreferences currentPreferences = new CurrentPreferences();
        currentPreferences.setTheme("Dark");
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
