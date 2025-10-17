package stroom.security.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.Set;

@JsonInclude(Include.NON_NULL)
public class DocumentUserPermissionsReport {

    @JsonProperty
    private final DocumentPermission explicitPermission;
    @JsonProperty
    private final Set<String> explicitCreatePermissions;
    @JsonProperty
    private final Map<String, List<String>> inheritedPermissionPaths;
    @JsonProperty
    private final Map<String, List<String>> inheritedCreatePermissionPaths;

    @JsonCreator
    public DocumentUserPermissionsReport(
            @JsonProperty("explicitPermission") final DocumentPermission explicitPermission,
            @JsonProperty("explicitCreatePermissions") final Set<String> explicitCreatePermissions,
            @JsonProperty("inheritedPermissionPaths") final Map<String, List<String>>
                    inheritedPermissionPaths,
            @JsonProperty("inheritedCreatePermissionPaths") final Map<String, List<String>>
                    inheritedCreatePermissionPaths) {
        this.explicitPermission = explicitPermission;
        this.explicitCreatePermissions = explicitCreatePermissions;
        this.inheritedPermissionPaths = inheritedPermissionPaths;
        this.inheritedCreatePermissionPaths = inheritedCreatePermissionPaths;
    }

    public DocumentPermission getExplicitPermission() {
        return explicitPermission;
    }

    public Set<String> getExplicitCreatePermissions() {
        return explicitCreatePermissions;
    }

    public Map<String, List<String>> getInheritedPermissionPaths() {
        return inheritedPermissionPaths;
    }

    public Map<String, List<String>> getInheritedCreatePermissionPaths() {
        return inheritedCreatePermissionPaths;
    }
}
