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
import stroom.persist.PersistService;
import stroom.task.TaskManager;
import stroom.util.io.FileUtil;

import java.io.IOException;

/**
 * Script to create some base data for testing.
 */
public final class SetupSampleData {
    public static void main(final String[] args) throws IOException {
        FileUtil.useDevTempDir();
        System.setProperty("stroom.connectionTesterClassName",
                "stroom.entity.util.StroomConnectionTesterOkOnException");

        final Injector injector = Guice.createInjector(new CoreTestModule());

        // Start persistance
        injector.getInstance(PersistService.class).start();

        // Start task manager
        injector.getInstance(TaskManager.class).startup();

        final CommonTestControl commonTestControl = injector.getInstance(CommonTestControl.class);

        commonTestControl.setup();

        final SetupSampleDataBean setupSampleDataBean = injector.getInstance(SetupSampleDataBean.class);
        setupSampleDataBean.run(true);

        // Stop task manager
        injector.getInstance(TaskManager.class).shutdown();

        // Stop persistance
        injector.getInstance(PersistService.class).stop();
    }
}
