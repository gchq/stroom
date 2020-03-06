package stroom.ui.config.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.validation.ValidRegex;

import javax.inject.Singleton;

@Singleton
@JsonPropertyOrder({"enabled", "title", "validationRegex"})
@JsonInclude(Include.NON_NULL)
public class InfoPopupConfig extends AbstractConfig {
    @JsonProperty
    @JsonPropertyDescription("If you would like users to provide some query info when performing a query set this property to true.")
    private boolean enabled;
    @JsonProperty
    @JsonPropertyDescription("The title of the query info popup.")
    private String title;
    @JsonProperty
    @JsonPropertyDescription("A regex used to validate query info.")
    @ValidRegex
    private String validationRegex;

    public InfoPopupConfig() {
        setDefaults();
    }

    @JsonCreator
    public InfoPopupConfig(@JsonProperty("enabled") final boolean enabled,
                           @JsonProperty("title") final String title,
                           @JsonProperty("validationRegex") @ValidRegex final String validationRegex) {
        this.enabled = enabled;
        this.title = title;
        this.validationRegex = validationRegex;

        setDefaults();
    }

    private void setDefaults() {
        if (title == null) {
            title = "Please Provide Query Info";
        }
        if (validationRegex == null) {
            validationRegex = "^[\\s\\S]{3,}$";
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(final String title) {
        this.title = title;
    }

    public String getValidationRegex() {
        return validationRegex;
    }

    public void setValidationRegex(final String validationRegex) {
        this.validationRegex = validationRegex;
    }

    @Override
    public String toString() {
        return "InfoPopupConfig{" +
                "enabled=" + enabled +
                ", title='" + title + '\'' +
                ", validationRegex='" + validationRegex + '\'' +
                '}';
    }
}
