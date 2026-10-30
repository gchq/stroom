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

package stroom.preferences.client.component;

import stroom.ui.config.shared.UserPreferences;
import stroom.ui.config.shared.UserPreferences.EditorKeyBindings;
import stroom.ui.config.shared.UserPreferences.TextContextMenu;
import stroom.ui.config.shared.UserPreferences.Toggle;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Component;
import stroom.widget.selection.client.DecoratedItem;
import stroom.widget.selection.client.SelectionBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/// The Editor tab of the user's preferences: the code editor's theme, its key bindings, whether it
/// completes as the user types, and whose menu a right-click in a text field shows.
public class EditorPreferences extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    SelectionBox<EditorThemeName> editorTheme;
    @UiField
    SelectionBox<String> editorKeyBindings;
    @UiField
    SelectionBox<String> editorLiveAutoCompletion;
    @UiField
    SelectionBox<String> textContextMenu;

    private Runnable onChange = () -> {
    };

    /// Makes an empty tab.
    public EditorPreferences() {
        initWidget(BINDER.createAndBindUi(this));

        Arrays.stream(EditorKeyBindings.values())
                .sorted(Comparator.comparing(EditorKeyBindings::getDisplayValue))
                .map(EditorKeyBindings::getDisplayValue)
                .forEach(displayName -> editorKeyBindings.addItem(displayName));
        editorLiveAutoCompletion.addItem(Toggle.ON.getDisplayValue());
        editorLiveAutoCompletion.addItem(Toggle.OFF.getDisplayValue());
        for (final TextContextMenu value : TextContextMenu.values()) {
            textContextMenu.addItem(value.getDisplayValue());
        }

        editorTheme.addValueChangeHandler(e -> onChange.run());
        editorKeyBindings.addValueChangeHandler(e -> onChange.run());
        editorLiveAutoCompletion.addValueChangeHandler(e -> onChange.run());
        textContextMenu.addValueChangeHandler(e -> onChange.run());
    }

    /// Offers editor themes, e.g. those for the light themes, and chooses one.
    ///
    /// @param editorThemes The themes' names.
    /// @param editorTheme  The name of the theme chosen.
    public void setEditorThemes(final List<String> editorThemes, final String editorTheme) {
        this.editorTheme.clear();
        this.editorTheme.addItems(NullSafe.stream(editorThemes)
                .map(EditorPreferences::createEditorThemeItem)
                .collect(Collectors.toList()));
        this.editorTheme.setValue(createEditorThemeItem(editorTheme));
    }

    /// Shows the user's editor preferences, but for the editor's theme.
    ///
    /// @param userPreferences The preferences.
    public void read(final UserPreferences userPreferences) {
        editorKeyBindings.setValue(userPreferences.getEditorKeyBindings().getDisplayValue());
        editorLiveAutoCompletion.setValue(
                Objects.requireNonNull(userPreferences.getEditorLiveAutoCompletion()).getDisplayValue());
        textContextMenu.setValue(Objects.requireNonNull(userPreferences.getTextContextMenu()).getDisplayValue());
    }

    /// Writes the preferences shown into the user's preferences' builder.
    ///
    /// @param builder The builder.
    public void write(final UserPreferences.Builder builder) {
        builder
                .editorTheme(editorTheme.getValue().getValue())
                .editorKeyBindings(EditorKeyBindings.fromDisplayValue(editorKeyBindings.getValue()))
                .editorLiveAutoCompletion(Toggle.fromDisplayValue(editorLiveAutoCompletion.getValue()))
                .textContextMenu(TextContextMenu.fromDisplayValue(textContextMenu.getValue()));
    }

    /// @param onChange What to run when the user changes a preference.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    // An editor theme's item, named as the user reads it: "tomorrow_night" is "Tomorrow night"
    private static EditorThemeName createEditorThemeItem(final String editorTheme) {
        String displayName = editorTheme;
        if (displayName != null) {
            displayName = displayName.replace('_', ' ');
            displayName = Character.toUpperCase(displayName.charAt(0))
                          + displayName.substring(1);
        }
        return new EditorThemeName(editorTheme, displayName);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, EditorPreferences> {

    }

    /// An editor theme, with its name as the user reads it.
    public static class EditorThemeName extends DecoratedItem<String> {

        /// @param value        The theme's name.
        /// @param displayValue Its name as the user reads it.
        public EditorThemeName(final String value, final String displayValue) {
            super(value, displayValue);
        }
    }
}
