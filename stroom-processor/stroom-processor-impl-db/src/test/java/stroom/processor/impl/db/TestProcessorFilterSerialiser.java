package stroom.processor.impl.db;

import stroom.index.shared.LuceneIndexDoc;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.QueryData;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.Param;
import stroom.query.api.TimeRange;

import jakarta.xml.bind.JAXBException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

class TestProcessorFilterSerialiser {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestProcessorFilterSerialiser.class);

    @Test
    void testMarshall() throws JAXBException {
        final QueryData queryData = new QueryData();
        queryData.setDataSource(LuceneIndexDoc.buildDocRef()
                .randomUuid()
                .name("Some idx")
                .build());
        queryData.setParams(List.of(new Param("key1", "val1")));
        queryData.setTimeRange(new TimeRange("MyName", Condition.BETWEEN, "week() -1w", "week()"));
        queryData.setExpression(
                ExpressionOperator.builder()
                        .addTerm(ExpressionTerm.builder()
                                .field("SomeField")
                                .condition(Condition.EQUALS)
                                .value("xxxx")
                                .build())
                        .build()
        );

        final ProcessorFilter processorFilter = new ProcessorFilter();
        // Blank tracker
        processorFilter.setReprocess(true);
        processorFilter.setEnabled(true);
        processorFilter.setPriority(1);
        processorFilter.setProcessor(null);
        processorFilter.setQueryData(queryData);
        processorFilter.setMinMetaCreateTimeMs(System.currentTimeMillis());
        processorFilter.setMaxMetaCreateTimeMs(System.currentTimeMillis());

        final QueryDataXMLSerialiser serialiser = new QueryDataXMLSerialiser();
        final String xml1 = serialiser.serialise(processorFilter.getQueryData());
        Assertions.assertThat(xml1)
                .isNotBlank();
        LOGGER.debug("marshalled:\n{}", xml1);

        // Now un-marshall

        final QueryData queryData2 = serialiser.deserialise(xml1);
        final ProcessorFilter processorFilter2 = new ProcessorFilter();
        processorFilter2.setQueryData(queryData2);

        Assertions.assertThat(processorFilter2)
                .isEqualTo(processorFilter);

        // Now re-marshall and compare

        final String xml2 = serialiser.serialise(processorFilter2.getQueryData());
        Assertions.assertThat(xml2).isEqualTo(xml1);
    }
}
