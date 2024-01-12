package stroom.app.commands;

import stroom.config.app.Config;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.security.api.SecurityContext;
import stroom.security.impl.UserService;
import stroom.security.impl.apikey.ApiKeyService;
import stroom.security.shared.CreateApiKeyRequest;
import stroom.security.shared.CreateApiKeyResponse;
import stroom.security.shared.User;
import stroom.util.NullSafe;
import stroom.util.logging.LogUtil;
import stroom.util.shared.UserName;

import com.google.inject.Injector;
import event.logging.CreateEventAction;
import event.logging.Outcome;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Set;
import javax.inject.Inject;

/**
 * Creates an API key for a user the internal identity provider
 */
public class CreateApiKeyCommand extends AbstractStroomAppCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(CreateApiKeyCommand.class);
    private static final String COMMAND_NAME = "create_api_key";
    private static final String COMMAND_DESCRIPTION = "Creates an API key for a user in the local identity provider";

    private static final String USER_ID_ARG_NAME = "user";
    private static final String EXPIRY_DAYS_ARG_NAME = "expiresDays";
    private static final String API_KEY_NAME_ARG_NAME = "keyName";
    private static final String OUTPUT_FILE_PATH_ARG_NAME = "outFile";
    private static final Set<String> ARGUMENT_NAMES = Set.of(
            USER_ID_ARG_NAME,
            EXPIRY_DAYS_ARG_NAME,
            API_KEY_NAME_ARG_NAME,
            OUTPUT_FILE_PATH_ARG_NAME);

    private final Path configFile;

    @Inject
    private ApiKeyService apiKeyService;
    @Inject
    private UserService userService;
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

        subparser.addArgument(asArg('u', USER_ID_ARG_NAME))
                .dest(USER_ID_ARG_NAME)
                .type(String.class)
                .required(true)
                .help("The user id of the account to issue the API key against, e.g. 'admin'");

        subparser.addArgument(asArg('e', EXPIRY_DAYS_ARG_NAME))
                .dest(EXPIRY_DAYS_ARG_NAME)
                .type(Integer.class)
                .required(false)
                .help("Expiry (in days) from the creation time");

        subparser.addArgument(asArg('n', API_KEY_NAME_ARG_NAME))
                .dest(API_KEY_NAME_ARG_NAME)
                .type(String.class)
                .required(true)
                .help("The name of the API key being created");

        subparser.addArgument(asArg('o', OUTPUT_FILE_PATH_ARG_NAME))
                .dest(OUTPUT_FILE_PATH_ARG_NAME)
                .type(String.class)
                .required(false)
                .help("Path to the file where the API key will be written");
    }

    @Override
    public Set<String> getArgumentNames() {
        return ARGUMENT_NAMES;
    }

    @Override
    protected void runSecuredCommand(final Bootstrap<Config> bootstrap,
                                     final Namespace namespace,
                                     final Config config,
                                     final Injector injector) {

        injector.injectMembers(this);

        final String userId = namespace.getString(USER_ID_ARG_NAME);
        final String outputPath = namespace.getString(OUTPUT_FILE_PATH_ARG_NAME);

        userService.getUserBySubjectId(userId)
                .map(User::asUserName)
                .ifPresentOrElse(
                        userName -> {
                            final CreateApiKeyResponse response = createApiKey(namespace, userName);
                            if (outputApiKey(response, outputPath)) {
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
                            final String msg = LogUtil.message("Cannot issue API key as Stroom user '{}' " +
                                    "does not exist", userId);
                            logEvent(userId, false, msg);
                            throw new RuntimeException(msg);
                        });
    }

    private CreateApiKeyResponse createApiKey(final Namespace namespace,
                                              final UserName userName) {
        final String apiKeyName = namespace.getString(API_KEY_NAME_ARG_NAME);
        if (NullSafe.isBlankString(apiKeyName)) {
            throw new RuntimeException("A name must be provided for the API key");
        }
        final Integer lifetimeDays = namespace.getInt(EXPIRY_DAYS_ARG_NAME);
        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        LOGGER.info("Creating API key for user '{}'", userName.getUserIdentityForAudit());

        Instant expirationTime;
        if (lifetimeDays != null) {
            expirationTime = now.plusDays(lifetimeDays).toInstant(ZoneOffset.UTC);
        } else {
            expirationTime = new Date(Long.MAX_VALUE).toInstant();
        }

        final CreateApiKeyResponse response = apiKeyService.create(new CreateApiKeyRequest(
                userName,
                expirationTime.toEpochMilli(),
                apiKeyName,
                "Created by 'create_api_key' command.",
                true));

        return response;
    }

    /**
     * Emit the API key token data to the console, or if an output file path was specified, to that file.
     * If the file write fails, the token will be deleted.
     *
     * @return Whether the token was successfully written
     */
    private boolean outputApiKey(final CreateApiKeyResponse createApiKeyResponse,
                                 final String path) {
        if (path != null) {
            // Output the API key to a file path specified by the CLI user
            try {
                final String apiKey = createApiKeyResponse.getApiKey();
                BufferedWriter writer = new BufferedWriter(new FileWriter(path));
                writer.write(apiKey);
                writer.close();

                final File fileInfo = new File(path);
                LOGGER.info("Wrote API key for user '{}' to file '{}'",
                        createApiKeyResponse.getHashedApiKey().getOwner().getUserIdentityForAudit(),
                        fileInfo.getAbsolutePath());
            } catch (IOException e) {
                LOGGER.error("API key for user '{}' could not be written to file. {}",
                        createApiKeyResponse.getHashedApiKey().getOwner().getUserIdentityForAudit(),
                        LogUtil.exceptionMessage(e));

                // Destroy the created API key
                apiKeyService.delete(createApiKeyResponse.getHashedApiKey().getId());
                return false;
            }
        } else {
            // Output API key to standard out
            LOGGER.info("Generated API key for user '{}': '{}'",
                    createApiKeyResponse.getHashedApiKey().getOwner().getUserIdentityForAudit(),
                    createApiKeyResponse.getApiKey());
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
                        .addUser(event.logging.User.builder()
                                .withName(username)
                                .build())
                        .withOutcome(Outcome.builder()
                                .withSuccess(wasSuccessful)
                                .withDescription(description)
                                .build())
                        .build());
    }
}
