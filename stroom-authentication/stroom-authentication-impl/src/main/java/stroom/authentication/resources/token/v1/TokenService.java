package stroom.authentication.resources.token.v1;

import org.jose4j.jwk.JsonWebKey;
import stroom.authentication.TokenVerifier;
import stroom.authentication.config.StroomConfig;
import stroom.authentication.resources.user.v1.User;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.security.api.SecurityContext;
import stroom.security.impl.UserService;
import stroom.security.shared.PermissionException;
import stroom.security.shared.PermissionNames;

import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import java.time.Instant;
import java.util.Optional;

public class TokenService {
    private final TokenDao dao;
    private final SecurityContext securityContext;
    private StroomConfig stroomConfig;
    private TokenVerifier tokenVerifier;
    private stroom.authentication.resources.user.v1.UserService userService;
    private final UserService securityUserService;
    private final StroomEventLoggingService stroomEventLoggingService;

    @Inject
    public TokenService(final TokenDao dao,
                        final SecurityContext securityContext,
                        final StroomConfig stroomConfig,
                        final TokenVerifier tokenVerifier,
                        final stroom.authentication.resources.user.v1.UserService userService,
                        final stroom.security.impl.UserService securityUserService,
                        final StroomEventLoggingService stroomEventLoggingService){

        this.dao = dao;
        this.securityContext = securityContext;
        this.stroomConfig = stroomConfig;
        this.tokenVerifier = tokenVerifier;
        this.userService = userService;
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

    public Token create(CreateTokenRequest createTokenRequest){
        checkPermission();

        final String userId = securityContext.getUserId();

        // Parse and validate tokenType
        Optional<Token.TokenType> tokenTypeToCreate = createTokenRequest.getParsedTokenType();
        if (!tokenTypeToCreate.isPresent()) {
            throw new BadRequestException("Unknown token type:" + createTokenRequest.getTokenType());
        }

        Instant expiryInstant = createTokenRequest.getExpiryDate() == null ? null : createTokenRequest.getExpiryDate().toInstant();
        Token token = dao.createToken(
                tokenTypeToCreate.get(),
                userId,
                expiryInstant,
                createTokenRequest.getUserEmail(),
                stroomConfig.getClientId(),
                createTokenRequest.isEnabled(),
                createTokenRequest.getComments());

        stroomEventLoggingService.createAction("CreateApiToken", "Create a token");

        return token;
    }

    public void deleteAll() {
        checkPermission();
        dao.deleteAllTokensExceptAdmins();
        stroomEventLoggingService.createAction("DeleteAllApiTokens", "Delete all tokens");
    }

    public void delete(int tokenId) {
        checkPermission();
        dao.deleteTokenById(tokenId);
        stroomEventLoggingService.createAction("DeleteApiToken", "Delete a token by ID");
    }

    public void delete(String token){
        checkPermission();
        dao.deleteTokenByTokenString(token);
        stroomEventLoggingService.createAction("DeleteApiToken", "Delete a token by the value of the actual token.");
    }

    public Optional<Token> read(String token){
        checkPermission();
        stroomEventLoggingService.createAction("ReadApiToken", "Read a token by the string value of the token.");
        return dao.readByToken(token);
    }

    public Optional<Token> read(int tokenId) {
        checkPermission();
        stroomEventLoggingService.createAction("ReadApiToken", "Read a token by the token ID.");
        return dao.readById(tokenId);
    }

    public void toggleEnabled(int tokenId, boolean isEnabled){
       checkPermission();
        final String userId = securityContext.getUserId();
        stroomEventLoggingService.createAction("ToggleApiTokenEnabled", "Toggle whether a token is enabled or not.");
        Optional<User> updatingUser = userService.get(userId);

        if (updatingUser.isPresent()) {
            dao.enableOrDisableToken(tokenId, isEnabled, updatingUser.get());
        }
    }

    public Optional<String> verifyToken(String token){
        Optional<Token> tokenRecord = dao.readByToken(token);
        if (!tokenRecord.isPresent()) {
            return Optional.empty();
        }
        Optional<String> usersEmail = tokenVerifier.verifyToken(token, tokenRecord);
        return usersEmail;
    }

    public String getPublicKey() {
        String jwkAsJson = tokenVerifier.getJwk().toJson(JsonWebKey.OutputControlLevel.PUBLIC_ONLY);
        stroomEventLoggingService.createAction("GetPublicApiKey", "Read a token by the token ID.");
        return jwkAsJson;
    }

    private void checkPermission() {
        if(!securityContext.hasAppPermission(PermissionNames.MANAGE_USERS_PERMISSION)) {
            throw new PermissionException(securityContext.getUserId(), "You do not have permission to manage users");
        }
    }
}
