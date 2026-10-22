/*
 * Copyright 2023 Crown Copyright
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

import stroom.document.client.event.ChangeUiHandlers;
import stroom.preferences.client.ThemePreferencesPresenter.ThemePreferencesView;
import stroom.ui.config.shared.UserPreferences;
import stroom.widget.presenter.client.MyPresenterWidget;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

import java.util.List;
import java.util.Objects;

public final class ThemePreferencesPresenter
        extends MyPresenterWidget<ThemePreferencesView>
        implements ChangeUiHandlers {

    private final UserPreferencesManager userPreferencesManager;

    private Runnable onChange = () -> {
    };

    @Inject
    public ThemePreferencesPresenter(
            final EventBus eventBus,
            final ThemePreferencesView view,
            final UserPreferencesManager userPreferencesManager) {
        super(eventBus, view);
        this.userPreferencesManager = userPreferencesManager;
        view.setUiHandlers(this);
    }

    @Override
    public void onChange() {
        onChange.run();
    }
//
//    private void triggerThemeChange(final CurrentPreferences currentPreferences) {
//        final HasHandlers handlers = event -> getEventBus().fireEvent(event);
//        ChangeCurrentPreferencesEvent.fire(handlers, currentPreferences);
//    }

    public void read(final UserPreferences userPreferences) {
        final String themeName = userPreferences.getTheme();
        getView().setThemes(userPreferencesManager.getThemes());
        getView().setTheme(themeName);
        // Get applicable editor themes for the stroom theme type
        getView().setDensity(userPreferences.getDensity());
        getView().setFonts(userPreferencesManager.getFonts());
        getView().setFont(userPreferences.getFont());
        getView().setFontSize(userPreferences.getFontSize());
        getView().setEnableTransparency(
                Objects.requireNonNullElse(userPreferences.getEnableTransparency(), true));
        getView().setHideConditionalStyles(
                Objects.requireNonNullElse(userPreferences.getHideConditionalStyles(), false));
    }

    public void write(final UserPreferences.Builder builder) {
        builder
                .theme(getView().getTheme())
                .density(getView().getDensity())
                .font(getView().getFont())
                .fontSize(getView().getFontSize())
                .enableTransparency(getView().isEnableTransparency())
                .hideConditionalStyles(getView().isHideConditionalStyles());
    }

    /// @param onChange What to run when the user changes a theme preference.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }


    // --------------------------------------------------------------------------------


    public interface ThemePreferencesView extends View, Focus, HasUiHandlers<ChangeUiHandlers> {

        String getTheme();

        void setTheme(String theme);

        void setThemes(List<String> themes);

        String getDensity();

        void setDensity(String density);

        String getFont();

        void setFont(String font);

        void setFonts(List<String> themes);

        String getFontSize();

        void setFontSize(String fontSize);

        boolean isEnableTransparency();

        void setEnableTransparency(boolean enableTransparency);

        boolean isHideConditionalStyles();

        void setHideConditionalStyles(boolean hideConditionalStyles);
    }
}
