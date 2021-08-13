package stroom.security.identity.token;

import stroom.security.api.SecurityContext;
import stroom.security.api.TokenException;
import stroom.security.api.TokenVerifier;
import stroom.security.identity.account.Account;
import stroom.security.identity.account.AccountDao;
import stroom.security.identity.account.AccountService;
import stroom.security.identity.exceptions.NoSuchUserException;
import stroom.security.openid.api.OpenIdClientFactory;
import stroom.security.shared.PermissionNames;
import stroom.util.HasHealthCheck;
import stroom.util.rest.RestUtil;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResultPage;

import com.codahale.metrics.health.HealthCheck;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;

public class ApiKeyServiceImpl implements ApiKeyService, HasHealthCheck {

    private final ApiKeyDao apiKeyDao;
    private final AccountDao accountDao;
    private final SecurityContext securityContext;
    private final AccountService accountService;
    private final TokenBuilderFactory tokenBuilderFactory;
    private final OpenIdClientFactory openIdClientDetailsFactory;
    private final TokenVerifier tokenVerifier;

    @Inject
    ApiKeyServiceImpl(final ApiKeyDao apiKeyDao,
                      final AccountDao accountDao,
                      final SecurityContext securityContext,
                      final AccountService accountService,
                      final TokenBuilderFactory tokenBuilderFactory,
                      final OpenIdClientFactory openIdClientDetailsFactory,
                      final TokenVerifier tokenVerifier) {
        this.apiKeyDao = apiKeyDao;
        this.accountDao = accountDao;
        this.securityContext = securityContext;
        this.accountService = accountService;
        this.tokenBuilderFactory = tokenBuilderFactory;
        this.openIdClientDetailsFactory = openIdClientDetailsFactory;
        this.tokenVerifier = tokenVerifier;
    }

    static Optional<ApiKeyType> getParsedTokenType(final String tokenType) {

        try {
            if (tokenType == null) {
                return Optional.empty();
            } else {
                return Optional.of(ApiKeyType.fromText(tokenType));
            }
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public ApiKeyResultPage list() {
        checkPermission();
        return apiKeyDao.list();
    }

    @Override
    public ApiKeyResultPage search(final SearchApiKeyRequest request) {
        checkPermission();
        return apiKeyDao.search(request);

//        // Validate filters
//        if (searchRequest.getFilters() != null) {
//            for (String key : searchRequest.getFilters().keySet()) {
//                switch (key) {
//                    case "expiresOn":
//                    case "issuedOn":
//                    case "updatedOn":
//                        throw RestUtil.badRequest("Filtering by date is not supported.");
//                }
//            }
//        }
//        return tokenDao.searchTokens(searchRequest);
    }

    @Override
    public ApiKey create(final CreateApiKeyRequest createApiKeyRequest) {
        checkPermission();

        final String userId = securityContext.getUserId();

        final Optional<Integer> optionalAccountId = accountDao.getId(createApiKeyRequest.getUserId());
        final Integer accountId = optionalAccountId.orElseThrow(() ->
                new NoSuchUserException("Cannot find user to associate with this API key!"));

        // Parse and validate tokenType
        final Optional<ApiKeyType> optionalTokenType = getParsedTokenType(createApiKeyRequest.getTokenType());
        final ApiKeyType apiKeyType = optionalTokenType.orElseThrow(() ->
                RestUtil.badRequest("Unknown token type:" + createApiKeyRequest.getTokenType()));

        final Instant expiryInstant = createApiKeyRequest.getExpiresOnMs() == null
                ? null
                : Instant.ofEpochMilli(createApiKeyRequest.getExpiresOnMs());

        final long now = System.currentTimeMillis();

        // TODO This assumes we have only one clientId. In theory we may have multiple
        //   and then the UI would need to manage the client IDs in use
        final String clientId = openIdClientDetailsFactory.getClient().getClientId();

        final TokenBuilder tokenBuilder = tokenBuilderFactory
                .builder()
                .expirationTime(expiryInstant)
                .clientId(clientId)
                .subject(createApiKeyRequest.getUserId());

        final Instant actualExpiryDate = tokenBuilder.getExpirationTime();
        final String data = tokenBuilder.build();

        final ApiKey apiKey = new ApiKey();
        apiKey.setCreateTimeMs(now);
        apiKey.setCreateUser(userId);
        apiKey.setUpdateTimeMs(now);
        apiKey.setUpdateUser(userId);
        apiKey.setUserId(createApiKeyRequest.getUserId());
        apiKey.setType(apiKeyType.getText());
        apiKey.setData(data);
        apiKey.setExpiresOnMs(actualExpiryDate.toEpochMilli());
        apiKey.setComments(createApiKeyRequest.getComments());
        apiKey.setEnabled(createApiKeyRequest.isEnabled());

        return apiKeyDao.create(accountId, apiKey);
    }

    @Override
    public int deleteAll() {
        checkPermission();

        return apiKeyDao.deleteAllTokensExceptAdmins();
    }

    @Override
    public int delete(int id) {
        checkPermission();

        return apiKeyDao.deleteTokenById(id);
    }

    @Override
    public int delete(String token) {
        checkPermission();

        return apiKeyDao.deleteTokenByTokenString(token);
    }

    @Override
    public Optional<ApiKey> read(String token) {
        checkPermission();

        return apiKeyDao.readByToken(token);
    }

    @Override
    public Optional<ApiKey> read(int id) {
        checkPermission();

        return apiKeyDao.readById(id);
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
    public int toggleEnabled(int id, boolean isEnabled) {
        checkPermission();
        final String userId = securityContext.getUserId();

        Optional<Account> updatingUser = accountService.read(userId);

        return updatingUser
                .map(account -> apiKeyDao.enableOrDisableToken(id, isEnabled, account))
                .orElse(0);
    }

    private void checkPermission() {
        if (!securityContext.hasAppPermission(PermissionNames.MANAGE_USERS_PERMISSION)) {
            throw new PermissionException(securityContext.getUserId(), "You do not have permission to manage users");
        }
    }

    // It could be argued that the validity of the api key should be a prop in Token
    // and the API Keys page could display the validity.
    @Override
    public HealthCheck.Result getHealth() {
        // Check all our enabled api keys are valid
        final ResultPage<ApiKey> resultPage = apiKeyDao.list();

        final HealthCheck.ResultBuilder builder = HealthCheck.Result.builder();

        boolean isHealthy = true;

        final Map<String, Object> invalidDetails = new HashMap<>();
        for (final ApiKey apiKey : resultPage.getValues()) {
            if (apiKey.isEnabled()) {
                try {
                    tokenVerifier.verifyToken(
                            apiKey.getData(),
                            openIdClientDetailsFactory.getClient().getClientId());
                } catch (TokenException e) {
                    isHealthy = false;
                    final Map<String, String> details = new HashMap<>();
                    details.put("expiry", apiKey.getExpiresOnMs() != null
                            ? Instant.ofEpochMilli(apiKey.getExpiresOnMs()).toString()
                            : null);
                    details.put("error", e.getMessage());
                    invalidDetails.put(apiKey.getId().toString(), details);
                }
            }
        }

        if (isHealthy) {
            builder
                    .healthy()
                    .withMessage("All enabled API keys are valid");
        } else {
            builder
                    .unhealthy()
                    .withMessage("Some enabled API keys are invalid")
                    .withDetail("invalidDetails", invalidDetails);
        }

        return builder.build();
    }

//    @Override
//    public TokenConfig fetchTokenConfig() {
//        return tokenConfig;
//    }
}
