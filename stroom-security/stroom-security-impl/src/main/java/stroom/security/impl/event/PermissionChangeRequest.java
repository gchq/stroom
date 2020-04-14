package stroom.security.impl.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PermissionChangeRequest {
    @JsonProperty
    private final PermissionChangeEvent event;

    @JsonCreator
    public PermissionChangeRequest(@JsonProperty("event") final PermissionChangeEvent event) {
        this.event = event;
    }

    public PermissionChangeEvent getEvent() {
        return event;
    }
}
