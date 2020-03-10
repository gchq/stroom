package stroom.authentication.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class EventLoggingConfig {

    @Valid
    @NotNull
    @JsonProperty
    private String system = "";

    @Valid
    @NotNull
    @JsonProperty
    private String environment = "";

    @Valid
    @NotNull
    @JsonProperty
    private String description = "";

    @Valid
    @NotNull
    @JsonProperty
    private String buildVersion = "";

    public String getSystem() {
        return system;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getDescription() {
        return description;
    }

    public String getBuildVersion() {
        return buildVersion;
    }
}
