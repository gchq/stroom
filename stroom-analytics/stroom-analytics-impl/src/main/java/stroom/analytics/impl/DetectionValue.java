package stroom.analytics.impl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"name", "value"})
@JsonInclude(Include.NON_NULL)
public class DetectionValue {

    @JsonProperty
    private final String name;
    @JsonProperty
    private final String value;

    @JsonCreator
    public DetectionValue(@JsonProperty("name") final String name,
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
}
