package stroom.security.identity.account;

import stroom.security.api.ProcessingUserIdentityProvider;
import stroom.security.api.UserIdentity;
import stroom.security.identity.token.Token;
import stroom.security.identity.token.TokenBuilder;
import stroom.security.identity.token.TokenBuilderFactory;
import stroom.security.identity.token.TokenDao;
import stroom.security.identity.token.TokenType;
import stroom.security.openid.api.OpenIdClientFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Singleton
class ProcessingUserIdentityProviderImpl implements ProcessingUserIdentityProvider {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProcessingUserIdentityProvider.class);
    private static final String INTERNAL_PROCESSING_USER = "INTERNAL_PROCESSING_USER";
    private static final long ONE_DAY = TimeUnit.DAYS.toMillis(1);
    private static final long THIRTY_DAYS = ONE_DAY * 30;

    private final AccountDao accountDao;
    private final TokenDao tokenDao;
    private final TokenBuilderFactory tokenBuilderFactory;
    private final OpenIdClientFactory openIdClientDetailsFactory;

    private AtomicLong lastFetchTime = new AtomicLong(0);
    private AtomicLong lastTokenCreationTime = new AtomicLong(0);
    private volatile UserIdentity userIdentity;


    @Inject
    ProcessingUserIdentityProviderImpl(final AccountDao accountDao,
                                       final TokenDao tokenDao,
                                       final TokenBuilderFactory tokenBuilderFactory,
                                       final OpenIdClientFactory openIdClientDetailsFactory) {
        this.accountDao = accountDao;
        this.tokenDao = tokenDao;
        this.tokenBuilderFactory = tokenBuilderFactory;
        this.openIdClientDetailsFactory = openIdClientDetailsFactory;
    }

    @Override
    public UserIdentity get() {
        final long now = System.currentTimeMillis();
        // Don't cache the user identity for more than a day in case its token expires.
        if (userIdentity == null || lastFetchTime.get() < now - ONE_DAY) {
            final Account account = getAccount(now);
            final Token token = getToken(now, account);
            userIdentity = new ProcessingUserIdentity(INTERNAL_PROCESSING_USER, token.getData());
            lastFetchTime.set(now);
        }

        return userIdentity;
    }

    @Override
    public boolean isProcessingUser(final UserIdentity userIdentity) {
        // It is possible that the passed user identity has a different jws than our
        // instance one but this is ok as we are regularly refreshing tokens for the
        // proc user and it has been authenticated at this point.
        if (userIdentity != null) {
            return UserIdentity.IDENTITY_COMPARATOR.compare(userIdentity, get()) == 0;
        } else {
            LOGGER.debug("Null userIdentity");
            return false;
        }
    }

    private Account getAccount(final long now) {
        final Optional<Account> existingAccount = accountDao.get(INTERNAL_PROCESSING_USER);
        if (existingAccount.isPresent()) {
            return existingAccount.get();
        }

        try {
            final Account account = new Account();
            account.setCreateTimeMs(now);
            account.setCreateUser(INTERNAL_PROCESSING_USER);
            account.setUpdateTimeMs(now);
            account.setUpdateUser(INTERNAL_PROCESSING_USER);
            account.setUserId(INTERNAL_PROCESSING_USER);
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

        return accountDao.get(INTERNAL_PROCESSING_USER).orElseThrow(() ->
                new RuntimeException("Unable to retrieve internal processing user"));
    }

    private Token getToken(final long now, final Account account) {
        final List<Token> tokens = tokenDao.getTokensForAccount(account.getId());

        if (shouldCreateToken(tokens, now)) {
            // Synch block to stop this node from creating multiple tokens
            // Difficult to stop multiple node doing this though
            synchronized (this) {
                final List<Token> tokens2 = tokenDao.getTokensForAccount(account.getId());

                if (shouldCreateToken(tokens, now)) {
                    lastTokenCreationTime.set(now);
                    return createToken(now, account);
                } else {
                    // Another thread beat us to it so just the latest token
                    return tokens2.get(tokens.size() - 1);
                }
            }
        } else {
            // Grab the most recent token
            final Token token = tokens.get(tokens.size() - 1);

            // Delete old tokens.
            for (int i = 0; i < tokens.size() - 1; i++) {
                try {
                    tokenDao.deleteTokenById(tokens.get(i).getId());
                } catch (final RuntimeException e) {
                    LOGGER.debug(e.getMessage(), e);
                }
            }

            return token;
        }
    }

    private boolean shouldCreateToken(final List<Token> tokens, final long now) {
        if (tokens.size() == 0) {
            return true;
        } else {
            if (lastTokenCreationTime.get() == 0) {
                // System just booted up so find out when we last created a token from the
                // DB tokens
                final long lastTokenCreationTime = updateLastTokenCreationTime(tokens);
                return lastTokenCreationTime < now - THIRTY_DAYS;
            } else {
                // We have a lastTokenCreationTime so see how old it is
                return lastTokenCreationTime.get() < now - THIRTY_DAYS;
            }
        }
    }

    private long updateLastTokenCreationTime(final List<Token> tokens) {

        return lastTokenCreationTime.updateAndGet(val -> {
            // Get latest token
            long newVal = tokens.get(tokens.size() - 1).getCreateTimeMs();
            return Math.max(newVal, val);
        });
    }

    private Token createToken(final long now, final Account account) {
        final Instant timeToExpiryInSeconds = LocalDateTime.now().plusYears(1).toInstant(ZoneOffset.UTC);
        final TokenType tokenType = TokenType.API;

        final TokenBuilder tokenBuilder = tokenBuilderFactory
                .expiryDateForApiKeys(timeToExpiryInSeconds)
                .newBuilder(tokenType)
                .clientId(openIdClientDetailsFactory.getClient().getClientId())
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

        return tokenDao.create(account.getId(), token);
    }
}
