package stroom.authentication.token;

import org.jose4j.jwk.JsonWebKey;
import stroom.authentication.account.Account;
import stroom.authentication.account.AccountService;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionException;
import stroom.security.shared.PermissionNames;

import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import java.time.Instant;
import java.util.Optional;

public class TokenServiceImpl implements TokenService {
    private final JwkCache jwkCache;
    private final TokenDao dao;
    private final SecurityContext securityContext;
    private TokenVerifier tokenVerifier;
    private AccountService accountService;
    private final StroomEventLoggingService stroomEventLoggingService;

    @Inject
    TokenServiceImpl(final JwkCache jwkCache,
                     final TokenDao dao,
                     final SecurityContext securityContext,
                     final TokenVerifier tokenVerifier,
                     final AccountService accountService,
                     final StroomEventLoggingService stroomEventLoggingService) {
        this.jwkCache = jwkCache;
        this.dao = dao;
        this.securityContext = securityContext;
        this.tokenVerifier = tokenVerifier;
        this.accountService = accountService;
        this.stroomEventLoggingService = stroomEventLoggingService;
    }


    @Override
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

    @Override
    public Token create(CreateTokenRequest createTokenRequest) {
        checkPermission();

        final String userId = securityContext.getUserId();

        // Parse and validate tokenType
        Optional<Token.TokenType> tokenTypeToCreate = getParsedTokenType(createTokenRequest.getTokenType());
        if (!tokenTypeToCreate.isPresent()) {
            throw new BadRequestException("Unknown token type:" + createTokenRequest.getTokenType());
        }

        Instant expiryInstant = createTokenRequest.getExpiryDate() == null ? null : createTokenRequest.getExpiryDate().toInstant();
        Token token = dao.createToken(
                tokenTypeToCreate.get(),
                userId,
                expiryInstant,
                createTokenRequest.getUserEmail(),
                createTokenRequest.getClientId(),
                createTokenRequest.isEnabled(),
                createTokenRequest.getComments());

        stroomEventLoggingService.createAction("CreateApiToken", "Create a token");

        return token;
    }

    @Override
    public int deleteAll() {
        checkPermission();
        stroomEventLoggingService.createAction("DeleteAllApiTokens", "Delete all tokens");
        return dao.deleteAllTokensExceptAdmins();
    }

    @Override
    public int delete(int tokenId) {
        checkPermission();
        stroomEventLoggingService.createAction("DeleteApiToken", "Delete a token by ID");
        return dao.deleteTokenById(tokenId);
    }

    @Override
    public int delete(String token) {
        checkPermission();
        stroomEventLoggingService.createAction("DeleteApiToken", "Delete a token by the value of the actual token.");
        return dao.deleteTokenByTokenString(token);
    }

    @Override
    public Optional<Token> read(String token) {
        checkPermission();
        stroomEventLoggingService.createAction("ReadApiToken", "Read a token by the string value of the token.");
        return dao.readByToken(token);
    }

    @Override
    public Optional<Token> read(int tokenId) {
        checkPermission();
        stroomEventLoggingService.createAction("ReadApiToken", "Read a token by the token ID.");
        return dao.readById(tokenId);
    }

    @Override
    public int toggleEnabled(int tokenId, boolean isEnabled) {
        checkPermission();
        final String userId = securityContext.getUserId();
        stroomEventLoggingService.createAction("ToggleApiTokenEnabled", "Toggle whether a token is enabled or not.");
        Optional<Account> updatingUser = accountService.get(userId);

        return updatingUser
                .map(account -> dao.enableOrDisableToken(tokenId, isEnabled, account))
                .orElse(0);
    }

//    @Override
//    public Optional<String> verifyToken(String token) {
////        Optional<Token> tokenRecord = dao.readByToken(token);
////        if (!tokenRecord.isPresent()) {
////            return Optional.empty();
////        }
//        return tokenVerifier.verifyToken(token);
//    }

    @Override
    public String getPublicKey() {
        String jwkAsJson = jwkCache.get().get(0).toJson(JsonWebKey.OutputControlLevel.PUBLIC_ONLY);
        stroomEventLoggingService.createAction("GetPublicApiKey", "Read a token by the token ID.");
        return jwkAsJson;
    }

    private void checkPermission() {
        if (!securityContext.hasAppPermission(PermissionNames.MANAGE_USERS_PERMISSION)) {
            throw new PermissionException(securityContext.getUserId(), "You do not have permission to manage users");
        }
    }

    static Optional<Token.TokenType> getParsedTokenType(String tokenType) {
        switch (tokenType.toLowerCase()) {
            case "api":
                return Optional.of(Token.TokenType.API);
            case "user":
                return Optional.of(Token.TokenType.USER);
            case "email_reset":
                return Optional.of(Token.TokenType.EMAIL_RESET);
            default:
                return Optional.empty();
        }
    }


}
