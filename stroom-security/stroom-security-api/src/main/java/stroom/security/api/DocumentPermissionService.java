package stroom.security.api;

public interface DocumentPermissionService {
    void clearDocumentPermissions(String documentType, String documentUuid);

    void addDocumentPermissions(String sourceType, String sourceUuid, String documentType, String documentUuid, boolean owner);
}
