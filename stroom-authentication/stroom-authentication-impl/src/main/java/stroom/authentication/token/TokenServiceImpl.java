package stroom.authentication.token;

import stroom.authentication.account.Account;
import stroom.authentication.account.AccountDao;
import stroom.authentication.account.AccountService;
import stroom.authentication.api.OpenIdClientDetailsFactory;
import stroom.authentication.config.TokenConfig;
import stroom.authentication.exceptions.NoSuchUserException;
import stroom.security.api.SecurityContext;
import stroom.security.api.TokenException;
import stroom.security.api.TokenVerifier;
import stroom.security.shared.PermissionNames;
import stroom.util.HasHealthCheck;
import stroom.util.shared.PermissionException;

import com.codahale.metrics.health.HealthCheck;
import org.jose4j.jwk.JsonWebKey;

import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class TokenServiceImpl implements TokenService, HasHealthCheck {
    private final JwkCache jwkCache;
    private final TokenDao tokenDao;
    private final AccountDao accountDao;
    private final SecurityContext securityContext;
    private final AccountService accountService;
    private final TokenBuilderFactory tokenBuilderFactory;
    private final TokenConfig tokenConfig;
    private final OpenIdClientDetailsFactory openIdClientDetailsFactory;
    private final TokenVerifier tokenVerifier;

    @Inject
    TokenServiceImpl(final JwkCache jwkCache,
                     final TokenDao tokenDao,
                     final AccountDao accountDao,
                     final SecurityContext securityContext,
                     final AccountService accountService,
                     final TokenBuilderFactory tokenBuilderFactory,
                     final TokenConfig tokenConfig,
                     final OpenIdClientDetailsFactory openIdClientDetailsFactory,
                     final TokenVerifier tokenVerifier) {
        this.jwkCache = jwkCache;
        this.tokenDao = tokenDao;
        this.accountDao = accountDao;
        this.securityContext = securityContext;
        this.accountService = accountService;
        this.tokenBuilderFactory = tokenBuilderFactory;
        this.tokenConfig = tokenConfig;
        this.openIdClientDetailsFactory = openIdClientDetailsFactory;
        this.tokenVerifier = tokenVerifier;
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
        return tokenDao.searchTokens(searchRequest);
    }

    @Override
    public Token create(final CreateTokenRequest createTokenRequest) {
        checkPermission();

        final String userId = securityContext.getUserId();

        final Optional<Integer> optionalAccountId = accountDao.getId(createTokenRequest.getUserEmail());
        final Integer accountId = optionalAccountId.orElseThrow(() ->
                new NoSuchUserException("Cannot find user to associate with this API key!"));

        // Parse and validate tokenType
        final Optional<Token.TokenType> optionalTokenType = getParsedTokenType(createTokenRequest.getTokenType());
        final Token.TokenType tokenType = optionalTokenType.orElseThrow(() ->
                new BadRequestException("Unknown token type:" + createTokenRequest.getTokenType()));

        final Instant expiryInstant = createTokenRequest.getExpiryDate() == null
                ? null :
                createTokenRequest.getExpiryDate().toInstant();

        final long now = System.currentTimeMillis();

        // TODO This assumes we have only one clientId. In theory we may have multiple
        //   and then the UI would need to manage the client IDs in use
        final String clientId = openIdClientDetailsFactory.getOAuth2Client().getClientId();

        final TokenBuilder tokenBuilder = tokenBuilderFactory
                .expiryDateForApiKeys(expiryInstant)
                .newBuilder(tokenType)
                .clientId(clientId)
                .subject(createTokenRequest.getUserEmail());

        final Instant actualExpiryDate = tokenBuilder.getExpiryDate();
        final String data = tokenBuilder.build();

        final Token token = new Token();
        token.setCreateTimeMs(now);
        token.setCreateUser(userId);
        token.setUpdateTimeMs(now);
        token.setUpdateUser(userId);
        token.setUserEmail(createTokenRequest.getUserEmail());
        token.setTokenType(tokenType.getText());
        token.setData(data);
        token.setExpiresOnMs(actualExpiryDate.toEpochMilli());
        token.setComments(createTokenRequest.getComments());
        token.setEnabled(createTokenRequest.isEnabled());

        return tokenDao.create(accountId, token);
    }


    @Override
    public Token createResetEmailToken(final Account account, final String clientId) {
        final Token.TokenType tokenType = Token.TokenType.EMAIL_RESET;
        long timeToExpiryInSeconds = tokenConfig.getMinutesUntilExpirationForEmailResetToken() * 60;
        final TokenBuilder tokenBuilder = tokenBuilderFactory
                .expiryDateForApiKeys(Instant.now().plusSeconds(timeToExpiryInSeconds))
                .newBuilder(tokenType)
                .clientId(clientId);

        final Instant actualExpiryDate = tokenBuilder.getExpiryDate();
        final String idToken = tokenBuilder.build();

        final String userId = securityContext.getUserId();

        final long now = System.currentTimeMillis();
        final Token token = new Token();
        token.setCreateTimeMs(now);
        token.setUpdateTimeMs(now);
        token.setCreateUser(userId);
        token.setUpdateUser(userId);
        token.setUserEmail(account.getEmail());
        token.setTokenType(tokenType.getText().toLowerCase());
        token.setData(idToken);
        token.setExpiresOnMs(actualExpiryDate.toEpochMilli());
        token.setComments("Created for password reset");
        token.setEnabled(true);

        return tokenDao.create(account.getId(), token);
    }

    @Override
    public int deleteAll() {
        checkPermission();

        return tokenDao.deleteAllTokensExceptAdmins();
    }

    @Override
    public int delete(int tokenId) {
        checkPermission();

        return tokenDao.deleteTokenById(tokenId);
    }

    @Override
    public int delete(String token) {
        checkPermission();

        return tokenDao.deleteTokenByTokenString(token);
    }

    @Override
    public Optional<Token> read(String token) {
        checkPermission();

        return tokenDao.readByToken(token);
    }

    @Override
    public Optional<Token> read(int tokenId) {
        checkPermission();

        return tokenDao.readById(tokenId);
    }

    @Override
    public int toggleEnabled(int tokenId, boolean isEnabled) {
        checkPermission();
        final String userId = securityContext.getUserId();

        Optional<Account> updatingUser = accountService.read(userId);

        return updatingUser
                .map(account -> tokenDao.enableOrDisableToken(tokenId, isEnabled, account))
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
        return jwkCache.get()
                .get(0)
                .toJson(JsonWebKey.OutputControlLevel.PUBLIC_ONLY);
    }

    private void checkPermission() {
        if (!securityContext.hasAppPermission(PermissionNames.MANAGE_USERS_PERMISSION)) {
            throw new PermissionException(securityContext.getUserId(), "You do not have permission to manage users");
        }
    }

    static Optional<Token.TokenType> getParsedTokenType(String tokenType) {
        // TODO why not enums?
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

    // It could be argued that the validity of the token should be a prop in Token
    // and the API Keys page could display the validity.
    @Override
    public HealthCheck.Result getHealth() {
        // Check all our enabled tokens are valid
        final SearchResponse searchResponse = tokenDao.searchTokens(
                new SearchRequest.SearchRequestBuilder()
                        .limit(Integer.MAX_VALUE)
                        .build());

        final HealthCheck.ResultBuilder builder = HealthCheck.Result.builder();

        boolean isHealthy = true;

        final Map<String, Object> invalidTokenDetails = new HashMap<>();
        for (final Token token : searchResponse.getTokens()) {
            if (token.isEnabled()) {
                try {
                    tokenVerifier.verifyToken(
                            token.getData(),
                            openIdClientDetailsFactory.getOAuth2Client().getClientId());
                } catch (TokenException e) {
                    isHealthy = false;
                    final Map<String, String> details = new HashMap<>();
                    details.put("expiry", token.getExpiresOnMs() != null
                            ? Instant.ofEpochMilli(token.getExpiresOnMs()).toString()
                            : null);
                    details.put("error", e.getMessage());
                    invalidTokenDetails.put(token.getId().toString(), details);
                }
            }
        }

        if (isHealthy) {
            builder
                    .healthy()
                    .withMessage("All enabled API key tokens are valid");
        } else {
            builder
                    .unhealthy()
                    .withMessage("Some enabled API key tokens are invalid")
                    .withDetail("invalidTokens", invalidTokenDetails);
        }

        return builder.build();
    }
}
