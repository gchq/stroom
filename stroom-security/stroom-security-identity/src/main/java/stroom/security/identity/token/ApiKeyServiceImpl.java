package stroom.security.identity.token;

import stroom.security.api.SecurityContext;
import stroom.security.identity.account.Account;
import stroom.security.identity.account.AccountDao;
import stroom.security.identity.account.AccountService;
import stroom.security.identity.exceptions.NoSuchUserException;
import stroom.security.openid.api.OpenIdClientFactory;
import stroom.security.shared.PermissionNames;
import stroom.util.shared.PermissionException;

import java.time.Instant;
import java.util.Optional;
import javax.inject.Inject;

public class ApiKeyServiceImpl implements ApiKeyService {

    private final ApiKeyDao apiKeyDao;
    private final AccountDao accountDao;
    private final SecurityContext securityContext;
    private final AccountService accountService;
    private final TokenBuilderFactory tokenBuilderFactory;
    private final OpenIdClientFactory openIdClientDetailsFactory;

    @Inject
    ApiKeyServiceImpl(final ApiKeyDao apiKeyDao,
                      final AccountDao accountDao,
                      final SecurityContext securityContext,
                      final AccountService accountService,
                      final TokenBuilderFactory tokenBuilderFactory,
                      final OpenIdClientFactory openIdClientDetailsFactory) {
        this.apiKeyDao = apiKeyDao;
        this.accountDao = accountDao;
        this.securityContext = securityContext;
        this.accountService = accountService;
        this.tokenBuilderFactory = tokenBuilderFactory;
        this.openIdClientDetailsFactory = openIdClientDetailsFactory;
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
    }

    @Override
    public ApiKey create(final CreateApiKeyRequest createApiKeyRequest) {
        checkPermission();

        final String userId = securityContext.getUserId();

        final Optional<Integer> optionalAccountId = accountDao.getId(createApiKeyRequest.getUserId());
        final Integer accountId = optionalAccountId.orElseThrow(() ->
                new NoSuchUserException("Cannot find user to associate with this API key!"));

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
        apiKey.setType(createApiKeyRequest.getTokenType());
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
}
