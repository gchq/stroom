package stroom.app.commands;

import stroom.app.BootstrapUtil;
import stroom.config.app.Config;
import stroom.db.util.DataSourceProxy;
import stroom.util.guice.GuiceUtil;

import com.google.inject.Injector;
import com.google.inject.Key;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;

/**
 * Additional DW Command so instead of
 * ... server ../local.yml
 * you can do
 * ... migrate ../local.yml
 * and it will run all the DB migrations without running up the app
 */
public class DbMigrationCommand extends ConfiguredCommand<Config> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DbMigrationCommand.class);
    private static final String COMMAND_NAME = "migrate";
    private static final String COMMAND_DESCRIPTION = "Runs all the DB migrations then quits.";

    private final Path configFile;

    public DbMigrationCommand(final Path configFile) {
        super(COMMAND_NAME, COMMAND_DESCRIPTION);
        this.configFile = configFile;
    }

    @Override
    protected void run(final Bootstrap bootstrap,
                       final Namespace namespace,
                       final Config config) throws Exception {

        LOGGER.info("Using application configuration file {}",
                configFile.toAbsolutePath().normalize());

        LOGGER.debug("Creating injector");
        try {
            LOGGER.debug("Creating bootstrap injector");
            final Injector bootstrapInjector = BootstrapUtil.bootstrapApplication(
                    config, configFile);

            // Force guice to get all datasource instances from the multibinder
            // so the migration will be run for each stroom module
            // Relies on all db modules adding an entry to the multibinder
            final Set<DataSource> dataSources = bootstrapInjector.getInstance(
                    Key.get(GuiceUtil.setOf(DataSource.class)));

            LOGGER.info("Used {} data sources:\n{}",
                    dataSources.size(),
                    dataSources.stream()
                            .map(dataSource -> dataSource instanceof DataSourceProxy
                                    ? ((DataSourceProxy) dataSource).getName()
                                    : dataSource.getClass().getName())
                            .map(name -> "  " + name)
                            .sorted()
                            .collect(Collectors.joining("\n")));

        } catch (Exception e) {
            LOGGER.error("Error running DB migrations", e);
            System.exit(1);
        }

        LOGGER.info("DB migration complete");
    }
}
