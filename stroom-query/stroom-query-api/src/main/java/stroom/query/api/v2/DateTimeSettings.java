package stroom.query.api.v2;

import stroom.query.api.v2.TimeZone.Use;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class DateTimeSettings {

    @Schema(description = "A date time formatting pattern string conforming to the specification of " +
            "java.time.format.DateTimeFormatter")
    @JsonProperty
    private final String dateTimePattern;

    @JsonProperty
    private final TimeZone timeZone;

    @Schema(description = "The local zone id to use when formatting date values in the search results. The " +
            "value is the string form of a java.time.ZoneId",
            required = true)
    @JsonProperty
    private final String localZoneId;

    /**
     * @param dateTimePattern The client date time pattern to use by default for formatting search results that contain
     *                        dates.
     * @param timeZone        The client timezone to use by default for formatting search results that contain dates.
     * @param localZoneId     The locale to use when formatting date values in the search results. The value is the
     *                        string form of a {@link java.time.ZoneId zoneId}
     */
    @JsonCreator
    public DateTimeSettings(@JsonProperty("dateTimePattern") final String dateTimePattern,
                            @JsonProperty("timeZone") final TimeZone timeZone,
                            @JsonProperty("localZoneId") final String localZoneId) {
        this.dateTimePattern = dateTimePattern;
        this.timeZone = timeZone;
        this.localZoneId = localZoneId;
    }

    public String getDateTimePattern() {
        return dateTimePattern;
    }

    public TimeZone getTimeZone() {
        return timeZone;
    }

    public String getLocalZoneId() {
        return localZoneId;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DateTimeSettings)) {
            return false;
        }
        final DateTimeSettings that = (DateTimeSettings) o;
        return Objects.equals(dateTimePattern, that.dateTimePattern) && Objects.equals(timeZone,
                that.timeZone) && Objects.equals(localZoneId, that.localZoneId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dateTimePattern, timeZone, localZoneId);
    }

    @Override
    public String toString() {
        return "ClientSettings{" +
                "dateTimePattern='" + dateTimePattern + '\'' +
                ", timeZone=" + timeZone +
                ", localZoneId='" + localZoneId + '\'' +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {

        private String dateTimePattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXX";
        private TimeZone timeZone = TimeZone.builder().use(Use.UTC).build();
        private String localZoneId = "Z";

        private Builder() {
        }

        private Builder(final DateTimeSettings dateTimeSettings) {
            this.dateTimePattern = dateTimeSettings.dateTimePattern;
            this.timeZone = dateTimeSettings.timeZone;
            this.localZoneId = dateTimeSettings.localZoneId;
        }

        public Builder dateTimePattern(final String dateTimePattern) {
            this.dateTimePattern = dateTimePattern;
            return this;
        }

        public Builder timeZone(final TimeZone timeZone) {
            this.timeZone = timeZone;
            return this;
        }

        public Builder localZoneId(final String localZoneId) {
            this.localZoneId = localZoneId;
            return this;
        }

        public DateTimeSettings build() {
            return new DateTimeSettings(dateTimePattern, timeZone, localZoneId);
        }
    }
}
