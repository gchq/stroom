package stroom.util.sysinfo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.validation.constraints.NotNull;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@JsonInclude(Include.NON_DEFAULT)
@JsonPropertyOrder(alphabetic = true)
public class SystemInfoResult {

    @NotNull
    @JsonProperty("name")
    private final String name;

    @JsonProperty("description")
    private final String description;

    @JsonProperty("details")
    private final Map<String, Object> detailsMap;

    @JsonCreator
    public SystemInfoResult(@JsonProperty("name") final String name,
                            @JsonProperty("description") final String description,
                            @JsonProperty("detail") final Map<String, Object> detailsMap) {
        this.name = name;
        this.description = description;
        this.detailsMap = Objects.requireNonNull(detailsMap);
    }

    public Map<String, Object> getDetails() {
        return detailsMap;
    }

    public String getName() {
        return name;
    }

//        public static SystemInfoResult merge(final String name,
//                                             final Collection<SystemInfoResult> systemInfoResults) {
//        long distinctNames = systemInfoResults.stream()
//                .map(SystemInfoResult::getName)
//                .distinct()
//                .count();
//
//        if (systemInfoResults.size() != distinctNames) {
//            throw new RuntimeException("Non unique names found");
//        }
//
//        final Builder builder = SystemInfoResult.builder(name);
//        systemInfoResults.forEach(systemInfoResult -> {
//            builder.withDetail(systemInfoResult.getName(), systemInfoResult);
//        });
//        return builder.build();
//    }

    public static Builder builder(final String name) {
        return new Builder(name);
    }

    @Override
    public String toString() {
        return "SystemInfoResult{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", detailMap=" + detailsMap +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final SystemInfoResult that = (SystemInfoResult) o;
        return name.equals(that.name) &&
                Objects.equals(description, that.description) &&
                detailsMap.equals(that.detailsMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, detailsMap);
    }

    public static class Builder {

        private final String name;
        private String description = null;
        private final Map<String, Object> detailMap = new HashMap<>();

        public Builder(final String name) {
            this.name = name;
        }

        public Builder withDescription(final String description) {
            this.description = description;
            return this;
        }

        public Builder withDetail(final String key, final Object value) {
            Objects.requireNonNull(key);
            detailMap.put(key, value);
            return this;
        }

        public Builder withError(final Throwable error) {
            Objects.requireNonNull(error);
            detailMap.put("error", error.getMessage());

            return this;
        }

        public SystemInfoResult build() {
            if (detailMap.isEmpty()) {
                return new SystemInfoResult(name, description, Collections.emptyMap());
            } else {
                return new SystemInfoResult(name, description, detailMap);
            }
        }
    }
}
