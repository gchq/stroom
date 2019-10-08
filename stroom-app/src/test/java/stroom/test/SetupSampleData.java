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

import com.google.inject.Guice;
import com.google.inject.Injector;
import stroom.task.api.TaskManager;

/**
 * A main() method for pre-loading the stroom database with content and data for manual testing
 * of the application.
 *
 * ***********************************************************************************************
 * IMPORTANT - This should only be run from the gradle task setupSampleData, NOT from the IDE.
 * The gradle task does the additional step of downloading content packs and placing them in the
 * appropriate directory for auto-import into stroom on boot. If you run it from the IDE you will
 * not get the content packs.
 * ***********************************************************************************************
 *
 * The aim of setupSampleData is to load test content and data that is located in
 * stroom-core/src/test/resources/samples. The content in this folder should NOT duplicate any content
 * that is available in content packs.
 *
 * The content packs that get downlaoded (for auto import) are defined in the root build.gradle file.
 */
public final class SetupSampleData {

    public static void main(final String[] args) {

        // We are running stroom so want to use a proper db
        final Injector injector = Guice.createInjector(new CoreTestModule(false));

        // Start task manager
        injector.getInstance(TaskManager.class).startup();

        final CommonTestControl commonTestControl = injector.getInstance(CommonTestControl.class);

        commonTestControl.setup();

        final SetupSampleDataBean setupSampleDataBean = injector.getInstance(SetupSampleDataBean.class);
        setupSampleDataBean.run(true);

        // Stop task manager
        injector.getInstance(TaskManager.class).shutdown();
    }
}
