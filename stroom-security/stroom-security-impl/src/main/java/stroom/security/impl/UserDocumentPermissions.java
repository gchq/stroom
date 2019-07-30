package stroom.security.impl;

import stroom.security.shared.DocumentPermissionNames;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class UserDocumentPermissions {
    private final Map<String, Set<String>> permissions = new ConcurrentHashMap<>();

    public boolean hasDocumentPermission(final String documentUuid, final String permission) {
        final Set<String> perms = permissions.get(documentUuid);
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

    public void addPermission(final String documentUuid, final String permission) {
        permissions.compute(documentUuid, (k, set) -> {
            if (set != null) {
                set.add(permission);
                return set;
            }

            final Set<String> newSet = new HashSet<>();
            newSet.add(permission);
            return newSet;
        });
    }

    public void removePermission(final String documentUuid, final String permission) {
        permissions.compute(documentUuid, (k, set) -> {
            if (set != null) {
                set.remove(permission);
                if (set.isEmpty()) {
                    return null;
                }
            }
            return set;
        });
    }

    public void clearDocumentPermissions(final String documentUuid) {
        permissions.remove(documentUuid);
    }
}
