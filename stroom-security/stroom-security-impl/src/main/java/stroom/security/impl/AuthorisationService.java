package stroom.security.impl;

public interface AuthorisationService {
    boolean hasDocumentPermission(String documentType, String documentUuid, String permission);
}
