package stroom.authentication.account;

import stroom.authentication.api.OpenIdClientDetailsFactory;
import stroom.authentication.token.Token;
import stroom.authentication.token.TokenBuilder;
import stroom.authentication.token.TokenBuilderFactory;
import stroom.authentication.token.TokenDao;
import stroom.security.api.ProcessingUserIdentityProvider;
import stroom.security.api.UserIdentity;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Singleton
class ProcessingUserIdentityProviderImpl implements ProcessingUserIdentityProvider {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProcessingUserIdentityProvider.class);
    private static final String INTERNAL_PROCESSING_USER = "INTERNAL_PROCESSING_USER";

    private final AccountDao accountDao;
    private final TokenDao tokenDao;
    private final TokenBuilderFactory tokenBuilderFactory;
    private final OpenIdClientDetailsFactory openIdClientDetailsFactory;

    private volatile UserIdentity userIdentity;

    @Inject
    ProcessingUserIdentityProviderImpl(final AccountDao accountDao,
                                       final TokenDao tokenDao,
                                       final TokenBuilderFactory tokenBuilderFactory,
                                       final OpenIdClientDetailsFactory openIdClientDetailsFactory) {
        this.accountDao = accountDao;
        this.tokenDao = tokenDao;
        this.tokenBuilderFactory = tokenBuilderFactory;
        this.openIdClientDetailsFactory = openIdClientDetailsFactory;
    }

    @Override
    public UserIdentity get() {
        if (userIdentity == null) {
            final Account account = getAccount();
            final Token token = getToken(account);
            userIdentity = new ProcessingUserIdentity(INTERNAL_PROCESSING_USER, token.getData());
        }

        return userIdentity;
    }

    private Account getAccount() {
        final Optional<Account> existingAccount = accountDao.get(INTERNAL_PROCESSING_USER);
        if (existingAccount.isPresent()) {
            return existingAccount.get();
        }

        try {
            final long now = System.currentTimeMillis();

            final Account account = new Account();
            account.setCreateTimeMs(now);
            account.setCreateUser(INTERNAL_PROCESSING_USER);
            account.setUpdateTimeMs(now);
            account.setUpdateUser(INTERNAL_PROCESSING_USER);
            account.setFirstName(INTERNAL_PROCESSING_USER);
            account.setLastName(INTERNAL_PROCESSING_USER);
            account.setEmail(INTERNAL_PROCESSING_USER);
            account.setComments(INTERNAL_PROCESSING_USER);
            account.setForcePasswordChange(false);
            account.setNeverExpires(true);
            account.setProcessingAccount(true);
            account.setLoginCount(0);
            account.setEnabled(true);
            accountDao.create(account, "");
        } catch (final RuntimeException e) {
            // Expected exception if the processing account already exists.
            LOGGER.debug(e::getMessage, e);
        }

        return accountDao.get(INTERNAL_PROCESSING_USER).orElseThrow(() -> new RuntimeException("Unable to retrieve internal processing user"));
    }

    private Token getToken(final Account account) {
        List<Token> tokens = tokenDao.getTokensForAccount(account.getId());
        if (tokens.size() > 0) {
            return tokens.get(0);
        }

        try {
            final long now = System.currentTimeMillis();
            final Instant timeToExpiryInSeconds = LocalDateTime.now().plusYears(1).toInstant(ZoneOffset.UTC);
            final Token.TokenType tokenType = Token.TokenType.API;

            final TokenBuilder tokenBuilder = tokenBuilderFactory
                    .expiryDateForApiKeys(timeToExpiryInSeconds)
                    .newBuilder(tokenType)
                    .clientId(openIdClientDetailsFactory.getOAuth2Client().getClientId())
                    .subject(INTERNAL_PROCESSING_USER);

            final Instant actualExpiryDate = tokenBuilder.getExpiryDate();
            final String data = tokenBuilder.build();

            final Token token = new Token();
            token.setCreateTimeMs(now);
            token.setCreateUser(INTERNAL_PROCESSING_USER);
            token.setUpdateTimeMs(now);
            token.setUpdateUser(INTERNAL_PROCESSING_USER);
            token.setUserEmail(INTERNAL_PROCESSING_USER);
            token.setTokenType(tokenType.getText());
            token.setData(data);
            token.setExpiresOnMs(actualExpiryDate.toEpochMilli());
            token.setComments(INTERNAL_PROCESSING_USER);
            token.setEnabled(true);

            tokenDao.create(account.getId(), token);

        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }

        tokens = tokenDao.getTokensForAccount(account.getId());
        if (tokens.size() > 0) {
            return tokens.get(0);
        }

        throw new RuntimeException("Unable to retrieve token for internal processing user");
    }
}
