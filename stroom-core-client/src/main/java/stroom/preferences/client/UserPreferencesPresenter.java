/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.preferences.client;

import stroom.alert.client.event.ConfirmEvent;
import stroom.editor.client.presenter.ChangeThemeEvent;
import stroom.preferences.client.UserPreferencesPresenter.UserPreferencesView;
import stroom.query.api.v2.TimeZone;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.ui.config.shared.UserPreferences;
import stroom.ui.config.shared.UserPreferences.Builder;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import com.google.gwt.event.shared.HasHandlers;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class UserPreferencesPresenter
        extends MyPresenterWidget<UserPreferencesView>
        implements UserPreferencesUiHandlers {

    private final UserPreferencesManager userPreferencesManager;
    private UserPreferences originalPreferences;

    @Inject
    public UserPreferencesPresenter(
            final EventBus eventBus,
            final UserPreferencesView view,
            final UserPreferencesManager userPreferencesManager,
            final ClientSecurityContext clientSecurityContext) {
        super(eventBus, view);
        this.userPreferencesManager = userPreferencesManager;

        view.setUiHandlers(this);
        view.setAsDefaultVisible(clientSecurityContext.hasAppPermission(PermissionNames.MANAGE_PROPERTIES_PERMISSION));
    }

    @Override
    protected void onBind() {
    }

    @Override
    public void onChange() {
        final UserPreferences before = userPreferencesManager.getCurrentPreferences();
        final UserPreferences after = write();
        userPreferencesManager.setCurrentPreferences(after);
        final String editorTheme = selectEditorTheme(before, after);
        if (!editorTheme.equals(after.getEditorTheme())) {
            // Editor theme was reset due to UI theme change, so show the new value in the dialog
            getView().setEditorTheme(editorTheme);
        }
        triggerThemeChange(after.getTheme(), editorTheme);
    }

    /**
     * Choose an appropriate editor theme based on whether the UI theme is light or dark.
     * If the UI theme has not changed, use the user's editor theme preference.
     */
    private String selectEditorTheme(final UserPreferences before, final UserPreferences after) {
        final String beforeTheme = before.getTheme();
        final String afterTheme = after.getTheme();
        if (!beforeTheme.equals(afterTheme) || after.getEditorTheme() == null) {
            // If the UI theme has changed, select an appropriate theme based on whether a light or dark theme
            // was selected
            if (afterTheme.toLowerCase(Locale.ROOT).contains("dark")) {
                return Builder.DEFAULT_EDITOR_THEME_DARK;
            } else {
                return Builder.DEFAULT_EDITOR_THEME;
            }
        } else {
            // No UI theme change, so accept the user's selection
            return after.getEditorTheme();
        }
    }

    private void triggerThemeChange(final String theme, final String editorTheme) {
        final HasHandlers handlers = event -> getEventBus().fireEvent(event);
        ChangeThemeEvent.fire(handlers, theme, editorTheme);
    }

    @Override
    public void onSetAsDefault() {
        ConfirmEvent.fire(this,
                "Are you sure you want to set the current preferences for all users?",
                (ok) -> {
                    if (ok) {
                        final UserPreferences userPreferences = write();
                        userPreferencesManager.setDefaultUserPreferences(userPreferences, this::reset);
                    }
                });
    }

    @Override
    public void onRevertToDefault() {
        userPreferencesManager.resetToDefaultUserPreferences(this::reset);
    }

    private void reset(final UserPreferences userPreferences) {
        originalPreferences = userPreferences;
        read(userPreferences);
        userPreferencesManager.setCurrentPreferences(userPreferences);
        final String editorTheme = selectEditorTheme(originalPreferences, userPreferences);
        triggerThemeChange(userPreferences.getTheme(), editorTheme);
    }

    public void show() {
        final String caption = "User Preferences";
        final PopupType popupType = PopupType.OK_CANCEL_DIALOG;
        final PopupUiHandlers popupUiHandlers = new PopupUiHandlers() {
            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                if (ok) {
                    final UserPreferences userPreferences = write();
                    userPreferencesManager.setCurrentPreferences(userPreferences);
                    if (!Objects.equals(userPreferences, originalPreferences)) {
                        userPreferencesManager.update(userPreferences, (result) -> hide());
                    } else {
                        hide();
                    }
                } else {
                    userPreferencesManager.setCurrentPreferences(originalPreferences);
                    hide();
                }
            }

            @Override
            public void onHide(final boolean autoClose, final boolean ok) {
            }
        };

        userPreferencesManager.fetch(userPreferences -> {
            originalPreferences = userPreferences;
            read(userPreferences);
            ShowPopupEvent.fire(
                    UserPreferencesPresenter.this,
                    UserPreferencesPresenter.this,
                    popupType,
                    getPopupSize(),
                    caption,
                    popupUiHandlers);
        });
    }

    private PopupSize getPopupSize() {
        return new PopupSize(
                700, 556,
                700, 556,
                1024, 556,
                true);
    }

    protected void hide() {
        HidePopupEvent.fire(
                UserPreferencesPresenter.this,
                UserPreferencesPresenter.this);
    }


    private void read(final UserPreferences userPreferences) {
        getView().setThemes(userPreferencesManager.getThemes());
        getView().setTheme(userPreferences.getTheme());
        getView().setEditorThemes(userPreferencesManager.getEditorThemes());
        getView().setFont(userPreferences.getFont());
        getView().setFontSize(userPreferences.getFontSize());
        getView().setPattern(userPreferences.getDateTimePattern());

        final String editorTheme = userPreferences.getEditorTheme();
        if (editorTheme != null) {
            getView().setEditorTheme(editorTheme);
        }
        final TimeZone timeZone = userPreferences.getTimeZone();
        if (timeZone != null) {
            getView().setTimeZoneUse(timeZone.getUse());
            getView().setTimeZoneId(timeZone.getId());
            getView().setTimeZoneOffsetHours(timeZone.getOffsetHours());
            getView().setTimeZoneOffsetMinutes(timeZone.getOffsetMinutes());
        }
    }

    private UserPreferences write() {
        final TimeZone timeZone = TimeZone.builder()
                .use(getView().getTimeZoneUse())
                .id(getView().getTimeZoneId())
                .offsetHours(getView().getTimeZoneOffsetHours())
                .offsetMinutes(getView().getTimeZoneOffsetMinutes())
                .build();

        return UserPreferences.builder()
                .theme(getView().getTheme())
                .editorTheme(getView().getEditorTheme())
                .font(getView().getFont())
                .fontSize(getView().getFontSize())
                .dateTimePattern(getView().getPattern())
                .timeZone(timeZone)
                .build();
    }

    public interface UserPreferencesView extends View, HasUiHandlers<UserPreferencesUiHandlers> {

        String getTheme();

        void setTheme(String theme);

        void setThemes(List<String> themes);

        String getEditorTheme();

        void setEditorTheme(String editorTheme);

        void setEditorThemes(List<String> editorThemes);

        String getFont();

        void setFont(String font);

        String getFontSize();

        void setFontSize(String fontSize);

        String getPattern();

        void setPattern(String pattern);

        TimeZone.Use getTimeZoneUse();

        void setTimeZoneUse(TimeZone.Use use);

        String getTimeZoneId();

        void setTimeZoneId(String timeZoneId);

        Integer getTimeZoneOffsetHours();

        void setTimeZoneOffsetHours(Integer timeZoneOffsetHours);

        Integer getTimeZoneOffsetMinutes();

        void setTimeZoneOffsetMinutes(Integer timeZoneOffsetMinutes);

        void setAsDefaultVisible(boolean visible);
    }
}
