package stroom.app.commands;

import stroom.config.app.Config;

import com.google.inject.Injector;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * Creates an account in the internal identity provider
 */
public class AddAppPermissionCommand extends AbstractStroomAccountConfiguredCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(AddAppPermissionCommand.class);
    private static final String COMMAND_NAME = "grant_permission";
    private static final String COMMAND_DESCRIPTION = "Grant an application permission to a user";
    private static final String USERNAME_ARG_NAME = "user";
    private static final String GROUP_ARG_NAME = "group";
    private static final String PERMISSION_ARG_NAME = "permission";

    private final Path configFile;

//    @Inject
//    private  AuthSeraccountDao;

    public AddAppPermissionCommand(final Path configFile) {
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
                .help("The id of the user");

        subparser.addArgument("-g", "--" + GROUP_ARG_NAME)
                .dest(GROUP_ARG_NAME)
                .type(String.class)
                .required(false)
                .help("The group to make the user a member of");

        subparser.addArgument("-p", "--" + PERMISSION_ARG_NAME)
                .dest(PERMISSION_ARG_NAME)
                .type(String.class)
                .required(true)
                .help("The name of the application permission to grant to the user (or group if a group is supplied)");

    }

    @Override
    protected void runCommand(final Bootstrap<Config> bootstrap,
                              final Namespace namespace,
                              final Config config,
                              final Injector injector) {

        final String username = namespace.getString(USERNAME_ARG_NAME);
        final String group = namespace.getString(GROUP_ARG_NAME);
        final String permissionName = namespace.getString(PERMISSION_ARG_NAME);

        LOGGER.debug("Granting permission {} to {} in group {}", permissionName, username, group);

        injector.injectMembers(this);



    }

    private List<String> getAppPermissionNames() {

        return Collections.emptyList();
    }
}
