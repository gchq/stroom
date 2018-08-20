package stroom.ui.config.shared;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import javax.inject.Singleton;

@Singleton
public class InfoPopupConfig {
    private boolean enabled;
    private String title = "Please Provide Query Info";
    private String validationRegex = "^[\\s\\S]{3,}$";

    @JsonPropertyDescription("If you would like users to provide some query info when performing a query set this property to true.")
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    @JsonPropertyDescription("The title of the query info popup.")
    public String getTitle() {
        return title;
    }

    public void setTitle(final String title) {
        this.title = title;
    }

    @JsonPropertyDescription("A regex used to validate query info.")
    public String getValidationRegex() {
        return validationRegex;
    }

    public void setValidationRegex(final String validationRegex) {
        this.validationRegex = validationRegex;
    }
}
