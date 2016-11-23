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

import org.springframework.stereotype.Component;
import stroom.entity.shared.EntityActionConfirmation;
import stroom.node.server.StroomPropertyService;
import stroom.util.logging.StroomLogger;
import stroom.util.spring.StroomStartup;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused")
@Component
public class ContentPackImport {

    protected static final StroomLogger LOGGER = StroomLogger.getLogger(ContentPackImport.class);

    public static final String AUTO_IMPORT_ENABLED_PROP_KEY = "stroom.contentPackImportEnabled";
    public static final String FAILED_DIR = "failed";
    public static final String IMPORTED_DIR = "imported";

    private ImportExportService importExportService;
    private StroomPropertyService stroomPropertyService;

    @SuppressWarnings("unused")
    @Inject
    public ContentPackImport(ImportExportService importExportService, StroomPropertyService stroomPropertyService) {
        this.importExportService = importExportService;
        this.stroomPropertyService = stroomPropertyService;
    }

    @StroomStartup
    public void startup(){

       final boolean isEnabled = stroomPropertyService.getBooleanProperty(AUTO_IMPORT_ENABLED_PROP_KEY, true);

       if (isEnabled) {
           doImport();
       }
    }

    private void doImport() {
        LOGGER.info("ContentPackImport started");

        final Path contentPacksDir = Paths.get("/tmp/autoImportTest/");


        try {
            if (!Files.isDirectory(contentPacksDir)){
                throw new RuntimeException(String.format("Content packs directory %s doesn't exist", contentPacksDir.toAbsolutePath()));
            }

            Path importedDir = Files.createDirectories(Paths.get(contentPacksDir.toString(), IMPORTED_DIR));
            Path failedDir = Files.createDirectories(Paths.get(contentPacksDir.toString(), FAILED_DIR));

            Files.list(contentPacksDir)
                    .filter(path -> path.toString().endsWith("zip"))
                    .sorted()
                    .forEachOrdered((contentPackPath) -> {
                        boolean result = importContentPack(contentPacksDir, contentPackPath);
                        Path destDir = result ? importedDir : failedDir;
                        //TODO finish the move off
                        //Files.move(contentPackPath, importedDir.resolve() )
                    });

        } catch (IOException e) {
            LOGGER.error("Unable to read content pack files from %s", contentPacksDir.toAbsolutePath(), e );
        }

        LOGGER.info("ContentPackImport finished");
    }

    private boolean importContentPack(Path parentPath, Path contentPack){
        LOGGER.info("Starting import of content pack %s", contentPack.toAbsolutePath());

//        List<EntityActionConfirmation> confirmations = new ArrayList<>();
        try {
//            importExportService.performImportWithConfirmation(contentPack.toFile(), confirmations);
            importExportService.performImportWithoutConfirmation(contentPack.toFile());

//            LOGGER.info("Results of import:");
//            confirmations.stream()
//                    .forEach(entityAction -> {
//                        LOGGER.info("Path: %s, type: %s, entityAction: %s, action: %s, ",
//                                entityAction.getPath(),
//                                entityAction.getEntityType(),
//                                entityAction.getEntityAction(),
//                                entityAction.isAction());
//
//                        entityAction.getMessageList().forEach(msg -> {
//                            LOGGER.info(msg);
//                        });
//                    });
            LOGGER.info("Completed import of content pack %s", contentPack.toAbsolutePath());

        } catch (Exception e) {
            LOGGER.error("Error importing content pack %s", contentPack.toAbsolutePath(), e);
            return false;
        }
        return true;

    }



}
