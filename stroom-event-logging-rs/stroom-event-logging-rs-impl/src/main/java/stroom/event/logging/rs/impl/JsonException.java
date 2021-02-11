package stroom.event.logging.rs.impl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"code", "message", "details"})
@JsonInclude(Include.NON_NULL)
public class JsonException {
    @JsonProperty
    private final int code;
    @JsonProperty
    private final String message;
    @JsonProperty
    private final String details;

    @JsonCreator
    public JsonException(@JsonProperty("code") final int code,
                         @JsonProperty("message") final String message,
                         @JsonProperty("details") final String details) {
        this.code = code;
        this.message = message;
        this.details = details;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getDetails() {
        return details;
    }
}
