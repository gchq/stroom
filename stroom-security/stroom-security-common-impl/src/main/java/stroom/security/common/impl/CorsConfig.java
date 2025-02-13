package stroom.security.common.impl;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Collections;
import java.util.List;

@JsonPropertyOrder(alphabetic = true)
public class CorsConfig extends AbstractConfig implements IsStroomConfig, IsProxyConfig {

    @JsonProperty
    @JsonPropertyDescription("A list of CORS parameters to set if users want to alter the defaults.")
    private final List<CorsParam> parameters;

    public CorsConfig() {
        parameters = Collections.emptyList();
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public CorsConfig(@JsonProperty("parameters") final List<CorsParam> parameters) {
        this.parameters = parameters;
    }

    public List<CorsParam> getParameters() {
        return parameters;
    }

    @Override
    public String toString() {
        return "CorsConfig{" +
               "parameters=" + parameters +
               '}';
    }

    @JsonPropertyOrder(alphabetic = true)
    public static class CorsParam {

        @JsonProperty
        @JsonPropertyDescription("The param name, e.g. 'allowedOrigins'.")
        private final String name;
        @JsonProperty
        @JsonPropertyDescription("The param value, e.g. '*'.")
        private final String value;

        @JsonCreator
        public CorsParam(@JsonProperty("name") final String name,
                         @JsonProperty("value") final String value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return "CorsParam{" +
                   "name='" + name + '\'' +
                   ", value='" + value + '\'' +
                   '}';
        }
    }
}
