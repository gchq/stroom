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

package stroom.statistics;

import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.streamstore.tools.StoreCreationTool;
import stroom.streamtask.StreamTaskCreator;
import stroom.task.TaskManager;
import stroom.test.CommonTestControl;
import stroom.test.CommonTranslationTest;

import javax.inject.Inject;

@Ignore("TODO: uncomment and update tests or delete")
public class TestPipelineStatistics {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestPipelineStatistics.class);

    @Inject
    private CommonTranslationTest commonPipelineTest;
    @Inject
    private StoreCreationTool storeCreationTool;
    @Inject
    private TaskManager taskManager;
    @Inject
    private CommonTestControl commonTestControl;
    @Inject
    private StreamTaskCreator streamTaskCreator;

    // FIXME : Sort out pipeline statistics.
    @Test
    public void test() {
    }
}
