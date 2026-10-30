/*
 * Copyright 2024 Crown Copyright
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

package stroom.editor.client.component;

import stroom.editor.client.ChangeCurrentPreferencesEvent;
import stroom.editor.client.CurrentPreferences;
import stroom.editor.client.DelegatingAceCompleter;
import stroom.editor.client.ace.AceEditorPane;
import stroom.ui.config.shared.AceEditorTheme;
import stroom.widget.component.client.Controller;
import stroom.widget.util.client.GlobalKeyHandler;

import com.google.gwt.core.client.Scheduler;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import edu.ycp.cs.dh.acegwt.client.ace.AceCompletionProvider;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/// What every Ace editor ([AceEditorPane]) in Stroom has from the user and the app: the user's theme, key
/// bindings and live auto completion (and their changes), the app's global keys, and the completion
/// providers registered for it with the shared completer, which it removes, and the editor itself
/// destroyed, when it is disposed. [CodeEditorController] owns one, attached to its editor.
public class AceEditorPaneController extends Controller {

    static final String VIM_KEY_BINDS_NAME = "VIM";

    private final DelegatingAceCompleter delegatingAceCompleter;
    private final CurrentPreferences currentPreferences;
    private final GlobalKeyHandler globalKeyHandler;
    private AceEditorPane editor;
    private CodeEditorOptions options;
    // The editor's ID once it has registered completion providers with the shared completer
    private String registeredEditorId;

    /// Made by GIN; [#attach] gives it its editor.
    ///
    /// @param eventBus               The application's event bus, which preference changes come on.
    /// @param delegatingAceCompleter The completer shared by every editor.
    /// @param currentPreferences     The user's preferences.
    /// @param globalKeyHandler       The app's global keys.
    @Inject
    public AceEditorPaneController(final EventBus eventBus,
                               final DelegatingAceCompleter delegatingAceCompleter,
                               final CurrentPreferences currentPreferences,
                               final GlobalKeyHandler globalKeyHandler) {
        super(eventBus);
        this.delegatingAceCompleter = delegatingAceCompleter;
        this.currentPreferences = currentPreferences;
        this.globalKeyHandler = globalKeyHandler;
        onDispose(this::releaseEditor);
    }

    /// Attaches this to an editor, setting it up as the user prefers.
    ///
    /// @param editor  The editor.
    /// @param options The editor's options, which the user's key bindings and live auto completion
    ///                start.
    /// @return This, for its owner to keep.
    public AceEditorPaneController attach(final AceEditorPane editor, final CodeEditorOptions options) {
        this.editor = Objects.requireNonNull(editor, "editor");
        this.options = Objects.requireNonNull(options, "options");
        editor.setTheme(getTheme(currentPreferences.getTheme(), currentPreferences.getEditorTheme()));
        // For the moment only standard and vim bindings are supported given the boolean
        // nature of the context menu
        options.setUserKeyBindingsPreference(
                VIM_KEY_BINDS_NAME.equalsIgnoreCase(currentPreferences.getEditorKeyBindings()));
        options.setUserLiveAutoCompletePreference(currentPreferences.getEditorLiveAutoCompletion().isOn());

        own(editor.addKeyDownHandler(globalKeyHandler::onKeyDown));
        own(editor.addKeyUpHandler(globalKeyHandler::onKeyUp));
        listen(ChangeCurrentPreferencesEvent.getType(), this::onPreferencesChange);
        return this;
    }

    /// Registers completion providers for this editor in a mode.
    ///
    /// @param aceEditorMode       The mode they complete in.
    /// @param completionProviders The providers.
    public void registerCompletionProviders(final AceEditorMode aceEditorMode,
                                            final AceCompletionProvider... completionProviders) {
        // scheduleDeferred to ensure editor is initialised before getId is called
        Scheduler.get().scheduleDeferred(() -> {
            // Not once disposed, or the completer would keep this editor
            if (!isDisposed()) {
                registeredEditorId = editor.getId();
                delegatingAceCompleter.registerCompletionProviders(
                        registeredEditorId, aceEditorMode, completionProviders);
            }
        });
    }

    /// Registers completion providers for this editor in any mode.
    ///
    /// @param completionProviders The providers.
    public void registerCompletionProviders(final AceCompletionProvider... completionProviders) {
        // scheduleDeferred to ensure editor is initialised before getId is called
        Scheduler.get().scheduleDeferred(() -> {
            // Not once disposed, or the completer would keep this editor
            if (!isDisposed()) {
                registeredEditorId = editor.getId();
                delegatingAceCompleter.registerCompletionProviders(
                        registeredEditorId, completionProviders);
            }
        });
    }

    /// Removes the completion providers registered for this editor.
    public void deRegisterCompletionProviders() {
        // scheduleDeferred to ensure editor is initialised before getId is called
        Scheduler.get().scheduleDeferred(() ->
                delegatingAceCompleter.deRegisterCompletionProviders(editor.getId()));
    }

    private void onPreferencesChange(final ChangeCurrentPreferencesEvent event) {
        editor.setTheme(getTheme(event.getTheme(), event.getEditorTheme()));
        // For the moment only standard and vim bindings are supported given the boolean
        // nature of the context menu
        options.setUserKeyBindingsPreference(VIM_KEY_BINDS_NAME.equalsIgnoreCase(event.getEditorKeyBindings()));
        options.setUserLiveAutoCompletePreference(event.getEditorLiveAutoCompletion().isOn());
    }

    private void releaseEditor() {
        // The completer is shared, so it would keep this editor's providers (and through them the
        // document) for good. An editor that was never shown has no ID and registered none
        if (registeredEditorId != null) {
            delegatingAceCompleter.deRegisterCompletionProviders(registeredEditorId);
            registeredEditorId = null;
        }
        if (editor != null) {
            editor.destroy();
        }
    }

    private static AceEditorTheme getTheme(final String theme, final String editorTheme) {
        // Just in case it is null
        return Optional.ofNullable(editorTheme)
                .map(AceEditorTheme::fromName)
                .orElseGet(() ->
                        theme != null && theme.toLowerCase(Locale.ROOT).contains("dark")
                                ? AceEditorTheme.DEFAULT_DARK_THEME
                                : AceEditorTheme.DEFAULT_LIGHT_THEME);
    }
}
