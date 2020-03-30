package stroom.ui.config.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.util.shared.AbstractConfig;

import javax.inject.Singleton;
import java.util.Objects;

@Singleton
@JsonPropertyOrder({"enabled", "chooseOnStartup", "managerTitle", "editorTitle", "editorBody"})
@JsonInclude(Include.NON_NULL)
public class ActivityConfig extends AbstractConfig {
    @JsonProperty
    @JsonPropertyDescription("If you would like users to be able to record some info about the activity they are performing set this property to true.")
    private boolean enabled;
    @JsonProperty
    @JsonPropertyDescription("Set to true if users should be prompted to choose an activity on login.")
    private boolean chooseOnStartup;
    @JsonProperty
    @JsonPropertyDescription("The title of the activity manager popup.")
    private String managerTitle;
    @JsonProperty
    @JsonPropertyDescription("The title of the activity editor popup.")
    private String editorTitle;
    @JsonProperty
    @JsonPropertyDescription("The HTML to display in the activity editor popup.")
    private String editorBody;

    public ActivityConfig() {
        setDefaults();
    }

    @JsonCreator
    public ActivityConfig(@JsonProperty("enabled") final boolean enabled,
                          @JsonProperty("chooseOnStartup") final boolean chooseOnStartup,
                          @JsonProperty("managerTitle") final String managerTitle,
                          @JsonProperty("editorTitle") final String editorTitle,
                          @JsonProperty("editorBody") final String editorBody) {
        this.enabled = enabled;
        this.chooseOnStartup = chooseOnStartup;
        this.managerTitle = managerTitle;
        this.editorTitle = editorTitle;
        this.editorBody = editorBody;

        setDefaults();
    }

    private void setDefaults() {
        if (managerTitle == null) {
            managerTitle = "Choose Activity";
        }
        if (editorTitle == null) {
            editorTitle = "Edit Activity";
        }
        if (editorBody == null) {
            editorBody = "Activity Code:</br>" +
                    "<input type=\"text\" name=\"code\"></input></br></br>" +
                    "Activity Description:</br>" +
                    "<textarea rows=\"4\" style=\"width:100%;height:80px\" name=\"description\" validation=\".{80,}\" validationMessage=\"The activity description must be at least 80 characters long.\" ></textarea>" +
                    "Explain what the activity is";
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isChooseOnStartup() {
        return chooseOnStartup;
    }

    public void setChooseOnStartup(final boolean chooseOnStartup) {
        this.chooseOnStartup = chooseOnStartup;
    }

    public String getManagerTitle() {
        return managerTitle;
    }

    public void setManagerTitle(final String managerTitle) {
        this.managerTitle = managerTitle;
    }

    public String getEditorTitle() {
        return editorTitle;
    }

    public void setEditorTitle(final String editorTitle) {
        this.editorTitle = editorTitle;
    }

    public String getEditorBody() {
        return editorBody;
    }

    public void setEditorBody(final String editorBody) {
        this.editorBody = editorBody;
    }

    @Override
    public String toString() {
        return "ActivityConfig{" +
            "enabled=" + enabled +
            ", chooseOnStartup=" + chooseOnStartup +
            ", managerTitle='" + managerTitle + '\'' +
            ", editorTitle='" + editorTitle + '\'' +
            ", editorBody='" + editorBody + '\'' +
            '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ActivityConfig that = (ActivityConfig) o;
        return enabled == that.enabled &&
            chooseOnStartup == that.chooseOnStartup &&
            Objects.equals(managerTitle, that.managerTitle) &&
            Objects.equals(editorTitle, that.editorTitle) &&
            Objects.equals(editorBody, that.editorBody);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, chooseOnStartup, managerTitle, editorTitle, editorBody);
    }
}
