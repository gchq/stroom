/*
 * Copyright 2016 Crown Copyright
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

package stroom.data.client.component;

import stroom.widget.component.client.Component;
import stroom.widget.text.client.Label;
import stroom.widget.util.client.CssProperties;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;

/// A data's classification (e.g. `OFFICIAL`), upper case, on the colour configured for it. The
/// colours come from outside ([#setLabelColours(Map)], which a [ClassificationLabelController]
/// attached to it calls); a classification no colour matches has the stylesheet's grey. The BEM
/// block `classification-label` (`classification-label.css`), its markup `ClassificationLabel.ui.xml`.
public class ClassificationLabel extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final String BACKGROUND_COLOUR_PROPERTY = "--classification-label__background-color";

    @UiField
    Label classification;

    private Map<String, String> labelColours = Collections.emptyMap();

    /// Makes a label with no classification and no colours.
    public ClassificationLabel() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// Shows a classification, in upper case, on the colour of the first label colour whose name
    /// it contains.
    ///
    /// @param text The classification, or null for none.
    public void setClassification(final String text) {
        classification.setText(text == null
                ? ""
                : text.toUpperCase(Locale.ROOT));
        applyColour();
    }

    /// Sets the colours of the classifications, which may arrive before or after the
    /// classification.
    ///
    /// @param labelColours Each colour (a CSS colour) by the upper-case name a classification
    ///                     contains, in the order they are matched.
    public void setLabelColours(final Map<String, String> labelColours) {
        this.labelColours = labelColours == null
                ? Collections.emptyMap()
                : labelColours;
        applyColour();
    }

    // The colour is set as a CSS variable, so the stylesheet can pick black or white text to suit
    // it; with none, the stylesheet's grey
    private void applyColour() {
        final String upper = classification.getText();
        for (final Map.Entry<String, String> labelColour : labelColours.entrySet()) {
            if (upper.contains(labelColour.getKey())) {
                CssProperties.set(classification.getElement(), BACKGROUND_COLOUR_PROPERTY, labelColour.getValue());
                return;
            }
        }
        CssProperties.remove(classification.getElement(), BACKGROUND_COLOUR_PROPERTY);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ClassificationLabel> {

    }
}
