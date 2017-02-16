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

package stroom.importexport.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.node.server.StroomPropertyService;
import stroom.node.shared.GlobalPropertyService;
import stroom.util.config.StroomProperties;
import stroom.util.spring.StroomStartup;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("unused")
@Component
public class ContentPackImport {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContentPackImport.class);

    static final String AUTO_IMPORT_ENABLED_PROP_KEY = "stroom.contentPackImportEnabled";
    static final String CONTENT_PACK_IMPORT_DIR = "contentPackImport";
    static final String FAILED_DIR = "failed";
    static final String IMPORTED_DIR = "imported";

    private ImportExportService importExportService;
    private StroomPropertyService stroomPropertyService;
    private GlobalPropertyService globalPropertyService;

    @SuppressWarnings("unused")
    @Inject
    ContentPackImport(ImportExportService importExportService, StroomPropertyService stroomPropertyService, GlobalPropertyService globalPropertyService) {
        this.importExportService = importExportService;
        this.stroomPropertyService = stroomPropertyService;
        this.globalPropertyService = globalPropertyService;
    }

    //Startup with very low priority to ensure it starts after everything else
    //in particular
    @StroomStartup(priority = -1000)
    public void startup() {
        final boolean isEnabled = stroomPropertyService.getBooleanProperty(AUTO_IMPORT_ENABLED_PROP_KEY, true);

        if (isEnabled) {
            doImport();
        } else {
            LOGGER.info("Content pack import currently disabled via property: %s", AUTO_IMPORT_ENABLED_PROP_KEY);
        }
    }

    private void doImport() {
        LOGGER.info("ContentPackImport started");

        final Optional<Path> optContentPacksDir = getContentPackBaseDir();

        if (optContentPacksDir.isPresent()) {
            final Path contentPacksDir = optContentPacksDir.get();
            try {
                if (!Files.isDirectory(contentPacksDir)) {
                    LOGGER.error("Content packs directory %s doesn't exist", contentPacksDir.toAbsolutePath());
                    return;
                }

                Path importedDir = Files.createDirectories(contentPacksDir.resolve(IMPORTED_DIR));
                Path failedDir = Files.createDirectories(contentPacksDir.resolve(FAILED_DIR));

                AtomicInteger successCounter = new AtomicInteger();
                AtomicInteger failedCounter = new AtomicInteger();

                Files.list(contentPacksDir)
                        .filter(path -> path.toString().endsWith("zip"))
                        .sorted()
                        .forEachOrdered((contentPackPath) -> {
                            boolean result = importContentPack(contentPacksDir, contentPackPath);
                            if (result) {
                                successCounter.incrementAndGet();
                            } else {
                                failedCounter.incrementAndGet();
                            }
                            Path destDir = result ? importedDir : failedDir;
                            Path filename = contentPackPath.getFileName();
                            Path destPath = destDir.resolve(filename);
                            try {
                                Files.move(contentPackPath, destPath, StandardCopyOption.REPLACE_EXISTING);
                            } catch (IOException e) {
                                throw new RuntimeException(String.format("Error moving file from %s to %s",
                                        contentPackPath.toAbsolutePath(), destPath.toAbsolutePath()));
                            }
                        });

                LOGGER.info("Content pack import counts - success: %s, failed: %s",
                        successCounter.get(),
                        failedCounter.get());

            } catch (IOException e) {
                LOGGER.error("Unable to read content pack files from %s", contentPacksDir.toAbsolutePath(), e);
            }

            LOGGER.info("ContentPackImport finished");
        } else {
            LOGGER.error("Unable to proceed with import, no base directory found");
        }
    }

    private boolean importContentPack(Path parentPath, Path contentPack) {
        LOGGER.info("Starting import of content pack %s", contentPack.toAbsolutePath());

        try {
            //It is possible to import a content pack (or packs) with missing dependencies
            //so the onus is on the person putting the file in the import directory to
            //ensure the packs they import are complete
            importExportService.performImportWithoutConfirmation(contentPack.toFile());

            LOGGER.info("Completed import of content pack %s", contentPack.toAbsolutePath());

        } catch (Exception e) {
            LOGGER.error("Error importing content pack %s", contentPack.toAbsolutePath(), e);
            return false;
        }
        return true;
    }

    private Optional<Path> getContentPackBaseDir() {
        Optional<Path> contentPackDir;

        String catalinaBase = System.getProperty("catalina.home");
        String userHome = System.getProperty("user.home");

        if (catalinaBase != null) {
            //running inside tomcat so use a subdir in there
            contentPackDir = Optional.of(Paths.get(catalinaBase, "..", CONTENT_PACK_IMPORT_DIR));
        } else if (userHome != null) {
            //not in tomcat so use the personal user conf dir as a base
            contentPackDir = Optional.of(Paths.get(userHome, StroomProperties.USER_CONF_DIR, CONTENT_PACK_IMPORT_DIR));
        } else {
            contentPackDir = Optional.empty();
        }
        return contentPackDir;
    }


}
