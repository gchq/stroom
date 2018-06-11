package stroom.streamstore.meta.db;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.jupiter.api.Test;
import stroom.entity.shared.BaseResultList;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Builder;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.security.impl.mock.MockSecurityContextModule;
import stroom.streamstore.meta.api.FindStreamCriteria;
import stroom.streamstore.meta.api.Stream;
import stroom.streamstore.meta.api.StreamMetaService;
import stroom.streamstore.meta.api.StreamProperties;
import stroom.streamstore.shared.StreamDataSource;

import static org.assertj.core.api.Assertions.assertThat;

class TestStreamMetaServiceImpl {
    @Test
    void testJooq() {
        final Injector injector = Guice.createInjector(new StreamStoreDBMetaModule(), new MockSecurityContextModule());
        StreamMetaService service = injector.getInstance(StreamMetaService.class);

        // Delete everything
        service.findDelete(new FindStreamCriteria());
        final Stream stream1 = service.createStream(createProperties("FEED1"));
        final Stream stream2 = service.createStream(createProperties("FEED2"));

        final ExpressionOperator expression = new Builder(Op.AND)
                .addTerm(StreamDataSource.STREAM_ID, Condition.EQUALS, String.valueOf(stream2.getId()))
                .build();
        final FindStreamCriteria criteria = new FindStreamCriteria(expression);

        final BaseResultList<Stream> streams = service.find(criteria);

        assertThat(streams.size()).isEqualTo(1);

        int deleted = service.findDelete(new FindStreamCriteria());
        assertThat(deleted).isEqualTo(2);
    }

    private StreamProperties createProperties(final String feedName) {
        return new StreamProperties.Builder()
                .createMs(System.currentTimeMillis())
                .feedName(feedName)
                .pipelineUuid("PIPELINE_UUID")
                .streamProcessorId(1)
                .streamTypeName("TEST_STREAM_TYPE")
                .build();
    }
}
