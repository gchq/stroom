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
    private final String backgroundAttachment;

    @JsonProperty
    @JsonPropertyDescription("GUI")
    private final String backgroundColour;

    @JsonProperty
    @JsonPropertyDescription("GUI")
    private final String backgroundImage;

    @JsonProperty
    @JsonPropertyDescription("GUI")
    private final String backgroundPosition;

    @JsonProperty
    @JsonPropertyDescription("GUI")
    private final String backgroundRepeat;

    @JsonProperty
    @JsonPropertyDescription("GUI")
    private final String backgroundOpacity;

    @JsonProperty
    @JsonPropertyDescription("GUI")
    private final String tubeVisible;

    @JsonProperty
    @JsonPropertyDescription("GUI")
    private final String tubeOpacity;

    @JsonProperty
    @JsonPropertyDescription("GUI")
    private final String topMenuTextColour;

    @JsonProperty
    @JsonPropertyDescription("A comma separated list of KV pairs to provide colours for labels.")
    private final String labelColours;

    public ThemeConfig() {
        backgroundAttachment = "scroll";
        backgroundColour = "#1E88E5";
        backgroundImage = "none";
        backgroundPosition = "0 0";
        backgroundRepeat = "repeat";
        backgroundOpacity = "0";
        tubeVisible = "hidden";
        tubeOpacity = "0.6";
        topMenuTextColour = "#FFFFFF";
        labelColours = "TEST1=#FF0000,TEST2=#FF9900";
    }

    @JsonCreator
    public ThemeConfig(@JsonProperty("backgroundAttachment") final String backgroundAttachment,
                       @JsonProperty("backgroundColour") final String backgroundColour,
                       @JsonProperty("backgroundImage") final String backgroundImage,
                       @JsonProperty("backgroundPosition") final String backgroundPosition,
                       @JsonProperty("backgroundRepeat") final String backgroundRepeat,
                       @JsonProperty("backgroundOpacity") final String backgroundOpacity,
                       @JsonProperty("tubeVisible") final String tubeVisible,
                       @JsonProperty("tubeOpacity") final String tubeOpacity,
                       @JsonProperty("topMenuTextColour") final String topMenuTextColour,
                       @JsonProperty("labelColours") final String labelColours) {
        this.backgroundAttachment = backgroundAttachment;
        this.backgroundColour = backgroundColour;
        this.backgroundImage = backgroundImage;
        this.backgroundPosition = backgroundPosition;
        this.backgroundRepeat = backgroundRepeat;
        this.backgroundOpacity = backgroundOpacity;
        this.tubeVisible = tubeVisible;
        this.tubeOpacity = tubeOpacity;
        this.topMenuTextColour = topMenuTextColour;
        this.labelColours = labelColours;
    }

    public String getBackgroundAttachment() {
        return backgroundAttachment;
    }

    public String getBackgroundColour() {
        return backgroundColour;
    }

    public String getBackgroundImage() {
        return backgroundImage;
    }

    public String getBackgroundPosition() {
        return backgroundPosition;
    }

    public String getBackgroundRepeat() {
        return backgroundRepeat;
    }

    public String getBackgroundOpacity() {
        return backgroundOpacity;
    }

    public String getTubeVisible() {
        return tubeVisible;
    }

    public String getTubeOpacity() {
        return tubeOpacity;
    }

    public String getTopMenuTextColour() {
        return topMenuTextColour;
    }

    public String getLabelColours() {
        return labelColours;
    }

    @Override
    public String toString() {
        return "ThemeConfig{" +
                "backgroundAttachment='" + backgroundAttachment + '\'' +
                ", backgroundColour='" + backgroundColour + '\'' +
                ", backgroundImage='" + backgroundImage + '\'' +
                ", backgroundPosition='" + backgroundPosition + '\'' +
                ", backgroundRepeat='" + backgroundRepeat + '\'' +
                ", backgroundOpacity='" + backgroundOpacity + '\'' +
                ", tubeVisible='" + tubeVisible + '\'' +
                ", tubeOpacity='" + tubeOpacity + '\'' +
                ", topMenuTextColour='" + topMenuTextColour + '\'' +
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
        return Objects.equals(backgroundAttachment, that.backgroundAttachment) &&
                Objects.equals(backgroundColour, that.backgroundColour) &&
                Objects.equals(backgroundImage, that.backgroundImage) &&
                Objects.equals(backgroundPosition, that.backgroundPosition) &&
                Objects.equals(backgroundRepeat, that.backgroundRepeat) &&
                Objects.equals(backgroundOpacity, that.backgroundOpacity) &&
                Objects.equals(tubeVisible, that.tubeVisible) &&
                Objects.equals(tubeOpacity, that.tubeOpacity) &&
                Objects.equals(topMenuTextColour, that.topMenuTextColour) &&
                Objects.equals(labelColours, that.labelColours);
    }

    @Override
    public int hashCode() {
        return Objects.hash(backgroundAttachment,
                backgroundColour,
                backgroundImage,
                backgroundPosition,
                backgroundRepeat,
                backgroundOpacity,
                tubeVisible,
                tubeOpacity,
                topMenuTextColour,
                labelColours);
    }
}
