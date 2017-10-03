package stroom.security.server;

import org.springframework.stereotype.Component;
import stroom.security.SecurityContext;

@Component
public class AuthorisationServiceImpl implements AuthorisationService {
    private SecurityContext securityContext;

    public AuthorisationServiceImpl(SecurityContext securityContext) {
        this.securityContext = securityContext;
    }

    @Override
    public boolean hasDocumentPermission(String documentType, String documentUuid, String permission) {
        return securityContext.hasDocumentPermission(documentType, documentUuid, permission);
    }



}
