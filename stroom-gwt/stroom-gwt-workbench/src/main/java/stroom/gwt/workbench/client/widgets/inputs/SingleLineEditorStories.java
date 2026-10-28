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

import stroom.editor.client.CurrentPreferences;
import stroom.editor.client.DelegatingAceCompleter;
import stroom.editor.client.component.AceEditorController;
import stroom.editor.client.component.SingleLineCodeEditor;
import stroom.editor.client.component.SingleLineCodeEditorController;
import stroom.gwt.workbench.client.StroomThemeDecorator;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.ui.config.shared.UserPreferences.Toggle;
import stroom.widget.component.client.ContextValue;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.ControllerNode;
import stroom.widget.util.client.GlobalKeyHandler;

import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.web.bindery.event.shared.SimpleEventBus;

/// Stories for [SingleLineCodeEditor], with its [SingleLineCodeEditorController].
///
/// The real component and controller are used: a one-line Ace editor.
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
                    // The Ace based editor has no placeholder.
                    final SingleLineCodeEditorController controller = singleLineEditor(context, "${feed}", false);
                    return controller.getEditor();
                })
                // Read-only - value cannot be edited
                .story("ReadOnly", context -> {
                    final SingleLineCodeEditorController controller =
                            singleLineEditor(context, "read-only value", true);
                    return controller.getEditor();
                });
    }

    // The editor, owned by a root that says whether it is read only, as a document's tab does
    private static SingleLineCodeEditorController singleLineEditor(final StoryContext context,
                                                                   final String text,
                                                                   final boolean readOnly) {
        final SimpleEventBus eventBus = new SimpleEventBus();
        final SingleLineCodeEditor view = new SingleLineCodeEditor();
        final SingleLineCodeEditorController controller = new SingleLineCodeEditorController(
                eventBus,
                new AceEditorController(
                        eventBus,
                        getDelegatingAceCompleter(),
                        currentPreferences(),
                        new NoOpGlobalKeyHandler()))
                .attach(view);
        final ControllerNode root = ControllerNode.root("story");
        root.provide(Contexts.READ_ONLY, ContextValue.of(readOnly));
        root.adopt(controller.getControllerNode());
        controller.setText(text);
        final Spy onChange = context.fn(InputWidgets.ON_CHANGE);
        controller.setOnChange(onChange::call);
        return controller;
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
