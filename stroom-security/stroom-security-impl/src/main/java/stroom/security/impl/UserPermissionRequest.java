package stroom.security.impl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class UserPermissionRequest {
    @JsonProperty
    private String permission;

    @JsonCreator
    public UserPermissionRequest(@JsonProperty("permission") final String permission) {
        this.permission = permission;
    }

    public String getPermission() {
        return permission;
    }
}
