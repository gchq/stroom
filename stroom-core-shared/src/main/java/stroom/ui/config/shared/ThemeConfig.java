package stroom.ui.config.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.util.shared.AbstractConfig;

import javax.inject.Singleton;

@Singleton
@JsonPropertyOrder({"backgroundAttachment", "backgroundColor", "backgroundImage", "backgroundPosition", "backgroundRepeat", "backgroundOpacity", "tubeVisible", "tubeOpacity", "labelColours"})
@JsonInclude(Include.NON_NULL)
public class ThemeConfig extends AbstractConfig {
    @JsonProperty
    @JsonPropertyDescription("GUI")
    private String backgroundAttachment;
    @JsonProperty
    @JsonPropertyDescription("GUI")
    private String backgroundColor;
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
    @JsonPropertyDescription("A comma separated list of KV pairs to provide colours for labels.")
    private String labelColours;

    public ThemeConfig() {
        setDefaults();
    }

    @JsonCreator
    public ThemeConfig(@JsonProperty("backgroundAttachment") final String backgroundAttachment,
                       @JsonProperty("backgroundColor") final String backgroundColor,
                       @JsonProperty("backgroundImage") final String backgroundImage,
                       @JsonProperty("backgroundPosition") final String backgroundPosition,
                       @JsonProperty("backgroundRepeat") final String backgroundRepeat,
                       @JsonProperty("backgroundOpacity") final String backgroundOpacity,
                       @JsonProperty("tubeVisible") final String tubeVisible,
                       @JsonProperty("tubeOpacity") final String tubeOpacity,
                       @JsonProperty("labelColours") final String labelColours) {
        this.backgroundAttachment = backgroundAttachment;
        this.backgroundColor = backgroundColor;
        this.backgroundImage = backgroundImage;
        this.backgroundPosition = backgroundPosition;
        this.backgroundRepeat = backgroundRepeat;
        this.backgroundOpacity = backgroundOpacity;
        this.tubeVisible = tubeVisible;
        this.tubeOpacity = tubeOpacity;
        this.labelColours = labelColours;

        setDefaults();
    }

    private void setDefaults() {
        if (backgroundAttachment == null) {
            backgroundAttachment = "scroll";
        }
        if (backgroundColor == null) {
            backgroundColor = "#1E88E5";
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
        if (labelColours == null) {
            labelColours = "TEST1=#FF0000,TEST2=#FF9900";
        }
    }

    public String getBackgroundAttachment() {
        return backgroundAttachment;
    }

    public String getBackgroundColor() {
        return backgroundColor;
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

    public String getLabelColours() {
        return labelColours;
    }

    @Override
    public String toString() {
        return "ThemeConfig{" +
                "backgroundAttachment='" + backgroundAttachment + '\'' +
                ", backgroundColor='" + backgroundColor + '\'' +
                ", backgroundImage='" + backgroundImage + '\'' +
                ", backgroundPosition='" + backgroundPosition + '\'' +
                ", backgroundRepeat='" + backgroundRepeat + '\'' +
                ", backgroundOpacity='" + backgroundOpacity + '\'' +
                ", tubeVisible='" + tubeVisible + '\'' +
                ", tubeOpacity='" + tubeOpacity + '\'' +
                ", labelColours='" + labelColours + '\'' +
                '}';
    }
}
