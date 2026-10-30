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

package stroom.query.client.result.component;

import stroom.query.api.TextAttributes;
import stroom.query.client.result.table.cf.ConditionalFormattingSwatchUtil;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.TextBox;
import stroom.widget.util.client.CssProperties;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Element;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// The Edit Custom Row Style dialog's content: a rule's background and text colours in the light
/// and dark themes, each with an example of how its rows look.
public class CustomRowStyleEditor extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    private static final String BACKGROUND_COLOUR = "--cf-swatch__background-color";
    private static final String TEXT_COLOUR = "--cf-swatch__color";

    @UiField
    TextBox lightBackgroundColour;
    @UiField
    TextBox lightTextColour;
    @UiField
    DivElement lightSwatch;
    @UiField
    TextBox darkBackgroundColour;
    @UiField
    TextBox darkTextColour;
    @UiField
    DivElement darkSwatch;

    private TextAttributes textAttributes;

    /// Makes empty content.
    public CustomRowStyleEditor() {
        initWidget(BINDER.createAndBindUi(this));
        lightBackgroundColour.addValueChangeHandler(e -> updateLightSwatch());
        lightTextColour.addValueChangeHandler(e -> updateLightSwatch());
        darkBackgroundColour.addValueChangeHandler(e -> updateDarkSwatch());
        darkTextColour.addValueChangeHandler(e -> updateDarkSwatch());
    }

    /// Puts the focus in the light background colour.
    public void focus() {
        lightBackgroundColour.setFocus(true);
    }

    /// Shows the colours, with the rule's text attributes in the examples.
    ///
    /// @param lightBackground The light theme's background colour, or null.
    /// @param lightText       The light theme's text colour, or null.
    /// @param darkBackground  The dark theme's background colour, or null.
    /// @param darkText        The dark theme's text colour, or null.
    /// @param textAttributes  The rule's bold and italic, or null.
    public void setColours(final String lightBackground,
                           final String lightText,
                           final String darkBackground,
                           final String darkText,
                           final TextAttributes textAttributes) {
        this.textAttributes = textAttributes;
        lightBackgroundColour.setValue(lightBackground);
        lightTextColour.setValue(lightText);
        darkBackgroundColour.setValue(darkBackground);
        darkTextColour.setValue(darkText);
        updateLightSwatch();
        updateDarkSwatch();
    }

    /// @return The light theme's background colour entered.
    public String getLightBackgroundColour() {
        return lightBackgroundColour.getValue();
    }

    /// @return The light theme's text colour entered.
    public String getLightTextColour() {
        return lightTextColour.getValue();
    }

    /// @return The dark theme's background colour entered.
    public String getDarkBackgroundColour() {
        return darkBackgroundColour.getValue();
    }

    /// @return The dark theme's text colour entered.
    public String getDarkTextColour() {
        return darkTextColour.getValue();
    }

    private void updateLightSwatch() {
        updateSwatch(lightSwatch, lightBackgroundColour.getValue(), lightTextColour.getValue());
    }

    private void updateDarkSwatch() {
        updateSwatch(darkSwatch, darkBackgroundColour.getValue(), darkTextColour.getValue());
    }

    // A colour that isn't one is left out, so the example shows the theme's
    private void updateSwatch(final Element swatch, final String background, final String text) {
        setColour(swatch, BACKGROUND_COLOUR, background);
        setColour(swatch, TEXT_COLOUR, text);
        swatch.setClassName("cf-colour-swatch cf-colour-swatch--custom "
                            + ConditionalFormattingSwatchUtil.getTextAttributeClassNames(textAttributes).build());
    }

    private static void setColour(final Element swatch, final String property, final String colour) {
        SafeHtmlUtil.asTrustedColour(colour).ifPresentOrElse(
                trusted -> CssProperties.set(swatch, property, trusted),
                () -> CssProperties.remove(swatch, property));
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, CustomRowStyleEditor> {

    }
}
