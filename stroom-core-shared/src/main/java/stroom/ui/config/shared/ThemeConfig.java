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

package stroom.ui.config.shared;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class ThemeConfig extends AbstractConfig implements IsStroomConfig {

    @JsonProperty
    @JsonPropertyDescription("Provide a valid HTML style value to style the main page, i.e. to add an environment " +
            "specific coloured border to the page to distinguish between environments (dev, test, production, etc.)." +
            "The value will be set on the elements style property. For example to add a coloured border left and " +
            "right set it to 'border: solid #BA2727; border-width: 0px 10px'")
    private final String pageBorder;

    @JsonProperty
    @JsonPropertyDescription("A comma separated list of key/value pairs to provide colours for classification " +
            "banners. Various screens in Stroom can show a data classification banner, e.g. the data view " +
            "for a feed. The classification banners can be coloured according to the classification label. " +
            "For example: 'OFFICIAL SENSITIVE=red,OFFICIAL=#eed202'. The colour values should be valid HTML" +
            "colour codes or names. Classification labels with no corresponding colour in this property " +
            "will be given a default grey colour.")
    private final String labelColours;

    @JsonProperty
    @JsonPropertyDescription("The highlight colour of the selected tab on the main tab bar. Should be a valid " +
                             "HTML colour code or name.")
    private final String selectedTabColour;

    public ThemeConfig() {
        pageBorder = null;
        labelColours = "TEST1=#FF0000,TEST2=#FF9900";
        selectedTabColour = null;
    }

    @JsonCreator
    public ThemeConfig(@JsonProperty("pageBorder") final String pageBorder,
                       @JsonProperty("labelColours") final String labelColours,
                       @JsonProperty("selectedTabColour") final String selectedTabColour) {
        this.pageBorder = pageBorder;
        this.labelColours = labelColours;
        this.selectedTabColour = selectedTabColour;
    }


    public String getPageBorder() {
        return pageBorder;
    }

    public String getLabelColours() {
        return labelColours;
    }

    public String getSelectedTabColour() {
        return selectedTabColour;
    }

    @Override
    public String toString() {
        return "ThemeConfig{" +
               "pageBorder='" + pageBorder + '\'' +
               ", labelColours='" + labelColours + '\'' +
               ", menuColour='" + selectedTabColour + '\'' +
               '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ThemeConfig that = (ThemeConfig) o;
        return Objects.equals(pageBorder, that.pageBorder) &&
                Objects.equals(labelColours, that.labelColours) &&
               Objects.equals(selectedTabColour, that.selectedTabColour);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pageBorder, labelColours, selectedTabColour);
    }
}
