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

package stroom.streamtask.server;

import org.junit.Assert;
import org.junit.Test;
import stroom.feed.shared.Feed;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.StreamType;
import stroom.streamtask.shared.StreamTask;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestControl;
import stroom.test.CommonTestScenarioCreator;

import javax.annotation.Resource;

public class TestStreamTaskCreatorTransactionHelper extends AbstractCoreIntegrationTest {
    @Resource
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Resource
    private CommonTestControl commonTestControl;
    @Resource
    private StreamTaskCreatorTransactionHelper streamTaskCreatorTransactionHelper;
    @Resource
    private StreamTaskDeleteExecutor streamTaskDeleteExecutor;

    @Test
    public void testBasic() {
        final Feed feed1 = commonTestScenarioCreator.createSimpleFeed();

        commonTestScenarioCreator.createSample2LineRawFile(feed1, StreamType.RAW_EVENTS);
        Assert.assertEquals(0, commonTestControl.countEntity(StreamTask.class));

        FindStreamCriteria findStreamCriteria = new FindStreamCriteria();
        Assert.assertEquals(1,
                streamTaskCreatorTransactionHelper.runSelectStreamQuery(null, findStreamCriteria, 0, 100).size());

        findStreamCriteria = new FindStreamCriteria();
        findStreamCriteria.obtainFeeds().obtainInclude().add(feed1);
        Assert.assertEquals(1,
                streamTaskCreatorTransactionHelper.runSelectStreamQuery(null, findStreamCriteria, 0, 100).size());

        findStreamCriteria = new FindStreamCriteria();
        findStreamCriteria.obtainFeeds().obtainInclude().add(feed1.getId() + 1);
        Assert.assertEquals(0,
                streamTaskCreatorTransactionHelper.runSelectStreamQuery(null, findStreamCriteria, 0, 100).size());

        findStreamCriteria = new FindStreamCriteria();
        findStreamCriteria.obtainPipelineIdSet().add(1L);
        Assert.assertEquals(0,
                streamTaskCreatorTransactionHelper.runSelectStreamQuery(null, findStreamCriteria, 0, 100).size());
    }

    @Test
    public void testDeleteQuery() {
        streamTaskDeleteExecutor.delete(0);
    }
}
