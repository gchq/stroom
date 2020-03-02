package stroom.authentication.resources.token.v1;

import stroom.authentication.impl.db.TokenDao;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.security.api.SecurityContext;
import stroom.security.impl.UserService;
import stroom.security.shared.PermissionException;
import stroom.security.shared.PermissionNames;

import javax.inject.Inject;
import javax.ws.rs.BadRequestException;

public class TokenService {
    private final TokenDao dao;
    private final SecurityContext securityContext;
    private final UserService securityUserService;
    private final StroomEventLoggingService stroomEventLoggingService;

    @Inject
    public TokenService(final TokenDao dao,
                        final SecurityContext securityContext,
                        final stroom.security.impl.UserService securityUserService,
                        final StroomEventLoggingService stroomEventLoggingService){

        this.dao = dao;
        this.securityContext = securityContext;
        this.securityUserService = securityUserService;
        this.stroomEventLoggingService = stroomEventLoggingService;
    }


    public SearchResponse search(SearchRequest searchRequest) {
        checkPermission();
        // Validate filters
        if (searchRequest.getFilters() != null) {
            for (String key : searchRequest.getFilters().keySet()) {
                switch (key) {
                    case "expiresOn":
                    case "issuedOn":
                    case "updatedOn":
                        throw new BadRequestException("Filtering by date is not supported.");
                }
            }
        }
        SearchResponse results = dao.searchTokens(searchRequest);
        stroomEventLoggingService.createAction("SearchTokens", "The user searched for an API token");
        return results;
    }

    private void checkPermission() {
        if(!securityContext.hasAppPermission(PermissionNames.MANAGE_USERS_PERMISSION)) {
            throw new PermissionException(securityContext.getUserId(), "You do not have permission to manage users");
        }
    }
}
