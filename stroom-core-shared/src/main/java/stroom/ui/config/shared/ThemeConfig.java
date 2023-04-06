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
    @JsonPropertyDescription("GUI")
    private final String backgroundColour;

    @JsonProperty
    @JsonPropertyDescription("Provide border for the entire page.")
    private final String pageBorder;

    @JsonProperty
    @JsonPropertyDescription("A comma separated list of KV pairs to provide colours for labels.")
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
