package stroom.ui.config.shared;

import stroom.util.shared.AbstractConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.inject.Singleton;
import java.util.Objects;

@Singleton
@JsonPropertyOrder({"allowPasswordResets", "dateFormat", "defaultApiKeyExpiryInMinutes"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UiPreferences extends AbstractConfig {
    @JsonProperty
    @JsonPropertyDescription("The date format to use in the UI")
    private String dateFormat;

    public UiPreferences() {
        setDefaults();
    }

    @JsonCreator
    public UiPreferences(@JsonProperty("dateFormat") final String dateFormat) {
        this.dateFormat = dateFormat;

        setDefaults();
    }

    private void setDefaults() {
        if (dateFormat == null) {
            dateFormat = "YYYY-MM-DDTHH:mm:ss.SSSZ";
        }
    }

    public String getDateFormat() {
        return dateFormat;
    }

    public void setDateFormat(final String dateFormat) {
        this.dateFormat = dateFormat;
    }


    @Override
    public String toString() {
        return "UiPreferences{" +
                ", dateFormat='" + dateFormat + '\'' +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final UiPreferences that = (UiPreferences) o;
        return Objects.equals(dateFormat, that.dateFormat);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dateFormat);
    }
}
