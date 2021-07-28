package stroom.app.commands;

import stroom.config.app.Config;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.security.api.SecurityContext;
import stroom.security.identity.account.Account;
import stroom.security.identity.account.AccountService;
import stroom.security.identity.token.Token;
import stroom.security.identity.token.TokenBuilder;
import stroom.security.identity.token.TokenBuilderFactory;
import stroom.security.identity.token.TokenDao;
import stroom.security.identity.token.TokenType;
import stroom.security.openid.api.OpenIdClientFactory;
import stroom.util.logging.LogUtil;

import com.google.inject.Injector;
import event.logging.CreateEventAction;
import event.logging.Outcome;
import event.logging.User;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import javax.inject.Inject;

/**
 * Creates an API key for a user the internal identity provider
 */
public class CreateApiKeyCommand extends AbstractStroomAccountConfiguredCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(CreateApiKeyCommand.class);
    private static final String COMMAND_NAME = "create_api_key";
    private static final String COMMAND_DESCRIPTION = "Creates an API key for a user in the local identity provider";

    private static final String USER_ID_ARG_NAME = "user";
    private static final String EXPIRY_DAYS_ARG_NAME = "expires-days";
    private static final String OUTPUT_FILE_PATH_ARG_NAME = "out-file";

    private static final String CLI_USER = "Stroom CLI";

    private final Path configFile;

    @Inject
    private TokenBuilderFactory tokenBuilderFactory;
    @Inject
    private OpenIdClientFactory openIdClientDetailsFactory;
    @Inject
    private TokenDao tokenDao;
    @Inject
    private AccountService accountService;
    @Inject
    private SecurityContext securityContext;
    @Inject
    private StroomEventLoggingService stroomEventLoggingService;

    public CreateApiKeyCommand(final Path configFile) {
        super(configFile, COMMAND_NAME, COMMAND_DESCRIPTION);
        this.configFile = configFile;
    }

    @Override
    public void configure(final Subparser subparser) {
        super.configure(subparser);

        subparser.addArgument("-u", "--" + USER_ID_ARG_NAME)
                .dest(USER_ID_ARG_NAME)
                .type(String.class)
                .required(true)
                .help("The user id of the account to issue the API key against, e.g. 'admin'");

        subparser.addArgument("-e", "--" + EXPIRY_DAYS_ARG_NAME)
                .dest(EXPIRY_DAYS_ARG_NAME)
                .type(Integer.class)
                .required(false)
                .help("Expiry (in days) from the creation time");

        subparser.addArgument("-o", "--" + OUTPUT_FILE_PATH_ARG_NAME)
                .dest(OUTPUT_FILE_PATH_ARG_NAME)
                .type(String.class)
                .required(false)
                .help("Path to the file where the API key will be written");
    }

    @Override
    protected void runCommand(final Bootstrap<Config> bootstrap,
                              final Namespace namespace,
                              final Config config,
                              final Injector injector) {

        injector.injectMembers(this);

        final String userId = namespace.getString(USER_ID_ARG_NAME);
        final String outputPath = namespace.getString(OUTPUT_FILE_PATH_ARG_NAME);

        try {
            securityContext.asProcessingUser(() -> {
                accountService.read(userId)
                        .ifPresentOrElse(
                                account -> {
                                    final Token token = createApiKey(namespace, account);
                                    if (outputToken(token, outputPath)) {
                                        final String msg = LogUtil.message("API key successfully created for user '{}'",
                                                userId);
                                        LOGGER.info(msg);
                                        System.exit(0);
                                    } else {
                                        final String msg = LogUtil.message("API key for user '{}' could not be output",
                                                userId);
                                        logEvent(userId, true, msg);
                                        System.exit(1);
                                    }
                                },
                                () -> {
                                    final String msg = LogUtil.message("Cannot issue API key as user account '{}' " +
                                            "does not exist", userId);
                                    LOGGER.info(msg);
                                    logEvent(userId, false, msg);
                                    System.exit(1);
                                });
            });
            System.exit(0);
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
            logEvent(userId, false, e.getMessage());
            System.exit(1);
        }
    }

    private Token createApiKey(final Namespace namespace, final Account account) {
        final Integer lifetimeDays = namespace.getInt(EXPIRY_DAYS_ARG_NAME);
        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        LOGGER.info("Creating API key for user '{}'", account.getUserId());

        Instant expiry;
        if (lifetimeDays != null) {
            expiry = now.plusDays(lifetimeDays).toInstant(ZoneOffset.UTC);
        } else {
            expiry = new Date(Long.MAX_VALUE).toInstant();
        }

        final TokenBuilder tokenBuilder = tokenBuilderFactory
                .expiryDateForApiKeys(expiry)
                .newBuilder(TokenType.API)
                .clientId(openIdClientDetailsFactory.getClient().getClientId())
                .subject(account.getUserId());

        final Token tokenParams = new Token();
        tokenParams.setCreateTimeMs(now.toEpochSecond(ZoneOffset.UTC));
        tokenParams.setCreateUser(CLI_USER);
        tokenParams.setUpdateTimeMs(now.toEpochSecond(ZoneOffset.UTC));
        tokenParams.setUpdateUser(CLI_USER);
        tokenParams.setUserId(account.getUserId());
        tokenParams.setUserEmail(account.getUserId());
        tokenParams.setTokenType(TokenType.API.getText());
        tokenParams.setData(tokenBuilder.build());
        tokenParams.setExpiresOnMs(tokenBuilder.getExpiryDate().toEpochMilli());
        tokenParams.setEnabled(true);

        // Register the token
        return tokenDao.create(account.getId(), tokenParams);
    }

    /**
     * Emit the API key token data to the console, or if an output file path was specified, to that file.
     * If the file write fails, the token will be deleted.
     * @return Whether the token was successfully written
     */
    private boolean outputToken(final Token token, final String path) {
        if (path != null) {
            // Output the token to a file path specified by the CLI user
            try {
                BufferedWriter writer = new BufferedWriter(new FileWriter(path));
                writer.write(token.getData());
                writer.close();
            } catch (IOException e) {
                LOGGER.error("API key for user '{}' could not be written to file. {}",
                        token.getUserId(), e.getMessage());

                // Destroy the created token
                tokenDao.deleteTokenById(token.getId());

                return false;
            }
        } else {
            // Output token to standard out
            LOGGER.info("Generated API key for user '{}': {}", token.getUserId(), token.getData());
        }

        return true;
    }

    private void logEvent(final String username,
                          final boolean wasSuccessful,
                          final String description) {

        stroomEventLoggingService.log(
                "CliCreateApiKey",
                LogUtil.message("An API key for user '{}' was created", username),
                CreateEventAction.builder()
                        .addUser(User.builder()
                                .withName(username)
                                .build())
                        .withOutcome(Outcome.builder()
                                .withSuccess(wasSuccessful)
                                .withDescription(description)
                                .build())
                        .build());
    }
}
