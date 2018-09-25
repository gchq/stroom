package stroom.ui.config.shared;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.docref.SharedObject;

import javax.inject.Singleton;

@Singleton
public class ActivityConfig implements SharedObject {
    private boolean enabled;
    private boolean chooseOnStartup;
    private String managerTitle = "Choose Activity";
    private String editorTitle = "Edit Activity";
    private String editorBody = "Activity Code:</br>" +
            "<input type=\"text\" name=\"code\"></input></br></br>" +
            "Activity Description:</br>" +
            "<textarea rows=\"4\" style=\"width:100%;height:80px\" name=\"description\"></textarea>" +
            "Explain what the activity is";

    public ActivityConfig() {
        // Default constructor necessary for GWT serialisation.
    }

    @JsonPropertyDescription("If you would like users to be able to record some info about the activity they are performing set this property to true.")
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    @JsonPropertyDescription("Set to true if users should be prompted to choose an activity on login.")
    public boolean isChooseOnStartup() {
        return chooseOnStartup;
    }

    public void setChooseOnStartup(final boolean chooseOnStartup) {
        this.chooseOnStartup = chooseOnStartup;
    }

    @JsonPropertyDescription("The title of the activity manager popup.")
    public String getManagerTitle() {
        return managerTitle;
    }

    public void setManagerTitle(final String managerTitle) {
        this.managerTitle = managerTitle;
    }

    @JsonPropertyDescription("The title of the activity editor popup.")
    public String getEditorTitle() {
        return editorTitle;
    }

    public void setEditorTitle(final String editorTitle) {
        this.editorTitle = editorTitle;
    }

    @JsonPropertyDescription("The HTML to display in the activity editor popup.")
    public String getEditorBody() {
        return editorBody;
    }

    public void setEditorBody(final String editorBody) {
        this.editorBody = editorBody;
    }
}
