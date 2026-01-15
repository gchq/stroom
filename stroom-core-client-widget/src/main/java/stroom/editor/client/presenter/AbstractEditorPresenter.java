/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.editor.client.presenter;

import stroom.ui.config.shared.AceEditorTheme;
import stroom.util.shared.NullSafe;
import stroom.widget.util.client.GlobalKeyHandler;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.event.logical.shared.HasValueChangeHandlers;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.HasText;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import edu.ycp.cs.dh.acegwt.client.ace.AceCompletionProvider;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import java.util.Locale;
import java.util.Optional;

public abstract class AbstractEditorPresenter<V extends BaseEditorView>
        extends MyPresenterWidget<V>
        implements HasText, HasValueChangeHandlers<String> {

    private final DelegatingAceCompleter delegatingAceCompleter;

    AbstractEditorPresenter(final EventBus eventBus,
                            final V view,
                            final DelegatingAceCompleter delegatingAceCompleter,
                            final CurrentPreferences currentPreferences,
                            final GlobalKeyHandler globalKeyHandler) {
        super(eventBus, view);
        this.delegatingAceCompleter = delegatingAceCompleter;

        view.setTheme(getTheme(currentPreferences));
        setEditorKeyBindings(view, currentPreferences.getEditorKeyBindings());
        view.setUserLiveAutoCompletePreference(currentPreferences.getEditorLiveAutoCompletion().isOn());

//        registerHandler(view.addMouseDownHandler(event -> contextMenu.hide()));

        registerHandler(view.addKeyDownHandler(globalKeyHandler::onKeyDown));
        registerHandler(view.addKeyUpHandler(globalKeyHandler::onKeyUp));
        registerHandler(eventBus.addHandler(
                ChangeCurrentPreferencesEvent.getType(),
                this::handlePreferencesChange));
    }

    protected void handlePreferencesChange(final ChangeCurrentPreferencesEvent event) {
        final V view = getView();
        view.setTheme(getTheme(event.getTheme(), event.getEditorTheme()));
        // For the moment only standard and vim bindings are supported given the boolean
        // nature of the context menu
        view.setUserKeyBindingsPreference("VIM".equalsIgnoreCase(event.getEditorKeyBindings()));
        view.setUserLiveAutoCompletePreference(event.getEditorLiveAutoCompletion().isOn());
    }

    protected void setEditorKeyBindings(final V view, final String editorKeyBindingsName) {
        // For the moment only standard and vim bindings are supported given the boolean
        // nature of the context menu
        view.setUserKeyBindingsPreference(EditorPresenter.VIM_KEY_BINDS_NAME.equalsIgnoreCase(editorKeyBindingsName));
    }

    protected AceEditorTheme getTheme(final CurrentPreferences currentPreferences) {
        return getTheme(currentPreferences.getTheme(), currentPreferences.getEditorTheme());
    }

    private AceEditorTheme getTheme(final String theme, final String editorTheme) {
        // Just in case it is null
        return Optional.ofNullable(editorTheme)
                .map(AceEditorTheme::fromName)
                .orElseGet(() ->
                        theme != null && theme.toLowerCase(Locale.ROOT).contains("dark")
                                ? AceEditorTheme.DEFAULT_DARK_THEME
                                : AceEditorTheme.DEFAULT_LIGHT_THEME);
    }

    public String getEditorId() {
        return getView().getEditorId();
    }

    public void focus() {
        getView().focus();
    }

    @Override
    public String getText() {
        return getView().getText();
    }

    /**
     * Sets the text for this control. If XML is supplied it will be turned into
     * HTML for styling.
     */
    @Override
    public void setText(final String text) {
        getView().setText(NullSafe.string(text));
    }

    public boolean isClean() {
        return getView().isClean();
    }

    public void markClean() {
        getView().markClean();
    }

    public void insertTextAtCursor(final String text) {
        getView().insertTextAtCursor(text);
    }

    public void replaceSelectedText(final String text) {
        getView().replaceSelectedText(text);
    }

    public void insertSnippet(final String snippet) {
        getView().insertSnippet(snippet);
    }

    public void setMode(final AceEditorMode mode) {
        getView().setMode(mode);
    }

    public void setTheme(final AceEditorTheme theme) {
        getView().setTheme(theme);
    }

    @Override
    public HandlerRegistration addValueChangeHandler(final ValueChangeHandler<String> handler) {
        return getView().addValueChangeHandler(handler);
    }

    /**
     * Registers completion providers specific to this editor instance and mode
     */
    public void registerCompletionProviders(final AceEditorMode aceEditorMode,
                                            final AceCompletionProvider... completionProviders) {
        // scheduleDeferred to ensure editor is initialised before getId is called
        Scheduler.get().scheduleDeferred(() -> {
            delegatingAceCompleter.registerCompletionProviders(
                    getEditorId(), aceEditorMode, completionProviders);
        });
    }

    /**
     * Registers mode agnostic completion providers specific to this editor instance
     */
    public void registerCompletionProviders(final AceCompletionProvider... completionProviders) {
        // scheduleDeferred to ensure editor is initialised before getId is called
        Scheduler.get().scheduleDeferred(() -> {
            delegatingAceCompleter.registerCompletionProviders(
                    getEditorId(), completionProviders);
        });
    }

    /**
     * Removes all completion providers specific to this editor instance
     */
    public void deRegisterCompletionProviders() {
        // scheduleDeferred to ensure editor is initialised before getId is called
        Scheduler.get().scheduleDeferred(() -> {
            delegatingAceCompleter.deRegisterCompletionProviders(getEditorId());
        });
    }


    public abstract void setReadOnly(final boolean readOnly);
}
