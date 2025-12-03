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

package stroom.test.common;

import stroom.util.io.FileUtil;
import stroom.util.io.HomeDirProvider;
import stroom.util.io.PathCreator;
import stroom.util.io.SimplePathCreator;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * A {@link PathCreator} that is useful for tests. It creates a temporary dir
 * and a home/temp dir within it. {@link AutoCloseable} to make it easy to destroy afterwards.
 * Delegates most methods to {@link SimplePathCreator}.
 */
public class TemporaryPathCreator implements PathCreator, AutoCloseable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TemporaryPathCreator.class);

    private final SimplePathCreator delegate;
    private final Path baseDir;
    private final Path homeDir;
    private final Path tempDir;
    private final HomeDirProvider homeDirProvider;
    private final TempDirProvider tempDirProvider;

    /**
     * Creates a temporary directory and builds home and temp sub dirs in it.
     */
    public TemporaryPathCreator() {
        this(createTempDir());
    }

    /**
     * Builds home and temp sub dirs in tempBaseDir
     */
    public TemporaryPathCreator(final Path tempBaseDir) {
        try {
            baseDir = tempBaseDir;
            homeDir = baseDir.resolve("home");
            tempDir = baseDir.resolve("temp");
            LOGGER.debug(() -> LogUtil.message(
                    "Created home dir: {}, temp dir: {}",
                    homeDir.toAbsolutePath().normalize(),
                    tempDir.toAbsolutePath().normalize()));
            Files.createDirectories(homeDir);
            Files.createDirectories(tempDir);
        } catch (final IOException e) {
            throw new RuntimeException("Error creating temp dir with prefix 'stroom'", e);
        }
        homeDirProvider = () -> homeDir;
        tempDirProvider = () -> tempDir;
        delegate = new SimplePathCreator(homeDirProvider, tempDirProvider);
    }

    public Path getBaseTempDir() {
        return baseDir;
    }

    public void delete() {
        FileUtil.deleteDir(baseDir);
    }

    public Path getTempDir() {
        return tempDirProvider.get();
    }

    public TempDirProvider getTempDirProvider() {
        return tempDirProvider;
    }

    public Path getHomeDir() {
        return homeDirProvider.get();
    }

    public HomeDirProvider getHomeDirProvider() {
        return homeDirProvider;
    }

    @Override
    public String replaceTimeVars(final String path) {
        return delegate.replaceTimeVars(path);
    }

    @Override
    public String replaceTimeVars(final String path, final ZonedDateTime dateTime) {
        return delegate.replaceTimeVars(path, dateTime);
    }

    @Override
    public String replaceSystemProperties(final String path) {
        return delegate.replaceSystemProperties(path);
    }

    @Override
    public Path toAppPath(final String pathString) {
        return delegate.toAppPath(pathString);
    }

    @Override
    public String replaceUUIDVars(final String path) {
        return delegate.replaceUUIDVars(path);
    }

    @Override
    public String replaceFileName(final String path, final String fileName) {
        return delegate.replaceFileName(path, fileName);
    }

    @Override
    public String[] findVars(final String path) {
        return delegate.findVars(path);
    }

    @Override
    public boolean containsVars(final String path) {
        return delegate.containsVars(path);
    }

    @Override
    public String replace(final String path,
                          final String var,
                          final LongSupplier replacementSupplier,
                          final int pad) {
        return delegate.replace(path, var, replacementSupplier, pad);
    }

    @Override
    public String replace(final String str,
                          final String var,
                          final Supplier<String> replacementSupplier) {
        return delegate.replace(str, var, replacementSupplier);
    }

    @Override
    public String replaceAll(final String path) {
        return delegate.replaceAll(path);
    }

    @Override
    public String replaceContextVars(final String path) {
        return delegate.replaceContextVars(path);
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    @Override
    public void close() {
        delete();
    }

    private static Path createTempDir() {
        try {
            // Ensure each gradle jvm gets a unique path by using the working number
            final String gradleWorker = Objects.requireNonNullElse(
                    System.getProperty("org.gradle.test.worker"),
                    "0");
            final String prefix = "stroom_" + gradleWorker + "_";
            return Files.createTempDirectory(prefix);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }
}
