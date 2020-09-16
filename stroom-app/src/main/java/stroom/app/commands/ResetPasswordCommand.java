package stroom.app.commands;

import stroom.config.app.Config;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.security.api.SecurityContext;
import stroom.security.identity.account.AccountService;
import stroom.security.identity.account.UpdateAccountRequest;
import stroom.util.logging.LogUtil;

import com.google.inject.Injector;
import event.logging.AuthenticateAction;
import event.logging.AuthenticateOutcome;
import event.logging.Event;
import event.logging.Event.EventDetail.Authenticate;
import event.logging.ObjectFactory;
import event.logging.User;
import event.logging.UserDetails;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.nio.file.Path;

/**
 * Resets the password of an account in the internal identity provider
 */
public class ResetPasswordCommand extends AbstractStroomAccountConfiguredCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResetPasswordCommand.class);

    private static final String COMMAND_NAME = "reset_password";
    private static final String COMMAND_DESCRIPTION = "Reset the password of the user account " +
            "in the internal identity provider";

    private static final String USERNAME_ARG_NAME = "user";
    private static final String PASSWORD_ARG_NAME = "password";

    private final Path configFile;

    @Inject
    private AccountService accountService;
    @Inject
    private SecurityContext securityContext;
    @Inject
    private StroomEventLoggingService stroomEventLoggingService;

    public ResetPasswordCommand(final Path configFile) {
        super(configFile, COMMAND_NAME, COMMAND_DESCRIPTION);
        this.configFile = configFile;
    }

    @Override
    public void configure(final Subparser subparser) {
        super.configure(subparser);

        subparser.addArgument("-u", "--" + USERNAME_ARG_NAME)
                .dest(USERNAME_ARG_NAME)
                .type(String.class)
                .required(true)
                .help("The user id of the account, e.g. 'admin'");

        subparser.addArgument("-p", "--" + PASSWORD_ARG_NAME)
                .dest(PASSWORD_ARG_NAME)
                .type(String.class)
                .required(true)
                .help("The new password for the account");
    }

    @Override
    protected void runCommand(final Bootstrap<Config> bootstrap,
                              final Namespace namespace,
                              final Config config,
                              final Injector injector) {

        final String username = namespace.getString(USERNAME_ARG_NAME);
        final String newPassword = namespace.getString(PASSWORD_ARG_NAME);

        LOGGER.debug("Resetting password for account {}", username);

        injector.injectMembers(this);


        securityContext.asProcessingUser(() -> {
            accountService.read(username)
                    .ifPresentOrElse(
                            account -> {
                                // Clear various flags to ensure it can be logged into.
                                account.setLocked(false);
                                account.setInactive(false);
                                account.setEnabled(true);
                                account.setLoginFailures(0);

                                final UpdateAccountRequest updateAccountRequest = new UpdateAccountRequest(
                                        account,
                                        newPassword,
                                        newPassword);

                                accountService.update(updateAccountRequest, account.getId());

                                String msg = LogUtil.message("Password reset complete for user {}", username);
                                LOGGER.info(msg);
                                logEvent(username, true, msg);
                                System.exit(0);
                            },
                            () -> {
                                String msg = LogUtil.message("User {} does not have an account", username);
                                LOGGER.error(msg);
                                logEvent(username, false, msg);
                                System.exit(1);
                            });
        });
    }

    private void logEvent(final String username,
                          final boolean wasSuccessful,
                          final String description) {
        final Event event = stroomEventLoggingService.createAction(
                "CliChangePassword",
                LogUtil.message("The password for user {} was changed from the command line", username));

        final ObjectFactory objectFactory = new ObjectFactory();
        final Authenticate authenticate = objectFactory.createEventEventDetailAuthenticate();
        event.getEventDetail().setAuthenticate(authenticate);

        authenticate.setAction(AuthenticateAction.CHANGE_PASSWORD);

        final AuthenticateOutcome authenticateOutcome = objectFactory.createAuthenticateOutcome();
        authenticate.setOutcome(authenticateOutcome);
        authenticateOutcome.setSuccess(wasSuccessful);
        authenticateOutcome.setDescription(description);

        final User user = objectFactory.createUser();
        authenticate.setUser(user);
        user.setName(username);

        // TODO @AT UserDetails appears to be empty for some reason so can't set the user's name on it
        final UserDetails userDetails = objectFactory.createUserDetails();
        user.setUserDetails(userDetails);

        // We are running as proc user so try and get the OS user, though that may just be a shared account
        event.getEventSource().getUser().setId(System.getProperty("user.name"));

        stroomEventLoggingService.log(event);
    }
}
