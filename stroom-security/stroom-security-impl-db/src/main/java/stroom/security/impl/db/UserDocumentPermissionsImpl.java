package stroom.security.impl.db;

import stroom.security.impl.UserDocumentPermissions;
import stroom.security.shared.DocumentPermissionNames;

import java.util.Map;
import java.util.Set;

public class UserDocumentPermissionsImpl implements UserDocumentPermissions {
    private final Map<String, Set<String>> permissions;

    UserDocumentPermissionsImpl(final Map<String, Set<String>> permissions) {
        this.permissions = permissions;
    }

    @Override
    public boolean hasDocumentPermission(final String docUuid, final String permission) {
        final Set<String> perms = permissions.get(docUuid);
        if (perms != null) {
            String p = permission;
            do {
                if (perms.contains(p)) {
                    return true;
                }
                p = DocumentPermissionNames.getHigherPermission(p);
            } while (p != null);
        }
        return false;
    }
}
