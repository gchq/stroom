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
 *
 */

package stroom.streamstore;

import org.junit.Assert;
import org.junit.Test;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.streamstore.shared.ExpressionUtil;
import stroom.streamstore.shared.FindStreamAttributeMapCriteria;
import stroom.streamstore.shared.StreamEntity;
import stroom.streamstore.shared.StreamDataSource;
import stroom.streamstore.shared.StreamTypeEntity;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestScenarioCreator;
import stroom.util.date.DateUtil;
import stroom.util.test.FileSystemTestUtil;

import javax.inject.Inject;

public class TestStreamAttributeMapServiceImpl extends AbstractCoreIntegrationTest {
    @Inject
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Inject
    private StreamAttributeValueFlush streamAttributeValueFlush;
    @Inject
    private StreamAttributeMapService streamAttributeMapService;

    @Test
    public void testSimple() {
        final String feedName = FileSystemTestUtil.getUniqueTestString();

        final StreamEntity md = commonTestScenarioCreator.createSample2LineRawFile(feedName, StreamTypeEntity.RAW_EVENTS.getName());

        streamAttributeValueFlush.flush();

        FindStreamAttributeMapCriteria criteria = new FindStreamAttributeMapCriteria();
        criteria.obtainFindStreamCriteria().obtainSelectedIdSet().add(md.getId());
        criteria.obtainFindStreamCriteria().setExpression(ExpressionUtil.createSimpleExpression(StreamDataSource.CREATE_TIME, Condition.EQUALS, DateUtil.createNormalDateTimeString(md.getCreateMs())));

        Assert.assertEquals(1, streamAttributeMapService.find(criteria).size());

        criteria = new FindStreamAttributeMapCriteria();
        criteria.obtainFindStreamCriteria().obtainSelectedIdSet().add(md.getId());
        criteria.obtainFindStreamCriteria().setExpression(ExpressionUtil.createSimpleExpression(StreamDataSource.CREATE_TIME, Condition.EQUALS, DateUtil.createNormalDateTimeString(0L)));

        Assert.assertEquals(0, streamAttributeMapService.find(criteria).size());

        criteria = new FindStreamAttributeMapCriteria();
        criteria.obtainFindStreamCriteria().obtainSelectedIdSet().add(md.getId());
        criteria.obtainFindStreamCriteria().setExpression(ExpressionUtil.createSimpleExpression(StreamDataSource.FILE_SIZE, Condition.GREATER_THAN, "0"));

        Assert.assertEquals(1, streamAttributeMapService.find(criteria).size());

        criteria = new FindStreamAttributeMapCriteria();
        criteria.obtainFindStreamCriteria().obtainSelectedIdSet().add(md.getId());
        criteria.obtainFindStreamCriteria().setExpression(ExpressionUtil.createSimpleExpression(StreamDataSource.FILE_SIZE, Condition.BETWEEN, "0,1000000"));

        Assert.assertEquals(1, streamAttributeMapService.find(criteria).size());
    }
}
