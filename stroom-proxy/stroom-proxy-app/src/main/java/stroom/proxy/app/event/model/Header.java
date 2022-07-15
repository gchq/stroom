package stroom.proxy.app.event.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({
        "name",
        "value"})
public class Header {

    @JsonProperty("name")
    private final String name;
    @JsonProperty("value")
    private final String value;

    @JsonCreator
    public Header(@JsonProperty("name") final String name,
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
