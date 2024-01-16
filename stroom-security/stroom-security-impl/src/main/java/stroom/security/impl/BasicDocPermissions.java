package stroom.security.impl;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Holds all the permissions for a document in doc/user UUID terms
 */
public class BasicDocPermissions {
    final String docUuid;
    final Map<String, Set<String>> permissions = new HashMap<>();

    public BasicDocPermissions(final String docUuid) {
        this.docUuid = docUuid;
    }

    public void add(final String userOrGroupUuid, final String permission) {
        permissions.computeIfAbsent(userOrGroupUuid, k -> new HashSet<>())
                .add(permission);
    }

    public String getDocUuid() {
        return docUuid;
    }

    public Set<String> getPermissions(final String userOrGroupUuid) {
        return Objects.requireNonNullElseGet(permissions.get(userOrGroupUuid), Collections::emptySet);
    }

    public void forEachUserUuid(final BiConsumer<? super String, ? super Set<String>> action) {
        permissions.forEach(action);
    }

    public Set<String> getUserUuids() {
        return permissions.keySet();
    }

    /**
     * @return A map of userOrGroupUuid => permissionNames
     */
    public Map<String, Set<String>> getPermissionsMap() {
        return Collections.unmodifiableMap(permissions);
    }
}
