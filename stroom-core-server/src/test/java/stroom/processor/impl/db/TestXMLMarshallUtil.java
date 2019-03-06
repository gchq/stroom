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
 */

package stroom.processor.impl.db;


import org.junit.jupiter.api.Test;
import stroom.meta.shared.MetaFieldNames;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.processor.shared.QueryData;
import stroom.processor.shared.ProcessorFilter;
import stroom.util.test.StroomUnitTest;
import stroom.streamstore.shared.QueryData;
import stroom.streamtask.shared.ProcessorFilter;
import stroom.test.common.util.test.StroomUnitTest;

import static org.assertj.core.api.Assertions.assertThat;

class TestXMLMarshallUtil extends StroomUnitTest {
    private static final StreamProcessorFilterMarshaller MARSHALLER = new StreamProcessorFilterMarshaller();

    @Test
    void testSimple() {
        final String createdPeriod = String.format("%d%s%d", 1L, ExpressionTerm.Condition.IN_CONDITION_DELIMITER, 2L);

        final QueryData queryData1 = new QueryData.Builder()
                .dataSource(MetaFieldNames.STREAM_STORE_DOC_REF)
                .expression(new ExpressionOperator.Builder(ExpressionOperator.Op.AND)
                        .addOperator(new ExpressionOperator.Builder(ExpressionOperator.Op.OR)
                                .addTerm(MetaFieldNames.ID, ExpressionTerm.Condition.EQUALS, Long.toString(999L))
                                .addTerm(MetaFieldNames.ID, ExpressionTerm.Condition.EQUALS, Long.toString(7L))
                                .addTerm(MetaFieldNames.ID, ExpressionTerm.Condition.EQUALS, Long.toString(77L))
                                .build())
                        .addOperator(new ExpressionOperator.Builder(ExpressionOperator.Op.OR)
                                .addTerm(MetaFieldNames.FEED_NAME, ExpressionTerm.Condition.EQUALS, Long.toString(88L))
                                .addTerm(MetaFieldNames.FEED_NAME, ExpressionTerm.Condition.EQUALS, Long.toString(889L))
                                .build())
                        .addTerm(MetaFieldNames.CREATE_TIME, ExpressionTerm.Condition.BETWEEN, createdPeriod)
                        .build())
                .build();

        // Test Writing
        ProcessorFilter streamProcessorFilter = new ProcessorFilter();
        streamProcessorFilter.setQueryData(queryData1);
        streamProcessorFilter = MARSHALLER.marshal(streamProcessorFilter);
        final String xml1 = streamProcessorFilter.getData();

        streamProcessorFilter = MARSHALLER.unmarshal(streamProcessorFilter);
        streamProcessorFilter = MARSHALLER.marshal(streamProcessorFilter);
        final String xml2 = streamProcessorFilter.getData();

        assertThat(xml1.contains("999")).isTrue();
        assertThat(xml2).isEqualTo(xml1);
    }

    @Test
    void testShort() {
        ProcessorFilter streamProcessorFilter = new ProcessorFilter();
        streamProcessorFilter.setData(
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><QueryData></QueryData>");
        streamProcessorFilter = MARSHALLER.unmarshal(streamProcessorFilter);

        final QueryData queryData = streamProcessorFilter.getQueryData();
        assertThat(queryData).isNotNull();
    }
}
