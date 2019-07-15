package stroom.security.impl;

public interface UserDocumentPermissions {
    boolean hasDocumentPermission(String docUuid, String permission);
}
