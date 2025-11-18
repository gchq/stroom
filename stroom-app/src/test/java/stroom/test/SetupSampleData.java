/*
 * Copyright 2017 Crown Copyright
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

package stroom.test;

import stroom.config.app.Config;
import stroom.config.app.StroomYamlUtil;
import stroom.importexport.impl.ContentPackImportConfig;
import stroom.task.api.TaskManager;
import stroom.test.common.util.test.ContentPackZipDownloader;
import stroom.util.io.PathCreator;
import stroom.util.yaml.YamlUtil;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import org.apache.hc.client5.http.classic.HttpClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A main() method for pre-loading the stroom database with content and data for manual testing
 * of the application.
 * <p>
 * ***********************************************************************************************
 * IMPORTANT - This should only be run from the gradle task setupSampleData, NOT from the IDE.
 * The gradle task does the additional step of downloading content packs and placing them in the
 * appropriate directory for auto-import into stroom on boot. If you run it from the IDE you will
 * not get the content packs.
 * ***********************************************************************************************
 * <p>
 * The aim of setupSampleData is to load test content and data that is located in
 * stroom-core/src/test/resources/samples. The content in this folder should NOT duplicate any content
 * that is available in content packs.
 * <p>
 * The content packs that get downloaded (for auto import) are defined in the root build.gradle file.
 */
public final class SetupSampleData {

    @Inject
    private TaskManager taskManager;
    @Inject
    private CommonTestControl commonTestControl;
    @Inject
    private ContentStoreTestSetup devSetup;
    @Inject
    private SetupSampleDataProcess setupSampleDataBean;

    public static void main(final String[] args) {
        if (args.length != 1) {
            throw new RuntimeException("Expected 1 argument that is the location of the config.");
        }
        final Path configFile = YamlUtil.getYamlFileFromArgs(args);
        final Config config;
        try {
            config = StroomYamlUtil.readConfig(configFile);
        } catch (final IOException e) {
            throw new RuntimeException("Unable to read yaml config");
        }

        new SetupSampleData().run(configFile, config);
    }

    private void run(final Path configFile, final Config config) {
        // We are running stroom so want to use a proper db
        final Injector injector = Guice.createInjector(new SetupSampleDataModule(config, configFile));
        injector.injectMembers(this);

        // Start task manager
        taskManager.startup();

        // Clear the DB and remove all content and data.
        commonTestControl.clear();
        // Setup the DB ready to load content and data.
        commonTestControl.setup(null);

        // Pull in content packs from the content store
        devSetup.installSampleDataPacks();

        // Load the sample data and content from the 'samples' dirs
        setupSampleDataBean.run(true);

        // Stop task manager
        taskManager.shutdown();
    }

    private static void downloadContent(final Path contentPacksDefinition,
                                        final PathCreator pathCreator,
                                        final ContentPackImportConfig contentPackImportConfig,
                                        final HttpClient httpClient) {
        try {
            final Path downloadDir =
                    pathCreator.toAppPath(ContentPackZipDownloader.CONTENT_PACK_DOWNLOAD_DIR);
            final Path importDir =
                    pathCreator.toAppPath(contentPackImportConfig.getImportDirectory());

            Files.createDirectories(downloadDir);
            Files.createDirectories(importDir);

            ContentPackZipDownloader.downloadZipPacks(
                    contentPacksDefinition,
                    downloadDir,
                    importDir,
                    httpClient);
        } catch (final IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
