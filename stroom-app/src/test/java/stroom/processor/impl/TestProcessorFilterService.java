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


import stroom.data.shared.StreamTypeNames;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.meta.shared.MetaFields;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.api.ProcessorService;
import stroom.processor.impl.db.QueryDataXMLSerialiser;
import stroom.processor.shared.CreateProcessFilterRequest;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterFields;
import stroom.processor.shared.QueryData;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.common.util.test.FileSystemTestUtil;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestProcessorFilterService extends AbstractCoreIntegrationTest {

    @Inject
    private ProcessorService processorService;
    @Inject
    private ProcessorFilterService processorFilterService;

//    @Override
//    protected void onBefore() {
//        super.onBefore();
//        deleteAll();
//    }
//
//    @Override
//    protected void onAfter() {
//        super.onAfter();
//        deleteAll();
//    }
//
//    private void deleteAll() {
//        final List<ProcessorFilter> filters = processorFilterService
//                .find(new ExpressionCriteria()).getValues();
//        for (final ProcessorFilter filter : filters) {
//            processorFilterService.delete(filter.getId());
//        }
//
//        final List<Processor> streamProcessors = processorService.find(new ExpressionCriteria()).getValues();
//        for (final Processor processor : streamProcessors) {
//            processorService.delete(processor.getId());
//        }
//    }

    @Test
    void testBasic() {
        // DB should be empty at this point
        assertThat(processorService.find(new ExpressionCriteria()).size())
                .isEqualTo(0);
        assertThat(processorFilterService.find(new ExpressionCriteria()).size())
                .isEqualTo(0);

        final DocRef pipelineRef = new DocRef(
                PipelineDoc.DOCUMENT_TYPE,
                "12345",
                "Test Pipeline");
        final ExpressionCriteria findProcessorFilterCriteria = new ExpressionCriteria();

        processorFilterService.create(
                CreateProcessFilterRequest
                        .builder()
                        .pipeline(pipelineRef)
                        .queryData(new QueryData())
                        .priority(1)
                        .build());
        assertThat(processorService.find(new ExpressionCriteria()).size())
                .isEqualTo(1);
        assertThat(processorFilterService.find(findProcessorFilterCriteria).size())
                .isEqualTo(1);

        processorFilterService.create(
                CreateProcessFilterRequest
                        .builder()
                        .pipeline(pipelineRef)
                        .queryData(new QueryData())
                        .build());
        assertThat(processorService.find(new ExpressionCriteria()).size())
                .isEqualTo(1);
        assertThat(processorFilterService.find(findProcessorFilterCriteria).size())
                .isEqualTo(2);

        final ExpressionOperator expression1 = ExpressionOperator.builder()
                .addIntegerTerm(ProcessorFilterFields.PRIORITY, Condition.GREATER_THAN_OR_EQUAL_TO, 10)
                .build();
        findProcessorFilterCriteria.setExpression(expression1);
        //PriorityRange(new Range<>(10, null));
        assertThat(processorFilterService.find(findProcessorFilterCriteria).size())
                .isEqualTo(1);

        final ExpressionOperator expression2 = ExpressionOperator.builder()
                .addIntegerTerm(ProcessorFilterFields.PRIORITY, Condition.GREATER_THAN_OR_EQUAL_TO, 1)
                .build();
        findProcessorFilterCriteria.setExpression(expression2);
//        findProcessorFilterCriteria.setPriorityRange(new Range<>(1, null));
        assertThat(processorFilterService.find(findProcessorFilterCriteria).size())
                .isEqualTo(2);
    }

    @Test
    void testFeedIncludeExclude() throws Exception {
        final DocRef pipelineRef = new DocRef(PipelineDoc.DOCUMENT_TYPE, "12345", "Test Pipeline");

        final String feedName1 = FileSystemTestUtil.getUniqueTestString();
        final String feedName2 = FileSystemTestUtil.getUniqueTestString();

        final QueryData findStreamQueryData = QueryData
                .builder()
                .dataSource(MetaFields.STREAM_STORE_DOC_REF)
                .expression(ExpressionOperator.builder()
                        .addOperator(ExpressionOperator.builder().op(Op.OR)
                                .addTextTerm(MetaFields.FEED, ExpressionTerm.Condition.EQUALS, feedName1)
                                .addTextTerm(MetaFields.FEED, ExpressionTerm.Condition.EQUALS, feedName2)
                                .build())
                        .addOperator(ExpressionOperator.builder().op(Op.OR)
                                .addTextTerm(MetaFields.TYPE,
                                        ExpressionTerm.Condition.EQUALS,
                                        StreamTypeNames.RAW_EVENTS)
                                .addTextTerm(MetaFields.TYPE,
                                        ExpressionTerm.Condition.EQUALS,
                                        StreamTypeNames.RAW_REFERENCE)
                                .build())
                        .build())
                .build();

        final ExpressionCriteria findProcessorFilterCriteria = new ExpressionCriteria();

        processorFilterService.create(
                CreateProcessFilterRequest
                        .builder()
                        .pipeline(pipelineRef)
                        .queryData(findStreamQueryData)
                        .priority(1)
                        .build());
        assertThat(processorService.find(new ExpressionCriteria()).size()).isEqualTo(1);

        final QueryDataXMLSerialiser serialiser = new QueryDataXMLSerialiser();
        final ResultPage<ProcessorFilter> filters = processorFilterService
                .find(findProcessorFilterCriteria);
        ProcessorFilter filter = filters.getFirst();
        String xml = buildXML(new String[]{feedName1, feedName2}, null);
        assertThat(serialiser.serialise(filter.getQueryData())).isEqualTo(xml);

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
        sb.append("""
                <?xml version="1.1" encoding="UTF-8"?>
                <query>
                   <dataSource>
                      <type>StreamStore</type>
                      <uuid>0</uuid>
                      <name>StreamStore</name>
                   </dataSource>
                   <expression>
                      <children>
                """);

        if (include != null && include.length > 0) {
            sb.append("""
                             <operator>
                                <op>OR</op>
                                <children>
                    """);
            for (final String feed : include) {
                sb.append("               <term>\n");
                sb.append("                  <field>")
                        .append(MetaFields.FEED)
                        .append("</field>\n");
                sb.append("                  <condition>EQUALS</condition>\n");
                sb.append("                  <value>")
                        .append(feed)
                        .append("</value>\n");
                sb.append("               </term>\n");
            }

            sb.append("""
                                </children>
                             </operator>
                    """);
        }

        sb.append("         <operator>\n");
        sb.append("            <op>OR</op>\n");
        sb.append("            <children>\n");
        sb.append("               <term>\n");
        sb.append("                  <field>")
                .append(MetaFields.TYPE)
                .append("</field>\n");
        sb.append("                  <condition>EQUALS</condition>\n");
        sb.append("                  <value>Raw Events</value>\n");
        sb.append("               </term>\n");
        sb.append("               <term>\n");
        sb.append("                  <field>")
                .append(MetaFields.TYPE)
                .append("</field>\n");
        sb.append("                  <condition>EQUALS</condition>\n");
        sb.append("                  <value>Raw Reference</value>\n");
        sb.append("               </term>\n");
        sb.append("            </children>\n");
        sb.append("         </operator>\n");
        sb.append("      </children>\n");
        sb.append("   </expression>\n");
        sb.append("</query>\n");

        return sb.toString();
    }

    @Test
    void testApplyAllCriteria() {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addIdTerm(ProcessorFilterFields.LAST_POLL_MS, Condition.GREATER_THAN_OR_EQUAL_TO, 1)
                .addIdTerm(ProcessorFilterFields.LAST_POLL_MS, Condition.LESS_THAN, 1)
                .addBooleanTerm(ProcessorFilterFields.ENABLED, Condition.EQUALS, true)
                .build();
        final ExpressionCriteria findProcessorFilterCriteria = new ExpressionCriteria(expression);
//        findProcessorFilterCriteria.setLastPollPeriod(new Period(1L, 1L));
//        findProcessorFilterCriteria.setProcessorFilterEnabled(true);
        assertThat(processorFilterService.find(findProcessorFilterCriteria).getPageSize()).isEqualTo(0);
    }
}
