package stroom.security.shared;

import stroom.util.shared.UserRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonInclude(Include.NON_NULL)
public class AppUserPermissionsReport {

    @JsonProperty
    private final Map<AppPermission, List<List<UserRef>>> inheritedPermissions;

    @JsonCreator
    public AppUserPermissionsReport(@JsonProperty("inheritedPermissions") Map<AppPermission, List<List<UserRef>>>
                                            inheritedPermissions) {
        this.inheritedPermissions = inheritedPermissions;
    }

    public Map<AppPermission, List<List<UserRef>>> getInheritedPermissions() {
        return inheritedPermissions;
    }
}
