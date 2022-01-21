package stroom.ui.config.shared;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.validation.ValidRegex;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class InfoPopupConfig extends AbstractConfig implements IsStroomConfig {

    @JsonProperty
    @JsonPropertyDescription("If you would like users to provide some query info when performing a query " +
            "set this property to true.")
    private final boolean enabled;

    @JsonProperty
    @JsonPropertyDescription("The title of the query info popup.")
    private final String title;

    @JsonProperty
    @JsonPropertyDescription("A regex used to validate query info.")
    @ValidRegex
    private final String validationRegex;

    public InfoPopupConfig() {
        enabled = false;
        title = "Please Provide Query Info";
        validationRegex = "^[\\s\\S]{3,}$";
    }

    @JsonCreator
    public InfoPopupConfig(@JsonProperty("enabled") final boolean enabled,
                           @JsonProperty("title") final String title,
                           @JsonProperty("validationRegex") @ValidRegex final String validationRegex) {
        this.enabled = enabled;
        this.title = title;
        this.validationRegex = validationRegex;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getTitle() {
        return title;
    }

    public String getValidationRegex() {
        return validationRegex;
    }

    @Override
    public String toString() {
        return "InfoPopupConfig{" +
                "enabled=" + enabled +
                ", title='" + title + '\'' +
                ", validationRegex='" + validationRegex + '\'' +
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
        final InfoPopupConfig that = (InfoPopupConfig) o;
        return enabled == that.enabled &&
                Objects.equals(title, that.title) &&
                Objects.equals(validationRegex, that.validationRegex);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, title, validationRegex);
    }
}
