package stroom.security.api;

import java.util.Set;

public interface DocumentPermissionService {

    void clearDocumentPermissions(String documentUuid);

    void addDocumentPermissions(String sourceUuid, String documentUuid, boolean owner);

    void setDocumentOwner(String documentUuid, String userUuid);

    Set<String> getDocumentOwnerUuids(String documentUuid);
}
