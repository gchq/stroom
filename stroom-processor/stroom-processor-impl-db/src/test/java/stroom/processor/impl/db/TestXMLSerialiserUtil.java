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
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;

import jakarta.xml.bind.JAXBException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestXMLSerialiserUtil {

    private static QueryDataXMLSerialiser serialiser;

    @Test
    void testSimple() {
        final String createdPeriod = String.format("%d%s%d", 1L, ExpressionTerm.Condition.IN_CONDITION_DELIMITER, 2L);

        final QueryData queryData1 = QueryData.builder()
                .dataSource(MetaFields.STREAM_STORE_DOC_REF)
                .expression(ExpressionOperator.builder()
                        .addOperator(ExpressionOperator.builder().op(Op.OR)
                                .addIdTerm(MetaFields.ID, ExpressionTerm.Condition.EQUALS, 999L)
                                .addIdTerm(MetaFields.ID, ExpressionTerm.Condition.EQUALS, 7L)
                                .addIdTerm(MetaFields.ID, ExpressionTerm.Condition.EQUALS, 77L)
                                .build())
                        .addOperator(ExpressionOperator.builder().op(Op.OR)
                                .addTextTerm(MetaFields.FEED, ExpressionTerm.Condition.EQUALS, Long.toString(88L))
                                .addTextTerm(MetaFields.FEED, ExpressionTerm.Condition.EQUALS, Long.toString(889L))
                                .build())
                        .addDateTerm(MetaFields.CREATE_TIME, ExpressionTerm.Condition.BETWEEN, createdPeriod)
                        .build())
                .build();

        // Test Writing
        ProcessorFilter processorFilter = new ProcessorFilter();
        processorFilter.setQueryData(queryData1);
        final String xml1 = getSerialiser().serialise(processorFilter.getQueryData());

        QueryData queryData = getSerialiser().deserialise(xml1);
        final String xml2 = getSerialiser().serialise(queryData);

        assertThat(xml1.contains("999")).isTrue();
        assertThat(xml2).isEqualTo(xml1);
    }

    @Test
    void testShort() {
        final ProcessorFilter processorFilter = new ProcessorFilter();
        final String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><QueryData></QueryData>";
        final QueryData queryData = getSerialiser().deserialise(xml);
        assertThat(queryData).isNotNull();
    }

    private QueryDataXMLSerialiser getSerialiser() {
        if (serialiser == null) {
            try {
                serialiser = new QueryDataXMLSerialiser();
            } catch (final JAXBException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
        return serialiser;
    }
}
