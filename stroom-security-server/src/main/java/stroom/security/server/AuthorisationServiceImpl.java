package stroom.security.server;

import org.springframework.stereotype.Component;
import stroom.security.Secured;
import stroom.security.SecurityContext;
import stroom.security.shared.User;

@Component
@Secured(User.MANAGE_USERS_PERMISSION)
public class AuthorisationServiceImpl implements AuthorisationService {

    private SecurityContext securityContext;

    public AuthorisationServiceImpl(SecurityContext securityContext){
        this.securityContext = securityContext;
    }

    @Override
    public boolean hasDocumentPermission(String documentType, String documentUuid, String permission) {
        return securityContext.hasDocumentPermission(documentType, documentUuid, permission);
    }
}
