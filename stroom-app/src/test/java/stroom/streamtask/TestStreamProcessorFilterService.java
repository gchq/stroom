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

package stroom.streamtask;


import org.junit.jupiter.api.Test;
import stroom.meta.shared.MetaFieldNames;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.Period;
import stroom.entity.shared.Range;
import stroom.processor.StreamProcessorFilterService;
import stroom.processor.StreamProcessorService;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.streamstore.shared.QueryData;
import stroom.streamstore.shared.StreamTypeNames;
import stroom.streamtask.shared.FindStreamProcessorCriteria;
import stroom.streamtask.shared.FindStreamProcessorFilterCriteria;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.test.FileSystemTestUtil;

import javax.inject.Inject;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestStreamProcessorFilterService extends AbstractCoreIntegrationTest {
    @Inject
    private StreamProcessorService streamProcessorService;
    @Inject
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
        final List<ProcessorFilter> filters = streamProcessorFilterService
                .find(new FindStreamProcessorFilterCriteria());
        for (final ProcessorFilter filter : filters) {
            streamProcessorFilterService.delete(filter);
        }

        final List<Processor> streamProcessors = streamProcessorService.find(new FindStreamProcessorCriteria());
        for (final Processor processor : streamProcessors) {
            streamProcessorService.delete(processor);
        }
    }

    @Test
    void testBasic() {
        Processor streamProcessor = new Processor();
        streamProcessor = streamProcessorService.save(streamProcessor);

        assertThat(streamProcessorService.find(new FindStreamProcessorCriteria()).size()).isEqualTo(1);

        final FindStreamProcessorFilterCriteria findStreamProcessorFilterCriteria = new FindStreamProcessorFilterCriteria();

        streamProcessorFilterService.addFindStreamCriteria(streamProcessor, 1, new QueryData());
        assertThat(streamProcessorFilterService.find(findStreamProcessorFilterCriteria).size()).isEqualTo(1);

        streamProcessorFilterService.addFindStreamCriteria(streamProcessor, 10, new QueryData());
        assertThat(streamProcessorFilterService.find(findStreamProcessorFilterCriteria).size()).isEqualTo(2);

        findStreamProcessorFilterCriteria.setPriorityRange(new Range<>(10, null));
        assertThat(streamProcessorFilterService.find(findStreamProcessorFilterCriteria).size()).isEqualTo(1);

        findStreamProcessorFilterCriteria.setPriorityRange(new Range<>(1, null));
        assertThat(streamProcessorFilterService.find(findStreamProcessorFilterCriteria).size()).isEqualTo(2);
    }

    @Test
    void testFeedIncludeExclude() {
        Processor streamProcessor = new Processor();
        streamProcessor = streamProcessorService.save(streamProcessor);
        assertThat(streamProcessorService.find(new FindStreamProcessorCriteria()).size()).isEqualTo(1);

        final String feedName1 = FileSystemTestUtil.getUniqueTestString();
        final String feedName2 = FileSystemTestUtil.getUniqueTestString();

        final QueryData findStreamQueryData = new QueryData.Builder()
                .dataSource(MetaFieldNames.STREAM_STORE_DOC_REF)
                .expression(new ExpressionOperator.Builder(ExpressionOperator.Op.AND)
                        .addOperator(new ExpressionOperator.Builder(ExpressionOperator.Op.OR)
                                .addTerm(MetaFieldNames.FEED_NAME, ExpressionTerm.Condition.EQUALS, feedName1)
                                .addTerm(MetaFieldNames.FEED_NAME, ExpressionTerm.Condition.EQUALS, feedName2)
                                .build())
                        .addOperator(new ExpressionOperator.Builder(ExpressionOperator.Op.OR)
                                .addTerm(MetaFieldNames.TYPE_NAME, ExpressionTerm.Condition.EQUALS, StreamTypeNames.RAW_EVENTS)
                                .addTerm(MetaFieldNames.TYPE_NAME, ExpressionTerm.Condition.EQUALS, StreamTypeNames.RAW_REFERENCE)
                                .build())
                        .build())
                .build();

        final FindStreamProcessorFilterCriteria findStreamProcessorFilterCriteria = new FindStreamProcessorFilterCriteria();

        streamProcessorFilterService.addFindStreamCriteria(streamProcessor, 1, findStreamQueryData);
        final BaseResultList<ProcessorFilter> filters = streamProcessorFilterService
                .find(findStreamProcessorFilterCriteria);
        ProcessorFilter filter = filters.getFirst();
        String xml = buildXML(new String[]{feedName1, feedName2}, null);
        assertThat(filter.getData()).isEqualTo(xml);

        // TODO DocRefId - Need to rewrite the build XML to handle expression operators
//        filter.getFindStreamCriteria().obtainFeeds().obtainInclude().remove(feed1);
//        filter = streamProcessorFilterService.save(filter);
//        xml = buildXML(new long[]{feed2.getId()}, null);
//        assertThat(filter.getMeta()).isEqualTo(xml);
//
//        filter.getFindStreamCriteria().obtainFeeds().obtainExclude().add(feed1);
//        filter = streamProcessorFilterService.save(filter);
//        xml = buildXML(new long[]{feed2.getId()}, new long[]{feed1.getId()});
//        assertThat(filter.getMeta()).isEqualTo(xml);
//
//        filter.getFindStreamCriteria().obtainFeeds().obtainInclude().add(feed1);
//        filter = streamProcessorFilterService.save(filter);
//        xml = buildXML(new long[]{feed1.getId(), feed2.getId()}, new long[]{feed1.getId()});
//        assertThat(filter.getMeta()).isEqualTo(xml);
    }

    private String buildXML(final String[] include, final String[] exclude) {
        final StringBuilder sb = new StringBuilder();
        String xml = "" +
                "<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n" +
                "<query>\n" +
                "   <dataSource>\n" +
                "      <type>StreamStore</type>\n" +
                "      <uuid>0</uuid>\n" +
                "      <name>StreamStore</name>\n" +
                "   </dataSource>\n" +
                "   <expression>\n" +
                "      <op>AND</op>\n" +
                "      <children>\n";

        if (include != null && include.length > 0) {
            xml += "" +
                    "         <operator>\n" +
                    "            <op>OR</op>\n" +
                    "            <children>\n";
            for (final String feed : include) {
                xml += "" +
                        "               <term>\n" +
                        "                  <field>" + MetaFieldNames.FEED_NAME + "</field>\n" +
                        "                  <condition>EQUALS</condition>\n" +
                        "                  <value>" + feed + "</value>\n" +
                        "               </term>\n";
            }

            xml += "" +
                    "            </children>\n" +
                    "         </operator>\n";
        }


        xml += "" +
                "         <operator>\n" +
                "            <op>OR</op>\n" +
                "            <children>\n" +
                "               <term>\n" +
                "                  <field>" + MetaFieldNames.TYPE_NAME + "</field>\n" +
                "                  <condition>EQUALS</condition>\n" +
                "                  <value>Raw Events</value>\n" +
                "               </term>\n" +
                "               <term>\n" +
                "                  <field>" + MetaFieldNames.TYPE_NAME + "</field>\n" +
                "                  <condition>EQUALS</condition>\n" +
                "                  <value>Raw Reference</value>\n" +
                "               </term>\n" +
                "            </children>\n" +
                "         </operator>\n" +
                "      </children>\n" +
                "   </expression>\n" +
                "</query>\n";

        return xml;
    }

    @Test
    void testApplyAllCriteria() {
        final FindStreamProcessorFilterCriteria findStreamProcessorFilterCriteria = new FindStreamProcessorFilterCriteria();
        findStreamProcessorFilterCriteria.setLastPollPeriod(new Period(1L, 1L));
        findStreamProcessorFilterCriteria.setStreamProcessorFilterEnabled(true);
        assertThat(streamProcessorFilterService.find(findStreamProcessorFilterCriteria).getSize()).isEqualTo(0);
    }
}
