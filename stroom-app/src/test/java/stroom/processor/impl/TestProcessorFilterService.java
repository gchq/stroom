/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.processor.impl;


import stroom.data.shared.StreamTypeNames;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.meta.shared.MetaFields;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.api.ProcessorService;
import stroom.processor.impl.db.QueryDataSerialiser;
import stroom.processor.shared.CreateProcessFilterRequest;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterFields;
import stroom.processor.shared.QueryData;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestProcessorFilterService extends AbstractCoreIntegrationTest {

    @Inject
    private ProcessorService processorService;
    @Inject
    private ProcessorFilterService processorFilterService;

    @Test
    void testBasic() {
        // DB should be empty at this point
        assertThat(processorService.find(new ExpressionCriteria()).size())
                .isEqualTo(0);
        assertThat(processorFilterService.find(new ExpressionCriteria()).size())
                .isEqualTo(0);

        final DocRef pipelineRef = new DocRef(
                PipelineDoc.TYPE,
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
        final DocRef pipelineRef = new DocRef(PipelineDoc.TYPE, "12345", "Test Pipeline");

        final String feedName1 = "1749655604143_1";
        final String feedName2 = "1749655604143_2";

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

        final QueryDataSerialiser serialiser = new QueryDataSerialiser();
        final ResultPage<ProcessorFilter> filters = processorFilterService
                .find(findProcessorFilterCriteria);
        final ProcessorFilter filter = filters.getFirst();
        final String json = getJson();

        assertThat(serialiser.serialise(filter.getQueryData())).isEqualTo(json);

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

    private String getJson() {
        return """
                {
                  "dataSource" : {
                    "type" : "StreamStore",
                    "uuid" : "StreamStore",
                    "name" : "Stream Store"
                  },
                  "expression" : {
                    "type" : "operator",
                    "children" : [ {
                      "type" : "operator",
                      "op" : "OR",
                      "children" : [ {
                        "type" : "term",
                        "field" : "Feed",
                        "condition" : "EQUALS",
                        "value" : "1749655604143_1"
                      }, {
                        "type" : "term",
                        "field" : "Feed",
                        "condition" : "EQUALS",
                        "value" : "1749655604143_2"
                      } ]
                    }, {
                      "type" : "operator",
                      "op" : "OR",
                      "children" : [ {
                        "type" : "term",
                        "field" : "Type",
                        "condition" : "EQUALS",
                        "value" : "Raw Events"
                      }, {
                        "type" : "term",
                        "field" : "Type",
                        "condition" : "EQUALS",
                        "value" : "Raw Reference"
                      } ]
                    } ]
                  }
                }""";
    }

    @Test
    void testApplyAllCriteria() {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addIdTerm(ProcessorFilterFields.LAST_POLL_MS, Condition.GREATER_THAN_OR_EQUAL_TO, 1)
                .addIdTerm(ProcessorFilterFields.LAST_POLL_MS, Condition.LESS_THAN, 1)
                .addBooleanTerm(ProcessorFilterFields.ENABLED, Condition.EQUALS, true)
                .build();
        final ExpressionCriteria findProcessorFilterCriteria = new ExpressionCriteria(expression);
        assertThat(processorFilterService.find(findProcessorFilterCriteria).getPageSize()).isEqualTo(0);
    }
}
