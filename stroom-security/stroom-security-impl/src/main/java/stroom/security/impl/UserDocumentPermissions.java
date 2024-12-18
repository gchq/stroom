package stroom.security.impl;

import stroom.docref.DocRef;
import stroom.security.shared.DocumentPermission;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Hold all the document permissions that a user holds.
 */
public class UserDocumentPermissions {

    //docUuid => DocumentPermissionEnum.primitiveValue
    private final Map<String, Byte> permissions;

    public UserDocumentPermissions() {
        permissions = new ConcurrentHashMap<>();
    }

    public UserDocumentPermissions(final Map<String, Byte> permissions) {
        this.permissions = permissions;
    }

    /**
     * @return True if the passed permission is directly held or inherited
     * (e.g. permission == 'Use' and this document holds 'Update' which
     * inherits Use so return true).
     */
    public boolean hasDocumentPermission(final DocRef docRef, final DocumentPermission permission) {
        final Byte perm = permissions.get(docRef.getUuid());
        if (perm != null) {
            return perm >= permission.getPrimitiveValue();
        }
        return false;
    }

    public void setPermission(final DocRef docRef, final DocumentPermission permission) {
        setPermission(docRef.getUuid(), permission);
    }

    public void setPermission(final String docUuid, final DocumentPermission permission) {
        permissions.put(docUuid, permission.getPrimitiveValue());
    }

    public void clearPermission(final DocRef docRef) {
        clearPermission(docRef.getUuid());
    }

    public void clearPermission(final String docUuid) {
        permissions.remove(docUuid);
    }

    /**
     * Mostly for use in tests
     *
     * @return A map of docUUID => {@link DocumentPermission}.
     */
    public Map<String, DocumentPermission> getPermissions() {
        return permissions.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Entry::getKey,
                        entry ->
                                DocumentPermission.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(entry.getValue())));
    }
}
