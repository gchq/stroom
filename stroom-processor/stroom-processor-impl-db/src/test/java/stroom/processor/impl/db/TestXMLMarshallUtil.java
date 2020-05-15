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

import stroom.meta.shared.MetaFields;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.QueryData;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;

import org.junit.jupiter.api.Test;

import javax.xml.bind.JAXBException;

import static org.assertj.core.api.Assertions.assertThat;

class TestXMLMarshallUtil {
    private static ProcessorFilterMarshaller marshaller;

    @Test
    void testSimple() {
        final String createdPeriod = String.format("%d%s%d", 1L, ExpressionTerm.Condition.IN_CONDITION_DELIMITER, 2L);

        final QueryData queryData1 = new QueryData.Builder()
                .dataSource(MetaFields.STREAM_STORE_DOC_REF)
                .expression(new ExpressionOperator.Builder(ExpressionOperator.Op.AND)
                        .addOperator(new ExpressionOperator.Builder(ExpressionOperator.Op.OR)
                                .addTerm(MetaFields.ID, ExpressionTerm.Condition.EQUALS, 999L)
                                .addTerm(MetaFields.ID, ExpressionTerm.Condition.EQUALS, 7L)
                                .addTerm(MetaFields.ID, ExpressionTerm.Condition.EQUALS, 77L)
                                .build())
                        .addOperator(new ExpressionOperator.Builder(ExpressionOperator.Op.OR)
                                .addTerm(MetaFields.FEED_NAME, ExpressionTerm.Condition.EQUALS, Long.toString(88L))
                                .addTerm(MetaFields.FEED_NAME, ExpressionTerm.Condition.EQUALS, Long.toString(889L))
                                .build())
                        .addTerm(MetaFields.CREATE_TIME, ExpressionTerm.Condition.BETWEEN, createdPeriod)
                        .build())
                .build();

        // Test Writing
        ProcessorFilter processorFilter = new ProcessorFilter();
        processorFilter.setQueryData(queryData1);
        processorFilter = getMarshaller().marshal(processorFilter);
        final String xml1 = processorFilter.getData();

        processorFilter = getMarshaller().unmarshal(processorFilter);
        processorFilter = getMarshaller().marshal(processorFilter);
        final String xml2 = processorFilter.getData();

        assertThat(xml1.contains("999")).isTrue();
        assertThat(xml2).isEqualTo(xml1);
    }

    @Test
    void testShort() {
        ProcessorFilter processorFilter = new ProcessorFilter();
        processorFilter.setData(
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><QueryData></QueryData>");
        processorFilter = getMarshaller().unmarshal(processorFilter);

        final QueryData queryData = processorFilter.getQueryData();
        assertThat(queryData).isNotNull();
    }

    private ProcessorFilterMarshaller getMarshaller() {
        if (marshaller == null) {
            try {
                marshaller = new ProcessorFilterMarshaller();
            } catch (final JAXBException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
        return marshaller;
    }
}
