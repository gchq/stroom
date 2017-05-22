/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.streamstore.server;

import org.junit.Assert;
import org.junit.Test;
import stroom.AbstractCoreIntegrationTest;
import stroom.CommonTestScenarioCreator;
import stroom.entity.shared.DocRefUtil;
import stroom.feed.shared.Feed;
import stroom.query.api.v1.ExpressionTerm.Condition;
import stroom.streamstore.shared.FindStreamAttributeKeyCriteria;
import stroom.streamstore.shared.FindStreamAttributeMapCriteria;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamAttributeCondition;
import stroom.streamstore.shared.StreamAttributeConstants;
import stroom.streamstore.shared.StreamAttributeKey;
import stroom.streamstore.shared.StreamAttributeKeyService;
import stroom.streamstore.shared.StreamAttributeMapService;
import stroom.streamstore.shared.StreamType;
import stroom.util.date.DateUtil;

import javax.annotation.Resource;
import java.io.IOException;

public class TestStreamAttributeMapServiceImpl extends AbstractCoreIntegrationTest {
    @Resource
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Resource
    private StreamAttributeValueFlush streamAttributeValueFlush;
    @Resource
    private StreamAttributeMapService streamAttributeMapService;
    @Resource
    private StreamAttributeKeyService streamAttributeKeyService;

    @Test
    public void testSimple() throws IOException {
        final Feed eventFeed = commonTestScenarioCreator.createSimpleFeed();

        final StreamAttributeKey createTimeAttributeKey = streamAttributeKeyService
                .find(new FindStreamAttributeKeyCriteria(StreamAttributeConstants.CREATE_TIME)).getFirst();
        final StreamAttributeKey fileSizeAttributeKey = streamAttributeKeyService
                .find(new FindStreamAttributeKeyCriteria(StreamAttributeConstants.FILE_SIZE)).getFirst();

        final Stream md = commonTestScenarioCreator.createSample2LineRawFile(eventFeed, StreamType.RAW_EVENTS);

        streamAttributeValueFlush.flush();

        FindStreamAttributeMapCriteria criteria = new FindStreamAttributeMapCriteria();
        criteria.obtainFindStreamCriteria().obtainStreamIdSet().add(md);
        criteria.obtainFindStreamCriteria().obtainAttributeConditionList()
                .add(new StreamAttributeCondition(DocRefUtil.create(createTimeAttributeKey), Condition.EQUALS,
                        DateUtil.createNormalDateTimeString(md.getCreateMs())));

        Assert.assertEquals(1, streamAttributeMapService.find(criteria).size());

        criteria = new FindStreamAttributeMapCriteria();
        criteria.obtainFindStreamCriteria().obtainStreamIdSet().add(md);
        criteria.obtainFindStreamCriteria().obtainAttributeConditionList()
                .add(new StreamAttributeCondition(DocRefUtil.create(createTimeAttributeKey), Condition.EQUALS,
                        DateUtil.createNormalDateTimeString(0L)));

        Assert.assertEquals(0, streamAttributeMapService.find(criteria).size());

        criteria = new FindStreamAttributeMapCriteria();
        criteria.obtainFindStreamCriteria().obtainStreamIdSet().add(md);
        criteria.obtainFindStreamCriteria().obtainAttributeConditionList().add(new StreamAttributeCondition(
                DocRefUtil.create(fileSizeAttributeKey), Condition.GREATER_THAN, "0"));

        Assert.assertEquals(1, streamAttributeMapService.find(criteria).size());

        criteria = new FindStreamAttributeMapCriteria();
        criteria.obtainFindStreamCriteria().obtainStreamIdSet().add(md);
        criteria.obtainFindStreamCriteria().obtainAttributeConditionList().add(new StreamAttributeCondition(
                DocRefUtil.create(fileSizeAttributeKey), Condition.BETWEEN, "0,1000000"));

        Assert.assertEquals(1, streamAttributeMapService.find(criteria).size());
    }
}
