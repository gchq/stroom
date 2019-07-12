package stroom.security.server;

public interface UserDocumentPermissions {
    boolean hasDocumentPermission(String docUuid, String permission);
}
