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

import stroom.db.util.DbModule;
import stroom.importexport.impl.ContentPackImport;
import stroom.task.api.TaskManager;
import stroom.util.io.TempDirProvider;

import com.google.inject.Guice;
import com.google.inject.Injector;

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
    public static void main(final String[] args) {
        // We are running stroom so want to use a proper db
        final Injector injector = Guice.createInjector(new DbModule(), new CoreTestModule());

        // Start task manager
        injector.getInstance(TaskManager.class).startup();
        final TempDirProvider tempDirProvider = injector.getInstance(TempDirProvider.class);

        final CommonTestControl commonTestControl = injector.getInstance(CommonTestControl.class);

        // Clear the DB and remove all content and data.
        commonTestControl.clear();
        // Setup the DB ready to load content and data.
        commonTestControl.setup(null);

        // Load the sample data and content from the 'samples' dirs
        final SetupSampleDataBean setupSampleDataBean = injector.getInstance(SetupSampleDataBean.class);
        setupSampleDataBean.run(true);

        // Load the content packs that gradle should have downloaded
        final ContentPackImport contentPackImport = injector.getInstance(ContentPackImport.class);
        contentPackImport.startup();

        // Stop task manager
        injector.getInstance(TaskManager.class).shutdown();
    }
}
