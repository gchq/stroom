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

package stroom.statistics.server;

import stroom.CommonTestControl;
import stroom.CommonTranslationTest;
import stroom.streamstore.server.tools.StoreCreationTool;
import stroom.streamtask.server.StreamTaskCreator;
import stroom.task.server.TaskManager;
import stroom.util.logging.StroomLogger;
import org.junit.Ignore;
import org.junit.Test;

import javax.annotation.Resource;

@Ignore("TODO: uncomment and update tests or delete")
public class TestPipelineStatistics {
    public static final StroomLogger LOGGER = StroomLogger.getLogger(TestPipelineStatistics.class);

    @Resource
    private CommonTranslationTest commonPipelineTest;
    @Resource
    private StoreCreationTool storeCreationTool;
    @Resource
    private TaskManager taskManager;
    @Resource
    private CommonTestControl commonTestControl;
    @Resource
    private StreamTaskCreator streamTaskCreator;

    // FIXME : Sort out pipeline statistics.
    @Test
    public void test() {
    }
}
