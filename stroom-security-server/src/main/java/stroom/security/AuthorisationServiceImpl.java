package stroom.security;

import stroom.security.SecurityContext;

import javax.inject.Inject;

class AuthorisationServiceImpl implements AuthorisationService {
    private final SecurityContext securityContext;

    @Inject
    AuthorisationServiceImpl(final SecurityContext securityContext) {
        this.securityContext = securityContext;
    }

    @Override
    public boolean hasDocumentPermission(String documentType, String documentUuid, String permission) {
        return securityContext.hasDocumentPermission(documentType, documentUuid, permission);
    }
}
