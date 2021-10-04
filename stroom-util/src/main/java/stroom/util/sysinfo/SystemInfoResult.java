package stroom.util.sysinfo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.validation.constraints.NotNull;

@JsonInclude(Include.NON_DEFAULT)
@JsonPropertyOrder(alphabetic = true)
public class SystemInfoResult {

    @NotNull
    @JsonProperty("name")
    private final String name;

    @JsonProperty("description")
    private final String description;

    @JsonProperty("details")
    private final Map<String, Object> details;

    @JsonCreator
    public SystemInfoResult(@JsonProperty("name") final String name,
                            @JsonProperty("description") final String description,
                            @JsonProperty("details") final Map<String, Object> details) {
        this.name = name;
        this.description = description;
        this.details = Objects.requireNonNull(details);
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return "SystemInfoResult{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", details=" + details +
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
        final SystemInfoResult that = (SystemInfoResult) o;
        return name.equals(that.name) &&
                Objects.equals(description, that.description) &&
                details.equals(that.details);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, details);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {

        private String name;
        private String description = null;
        private Map<String, Object> details = new HashMap<>();

        private Builder() {
        }

        private Builder(final SystemInfoResult systemInfoResult) {
            name = systemInfoResult.name;
            description = systemInfoResult.description;
            details = systemInfoResult.details;
        }

        public Builder name(final String name) {
            this.name = name;
            return this;
        }

        public Builder description(final String description) {
            this.description = description;
            return this;
        }

        public Builder addDetail(final String key, final Object value) {
            Objects.requireNonNull(key);
            details.put(key, value);
            return this;
        }

        public Builder addError(final Throwable error) {
            Objects.requireNonNull(error);
            details.put("error", error.getMessage());

            return this;
        }

        public Builder addError(final String error) {
            Objects.requireNonNull(error);
            details.put("error", error);

            return this;
        }

        public SystemInfoResult build() {
            if (details.isEmpty()) {
                return new SystemInfoResult(name, description, Collections.emptyMap());
            } else {
                return new SystemInfoResult(name, description, details);
            }
        }
    }
}
