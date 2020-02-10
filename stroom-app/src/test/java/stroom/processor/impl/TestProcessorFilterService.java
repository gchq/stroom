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

package stroom.processor.impl;


import org.junit.jupiter.api.Test;
import stroom.data.shared.StreamTypeNames;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.meta.shared.MetaFields;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.api.ProcessorService;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterDataSource;
import stroom.processor.shared.QueryData;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.common.util.test.FileSystemTestUtil;
import stroom.util.shared.ResultList;

import javax.inject.Inject;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestProcessorFilterService extends AbstractCoreIntegrationTest {
    @Inject
    private ProcessorService processorService;
    @Inject
    private ProcessorFilterService processorFilterService;

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
        final List<ProcessorFilter> filters = processorFilterService
                .find(new ExpressionCriteria());
        for (final ProcessorFilter filter : filters) {
            processorFilterService.delete(filter.getId());
        }

        final List<Processor> streamProcessors = processorService.find(new ExpressionCriteria());
        for (final Processor processor : streamProcessors) {
            processorService.delete(processor.getId());
        }
    }

    @Test
    void testBasic() {
        final DocRef pipelineRef = new DocRef(PipelineDoc.DOCUMENT_TYPE, "12345", "Test Pipeline");
        final ExpressionCriteria findProcessorFilterCriteria = new ExpressionCriteria();

        processorFilterService.create(pipelineRef, new QueryData(), 1, true);
        assertThat(processorService.find(new ExpressionCriteria()).size()).isEqualTo(1);
        assertThat(processorFilterService.find(findProcessorFilterCriteria).size()).isEqualTo(1);

        processorFilterService.create(pipelineRef, new QueryData(), 10, true);
        assertThat(processorService.find(new ExpressionCriteria()).size()).isEqualTo(1);
        assertThat(processorFilterService.find(findProcessorFilterCriteria).size()).isEqualTo(2);

        final ExpressionOperator expression1 = new ExpressionOperator.Builder()
                .addTerm(ProcessorFilterDataSource.PRIORITY, Condition.GREATER_THAN_OR_EQUAL_TO, 10)
                .build();
        findProcessorFilterCriteria.setExpression(expression1);
        //PriorityRange(new Range<>(10, null));
        assertThat(processorFilterService.find(findProcessorFilterCriteria).size()).isEqualTo(1);

        final ExpressionOperator expression2 = new ExpressionOperator.Builder()
                .addTerm(ProcessorFilterDataSource.PRIORITY, Condition.GREATER_THAN_OR_EQUAL_TO, 1)
                .build();
        findProcessorFilterCriteria.setExpression(expression2);
//        findProcessorFilterCriteria.setPriorityRange(new Range<>(1, null));
        assertThat(processorFilterService.find(findProcessorFilterCriteria).size()).isEqualTo(2);
    }

    @Test
    void testFeedIncludeExclude() {
        final DocRef pipelineRef = new DocRef(PipelineDoc.DOCUMENT_TYPE, "12345", "Test Pipeline");

        final String feedName1 = FileSystemTestUtil.getUniqueTestString();
        final String feedName2 = FileSystemTestUtil.getUniqueTestString();

        final QueryData findStreamQueryData = new QueryData.Builder()
                .dataSource(MetaFields.STREAM_STORE_DOC_REF)
                .expression(new ExpressionOperator.Builder(ExpressionOperator.Op.AND)
                        .addOperator(new ExpressionOperator.Builder(ExpressionOperator.Op.OR)
                                .addTerm(MetaFields.FEED_NAME, ExpressionTerm.Condition.EQUALS, feedName1)
                                .addTerm(MetaFields.FEED_NAME, ExpressionTerm.Condition.EQUALS, feedName2)
                                .build())
                        .addOperator(new ExpressionOperator.Builder(ExpressionOperator.Op.OR)
                                .addTerm(MetaFields.TYPE_NAME, ExpressionTerm.Condition.EQUALS, StreamTypeNames.RAW_EVENTS)
                                .addTerm(MetaFields.TYPE_NAME, ExpressionTerm.Condition.EQUALS, StreamTypeNames.RAW_REFERENCE)
                                .build())
                        .build())
                .build();

        final ExpressionCriteria findProcessorFilterCriteria = new ExpressionCriteria();

        processorFilterService.create(pipelineRef, findStreamQueryData, 1, true);
        assertThat(processorService.find(new ExpressionCriteria()).size()).isEqualTo(1);

        final ResultList<ProcessorFilter> filters = processorFilterService
                .find(findProcessorFilterCriteria);
        ProcessorFilter filter = filters.getFirst();
        String xml = buildXML(new String[]{feedName1, feedName2}, null);
        assertThat(filter.getData()).isEqualTo(xml);

        // TODO DocRefId - Need to rewrite the build XML to handle expression operators
//        filter.getFindStreamCriteria().obtainFeeds().obtainInclude().remove(feed1);
//        filter = processorFilterService.save(filter);
//        xml = buildXML(new long[]{feed2.getId()}, null);
//        assertThat(filter.getMeta()).isEqualTo(xml);
//
//        filter.getFindStreamCriteria().obtainFeeds().obtainExclude().add(feed1);
//        filter = processorFilterService.save(filter);
//        xml = buildXML(new long[]{feed2.getId()}, new long[]{feed1.getId()});
//        assertThat(filter.getMeta()).isEqualTo(xml);
//
//        filter.getFindStreamCriteria().obtainFeeds().obtainInclude().add(feed1);
//        filter = processorFilterService.save(filter);
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
                "      <enabled>true</enabled>\n" +
                "      <op>AND</op>\n" +
                "      <children>\n";

        if (include != null && include.length > 0) {
            xml += "" +
                    "         <operator>\n" +
                    "            <enabled>true</enabled>\n" +
                    "            <op>OR</op>\n" +
                    "            <children>\n";
            for (final String feed : include) {
                xml += "" +
                        "               <term>\n" +
                        "                  <enabled>true</enabled>\n" +
                        "                  <field>" + MetaFields.FEED_NAME + "</field>\n" +
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
                "            <enabled>true</enabled>\n" +
                "            <op>OR</op>\n" +
                "            <children>\n" +
                "               <term>\n" +
                "                  <enabled>true</enabled>\n" +
                "                  <field>" + MetaFields.TYPE_NAME + "</field>\n" +
                "                  <condition>EQUALS</condition>\n" +
                "                  <value>Raw Events</value>\n" +
                "               </term>\n" +
                "               <term>\n" +
                "                  <enabled>true</enabled>\n" +
                "                  <field>" + MetaFields.TYPE_NAME + "</field>\n" +
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
        final ExpressionOperator expression = new ExpressionOperator.Builder()
                .addTerm(ProcessorFilterDataSource.LAST_POLL_MS, Condition.GREATER_THAN_OR_EQUAL_TO, 1)
                .addTerm(ProcessorFilterDataSource.LAST_POLL_MS, Condition.LESS_THAN, 1)
                .addTerm(ProcessorFilterDataSource.PROCESSOR_FILTER_ENABLED, Condition.EQUALS, true)
                .build();
        final ExpressionCriteria findProcessorFilterCriteria = new ExpressionCriteria(expression);
//        findProcessorFilterCriteria.setLastPollPeriod(new Period(1L, 1L));
//        findProcessorFilterCriteria.setProcessorFilterEnabled(true);
        assertThat(processorFilterService.find(findProcessorFilterCriteria).getSize()).isEqualTo(0);
    }
}
