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
    @JsonPropertyDescription("A valid HTML colour value, e.g. 'olive' or '#BA2727' to use for the background " +
            "colour on the login page. If Stroom is configured to use an external IDP or authenticates using " +
            "certificates then users will not see the login page.")
    private final String backgroundColour;

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

    public ThemeConfig() {
        backgroundColour = "#1E88E5";
        pageBorder = null;
        labelColours = "TEST1=#FF0000,TEST2=#FF9900";
    }

    @JsonCreator
    public ThemeConfig(@JsonProperty("backgroundColour") final String backgroundColour,
                       @JsonProperty("pageBorder") final String pageBorder,
                       @JsonProperty("labelColours") final String labelColours) {
        this.backgroundColour = backgroundColour;
        this.pageBorder = pageBorder;
        this.labelColours = labelColours;
    }

    public String getBackgroundColour() {
        return backgroundColour;
    }

    public String getPageBorder() {
        return pageBorder;
    }

    public String getLabelColours() {
        return labelColours;
    }

    @Override
    public String toString() {
        return "ThemeConfig{" +
                "backgroundColour='" + backgroundColour + '\'' +
                ", pageBorder='" + pageBorder + '\'' +
                ", labelColours='" + labelColours + '\'' +
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
        return Objects.equals(backgroundColour, that.backgroundColour) &&
                Objects.equals(pageBorder, that.pageBorder) &&
                Objects.equals(labelColours, that.labelColours);
    }

    @Override
    public int hashCode() {
        return Objects.hash(backgroundColour, pageBorder, labelColours);
    }
}
