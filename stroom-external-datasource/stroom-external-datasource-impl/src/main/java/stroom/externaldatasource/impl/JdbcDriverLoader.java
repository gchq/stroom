/*
 * Copyright 2026 Crown Copyright
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

package stroom.externaldatasource.impl;

import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.logging.Logger;
import java.util.stream.Stream;

/// Loads the JDBC drivers in the administrator's driver directory, once, so that connection pools
/// can find them by URL.
///
/// [DriverManager] only hands out drivers loaded by the caller's class loader, so each driver is
/// registered through a [DriverShim] that Stroom's class loader owns. The MySQL and PostgreSQL
/// drivers Stroom ships are on the class path and need no loading.
@Singleton
public class JdbcDriverLoader {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(JdbcDriverLoader.class);

    private final Provider<ExternalDataSourceConfig> configProvider;
    private final PathCreator pathCreator;

    private volatile boolean loaded;

    /// Creates the instance. Guice supplies every argument.
    @Inject
    JdbcDriverLoader(final Provider<ExternalDataSourceConfig> configProvider,
                     final PathCreator pathCreator) {
        this.configProvider = configProvider;
        this.pathCreator = pathCreator;
    }

    /// Loads the drivers in the driver directory the first time it is called, and does nothing after.
    ///
    /// A jar that cannot be read, or a driver that cannot be registered, is logged and skipped so
    /// that one bad jar does not stop every External Data Source.
    public void ensureLoaded() {
        if (!loaded) {
            synchronized (this) {
                if (!loaded) {
                    loadDrivers();
                    loaded = true;
                }
            }
        }
    }

    private void loadDrivers() {
        final String driverDirectory = configProvider.get().getDriverDirectory();
        if (NullSafe.isBlankString(driverDirectory)) {
            LOGGER.debug("No JDBC driver directory configured");
            return;
        }

        final Path directory = pathCreator.toAppPath(driverDirectory);
        if (!Files.isDirectory(directory)) {
            LOGGER.warn("JDBC driver directory '{}' does not exist, so no extra drivers were loaded", directory);
            return;
        }

        final List<URL> jarUrls = findJarUrls(directory);
        if (jarUrls.isEmpty()) {
            LOGGER.info("No JDBC driver jars found in '{}'", directory);
            return;
        }

        // Never closed: the drivers it loads stay registered for the life of the JVM.
        final URLClassLoader classLoader = new URLClassLoader(
                jarUrls.toArray(new URL[0]),
                JdbcDriverLoader.class.getClassLoader());
        for (final Driver driver : ServiceLoader.load(Driver.class, classLoader)) {
            // The service loader also finds drivers already on the class path, which need no shim.
            if (driver.getClass().getClassLoader() == classLoader) {
                register(driver);
            }
        }
    }

    private static List<URL> findJarUrls(final Path directory) {
        final List<URL> jarUrls = new ArrayList<>();
        try (final Stream<Path> paths = Files.list(directory)) {
            paths.filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .sorted()
                    .forEach(path -> {
                        try {
                            jarUrls.add(path.toUri().toURL());
                        } catch (final MalformedURLException e) {
                            LOGGER.error(() -> LogUtil.message(
                                    "Unable to read JDBC driver jar '{}': {}", path, e.getMessage()));
                        }
                    });
        } catch (final IOException e) {
            LOGGER.error(() -> LogUtil.message(
                    "Unable to list JDBC driver directory '{}': {}", directory, e.getMessage()), e);
        }
        return jarUrls;
    }

    private static void register(final Driver driver) {
        try {
            DriverManager.registerDriver(new DriverShim(driver));
            LOGGER.info(() -> LogUtil.message("Loaded JDBC driver {} {}.{}",
                    driver.getClass().getName(),
                    driver.getMajorVersion(),
                    driver.getMinorVersion()));
        } catch (final SQLException | RuntimeException e) {
            LOGGER.error(() -> LogUtil.message("Unable to register JDBC driver {}: {}",
                    driver.getClass().getName(), e.getMessage()), e);
        }
    }

    /// Wraps a driver from another class loader so that [DriverManager] will hand it out.
    private record DriverShim(Driver driver) implements Driver {

        @Override
        public Connection connect(final String url, final Properties info) throws SQLException {
            return driver.connect(url, info);
        }

        @Override
        public boolean acceptsURL(final String url) throws SQLException {
            return driver.acceptsURL(url);
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(final String url, final Properties info) throws SQLException {
            return driver.getPropertyInfo(url, info);
        }

        @Override
        public int getMajorVersion() {
            return driver.getMajorVersion();
        }

        @Override
        public int getMinorVersion() {
            return driver.getMinorVersion();
        }

        @Override
        public boolean jdbcCompliant() {
            return driver.jdbcCompliant();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return driver.getParentLogger();
        }
    }
}
