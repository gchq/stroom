package stroom.app.commands;

import stroom.app.guice.BootstrapModule;
import stroom.config.app.Config;
import stroom.config.global.impl.ConfigProvidersModule;
import stroom.event.logging.impl.EventLoggingModule;
import stroom.security.identity.account.AccountModule;
import stroom.security.identity.db.IdentityDaoModule;
import stroom.security.impl.SecurityContextModule;
import stroom.security.impl.SecurityModule;
import stroom.security.impl.db.SecurityDaoModule;
import stroom.util.guice.GuiceUtil;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import javax.sql.DataSource;

/**
 * Additional DW Command so instead of
 * ... server ../local.yml
 * you can do
 * ... migrate ../local.yml
 * and it will run all the DB migrations without running up the app
 */
public abstract class AbstractStroomAccountConfiguredCommand extends ConfiguredCommand<Config> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractStroomAccountConfiguredCommand.class);

    private final Path configFile;

    public AbstractStroomAccountConfiguredCommand(final Path configFile,
                                                  final String commandName,
                                                  final String description) {
        super(commandName, description);
        this.configFile = configFile;
    }

    @Override
    protected void run(final Bootstrap<Config> bootstrap,
                       final Namespace namespace,
                       final Config config) throws Exception {

        LOGGER.info("Using application configuration file {}",
                configFile.toAbsolutePath().normalize());

        LOGGER.info("Running all DB migrations");

        LOGGER.debug("Creating dbMigrationModule");

        final AbstractModule module = new AbstractModule() {
            @Override
            protected void configure() {
                install(new BootstrapModule(config, configFile));
                install(new AccountModule());
                install(new ConfigProvidersModule());
                install(new EventLoggingModule());
                install(new IdentityDaoModule());
                install(new SecurityContextModule());
                install(new SecurityDaoModule());
                install(new SecurityModule());
            }
        };

        LOGGER.debug("Creating injector");
        try {
            final Injector injector = Guice.createInjector(module);

            // Force guice to get all datasource instances from the multibinder
            // so the migration will be run for each stroom module
            // Relies on all db modules adding an entry to the multibinder
            injector.getInstance(Key.get(GuiceUtil.setOf(DataSource.class)));

            LOGGER.info("DB migration complete");

            runCommand(bootstrap, namespace, config, injector);
        } catch (Exception e) {
            LOGGER.error("Error running DB migrations", e);
            System.exit(1);
        }
    }

    protected abstract void runCommand(final Bootstrap<Config> bootstrap,
                                       final Namespace namespace,
                                       final Config config,
                                       final Injector injector);
}
