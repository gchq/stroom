/*
 * Copyright 2021 Crown Copyright
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

package stroom.preferences.client.component;

import stroom.alert.client.event.ConfirmEvent;
import stroom.dispatch.client.DefaultErrorHandler;
import stroom.dispatch.client.RestErrorHandler;
import stroom.editor.client.presenter.ChangeCurrentPreferencesEvent;
import stroom.editor.client.presenter.CurrentPreferences;
import stroom.preferences.client.UserPreferencesManager;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.task.client.TaskMonitorFactory;
import stroom.ui.config.shared.Theme;
import stroom.ui.config.shared.ThemeType;
import stroom.ui.config.shared.UserPreferences;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.ControllerLayer;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;

/// The user's preferences ([UserPreferencesDialog]), made for one use: it fetches the saved
/// preferences, shows them in tabs and applies each change as it is made. OK saves them, Cancel
/// goes back to the saved ones.
public class UserPreferencesDialogController extends Controller {

    private static final TabData THEME = new TabDataImpl("Theme");
    private static final TabData EDITOR = new TabDataImpl("Editor");
    private static final TabData DATE_AND_TIME = new TabDataImpl("Date and Time");
    private static final TabData KEYBOARD_SHORTCUTS = new TabDataImpl("Keyboard Shortcuts");

    private final UserPreferencesDialog view;
    private final UserPreferencesManager userPreferencesManager;
    private final ThemePreferencesController themePreferences;
    private final ControllerLayer themePreferencesLayer;
    private final EditorPreferencesController editorPreferences;
    private final ControllerLayer editorPreferencesLayer;
    private final TimePreferencesController timePreferences;
    private final ControllerLayer timePreferencesLayer;
    private final KeyBindingPreferencesController keyBindingPreferences;
    private final ControllerLayer keyBindingPreferencesLayer;
    private UserPreferences fetchedUserPreferences;

    /// Made by GIN, with its content and its tabs.
    ///
    /// @param eventBus               The application's event bus.
    /// @param view                   The content.
    /// @param userPreferencesManager Fetches, applies and saves the preferences.
    /// @param themePreferences       The Theme tab.
    /// @param editorPreferences      The Editor tab.
    /// @param timePreferences        The Date and Time tab.
    /// @param keyBindingPreferences  The Keyboard Shortcuts tab.
    /// @param clientSecurityContext  Says whether the user may change the defaults.
    @Inject
    public UserPreferencesDialogController(final EventBus eventBus,
                                           final UserPreferencesDialog view,
                                           final UserPreferencesManager userPreferencesManager,
                                           final ThemePreferencesController themePreferences,
                                           final EditorPreferencesController editorPreferences,
                                           final TimePreferencesController timePreferences,
                                           final KeyBindingPreferencesController keyBindingPreferences,
                                           final ClientSecurityContext clientSecurityContext) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        this.userPreferencesManager = userPreferencesManager;
        this.themePreferences = own(themePreferences);
        themePreferencesLayer = new ControllerLayer(themePreferences);
        this.editorPreferences = own(editorPreferences);
        editorPreferencesLayer = new ControllerLayer(editorPreferences);
        this.timePreferences = own(timePreferences);
        timePreferencesLayer = new ControllerLayer(timePreferences);
        this.keyBindingPreferences = own(keyBindingPreferences);
        keyBindingPreferencesLayer = new ControllerLayer(keyBindingPreferences);

        view.setSetAsDefaultVisible(clientSecurityContext
                .hasAppPermission(AppPermission.MANAGE_PROPERTIES_PERMISSION));
        view.addTab(THEME);
        view.addTab(EDITOR);
        view.addTab(DATE_AND_TIME);
        view.addTab(KEYBOARD_SHORTCUTS);
        switchTab(THEME);

        view.setOnTabSelected(this::switchTab);
        view.setOnSetAsDefault(this::onSetAsDefault);
        view.setOnRevertToDefault(this::onRevertToDefault);
        themePreferences.setOnChange(this::onChange);
        editorPreferences.setOnChange(this::onChange);
        timePreferences.setOnChange(this::onChange);
        keyBindingPreferences.setOnChange(this::onChange);
    }

    /// Fetches the saved preferences and shows them. The opener owns the dialog until it is
    /// hidden; if they can't be fetched, it says why and the dialog isn't shown.
    ///
    /// @param opener What shows it.
    public void show(final HasControllerNode opener) {
        userPreferencesManager.fetch(guard(userPreferences -> {
            read(userPreferences);
            ControllerDialog.showOnce(opener, this)
                    .popupType(PopupType.OK_CANCEL_DIALOG)
                    .popupSize(PopupSize.resizable(640, 588, 533, 588))
                    .caption("User Preferences")
                    .onShow(e -> themePreferences.focus())
                    .onHideRequest(this::onHideRequest)
                    .fire();
        }), new DefaultErrorHandler(this, this::dispose), taskMonitor());
    }

    // Shows the preferences last saved, which Cancel goes back to
    void read(final UserPreferences userPreferences) {
        fetchedUserPreferences = userPreferences;
        themePreferences.read(userPreferences);
        editorPreferences.read(userPreferences);
        timePreferences.read(userPreferences);
        keyBindingPreferences.read(userPreferences);
    }

    // OK saves the preferences if they have changed, and the dialog stays open if the save fails;
    // Cancel goes back to the saved ones
    void onHideRequest(final HidePopupRequestEvent e) {
        if (e.isOk()) {
            final UserPreferences before = userPreferencesManager.getCurrentUserPreferences();
            final UserPreferences newUserPreferences = write(before);
            userPreferencesManager.setCurrentPreferences(newUserPreferences);
            if (!Objects.equals(newUserPreferences, fetchedUserPreferences)) {
                // If the save fails, let the user try again or cancel
                userPreferencesManager.update(newUserPreferences,
                        result -> e.hide(),
                        RestErrorHandler.forPopup(this, e),
                        taskMonitor());
            } else {
                e.hide();
            }
        } else {
            userPreferencesManager.setCurrentPreferences(fetchedUserPreferences);
            // Ensure screens revert to the old prefs
            triggerThemeChange(userPreferencesManager.getCurrentPreferences());
            e.hide();
        }
    }

    private void switchTab(final TabData tabData) {
        if (Objects.equals(THEME, tabData)) {
            view.showTab(tabData, themePreferencesLayer);
        } else if (Objects.equals(EDITOR, tabData)) {
            view.showTab(tabData, editorPreferencesLayer);
        } else if (Objects.equals(DATE_AND_TIME, tabData)) {
            view.showTab(tabData, timePreferencesLayer);
        } else if (Objects.equals(KEYBOARD_SHORTCUTS, tabData)) {
            view.showTab(tabData, keyBindingPreferencesLayer);
        }
    }

    private void onChange() {
        final UserPreferences before = userPreferencesManager.getCurrentUserPreferences();
        UserPreferences after = write(before);

        if (!Objects.equals(before.getTheme(), after.getTheme())) {
            // Theme changed
            final ThemeType themeTypeBefore = Theme.getThemeType(before.getTheme());
            final ThemeType themeTypeAfter = Theme.getThemeType(after.getTheme());
            final boolean change = editorPreferences.updateTheme(themeTypeBefore, themeTypeAfter);
            if (change) {
                // Update the prefs with the change
                after = write(before);
            }
        }
        userPreferencesManager.setCurrentPreferences(after);
        triggerThemeChange(userPreferencesManager.getCurrentPreferences());
    }

    private void triggerThemeChange(final CurrentPreferences currentPreferences) {
        ChangeCurrentPreferencesEvent.fire(event -> getEventBus().fireEvent(event), currentPreferences);
    }

    private void onSetAsDefault() {
        ConfirmEvent.fire(this,
                "Are you sure you want to set the current preferences as the defaults for ALL users?" +
                "\nThis will not change individual users' saved preferences.",
                ok -> {
                    if (ok) {
                        final UserPreferences before = userPreferencesManager.getCurrentUserPreferences();
                        final UserPreferences userPreferences = write(before);
                        userPreferencesManager.setDefaultUserPreferences(userPreferences,
                                guard(this::reset),
                                taskMonitor());
                    }
                });
    }

    private void onRevertToDefault() {
        userPreferencesManager.resetToDefaultUserPreferences(guard(this::reset), taskMonitor());
    }

    private void reset(final UserPreferences userPreferences) {
        read(userPreferences);
        userPreferencesManager.setCurrentPreferences(userPreferences);
        triggerThemeChange(userPreferencesManager.getCurrentPreferences());
    }

    private UserPreferences write(final UserPreferences userPreferences) {
        final UserPreferences.Builder builder = userPreferences.copy();
        themePreferences.write(builder);
        editorPreferences.write(builder);
        timePreferences.write(builder);
        keyBindingPreferences.write(builder);
        return builder.build();
    }

    private TaskMonitorFactory taskMonitor() {
        final TaskMonitorFactory provided = getProvidedTaskMonitor();
        return provided != null ? provided : new DefaultTaskMonitorFactory(this);
    }
}
