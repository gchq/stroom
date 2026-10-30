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
import stroom.visualisation.shared.VisPalette;
import stroom.widget.component.client.Component;
import stroom.widget.selection.client.SelectionBox;
import stroom.widget.tickbox.client.TickBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;
import java.util.Objects;

/// The Theme tab of the user's preferences: the theme, layout density, font and its size, the
/// visualisations' colours, and whether dialogs are translucent and tables show conditional styles.
public class ThemePreferences extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    // The layout densities, the first being how the app looks with none saved (no density class)
    private static final List<String> DENSITIES = List.of("Comfortable", "Compact");

    @UiField
    SelectionBox<String> theme;
    @UiField
    SelectionBox<String> density;
    @UiField
    SelectionBox<String> font;
    @UiField
    SelectionBox<String> fontSize;
    @UiField
    TickBox enableTransparency;
    @UiField
    TickBox hideConditionalStyles;
    @UiField
    SelectionBox<String> visualisationPalette;

    private Runnable onChange = () -> {
    };

    /// Makes an empty tab.
    public ThemePreferences() {
        initWidget(BINDER.createAndBindUi(this));

        density.addItems(DENSITIES);

        fontSize.addItem("Small");
        fontSize.addItem("Medium");
        fontSize.addItem("Large");

        for (final VisPalette palette : VisPalette.values()) {
            visualisationPalette.addItem(palette.getDisplayValue());
        }

        theme.addValueChangeHandler(e -> onChange.run());
        density.addValueChangeHandler(e -> onChange.run());
        font.addValueChangeHandler(e -> onChange.run());
        fontSize.addValueChangeHandler(e -> onChange.run());
        enableTransparency.addValueChangeHandler(e -> onChange.run());
        visualisationPalette.addValueChangeHandler(e -> onChange.run());
        hideConditionalStyles.addValueChangeHandler(e -> onChange.run());
    }

    /// Shows the user's theme preferences.
    ///
    /// @param userPreferences The preferences.
    /// @param themes          The themes to choose from.
    /// @param fonts           The fonts to choose from.
    public void read(final UserPreferences userPreferences,
                     final List<String> themes,
                     final List<String> fonts) {
        theme.clear();
        theme.addItems(themes);
        theme.setValue(userPreferences.getTheme());
        density.setValue(shownDensity(userPreferences.getDensity()));
        font.clear();
        font.addItems(fonts);
        font.setValue(userPreferences.getFont());
        fontSize.setValue(userPreferences.getFontSize());
        enableTransparency.setValue(
                Objects.requireNonNullElse(userPreferences.getEnableTransparency(), true));
        hideConditionalStyles.setValue(
                Objects.requireNonNullElse(userPreferences.getHideConditionalStyles(), false));
        visualisationPalette.setValue(
                VisPalette.fromDisplayValue(userPreferences.getVisualisationPalette()).getDisplayValue());
    }

    /// The density to show for a saved one: the saved one if it is an option, otherwise (none saved,
    /// or "Default", which this tab once saved for none) the first, which is how the app looks
    /// without one.
    ///
    /// @param saved The saved density, or null for none.
    /// @return One of the options.
    static String shownDensity(final String saved) {
        return saved != null && DENSITIES.contains(saved)
                ? saved
                : DENSITIES.get(0);
    }

    /// Writes the preferences shown into the user's preferences' builder.
    ///
    /// @param builder The builder.
    public void write(final UserPreferences.Builder builder) {
        builder
                .theme(theme.getValue())
                .density(density.getValue())
                .font(font.getValue())
                .fontSize(fontSize.getValue())
                .enableTransparency(enableTransparency.getValue())
                .hideConditionalStyles(hideConditionalStyles.getValue())
                .visualisationPalette(visualisationPalette.getValue());
    }

    /// Puts the focus on the theme.
    public void focus() {
        theme.focus();
    }

    /// @param onChange What to run when the user changes a preference.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ThemePreferences> {

    }
}
