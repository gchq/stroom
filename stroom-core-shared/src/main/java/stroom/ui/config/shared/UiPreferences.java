package stroom.ui.config.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.util.shared.AbstractConfig;

import javax.inject.Singleton;
import java.util.Objects;

@Singleton
@JsonPropertyOrder({"allowPasswordResets", "dateFormat", "defaultApiKeyExpiryInMinutes"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UiPreferences extends AbstractConfig {
    @JsonProperty
    @JsonPropertyDescription("Will the UI allow password resets")
    private Boolean allowPasswordResets;
    @JsonProperty
    @JsonPropertyDescription("The date format to use in the UI")
    private String dateFormat;
    @JsonProperty
    @JsonPropertyDescription("The default API key expiry time")
    private Long defaultApiKeyExpiryInMinutes;

    public UiPreferences() {
        setDefaults();
    }

    @JsonCreator
    public UiPreferences(@JsonProperty("allowPasswordResets") final boolean allowPasswordResets,
                         @JsonProperty("dateFormat") final String dateFormat,
                         @JsonProperty("defaultApiKeyExpiryInMinutes") final Long defaultApiKeyExpiryInMinutes) {
        this.allowPasswordResets = allowPasswordResets;
        this.dateFormat = dateFormat;
        this.defaultApiKeyExpiryInMinutes = defaultApiKeyExpiryInMinutes;

        setDefaults();
    }

    private void setDefaults() {
        if (allowPasswordResets == null) {
            allowPasswordResets = true;
        }
        if (dateFormat == null) {
            dateFormat = "YYYY-MM-DDTHH:mm:ss.SSSZ";
        }
        if (defaultApiKeyExpiryInMinutes == null) {
            defaultApiKeyExpiryInMinutes = 525600L;
        }
    }

    public boolean isAllowPasswordResets() {
        return allowPasswordResets;
    }

    public void setAllowPasswordResets(final boolean allowPasswordResets) {
        this.allowPasswordResets = allowPasswordResets;
    }

    public String getDateFormat() {
        return dateFormat;
    }

    public void setDateFormat(final String dateFormat) {
        this.dateFormat = dateFormat;
    }

    public Long getDefaultApiKeyExpiryInMinutes() {
        return defaultApiKeyExpiryInMinutes;
    }

    public void setDefaultApiKeyExpiryInMinutes(final Long defaultApiKeyExpiryInMinutes) {
        this.defaultApiKeyExpiryInMinutes = defaultApiKeyExpiryInMinutes;
    }

    @Override
    public String toString() {
        return "UiPreferences{" +
                "allowPasswordResets=" + allowPasswordResets +
                ", dateFormat='" + dateFormat + '\'' +
                ", defaultApiKeyExpiryInMinutes=" + defaultApiKeyExpiryInMinutes +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final UiPreferences that = (UiPreferences) o;
        return Objects.equals(allowPasswordResets, that.allowPasswordResets) &&
                Objects.equals(dateFormat, that.dateFormat) &&
                Objects.equals(defaultApiKeyExpiryInMinutes, that.defaultApiKeyExpiryInMinutes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(allowPasswordResets, dateFormat, defaultApiKeyExpiryInMinutes);
    }
}
