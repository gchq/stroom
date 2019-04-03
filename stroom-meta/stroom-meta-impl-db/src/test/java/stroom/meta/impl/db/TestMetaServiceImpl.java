package stroom.meta.impl.db;

import com.google.inject.Guice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import stroom.cluster.lock.mock.MockClusterLockModule;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFieldNames;
import stroom.meta.shared.MetaProperties;
import stroom.meta.shared.MetaService;
import stroom.meta.shared.Status;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Builder;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.security.mock.MockSecurityContextModule;
import stroom.util.shared.BaseResultList;

import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

class TestMetaServiceImpl {
    @Inject
    private Cleanup cleanup;
    @Inject
    private MetaService metaService;

    @BeforeEach
    void setup() {
        Guice.createInjector(new MetaDbModule(), new MockClusterLockModule(), new MockSecurityContextModule()).injectMembers(this);
        // Delete everything
        cleanup.clear();
    }

    @Test
    void test() {
        final Meta meta1 = metaService.create(createProperties("FEED1"));
        final Meta meta2 = metaService.create(createProperties("FEED2"));

        final ExpressionOperator expression = new Builder(Op.AND)
                .addTerm(MetaFieldNames.ID, Condition.EQUALS, String.valueOf(meta2.getId()))
                .build();
        final FindMetaCriteria criteria = new FindMetaCriteria(expression);

        final BaseResultList<Meta> list = metaService.find(criteria);

        assertThat(list.size()).isEqualTo(1);

        int deleted = metaService.updateStatus(new FindMetaCriteria(), Status.DELETED);
        assertThat(deleted).isEqualTo(2);
    }

    private MetaProperties createProperties(final String feedName) {
        return new MetaProperties.Builder()
                .createMs(System.currentTimeMillis())
                .feedName(feedName)
                .processorUuid("12345")
                .pipelineUuid("PIPELINE_UUID")
                .typeName("TEST_STREAM_TYPE")
                .build();
    }
}
