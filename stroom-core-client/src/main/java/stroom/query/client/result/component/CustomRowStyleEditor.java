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
import stroom.widget.util.client.ColourContrast;
import stroom.widget.util.client.CssProperties;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Element;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.List;

/// The Edit Custom Row Style dialog's content: a rule's background and text colours in the light
/// and dark themes, each with an example of how its rows look and the contrast of its text on its
/// background, with a warning when that is below WCAG's minimum for text (the colours are still
/// kept: the author may have a reason).
public class CustomRowStyleEditor extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    private static final String BACKGROUND_COLOUR = "--cf-swatch__background-color";
    private static final String TEXT_COLOUR = "--cf-swatch__color";
    private static final String CONTRAST_CLASS = "cf-contrast";
    private static final String LOW_CONTRAST_CLASS = CONTRAST_CLASS + " cf-contrast--low";

    @UiField
    TextBox lightBackgroundColour;
    @UiField
    TextBox lightTextColour;
    @UiField
    DivElement lightSwatch;
    @UiField
    DivElement lightContrast;
    @UiField
    TextBox darkBackgroundColour;
    @UiField
    TextBox darkTextColour;
    @UiField
    DivElement darkSwatch;
    @UiField
    DivElement darkContrast;

    private TextAttributes textAttributes;

    /// Makes empty content.
    public CustomRowStyleEditor() {
        initWidget(BINDER.createAndBindUi(this));
        lightBackgroundColour.addValueChangeHandler(e -> updateLightSwatch());
        lightTextColour.addValueChangeHandler(e -> updateLightSwatch());
        darkBackgroundColour.addValueChangeHandler(e -> updateDarkSwatch());
        darkTextColour.addValueChangeHandler(e -> updateDarkSwatch());
        // The contrast is of the colours as shown, which are known only once they are
        addAttachHandler(e -> {
            if (e.isAttached()) {
                updateContrast(lightSwatch, lightContrast);
                updateContrast(darkSwatch, darkContrast);
            }
        });
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
        updateContrast(lightSwatch, lightContrast);
    }

    private void updateDarkSwatch() {
        updateSwatch(darkSwatch, darkBackgroundColour.getValue(), darkTextColour.getValue());
        updateContrast(darkSwatch, darkContrast);
    }

    // Says the contrast of the example's text on its background, as shown (a colour not given is
    // the theme's), warning when it is below the minimum; says nothing if it can't be worked out
    // (e.g. the dialog isn't shown yet)
    private static void updateContrast(final Element swatch, final Element contrast) {
        final Double ratio = isConnected(swatch)
                ? ColourContrast.ratio(computedColour(swatch), backgroundLayers(swatch))
                : null;
        if (ratio == null) {
            contrast.setInnerText("");
            contrast.setClassName(CONTRAST_CLASS);
        } else if (ratio < ColourContrast.MINIMUM) {
            contrast.setInnerText("Contrast " + ColourContrast.format(ratio) + ": below "
                                  + ColourContrast.format(ColourContrast.MINIMUM)
                                  + ", so the text may be hard to read");
            contrast.setClassName(LOW_CONTRAST_CLASS);
        } else {
            contrast.setInnerText("Contrast " + ColourContrast.format(ratio));
            contrast.setClassName(CONTRAST_CLASS);
        }
    }

    // The computed background colours from the element out to the first opaque one (or the page)
    private static List<String> backgroundLayers(final Element element) {
        final JsArrayString found = computedBackgrounds(element);
        final List<String> layers = new ArrayList<>();
        for (int i = 0; i < found.length(); i++) {
            layers.add(found.get(i));
        }
        return layers;
    }

    private static native boolean isConnected(Element element) /*-{
        return element.isConnected;
    }-*/;

    private static native String computedColour(Element element) /*-{
        return $wnd.getComputedStyle(element).color;
    }-*/;

    private static native JsArrayString computedBackgrounds(Element element) /*-{
        var layers = [];
        for (var e = element; e && e.nodeType === 1; e = e.parentElement) {
            var colour = $wnd.getComputedStyle(e).backgroundColor;
            layers.push(colour);
            var match = /^rgba\((.*)\)$/.exec(colour);
            var alpha = match ? parseFloat(match[1].split(',')[3]) : 1;
            if (colour !== 'transparent' && !(alpha < 1)) {
                break;
            }
        }
        return layers;
    }-*/;

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
