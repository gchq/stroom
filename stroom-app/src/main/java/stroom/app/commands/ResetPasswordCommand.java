package stroom.app.commands;

import stroom.config.app.Config;
import stroom.security.identity.account.AccountDao;
import stroom.security.identity.exceptions.NoSuchUserException;

import com.google.inject.Injector;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.nio.file.Path;

/**
 * Creates an account in the internal identity provider
 */
public class ResetPasswordCommand extends AbstractStroomAccountConfiguredCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResetPasswordCommand.class);

    private static final String COMMAND_NAME = "reset_password";
    private static final String COMMAND_DESCRIPTION = "Reset the password of the user account in the internal identity provider";

    private static final String USERNAME_ARG_NAME = "user";
    private static final String PASSWORD_ARG_NAME = "password";

    private final Path configFile;

    @Inject
    private AccountDao accountDao;

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
                .help("The user id of the account");

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
        final String password = namespace.getString(PASSWORD_ARG_NAME);

        LOGGER.debug("Resetting password for account {} with password {}", username, password);

        injector.injectMembers(this);

        try {
            accountDao.changePassword(username, password);
        } catch (NoSuchUserException e) {
            LOGGER.error("User {} does not have an account", username);
            System.exit(1);
        }

        LOGGER.info("Password reset complete for user {}", username);
        System.exit(0);
    }
}
