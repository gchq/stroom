package stroom.app.commands;

import stroom.config.app.Config;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.security.api.SecurityContext;
import stroom.security.identity.account.AccountService;
import stroom.security.identity.account.CreateAccountRequest;
import stroom.security.impl.UserService;
import stroom.util.logging.LogUtil;

import com.google.inject.Injector;
import event.logging.Event;
import event.logging.ObjectFactory;
import event.logging.ObjectOutcome;
import event.logging.Outcome;
import event.logging.User;
import event.logging.UserDetails;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.nio.file.Path;

/**
 * Creates an account in the internal identity provider
 */
public class CreateAccountCommand extends AbstractStroomAccountConfiguredCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(CreateAccountCommand.class);
    private static final String COMMAND_NAME = "create_account";
    private static final String COMMAND_DESCRIPTION = "Creates the specified user account in the internal identity provider";

    private static final String USERNAME_ARG_NAME = "user";
    private static final String PASSWORD_ARG_NAME = "password";
    private static final String EMAIL_ARG_NAME = "email";
    private static final String FIRST_NAME_ARG_NAME = "firstName";
    private static final String LAST_NAME_ARG_NAME = "lastName";
    private static final String NO_PASSWORD_CHANGE = "noPasswordChange";
    private static final String NEVER_EXPIRES_CHANGE_ARG_NAME = "neverExpires";

    private final Path configFile;

    @Inject
    private UserService userService;
    @Inject
    private AccountService accountService;
    @Inject
    private SecurityContext securityContext;
    @Inject
    private StroomEventLoggingService stroomEventLoggingService;

    public CreateAccountCommand(final Path configFile) {
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
                .help("The password for the account");

        subparser.addArgument("-e", "--" + EMAIL_ARG_NAME)
                .dest(EMAIL_ARG_NAME)
                .type(String.class)
                .required(false)
                .help("The email address for the account");

        subparser.addArgument("-f", "--" + FIRST_NAME_ARG_NAME)
                .dest(FIRST_NAME_ARG_NAME)
                .type(String.class)
                .required(false)
                .help("The user's first name");

        subparser.addArgument("-s", "--" + LAST_NAME_ARG_NAME)
                .dest(FIRST_NAME_ARG_NAME)
                .type(String.class)
                .required(false)
                .help("The user's last name");

        subparser.addArgument("--" + NO_PASSWORD_CHANGE)
                .dest(NO_PASSWORD_CHANGE)
                .action(Arguments.storeTrue())
                .setDefault(false)
                .required(false)
                .help("If set do not require a password change on first login");

        subparser.addArgument("--" + NEVER_EXPIRES_CHANGE_ARG_NAME)
                .dest(NEVER_EXPIRES_CHANGE_ARG_NAME)
                .action(Arguments.storeTrue())
                .required(false)
                .help("The account will never expire");
    }

    @Override
    protected void runCommand(final Bootstrap<Config> bootstrap,
                              final Namespace namespace,
                              final Config config,
                              final Injector injector) {

        injector.injectMembers(this);

        final String username = namespace.getString(USERNAME_ARG_NAME);

        try {
            securityContext.asProcessingUser(() -> {
                accountService.read(username)
                        .ifPresentOrElse(
                                account -> {
                                    final String msg = LogUtil.message("An account for user '{}' already exists", username);
                                    LOGGER.error(msg);
                                    logEvent(username, false, msg);
                                    System.exit(1);
                                },
                                () -> {
                                    createAccount(namespace, username);
                                    final String msg = LogUtil.message("Account creation complete for user '{}'", username);
                                    LOGGER.info(msg);
                                    logEvent(username, true, msg);
                                    System.exit(0);
                                });
            });
            System.exit(0);
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
            logEvent(username, false, e.getMessage());
            System.exit(1);
        }
    }

    private void createAccount(final Namespace namespace, final String username) {
        final String password = namespace.getString(PASSWORD_ARG_NAME);
        final String email = namespace.getString(EMAIL_ARG_NAME);
        final String firstName = namespace.getString(FIRST_NAME_ARG_NAME);
        final String lastName = namespace.getString(LAST_NAME_ARG_NAME);
        final boolean noPasswordChange = namespace.getBoolean(NO_PASSWORD_CHANGE);
        final boolean neverExpires = namespace.getBoolean(NEVER_EXPIRES_CHANGE_ARG_NAME);
        final long now = System.currentTimeMillis();

        LOGGER.info("Creating account for user '{}'", username);

        CreateAccountRequest createAccountRequest = new CreateAccountRequest(
                firstName,
                lastName,
                username,
                email,
                null,
                password,
                password,
                !noPasswordChange,
                neverExpires);

        accountService.create(createAccountRequest);
    }

    private void logEvent(final String username,
                          final boolean wasSuccessful,
                          final String description) {

        final Event event = stroomEventLoggingService.createAction(
                "CliCreateInternalIdentityProviderUser",
                LogUtil.message("An account for user {} was created in the internal identity provider", username));

        final ObjectFactory objectFactory = new ObjectFactory();
        final ObjectOutcome createAction = objectFactory.createObjectOutcome();
        event.getEventDetail().setCreate(createAction);

        final User user = objectFactory.createUser();
        createAction.getObjects().add(user);
        user.setName(username);

        // TODO @AT UserDetails appears to be empty for some reason so can't set the user's name on it
        final UserDetails userDetails = objectFactory.createUserDetails();
        user.setUserDetails(userDetails);

        final Outcome outcome = objectFactory.createOutcome();
        createAction.setOutcome(outcome);
        outcome.setSuccess(wasSuccessful);
        outcome.setDescription(description);

        stroomEventLoggingService.log(event);
    }
}
