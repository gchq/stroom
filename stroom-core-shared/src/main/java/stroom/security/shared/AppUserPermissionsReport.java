package stroom.security.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.Set;

@JsonInclude(Include.NON_NULL)
public class AppUserPermissionsReport {

    @JsonProperty
    private final Set<AppPermission> explicitPermissions;
    @JsonProperty
    private final Map<AppPermission, List<String>> inheritedPermissions;

    @JsonCreator
    public AppUserPermissionsReport(
            @JsonProperty("explicitPermissions") final Set<AppPermission> explicitPermissions,
            @JsonProperty("inheritedPermissions") final Map<AppPermission, List<String>> inheritedPermissions) {

        this.explicitPermissions = explicitPermissions;
        this.inheritedPermissions = inheritedPermissions;
    }

    public Set<AppPermission> getExplicitPermissions() {
        return explicitPermissions;
    }

    public Map<AppPermission, List<String>> getInheritedPermissions() {
        return inheritedPermissions;
    }
}
