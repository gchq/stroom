/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.app.commands;

import stroom.config.app.Config;
import stroom.db.util.DataSourceProxy;
import stroom.util.guice.GuiceUtil;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import io.dropwizard.core.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;
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
public class DbMigrationCommand extends AbstractStroomBaseCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(DbMigrationCommand.class);
    private static final String COMMAND_NAME = "migrate";
    private static final String COMMAND_DESCRIPTION = "Runs all the DB migrations then quits.";

    private final Path configFile;

    public DbMigrationCommand(final Path configFile) {
        super(configFile, COMMAND_NAME, COMMAND_DESCRIPTION);
        this.configFile = configFile;
    }

    @Override
    protected void runCommand(final Bootstrap<Config> bootstrap,
                              final Namespace namespace,
                              final Config config,
                              final Injector injector) {

        // Force guice to get all datasource instances from the multibinder
        // so the migration will be run for each stroom module
        // Relies on all db modules adding an entry to the multibinder
        final Set<DataSource> dataSources = injector.getInstance(
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

    }

    @Override
    protected Set<String> getArgumentNames() {
        return Collections.emptySet();
    }

    @Override
    protected Optional<Module> getChildInjectorModule() {
        return Optional.empty();
    }
}
