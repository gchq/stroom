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
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.Period;
import stroom.entity.shared.Range;
import stroom.feed.shared.Feed;
import stroom.feed.shared.FeedService;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.StreamType;
import stroom.streamtask.shared.FindStreamProcessorCriteria;
import stroom.streamtask.shared.FindStreamProcessorFilterCriteria;
import stroom.streamtask.shared.StreamProcessor;
import stroom.streamtask.shared.StreamProcessorFilter;
import stroom.streamtask.shared.StreamProcessorFilterService;
import stroom.streamtask.shared.StreamProcessorService;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestScenarioCreator;

import javax.annotation.Resource;
import java.util.List;

public class TestStreamProcessorFilterService extends AbstractCoreIntegrationTest {
    @Resource
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Resource
    private FeedService feedService;
    @Resource
    private StreamProcessorService streamProcessorService;
    @Resource
    private StreamProcessorFilterService streamProcessorFilterService;

    @Override
    protected void onBefore() {
        super.onBefore();
        deleteAll();
    }

    @Override
    protected void onAfter() {
        super.onAfter();
        deleteAll();
    }

    private void deleteAll() {
        final List<StreamProcessorFilter> filters = streamProcessorFilterService
                .find(new FindStreamProcessorFilterCriteria());
        for (final StreamProcessorFilter filter : filters) {
            streamProcessorFilterService.delete(filter);
        }

        final List<StreamProcessor> streamProcessors = streamProcessorService.find(new FindStreamProcessorCriteria());
        for (final StreamProcessor processor : streamProcessors) {
            streamProcessorService.delete(processor);
        }
    }

    @Test
    public void testBasic() {
        StreamProcessor streamProcessor = new StreamProcessor();
        streamProcessor = streamProcessorService.save(streamProcessor);

        Assert.assertEquals(1, streamProcessorService.find(new FindStreamProcessorCriteria()).size());

        final FindStreamProcessorFilterCriteria findStreamProcessorFilterCriteria = new FindStreamProcessorFilterCriteria();

        streamProcessorFilterService.addFindStreamCriteria(streamProcessor, 1, new FindStreamCriteria());
        Assert.assertEquals(1, streamProcessorFilterService.find(findStreamProcessorFilterCriteria).size());

        streamProcessorFilterService.addFindStreamCriteria(streamProcessor, 10, new FindStreamCriteria());
        Assert.assertEquals(2, streamProcessorFilterService.find(findStreamProcessorFilterCriteria).size());

        findStreamProcessorFilterCriteria.setPriorityRange(new Range<Integer>(10, null));
        Assert.assertEquals(1, streamProcessorFilterService.find(findStreamProcessorFilterCriteria).size());

        findStreamProcessorFilterCriteria.setPriorityRange(new Range<Integer>(1, null));
        Assert.assertEquals(2, streamProcessorFilterService.find(findStreamProcessorFilterCriteria).size());
    }

    @Test
    public void testFeedIncludeExclude() {
        StreamProcessor streamProcessor = new StreamProcessor();
        streamProcessor = streamProcessorService.save(streamProcessor);
        Assert.assertEquals(1, streamProcessorService.find(new FindStreamProcessorCriteria()).size());

        final Feed feed1 = commonTestScenarioCreator.createSimpleFeed("Feed1");
        final Feed feed2 = commonTestScenarioCreator.createSimpleFeed("Feed2");

        final FindStreamCriteria findStreamCriteria = new FindStreamCriteria();
        findStreamCriteria.obtainFeeds().obtainInclude().add(feed1);
        findStreamCriteria.obtainFeeds().obtainInclude().add(feed2);
        findStreamCriteria.obtainStreamTypeIdSet().add(StreamType.RAW_EVENTS);
        findStreamCriteria.obtainStreamTypeIdSet().add(StreamType.RAW_REFERENCE);

        final FindStreamProcessorFilterCriteria findStreamProcessorFilterCriteria = new FindStreamProcessorFilterCriteria();

        streamProcessorFilterService.addFindStreamCriteria(streamProcessor, 1, findStreamCriteria);
        final BaseResultList<StreamProcessorFilter> filters = streamProcessorFilterService
                .find(findStreamProcessorFilterCriteria);
        StreamProcessorFilter filter = filters.getFirst();
        String xml = buildXML(new long[] { feed1.getId(), feed2.getId() }, null);
        Assert.assertEquals(xml, filter.getData());

        filter.getFindStreamCriteria().obtainFeeds().obtainInclude().remove(feed1);
        filter = streamProcessorFilterService.save(filter);
        xml = buildXML(new long[] { feed2.getId() }, null);
        Assert.assertEquals(xml, filter.getData());

        filter.getFindStreamCriteria().obtainFeeds().obtainExclude().add(feed1);
        filter = streamProcessorFilterService.save(filter);
        xml = buildXML(new long[] { feed2.getId() }, new long[] { feed1.getId() });
        Assert.assertEquals(xml, filter.getData());

        filter.getFindStreamCriteria().obtainFeeds().obtainInclude().add(feed1);
        filter = streamProcessorFilterService.save(filter);
        xml = buildXML(new long[] { feed1.getId(), feed2.getId() }, new long[] { feed1.getId() });
        Assert.assertEquals(xml, filter.getData());
    }

    private String buildXML(final long[] include, final long[] exclude) {
        final StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n");
        sb.append("<findStreamCriteria>\n");
        sb.append("   <feeds>\n");
        if (include != null && include.length > 0) {
            sb.append("      <include>\n");
            for (final long inc : include) {
                sb.append("         <id>");
                sb.append(inc);
                sb.append("</id>\n");
            }
            sb.append("      </include>\n");
        }
        if (exclude != null && exclude.length > 0) {
            sb.append("      <exclude>\n");
            for (final long exc : exclude) {
                sb.append("         <id>");
                sb.append(exc);
                sb.append("</id>\n");
            }
            sb.append("      </exclude>\n");
        }
        sb.append("   </feeds>\n");
        sb.append("   <streamTypeIdSet>\n");
        sb.append("      <id>11</id>\n");
        sb.append("      <id>12</id>\n");
        sb.append("   </streamTypeIdSet>\n");
        sb.append("</findStreamCriteria>\n");
        return sb.toString();
    }

    @Test
    public void testApplyAllCriteria() {
        final FindStreamProcessorFilterCriteria findStreamProcessorFilterCriteria = new FindStreamProcessorFilterCriteria();
        findStreamProcessorFilterCriteria.setLastPollPeriod(new Period(1L, 1L));
        findStreamProcessorFilterCriteria.setStreamProcessorFilterEnabled(true);
        Assert.assertEquals(0, streamProcessorFilterService.find(findStreamProcessorFilterCriteria).getSize());
    }
}
