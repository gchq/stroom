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
import stroom.config.app.Config;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import io.dropwizard.core.cli.ConfiguredCommand;
import io.dropwizard.core.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This a base command class for all stroom commands to extend from. It sets up the minimal
 * bootstrap injector. Subclasses can optionally provide an additional guice module to create
 * a childInjector from.
 */
public abstract class AbstractStroomBaseCommand extends ConfiguredCommand<Config> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractStroomBaseCommand.class);

    private final Path configFile;
    private final String commandName;
    private Injector bootstrapInjector;
    private Injector childInjector;

    public AbstractStroomBaseCommand(final Path configFile,
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

        info(LOGGER, "Using application configuration file {}",
                configFile.toAbsolutePath().normalize());

        try {
            LOGGER.debug("Creating bootstrap injector");
            bootstrapInjector = BootstrapUtil.bootstrapApplication(
                    config, configFile);
            final Optional<Module> optChildInjectorModule = getChildInjectorModule();
            if (optChildInjectorModule.isPresent()) {
                final Module module = optChildInjectorModule.get();
                LOGGER.debug(() -> "Creating child injector using module " + module.getClass().getName());
                childInjector = bootstrapInjector.createChildInjector(module);
            } else {
                // No child module so just use the bootstrapInjector as our main injector
                LOGGER.debug(() -> "No child injector module provided, using bootstrapInjector");
                childInjector = bootstrapInjector;
            }
            info(LOGGER, "Running command " + commandName + " with arguments: " + argsToString(namespace));

            try {
                runCommand(bootstrap, namespace, config, childInjector);
            } catch (final Exception e) {
                final String msg = "Error running command "
                        + commandName
                        + ": " + e.getMessage()
                        + ". Check logs for more detail.";
                error(LOGGER, msg, e);
                System.exit(1);
            }

            info(LOGGER, "Command " + commandName + " completed successfully");
            System.exit(0);
        } catch (final Exception e) {
            final String msg = "Error initialising application";
            error(LOGGER, msg, e);
            System.exit(1);
        }
    }

    /**
     * Convenience method to get an instance from the injector.
     * See {@link Injector#getInstance(Class)}.
     */
    <T> T getInstance(final Class<T> type) {
        return childInjector.getInstance(type);
    }

    /**
     * Convenience method to get an instance from the injector.
     * See {@link Injector#getInstance(Key)}.
     */
    <T> T getInstance(final Key<T> key) {
        return childInjector.getInstance(key);
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
     * @param template The message template with '{}' placeholders
     * @param args     The args for the template
     */
    protected void info(final Logger logger, final String template, final Object... args) {
        final String msg = LogUtil.message(template, args);
        logger.info(msg);
        System.out.println(msg);
    }

    /**
     * Log to logger as INFO and stdout
     *
     * @param msg    The message
     * @param indent Indent used for stdout message
     */
    protected void indentedInfo(final Logger logger, final String msg, final String indent) {
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
     * @param template The message template with '{}' placeholders
     * @param args     The args for the template
     */
    protected void warn(final Logger logger, final String template, final Object... args) {
        final String msg = LogUtil.message(template, args);
        logger.warn(msg);
        System.out.println(msg);
    }

    /**
     * Log to logger as WARN and stdout
     *
     * @param msg    The message
     * @param indent Indent used for stdout message
     */
    protected void indentedWarn(final Logger logger, final String msg, final String indent) {
        logger.warn(msg);
        System.out.println(indent + msg);
    }

    /**
     * Log to logger as ERROR and stderr
     *
     * @param msg The message
     */
    protected void error(final Logger logger, final String msg) {
        logger.error(msg);
        System.err.println(msg);
    }

    /**
     * Log to logger as ERROR and stderr
     *
     * @param msg The message
     */
    protected void error(final Logger logger, final String msg, final Exception e) {
        logger.error(msg, e);
        System.err.println(msg);
    }

    /**
     * Log to logger as ERROR and stderr
     *
     * @param template The message template with '{}' placeholders
     * @param args     The args for the template
     */
    protected void error(final Logger logger, final String template, final Object... args) {
        final String msg = LogUtil.message(template, args);
        logger.error(msg);
        System.err.println(msg);
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
     * Prefixes argName with '--' to make it into a long form argument.
     */
    protected String asArg(final String argName) {
        return "--" + argName;
    }

    /**
     * Prefixes name with '-' to make it into a short form argument.
     */
    protected String asArg(final char argChar) {
        return "-" + argChar;
    }

    /**
     * Returns both a short form and long form argument, e.g. -p and --password
     */
    protected String[] asArg(final char shortForm, final String longForm) {
        return new String[]{asArg(shortForm), asArg(longForm)};
    }

    protected <T> List<T> extractArgs(final Namespace namespace,
                                      final String dest,
                                      final Function<List<String>, T> argsMapper) {
        final List<List<String>> values = namespace.get(dest);
        if (values != null) {
            return values.stream()
                    .map(argsMapper)
                    .toList();
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * Run the CLI command under the context of the Admin user.
     * Do not call {@link System#exit(int)} as the superclass will handle that.
     */
    protected abstract void runCommand(final Bootstrap<Config> bootstrap,
                                       final Namespace namespace,
                                       final Config config,
                                       final Injector injector) throws Exception;

    /**
     * @return The set of argument names used by this command
     */
    protected abstract Set<String> getArgumentNames();

    /**
     * @return An optional guice module to create a child injector with. The child injector
     * will be a child of the bootstrap module injector created by this class.
     */
    protected abstract Optional<Module> getChildInjectorModule();
}
