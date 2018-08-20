package stroom.ui.config.shared;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import javax.inject.Singleton;

@Singleton
public class ThemeConfig {
    private String backgroundAttachment = "scroll";
    private String backgroundColor = "#1E88E5";
    private String backgroundImage = "none";
    private String backgroundPosition = "0 0";
    private String backgroundRepeat = "repeat";
    private String backgroundOpacity = "0";
    private String tubeVisible = "hidden";
    private String tubeOpacity = "0.6";
    private String labelColours = "TEST1=#FF0000,TEST2=#FF9900";

    @JsonPropertyDescription("GUI")
    public String getBackgroundAttachment() {
        return backgroundAttachment;
    }

    public void setBackgroundAttachment(final String backgroundAttachment) {
        this.backgroundAttachment = backgroundAttachment;
    }

    @JsonPropertyDescription("GUI")
    public String getBackgroundColor() {
        return backgroundColor;
    }

    public void setBackgroundColor(final String backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    @JsonPropertyDescription("GUI")
    public String getBackgroundImage() {
        return backgroundImage;
    }

    public void setBackgroundImage(final String backgroundImage) {
        this.backgroundImage = backgroundImage;
    }

    @JsonPropertyDescription("GUI")
    public String getBackgroundPosition() {
        return backgroundPosition;
    }

    public void setBackgroundPosition(final String backgroundPosition) {
        this.backgroundPosition = backgroundPosition;
    }

    @JsonPropertyDescription("GUI")
    public String getBackgroundRepeat() {
        return backgroundRepeat;
    }

    public void setBackgroundRepeat(final String backgroundRepeat) {
        this.backgroundRepeat = backgroundRepeat;
    }

    @JsonPropertyDescription("GUI")
    public String getBackgroundOpacity() {
        return backgroundOpacity;
    }

    public void setBackgroundOpacity(final String backgroundOpacity) {
        this.backgroundOpacity = backgroundOpacity;
    }

    @JsonPropertyDescription("GUI")
    public String getTubeVisible() {
        return tubeVisible;
    }

    public void setTubeVisible(final String tubeVisible) {
        this.tubeVisible = tubeVisible;
    }

    @JsonPropertyDescription("GUI")
    public String getTubeOpacity() {
        return tubeOpacity;
    }

    public void setTubeOpacity(final String tubeOpacity) {
        this.tubeOpacity = tubeOpacity;
    }

    @JsonPropertyDescription("A comma separated list of KV pairs to provide colours for labels.")
    public String getLabelColours() {
        return labelColours;
    }

    public void setLabelColours(final String labelColours) {
        this.labelColours = labelColours;
    }
}
