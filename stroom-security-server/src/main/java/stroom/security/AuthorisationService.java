package stroom.security;

public interface AuthorisationService {
    boolean hasDocumentPermission(String documentType, String documentUuid, String permission);
}
