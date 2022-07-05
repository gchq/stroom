package stroom.util.sysinfo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public interface HasSystemInfo {

    /**
     * @return The name for the system information being provided. Should be limited
     * to [A-Za-z_-] to avoid URL encoding issues. By default the qualified class
     * name will be used.
     */
    default String getSystemInfoName() {
        return this.getClass().getName();
    }

    /**
     * @return A {@link SystemInfoResult} for part of the system. e.g. for dumping debug information,
     * the sizes of in memory collections/queues, etc.
     * Implementations do not need to perform permission checks unless additional permissions beyond
     * VIEW_SYSTEM_INFO_PERMISSION are required.
     */
    SystemInfoResult getSystemInfo();

    default SystemInfoResult getSystemInfo(final Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return getSystemInfo();
        } else {
            throw new UnsupportedOperationException("This system info provider does not support parameters");
        }
    }

    // TODO Change this to a list of ParamInfo
    default List<ParamInfo> getParamInfo() {
        return Collections.emptyList();
    }

    @JsonPropertyOrder(alphabetic = true)
    @JsonInclude(Include.NON_NULL)
    class ParamInfo {

        @JsonProperty
        private final String name;
        @JsonProperty
        private final String description;
        @JsonProperty
        private final ParamType paramType;

        @JsonCreator
        public ParamInfo(@JsonProperty("name") final String name,
                         @JsonProperty("description") final String description,
                         @JsonProperty("paramType") final ParamType paramType) {
            this.name = Objects.requireNonNull(name);
            this.description = Objects.requireNonNull(description);
            this.paramType = Objects.requireNonNull(paramType);
        }

        public static ParamInfo optionalParam(final String name,
                                              final String description) {
            return new ParamInfo(name, description, ParamType.OPTIONAL);
        }

        public static ParamInfo mandatoryParam(final String name,
                                               final String description) {
            return new ParamInfo(name, description, ParamType.MANDATORY);
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public ParamType getParamType() {
            return paramType;
        }

        @JsonIgnore
        public boolean isMandatory() {
            return ParamType.MANDATORY.equals(paramType);
        }
    }

    enum ParamType {
        OPTIONAL,
        MANDATORY
    }
}
