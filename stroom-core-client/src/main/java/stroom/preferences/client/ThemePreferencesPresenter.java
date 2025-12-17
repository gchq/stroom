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
import stroom.preferences.client.ThemePreferencesPresenter.ThemePreferencesView;
import stroom.ui.config.shared.UserPreferences;
import stroom.util.shared.NullSafe;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.List;

public final class ThemePreferencesPresenter
        extends MyPresenterWidget<ThemePreferencesView>
        implements DirtyUiHandlers, HasDirtyHandlers {

    private final UserPreferencesManager userPreferencesManager;

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
    public void onDirty() {
        DirtyEvent.fire(this, true);

//        UserPreferences after = write();
//
//        userPreferencesManager.setCurrentPreferences(after);
//
//        GWT.log("theme: " + userPreferencesManager.getCurrentPreferences().getTheme()
//                + " editorTheme: " + userPreferencesManager.getCurrentPreferences().getEditorTheme());
//        triggerThemeChange(userPreferencesManager.getCurrentPreferences());
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
                NullSafe.requireNonNullElse(userPreferences.getEnableTransparency(), true));
        getView().setHideConditionalStyles(
                NullSafe.requireNonNullElse(userPreferences.getHideConditionalStyles(), false));
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

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }


    // --------------------------------------------------------------------------------


    public interface ThemePreferencesView extends View, Focus, HasUiHandlers<DirtyUiHandlers> {

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
