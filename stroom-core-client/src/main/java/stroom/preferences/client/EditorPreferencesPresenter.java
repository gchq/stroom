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

package stroom.preferences.client;

import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.DirtyUiHandlers;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.preferences.client.EditorPreferencesPresenter.EditorPreferencesView;
import stroom.ui.config.shared.AceEditorTheme;
import stroom.ui.config.shared.Theme;
import stroom.ui.config.shared.ThemeType;
import stroom.ui.config.shared.UserPreferences;
import stroom.ui.config.shared.UserPreferences.EditorKeyBindings;
import stroom.ui.config.shared.UserPreferences.Toggle;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.List;
import java.util.Objects;

public final class EditorPreferencesPresenter
        extends MyPresenterWidget<EditorPreferencesView>
        implements DirtyUiHandlers, HasDirtyHandlers {

    private final UserPreferencesManager userPreferencesManager;

    @Inject
    public EditorPreferencesPresenter(
            final EventBus eventBus,
            final EditorPreferencesView view,
            final UserPreferencesManager userPreferencesManager) {
        super(eventBus, view);
        this.userPreferencesManager = userPreferencesManager;
        view.setUiHandlers(this);
    }

    @Override
    public void onDirty() {
        DirtyEvent.fire(this, true);
    }

    public boolean updateTheme(final ThemeType themeTypeBefore, final ThemeType themeTypeAfter) {
        // Theme changed
        if (!Objects.equals(themeTypeBefore, themeTypeAfter)) {
            // Different theme type so change the list of editor themes
            final List<String> editorThemes = userPreferencesManager.getEditorThemes(themeTypeAfter);
            getView().setEditorThemes(editorThemes);
            // As we have changed theme type, we need to set an editor theme for that new type
            final String defaultEditorTheme = userPreferencesManager.getDefaultEditorTheme(themeTypeAfter);
//                GWT.log("defaultEditorTheme: " + defaultEditorTheme);
            getView().setEditorTheme(defaultEditorTheme);
            return true;
        }
        return false;
    }

    public void read(final UserPreferences userPreferences) {
        final String themeName = userPreferences.getTheme();
        final ThemeType themeType = Theme.getThemeType(themeName);

        String editorThemeName = userPreferences.getEditorTheme();
        if (!AceEditorTheme.isValidThemeName(editorThemeName)
                || !AceEditorTheme.matchesThemeType(editorThemeName, themeType)) {
            // e.g. light editor theme with a dark stroom theme, so use the default dark editor theme
            editorThemeName = AceEditorTheme.getDefaultEditorTheme(themeType).getName();
        }

        // Get applicable editor themes for the stroom theme type
        getView().setEditorThemes(userPreferencesManager.getEditorThemes(themeType));
        getView().setEditorTheme(editorThemeName);
        getView().setEditorKeyBindings(userPreferences.getEditorKeyBindings());
        getView().setEditorLiveAutoCompletion(userPreferences.getEditorLiveAutoCompletion());
    }

    public void write(final UserPreferences.Builder builder) {
        builder
                .editorTheme(getView().getEditorTheme())
                .editorKeyBindings(getView().getEditorKeyBindings())
                .editorLiveAutoCompletion(getView().getEditorLiveAutoCompletion());
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }


    // --------------------------------------------------------------------------------


    public interface EditorPreferencesView extends View, Focus, HasUiHandlers<DirtyUiHandlers> {

        String getEditorTheme();

        void setEditorTheme(String editorTheme);

        void setEditorThemes(List<String> editorThemes);

        EditorKeyBindings getEditorKeyBindings();

        void setEditorKeyBindings(EditorKeyBindings editorKeyBindings);

        Toggle getEditorLiveAutoCompletion();

        void setEditorLiveAutoCompletion(Toggle editorLiveAutoCompletion);
    }
}
