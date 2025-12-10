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

import stroom.app.BootstrapUtil;
import stroom.app.guice.AppModule;
import stroom.config.app.Config;
import stroom.security.api.SecurityContext;
import stroom.util.guice.GuiceUtil;

import com.google.inject.Injector;
import com.google.inject.Key;
import io.dropwizard.core.cli.ConfiguredCommand;
import io.dropwizard.core.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.sql.DataSource;

// TODO consider refactoring this into AbstractStroomCommand with an abstract method to return a guice module
//  to create the child injector with.
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
    private final String commandName;

    public AbstractStroomAccountConfiguredCommand(final Path configFile,
                                                  final String commandName,
                                                  final String description) {
        super(commandName, description);
        this.configFile = configFile;
        this.commandName = commandName;
    }

    @Override
    protected void run(final Bootstrap<Config> bootstrap,
                       final Namespace namespace,
                       final Config config) throws Exception {

        LOGGER.info("Using application configuration file {}",
                configFile.toAbsolutePath().normalize());

        try {
            LOGGER.debug("Creating bootstrap injector");
            final Injector bootstrapInjector = BootstrapUtil.bootstrapApplication(
                    config, configFile);

            LOGGER.debug("Creating app injector");
            final Injector appInjector = bootstrapInjector.createChildInjector(
                    new AppModule());

            // Force guice to get all datasource instances from the multibinder
            // so the migration will be run for each stroom module
            // Relies on all db modules adding an entry to the multibinder
            appInjector.getInstance(Key.get(GuiceUtil.setOf(DataSource.class)));

            LOGGER.info("DB migration complete");

            final SecurityContext securityContext = appInjector.getInstance(SecurityContext.class);

            securityContext.asProcessingUser(() -> {
                info(LOGGER, "Running command " + commandName + " with arguments: " + argsToString(namespace));

                try {
                    runCommand(bootstrap, namespace, config, appInjector);
                } catch (final Exception e) {
                    final String msg = "Error running command "
                            + commandName
                            + ": " + e.getMessage()
                            + ". Check logs for more detail.";
                    System.err.println(msg);
                    LOGGER.error(msg, e);
                    System.exit(1);
                }

                info(LOGGER, "Command " + commandName + " completed successfully");
                System.exit(0);
            });
        } catch (final Exception e) {
            final String msg = "Error initialising application";
            LOGGER.error(msg, e);
            System.err.println(msg);
            System.exit(1);
        }
    }

    /**
     * Log to logger as INFO and stdout
     *
     * @param msg The message
     */
    protected void info(final Logger logger, final String msg) {
        logger.info(msg);
        System.out.println(msg);
    }

    /**
     * Log to logger as INFO and stdout
     *
     * @param msg    The message
     * @param indent Indent used for stdout message
     */
    protected void info(final Logger logger, final String msg, final String indent) {
        logger.info(msg);
        System.out.println(indent + msg);
    }

    /**
     * Log to logger as WARN and stdout
     *
     * @param msg The message
     */
    protected void warn(final Logger logger, final String msg) {
        logger.warn(msg);
        System.out.println(msg);
    }

    /**
     * Log to logger as WARN and stdout
     *
     * @param msg    The message
     * @param indent Indent used for stdout message
     */
    protected void warn(final Logger logger, final String msg, final String indent) {
        logger.warn(msg);
        System.out.println(indent + msg);
    }

    protected String argsToString(final Namespace namespace) {
        if (namespace == null) {
            return "";
        } else {
            return namespace.getAttrs()
                    .entrySet()
                    .stream()
                    .filter(entry ->
                            getArgumentNames().contains(entry.getKey()))
                    .filter(entry -> entry.getValue() != null)
                    .flatMap(entry -> {
                        final Object value = entry.getValue();
                        if (value instanceof final List<?> listVal) {
                            return listVal.stream()
                                    .map(item -> Map.entry(entry.getKey(), (Object) item));
                        } else {
                            return Stream.of(entry);
                        }
                    })
                    .sorted(Entry.comparingByKey())
                    .map(entry ->
                            "--" + entry.getKey() + " " + argValueToString(entry.getValue()))
                    .collect(Collectors.joining(" "));
        }
    }

    final String argValueToString(final Object value) {
        if (value instanceof final List<?> listVal) {
            return listVal.stream()
                    .map(item -> "'" + item.toString() + "'")
                    .collect(Collectors.joining(" "));
        } else {
            return value.toString();
        }
    }

    /**
     * Run the CLI command under the context of the Admin user.
     */
    protected abstract void runCommand(final Bootstrap<Config> bootstrap,
                                       final Namespace namespace,
                                       final Config config,
                                       final Injector injector);

    protected abstract Set<String> getArgumentNames();
}
