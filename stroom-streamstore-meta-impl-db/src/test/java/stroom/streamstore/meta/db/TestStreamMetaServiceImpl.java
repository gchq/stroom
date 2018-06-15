package stroom.streamstore.meta.db;

import com.google.inject.Guice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import stroom.entity.shared.BaseResultList;
import stroom.properties.impl.mock.MockPropertyModule;
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

import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

class TestStreamMetaServiceImpl {
    @Inject
    private StreamMetaServiceImpl streamMetaService;

    @BeforeEach
    void setup() {
        Guice.createInjector(new StreamStoreMetaDbModule(), new MockSecurityContextModule(), new MockPropertyModule()).injectMembers(this);
    }

    @Test
    void test() {
        // Delete everything
        streamMetaService.deleteAll();

        final Stream stream1 = streamMetaService.createStream(createProperties("FEED1"));
        final Stream stream2 = streamMetaService.createStream(createProperties("FEED2"));

        final ExpressionOperator expression = new Builder(Op.AND)
                .addTerm(StreamDataSource.STREAM_ID, Condition.EQUALS, String.valueOf(stream2.getId()))
                .build();
        final FindStreamCriteria criteria = new FindStreamCriteria(expression);

        final BaseResultList<Stream> streams = streamMetaService.find(criteria);

        assertThat(streams.size()).isEqualTo(1);

        int deleted = streamMetaService.findDelete(new FindStreamCriteria());
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
