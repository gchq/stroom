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

package stroom.credentials.impl;

import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/// Checks that the path of a 'Key Store File' credential is inside one of the directories
/// allowed by [CredentialsConfig#getAllowedKeyStoreFileDirs()].
///
/// Without this, anyone able to create credentials could point Stroom at any file the Stroom
/// process can read, e.g. Stroom's own server key store, and use it as a client key store.
public class KeyStoreFilePathChecker {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(KeyStoreFilePathChecker.class);

    static final String CONFIG_PROPERTY = "stroom.credentials.allowedKeyStoreFileDirs";

    /// The size of the credential.key_store_path column.
    static final int MAX_PATH_LENGTH = 1000;

    private final PathCreator pathCreator;
    private final Provider<CredentialsConfig> credentialsConfigProvider;

    /// @param pathCreator               Resolves `${stroom.home}` etc. in the allowed directories.
    /// @param credentialsConfigProvider Provides the allowed directories.
    @Inject
    public KeyStoreFilePathChecker(final PathCreator pathCreator,
                                   final Provider<CredentialsConfig> credentialsConfigProvider) {
        this.pathCreator = pathCreator;
        this.credentialsConfigProvider = credentialsConfigProvider;
    }

    /// Checks that the given key store file path is allowed and that it is an existing file.
    ///
    /// The path is checked before the file system is touched, so that the result gives nothing
    /// away about files outside the allowed directories. It is then checked again after
    /// following any symbolic links, so a link can't be used to get out of them.
    ///
    /// @param keyStorePath The absolute path of the key store file.
    /// @return The real path of the file, with any links followed. This is what must be opened,
    /// so that a link changed after this check can't point the caller at a file outside the
    /// allowed directories. As this is called on each use, a link that a key manager such as
    /// cert-manager moves to a new file is still followed.
    /// @throws IllegalArgumentException If the path is not allowed or is not an existing file.
    public Path check(final String keyStorePath) {
        final List<Path> allowedDirs = getAllowedDirs();
        if (allowedDirs.isEmpty()) {
            throw new IllegalArgumentException("Key Store File credentials are not enabled. "
                                               + "An administrator must set " + CONFIG_PROPERTY
                                               + " to the directories that key store files may be in.");
        }
        if (NullSafe.isBlankString(keyStorePath)) {
            throw new IllegalArgumentException("The path of the key store file must be given");
        }
        if (keyStorePath.length() > MAX_PATH_LENGTH) {
            throw new IllegalArgumentException("The path of the key store file must be no more than "
                                               + MAX_PATH_LENGTH + " characters long");
        }

        final Path path;
        try {
            path = Path.of(keyStorePath);
        } catch (final InvalidPathException e) {
            throw new IllegalArgumentException("'" + keyStorePath + "' is not a valid path");
        }
        if (!path.isAbsolute()) {
            throw new IllegalArgumentException("The path of the key store file must be absolute");
        }
        if (!isInAllowedDir(path.normalize(), allowedDirs)) {
            throw notAllowed(keyStorePath);
        }

        final Path realPath;
        try {
            realPath = path.toRealPath();
        } catch (final NoSuchFileException e) {
            throw new IllegalArgumentException("Key store file '" + keyStorePath + "' does not exist");
        } catch (final IOException e) {
            throw new IllegalArgumentException("Unable to read key store file '" + keyStorePath + "'", e);
        }
        if (!isInAllowedDir(realPath, getRealAllowedDirs(allowedDirs))) {
            throw notAllowed(keyStorePath);
        }
        // Not a pipe or device, which could block whoever reads it
        if (!Files.isRegularFile(realPath)) {
            throw new IllegalArgumentException("Key store file '" + keyStorePath + "' is not a file");
        }
        return realPath;
    }

    private List<Path> getAllowedDirs() {
        final List<String> dirs = NullSafe.getOrElse(
                credentialsConfigProvider.get(),
                CredentialsConfig::getAllowedKeyStoreFileDirs,
                List.of());
        // Blank dirs have already been removed by CredentialsConfig
        return NullSafe.stream(dirs)
                .map(pathCreator::toAppPath)
                .map(dir -> dir.toAbsolutePath().normalize())
                .toList();
    }

    private static List<Path> getRealAllowedDirs(final List<Path> allowedDirs) {
        final List<Path> realDirs = new ArrayList<>();
        for (final Path dir : allowedDirs) {
            try {
                realDirs.add(dir.toRealPath());
            } catch (final IOException e) {
                // A missing allowed dir can't contain the file, so just skip it
                LOGGER.debug("Allowed key store file dir '{}' can't be resolved: {}", dir, e.getMessage());
            }
        }
        return realDirs;
    }

    private static boolean isInAllowedDir(final Path path, final List<Path> allowedDirs) {
        // Path.startsWith compares whole path elements, so /certs-other is not inside /certs
        return allowedDirs.stream().anyMatch(dir -> !path.equals(dir) && path.startsWith(dir));
    }

    private static IllegalArgumentException notAllowed(final String keyStorePath) {
        return new IllegalArgumentException("Key store file '" + keyStorePath
                                            + "' is not in a directory allowed by " + CONFIG_PROPERTY);
    }
}
