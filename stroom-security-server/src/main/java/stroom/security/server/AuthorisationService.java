package stroom.security.server;

public interface AuthorisationService {
    boolean hasDocumentPermission(String documentType, String documentUuid, String permission);
}
