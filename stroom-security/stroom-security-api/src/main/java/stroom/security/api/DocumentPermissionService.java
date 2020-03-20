package stroom.security.api;

public interface DocumentPermissionService {
    void clearDocumentPermissions(String documentUuid);

    void addDocumentPermissions(String sourceUuid, String documentUuid, boolean owner);
}
