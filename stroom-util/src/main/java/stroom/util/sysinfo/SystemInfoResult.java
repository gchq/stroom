package stroom.util.sysinfo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotNull;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class SystemInfoResult {

    @NotNull
    @JsonProperty("name")
    private final String name;

    @JsonProperty("description")
    private final String description;

    @JsonProperty("detail")
    private final Map<String, Object> detailMap;

    @JsonCreator
    public SystemInfoResult(@JsonProperty("name") final String name,
                            @JsonProperty("description") final String description,
                            @JsonProperty("detail") final Map<String, Object> detailMap) {
        this.name = name;
        this.description = description;
        this.detailMap = Objects.requireNonNull(detailMap);
    }

    public Map<String, Object> getDetail() {
        return detailMap;
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
                ", detailMap=" + detailMap +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final SystemInfoResult that = (SystemInfoResult) o;
        return name.equals(that.name) &&
                Objects.equals(description, that.description) &&
                detailMap.equals(that.detailMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, detailMap);
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

        public SystemInfoResult build() {
            if (detailMap.isEmpty()) {
                return new SystemInfoResult(name, description, Collections.emptyMap());
            } else {
                return new SystemInfoResult(name, description, detailMap);
            }
        }
    }
}
