package stroom.app.commands;

import stroom.config.app.Config;
import stroom.security.api.HasJwt;
import stroom.security.api.ServiceUserFactory;
import stroom.security.api.UserIdentity;
import stroom.security.openid.api.IdpType;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.util.NullSafe;
import stroom.util.authentication.HasRefreshable;
import stroom.util.authentication.Refreshable;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.google.inject.Injector;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import javax.inject.Inject;

/**
 * Command for fetching an acess token from the external IDP using the client credentials
 * flow with Stroom's client credentials, i.e. a processing user token.
 */
public class FetchAccessTokenCommand extends AbstractStroomAppCommand {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FetchAccessTokenCommand.class);

    private static final String COMMAND_NAME = "fetch_proc_user_token";
    private static final String COMMAND_DESCRIPTION = "Fetches an OIDC access token for the internal processing " +
            "user from the configured identity provider using Stroom's client credentials.";

    private static final String OUTPUT_FILE_PATH_ARG_NAME = "outFile";
    private static final Set<String> ARGUMENT_NAMES = Set.of(OUTPUT_FILE_PATH_ARG_NAME);

    @Inject
    private ServiceUserFactory serviceUserFactory;
    @Inject
    private OpenIdConfiguration openIdConfigProvider;

    public FetchAccessTokenCommand(final Path configFile) {
        super(configFile, COMMAND_NAME, COMMAND_DESCRIPTION);
    }

    @Override
    public void configure(final Subparser subparser) {
        super.configure(subparser);

        subparser.addArgument(asArg('o', OUTPUT_FILE_PATH_ARG_NAME))
                .dest(OUTPUT_FILE_PATH_ARG_NAME)
                .type(String.class)
                .required(false)
                .help("Path to the file where the access token will be written");
    }

    @Override
    protected void runSecuredCommand(final Bootstrap<Config> bootstrap,
                                     final Namespace namespace,
                                     final Config config,
                                     final Injector injector) throws Exception {

        injector.injectMembers(this);
        final Path outputPath = NullSafe.get(namespace.getString(OUTPUT_FILE_PATH_ARG_NAME), Path::of);
        final IdpType idpType = Objects.requireNonNull(openIdConfigProvider.getIdentityProviderType(),
                "IDP type should not be null in the configuration");

        info(LOGGER, "Configured IDP type: " + idpType);

        if (idpType == IdpType.INTERNAL_IDP || idpType == IdpType.EXTERNAL_IDP) {
            info(LOGGER, LogUtil.message("Fetching token from IDP {}",
                    openIdConfigProvider.getTokenEndpoint()));
            final UserIdentity serviceUserIdentity = serviceUserFactory.createServiceUserIdentity();

            extractToken(serviceUserIdentity, outputPath);
        } else {
            throw new UnsupportedOperationException(LogUtil.message(
                    "Command {} is not supported for IDP type {}", COMMAND_NAME, idpType));
        }
    }

    private void extractToken(final UserIdentity serviceUserIdentity,
                              final Path outputPath)
            throws IOException {

        if (serviceUserIdentity instanceof final HasJwt hasJwt) {
            final String jwt = hasJwt.getJwt();
            Objects.requireNonNull(jwt, "JWT is missing");
            outputJwt(jwt, outputPath);

            Instant expiry = null;
            Duration expiryDuration = null;
            if (serviceUserIdentity instanceof final HasRefreshable<?> hasRefreshable) {
                final Refreshable refreshable = hasRefreshable.getRefreshable();
                expiry = NullSafe.get(refreshable,
                        Refreshable::getExpireTimeEpochMs,
                        Instant::ofEpochMilli);
                expiryDuration = NullSafe.get(expiry,
                        expiry2 -> Duration.between(Instant.now(), expiry2));
            }

            info(LOGGER, "Access token successfully obtained. Expire time: {}, expires in: {}",
                    Objects.requireNonNullElse(expiry, "?"),
                    Objects.requireNonNullElse(expiryDuration, "?"));
        } else if (serviceUserIdentity == null) {
            throw new RuntimeException("Null service user identity");
        } else {
            throw new RuntimeException(LogUtil.message(
                    "User identity type {} does not have a token.", serviceUserIdentity.getClass().getSimpleName()));
        }
    }

    @Override
    protected Set<String> getArgumentNames() {
        return ARGUMENT_NAMES;
    }

    /**
     * Emit the API key token data to the console, or if an output file path was specified, to that file.
     * If the file write fails, the token will be deleted.
     *
     * @return Whether the token was successfully written
     */
    private boolean outputJwt(final String jwt, final Path path) throws IOException {
        if (path != null) {
            // Output the key to a file path specified by the CLI user
            try {
                Files.createDirectories(path.getParent());
                Files.writeString(
                        path,
                        jwt,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING);

                info(LOGGER, "Wrote access token to file '{}'",
                        path.toAbsolutePath().normalize());
            } catch (IOException e) {
                error(LOGGER, "Error writing token to file '{}': {}",
                        path.toAbsolutePath().normalize(), LogUtil.exceptionMessage(e));

                throw e;
            }
        } else {
            // Output API key to standard out
            info(LOGGER, "Generated processing user access token: {}", jwt);
        }
        return true;
    }
}
