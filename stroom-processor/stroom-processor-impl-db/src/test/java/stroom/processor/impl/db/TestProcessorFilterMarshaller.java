package stroom.processor.impl.db;

import stroom.docref.DocRef;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.QueryData;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import javax.xml.bind.JAXBException;

class TestProcessorFilterMarshaller {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestProcessorFilterMarshaller.class);

    @Test
    void testMarshall() throws JAXBException {
        final QueryData queryData = new QueryData();
        queryData.setDataSource(DocRef.builder()
                .uuid(UUID.randomUUID().toString())
                .type("Index")
                .name("Some idx")
                .build());
        queryData.setExpression(
                ExpressionOperator.builder()
                        .addTerm(ExpressionTerm.builder()
                                .field("SomeField")
                                .condition(Condition.EQUALS)
                                .value("xxxx")
                                .build())
                        .build()

        );
        queryData.setParams(null);

        final ProcessorFilter processorFilter = new ProcessorFilter();
        // Blank tracker
        processorFilter.setReprocess(true);
        processorFilter.setEnabled(true);
        processorFilter.setPriority(1);
        processorFilter.setProcessor(null);
        processorFilter.setQueryData(queryData);
        processorFilter.setMinMetaCreateTimeMs(System.currentTimeMillis());
        processorFilter.setMaxMetaCreateTimeMs(System.currentTimeMillis());

        final ProcessorFilterMarshaller processorFilterMarshaller = new ProcessorFilterMarshaller();
        final ProcessorFilter marshalled = processorFilterMarshaller.marshal(processorFilter);

        Assertions.assertThat(marshalled.getData())
                .isNotBlank();
        LOGGER.debug("marshalled:\n{}", marshalled.getData());
    }
}
