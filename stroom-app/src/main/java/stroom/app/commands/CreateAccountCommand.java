package stroom.app.commands;

import stroom.config.app.Config;
import stroom.security.identity.account.Account;
import stroom.security.identity.account.AccountDao;

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
public class CreateAccountCommand extends AbstractStroomAccountConfiguredCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(CreateAccountCommand.class);
    private static final String COMMAND_NAME = "create_account";
    private static final String COMMAND_DESCRIPTION = "Creates the specified user account in the internal identity provider";
    private static final String USERNAME_ARG_NAME = "user";
    private static final String PASSWORD_ARG_NAME = "password";

    private final Path configFile;

    @Inject
    private AccountDao accountDao;

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
                .help("The user id of the account");

        subparser.addArgument("-p", "--" + PASSWORD_ARG_NAME)
                .dest(PASSWORD_ARG_NAME)
                .type(String.class)
                .required(true)
                .help("The password for the account");
    }

    @Override
    protected void runCommand(final Bootstrap<Config> bootstrap,
                              final Namespace namespace,
                              final Config config,
                              final Injector injector) {

        final String username = namespace.getString(USERNAME_ARG_NAME);
        final String password = namespace.getString(PASSWORD_ARG_NAME);

        LOGGER.debug("Creating account for {} with password {}", username, password);

        injector.injectMembers(this);

        accountDao.get(username)
                .ifPresentOrElse(
                        account -> {
                            LOGGER.error("An account for user {} already exists", username);
                            System.exit(1);
                        },
                        () -> {
                            createAccount(username, password);
                            LOGGER.info("Account creation complete for user {}", username);
                            System.exit(0);
                        });
    }

    private void createAccount(final String username, final String password) {
        final long now = System.currentTimeMillis();

        final Account account = new Account();
        account.setUserId(username);
        account.setNeverExpires(true);
        account.setForcePasswordChange(true);
        account.setCreateTimeMs(now);
        account.setCreateUser("INTERNAL_PROCESSING_USER");
        account.setUpdateTimeMs(now);
        account.setUpdateUser("INTERNAL_PROCESSING_USER");
        account.setEnabled(true);

        accountDao.create(account, password);
    }
}
