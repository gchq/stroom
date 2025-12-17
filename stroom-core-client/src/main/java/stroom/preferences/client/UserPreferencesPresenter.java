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

import stroom.alert.client.event.ConfirmEvent;
import stroom.editor.client.presenter.ChangeCurrentPreferencesEvent;
import stroom.editor.client.presenter.CurrentPreferences;
import stroom.preferences.client.UserPreferencesPresenter.UserPreferencesView;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.ui.config.shared.Theme;
import stroom.ui.config.shared.ThemeType;
import stroom.ui.config.shared.UserPreferences;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.tab.client.presenter.TabBar;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.gwt.event.shared.HasHandlers;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.LayerContainer;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.Objects;

public final class UserPreferencesPresenter
        extends MyPresenterWidget<UserPreferencesView>
        implements UserPreferencesUiHandlers {

    private static final TabData THEME = new TabDataImpl("Theme");
    private static final TabData EDITOR = new TabDataImpl("Editor");
    private static final TabData DATE_AND_TIME = new TabDataImpl("Date and Time");

    private final UserPreferencesManager userPreferencesManager;
    private final ThemePreferencesPresenter themePreferencesPresenter;
    private final EditorPreferencesPresenter editorPreferencesPresenter;
    private final TimePreferencesPresenter timePreferencesPresenter;
    private UserPreferences fetchedUserPreferences;

    @Inject
    public UserPreferencesPresenter(
            final EventBus eventBus,
            final UserPreferencesView view,
            final UserPreferencesManager userPreferencesManager,
            final ThemePreferencesPresenter themePreferencesPresenter,
            final EditorPreferencesPresenter editorPreferencesPresenter,
            final TimePreferencesPresenter timePreferencesPresenter,
            final ClientSecurityContext clientSecurityContext) {
        super(eventBus, view);
        this.userPreferencesManager = userPreferencesManager;
        this.themePreferencesPresenter = themePreferencesPresenter;
        this.editorPreferencesPresenter = editorPreferencesPresenter;
        this.timePreferencesPresenter = timePreferencesPresenter;
        view.setUiHandlers(this);
        view.setAsDefaultVisible(clientSecurityContext
                .hasAppPermission(AppPermission.MANAGE_PROPERTIES_PERMISSION));

        addTab(THEME, themePreferencesPresenter);
        addTab(EDITOR, editorPreferencesPresenter);
        addTab(DATE_AND_TIME, timePreferencesPresenter);
        view.getTabBar().selectTab(THEME);
        switchTab(THEME);
    }

    private void addTab(final TabData tabData, final MyPresenterWidget<?> presenterWidget) {
        getView().getTabBar().addTab(tabData);
//        getView().getLayerContainer().add(presenterWidget.getWidget());
    }

    @Override
    protected void onBind() {
        registerHandler(getView().getTabBar().addSelectionHandler(e ->
                switchTab(e.getSelectedItem())));
        registerHandler(themePreferencesPresenter.addDirtyHandler(e -> onChange()));
        registerHandler(editorPreferencesPresenter.addDirtyHandler(e -> onChange()));
        registerHandler(timePreferencesPresenter.addDirtyHandler(e -> onChange()));
    }

    private void switchTab(final TabData tabData) {
        getView().getTabBar().selectTab(tabData);
        if (Objects.equals(THEME, tabData)) {
            setLayer(themePreferencesPresenter);
        } else if (Objects.equals(EDITOR, tabData)) {
            setLayer(editorPreferencesPresenter);
        } else if (Objects.equals(DATE_AND_TIME, tabData)) {
            setLayer(timePreferencesPresenter);
        }
    }

    private void setLayer(final MyPresenterWidget<?> presenterWidget) {
        getView().getLayerContainer().show(presenterWidget);
    }

    @Override
    public void onChange() {
        final UserPreferences before = userPreferencesManager.getCurrentUserPreferences();
        UserPreferences after = write();

        if (!Objects.equals(before.getTheme(), after.getTheme())) {
            // Theme changed
            final ThemeType themeTypeBefore = Theme.getThemeType(before.getTheme());
            final ThemeType themeTypeAfter = Theme.getThemeType(after.getTheme());
            final boolean change = editorPreferencesPresenter.updateTheme(themeTypeBefore, themeTypeAfter);
            if (change) {
                // Update the prefs with the change
                after = write();
            }
        }
        userPreferencesManager.setCurrentPreferences(after);

//        GWT.log("theme: " + userPreferencesManager.getCurrentPreferences().getTheme()
//                + ", editorTheme: " + userPreferencesManager.getCurrentPreferences().getEditorTheme());
        triggerThemeChange(userPreferencesManager.getCurrentPreferences());
    }

    private void triggerThemeChange(final CurrentPreferences currentPreferences) {
        final HasHandlers handlers = event -> getEventBus().fireEvent(event);
        ChangeCurrentPreferencesEvent.fire(handlers, currentPreferences);
    }

    @Override
    public void onSetAsDefault() {
        ConfirmEvent.fire(this,
                "Are you sure you want to set the current preferences as the defaults for ALL users?" +
                        "\nThis will not change individual users' saved preferences.",
                (ok) -> {
                    if (ok) {
                        final UserPreferences userPreferences = write();
                        userPreferencesManager.setDefaultUserPreferences(userPreferences, this::reset, this);
                    }
                });
    }

    @Override
    public void onRevertToDefault() {
        userPreferencesManager.resetToDefaultUserPreferences(this::reset, this);
    }

    private void reset(final UserPreferences userPreferences) {
        fetchedUserPreferences = userPreferences;
        read(userPreferences);
        userPreferencesManager.setCurrentPreferences(userPreferences);
//        final String editorTheme = selectEditorTheme(originalPreferences, userPreferences);
//        triggerThemeChange(userPreferences.getTheme(), editorTheme, userPreferences.getEditorKeyBindings());
        triggerThemeChange(userPreferencesManager.getCurrentPreferences());
    }

    public void show() {
        final String caption = "User Preferences";

        userPreferencesManager.fetch(userPreferences -> {
            fetchedUserPreferences = userPreferences;
            read(userPreferences);
            ShowPopupEvent.builder(this)
                    .popupType(PopupType.OK_CANCEL_DIALOG)
                    .popupSize(PopupSize.resizable(533, 588, 533, 588))
                    .caption(caption)
                    .onShow(e -> themePreferencesPresenter.getView().focus())
                    .onHideRequest(e -> {
                        if (e.isOk()) {
                            final UserPreferences newUserPreferences = write();
                            userPreferencesManager.setCurrentPreferences(newUserPreferences);
                            if (!Objects.equals(newUserPreferences, fetchedUserPreferences)) {
                                userPreferencesManager.update(newUserPreferences, (result) -> e.hide(), this);
                            } else {
                                e.hide();
                            }
                        } else {
                            userPreferencesManager.setCurrentPreferences(fetchedUserPreferences);
                            // Ensure screens revert to the old prefs
                            triggerThemeChange(userPreferencesManager.getCurrentPreferences());
                            e.hide();
                        }
                    })
                    .fire();
        }, this);
    }

    private void read(final UserPreferences userPreferences) {
        themePreferencesPresenter.read(userPreferences);
        editorPreferencesPresenter.read(userPreferences);
        timePreferencesPresenter.read(userPreferences);
    }

    private UserPreferences write() {
        final UserPreferences.Builder builder = UserPreferences.builder();
        themePreferencesPresenter.write(builder);
        editorPreferencesPresenter.write(builder);
        timePreferencesPresenter.write(builder);
        return builder.build();
    }

    public interface UserPreferencesView extends View, HasUiHandlers<UserPreferencesUiHandlers> {

        TabBar getTabBar();

        LayerContainer getLayerContainer();

        void setAsDefaultVisible(boolean visible);
    }
}
