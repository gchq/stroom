/*
 * Copyright 2016 Crown Copyright
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

package stroom.importexport.impl;

import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.security.shared.User;
import stroom.util.io.FileUtil;
import stroom.util.io.TempDirProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings("unused")
public class ContentPackImport {
    static final Path CONTENT_PACK_IMPORT_DIR = Paths.get("contentPackImport");
    static final String FAILED_DIR = "failed";
    static final String IMPORTED_DIR = "imported";
    private static final Logger LOGGER = LoggerFactory.getLogger(ContentPackImport.class);

    private final ImportExportService importExportService;
    private final ContentPackImportConfig config;
    private final TempDirProvider tempDirProvider;
    private final SecurityContext securityContext;

    @SuppressWarnings("unused")
    @Inject
    ContentPackImport(final ImportExportService importExportService,
                      final ContentPackImportConfig config,
                      final TempDirProvider tempDirProvider,
                      final SecurityContext securityContext) {
        this.importExportService = importExportService;
        this.config = config;
        this.tempDirProvider = tempDirProvider;
        this.securityContext = securityContext;
    }

    public void startup() {
        // TODO I'm not sure we want to scan all these dirs, see https://github.com/gchq/stroom/issues/1728
        final List<Path> contentPacksDirs = getContentPackBaseDirs();
        startup(contentPacksDirs);
    }

    // pkg private for testing
    void startup(final List<Path> contentPacksDirs) {
        final boolean isEnabled = config.isEnabled();

        if (isEnabled) {
            final UserIdentity admin = securityContext.createIdentity(User.ADMIN_USER_NAME);
            securityContext.asUser(admin, () ->
                    doImport(contentPacksDirs));
        } else {
            LOGGER.info("Content pack import currently disabled via property");
        }
    }

    private void doImport(final List<Path> contentPacksDirs) {

        final AtomicInteger successCounter = new AtomicInteger();
        final AtomicInteger failedCounter = new AtomicInteger();

        final String dirsStr = contentPacksDirs.stream()
                .map(dir -> "  " + FileUtil.getCanonicalPath(dir))
                .collect(Collectors.joining("\n"));

        LOGGER.info("ContentPackImport started, checking the following locations for content packs to import,\n{}", dirsStr);

        contentPacksDirs.forEach(contentPacksDir -> {
            if (!Files.isDirectory(contentPacksDir)) {
                LOGGER.info("Directory {} doesn't exist, ignoring.", FileUtil.getCanonicalPath(contentPacksDir));

            } else {
                LOGGER.info("Processing content packs in directory {}", FileUtil.getCanonicalPath(contentPacksDir));

                try (final DirectoryStream<Path> stream = Files.newDirectoryStream(contentPacksDir, "*.zip")) {
                    stream.forEach(file -> {
                        try {
                            boolean result = importContentPack(contentPacksDir, file);
                            if (result) {
                                successCounter.incrementAndGet();
                            } else {
                                failedCounter.incrementAndGet();
                            }
                        } catch (final RuntimeException e) {
                            LOGGER.error(e.getMessage(), e);
                        }
                    });
                } catch (final IOException e) {
                    LOGGER.error("Unable to read content pack files from {}", FileUtil.getCanonicalPath(contentPacksDir), e);
                }
            }
        });
        LOGGER.info("Content pack import counts - success: {}, failed: {}",
                successCounter.get(),
                failedCounter.get());

        LOGGER.info("ContentPackImport finished");
    }

    private boolean importContentPack(Path parentPath, Path contentPack) {
        LOGGER.info("Starting import of content pack {}", FileUtil.getCanonicalPath(contentPack));

        try {
            //It is possible to import a content pack (or packs) with missing dependencies
            //so the onus is on the person putting the file in the import directory to
            //ensure the packs they import are complete
            importExportService.performImportWithoutConfirmation(contentPack);
            moveFile(contentPack, contentPack.getParent().resolve(IMPORTED_DIR));

            LOGGER.info("Completed import of content pack {}", FileUtil.getCanonicalPath(contentPack));

        } catch (final RuntimeException e) {
            LOGGER.error("Error importing content pack {}", FileUtil.getCanonicalPath(contentPack), e);
            moveFile(contentPack, contentPack.getParent().resolve(FAILED_DIR));
            return false;
        }
        return true;
    }

    private void moveFile(Path contentPack, Path destDir) {
        Path destPath = destDir.resolve(contentPack.getFileName());
        try {
            //make sure the directory exists
            Files.createDirectories(destDir);
            Files.move(contentPack, destPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (final IOException e) {
            throw new UncheckedIOException(String.format("Error moving file from %s to %s",
                    FileUtil.getCanonicalPath(contentPack), FileUtil.getCanonicalPath(destPath)), e);
        }
    }

    private List<Path> getContentPackBaseDirs() {
        // Look in a number of places for content packs:
        //  Relative to the jar if we are running in a jar
        //  ~/.stroom
        // stroom.temp
        return Stream.of(
                getConfiguredContentPackImportDir(),
                getApplicationJarDir(),
                getDotStroomDir(),
                Optional.of(tempDirProvider.get().resolve(CONTENT_PACK_IMPORT_DIR))
        )
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    Optional<Path> getConfiguredContentPackImportDir() {
        return Optional.ofNullable(config.getImportDirectory())
                .map(Paths::get);
    }


    private Optional<Path> getDotStroomDir() {
        final String userHome = System.getProperty("user.home");
        if (userHome == null) {
            return Optional.empty();
        } else {
            final Path dotStroomDir = Paths.get(userHome)
                    .resolve(".stroom")
                    .resolve(CONTENT_PACK_IMPORT_DIR);
            return Optional.of(dotStroomDir);
        }
    }

    private Optional<Path> getApplicationJarDir() {
        try {
            //This isn't ideal when running in junit, as it will be the location of the junit class
            //however it won't find any zips in here so will carry on regardless
            final String codeSourceLocation = this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
            return Optional.of(Paths.get(codeSourceLocation)
                    .getParent()
                    .resolve(CONTENT_PACK_IMPORT_DIR));
        } catch (final RuntimeException e) {
            LOGGER.warn("Unable to determine application jar directory due to: {}", e.getMessage());
            return Optional.empty();
        }
    }

}
