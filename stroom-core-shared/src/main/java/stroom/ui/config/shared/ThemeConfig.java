package stroom.ui.config.shared;

import stroom.util.shared.AbstractConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;
import javax.inject.Singleton;

@Singleton
@JsonPropertyOrder({
        "backgroundAttachment",
        "backgroundColour",
        "backgroundImage",
        "backgroundPosition",
        "backgroundRepeat",
        "backgroundOpacity",
        "tubeVisible",
        "tubeOpacity",
        "topMenuTextColour",
        "labelColours"})
@JsonInclude(Include.NON_NULL)
public class ThemeConfig extends AbstractConfig {

    @JsonProperty
    @JsonPropertyDescription("GUI")
    private String backgroundAttachment;

    @JsonProperty
    @JsonPropertyDescription("GUI")
    private String backgroundColour;

    @JsonProperty
    @JsonPropertyDescription("GUI")
    private String backgroundImage;

    @JsonProperty
    @JsonPropertyDescription("GUI")
    private String backgroundPosition;

    @JsonProperty
    @JsonPropertyDescription("GUI")
    private String backgroundRepeat;

    @JsonProperty
    @JsonPropertyDescription("GUI")
    private String backgroundOpacity;

    @JsonProperty
    @JsonPropertyDescription("GUI")
    private String tubeVisible;

    @JsonProperty
    @JsonPropertyDescription("GUI")
    private String tubeOpacity;

    @JsonProperty
    @JsonPropertyDescription("GUI")
    private String topMenuTextColour;

    @JsonProperty
    @JsonPropertyDescription("A comma separated list of KV pairs to provide colours for labels.")
    private String labelColours;

    public ThemeConfig() {
        setDefaults();
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

        setDefaults();
    }

    private void setDefaults() {
        if (backgroundAttachment == null) {
            backgroundAttachment = "scroll";
        }
        if (backgroundColour == null) {
            backgroundColour = "#1E88E5";
        }
        if (backgroundImage == null) {
            backgroundImage = "none";
        }
        if (backgroundPosition == null) {
            backgroundPosition = "0 0";
        }
        if (backgroundRepeat == null) {
            backgroundRepeat = "repeat";
        }
        if (backgroundOpacity == null) {
            backgroundOpacity = "0";
        }
        if (tubeVisible == null) {
            tubeVisible = "hidden";
        }
        if (tubeOpacity == null) {
            tubeOpacity = "0.6";
        }
        if (topMenuTextColour == null) {
            topMenuTextColour = "#FFFFFF";
        }
        if (labelColours == null) {
            labelColours = "TEST1=#FF0000,TEST2=#FF9900";
        }
    }

    public String getBackgroundAttachment() {
        return backgroundAttachment;
    }

    public void setBackgroundAttachment(final String backgroundAttachment) {
        this.backgroundAttachment = backgroundAttachment;
    }

    public String getBackgroundColour() {
        return backgroundColour;
    }

    public void setBackgroundColour(final String backgroundColour) {
        this.backgroundColour = backgroundColour;
    }

    public String getBackgroundImage() {
        return backgroundImage;
    }

    public void setBackgroundImage(final String backgroundImage) {
        this.backgroundImage = backgroundImage;
    }

    public String getBackgroundPosition() {
        return backgroundPosition;
    }

    public void setBackgroundPosition(final String backgroundPosition) {
        this.backgroundPosition = backgroundPosition;
    }

    public String getBackgroundRepeat() {
        return backgroundRepeat;
    }

    public void setBackgroundRepeat(final String backgroundRepeat) {
        this.backgroundRepeat = backgroundRepeat;
    }

    public String getBackgroundOpacity() {
        return backgroundOpacity;
    }

    public void setBackgroundOpacity(final String backgroundOpacity) {
        this.backgroundOpacity = backgroundOpacity;
    }

    public String getTubeVisible() {
        return tubeVisible;
    }

    public void setTubeVisible(final String tubeVisible) {
        this.tubeVisible = tubeVisible;
    }

    public String getTubeOpacity() {
        return tubeOpacity;
    }

    public void setTubeOpacity(final String tubeOpacity) {
        this.tubeOpacity = tubeOpacity;
    }

    public String getTopMenuTextColour() {
        return topMenuTextColour;
    }

    public void setTopMenuTextColour(final String topMenuTextColour) {
        this.topMenuTextColour = topMenuTextColour;
    }

    public String getLabelColours() {
        return labelColours;
    }

    public void setLabelColours(final String labelColours) {
        this.labelColours = labelColours;
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
