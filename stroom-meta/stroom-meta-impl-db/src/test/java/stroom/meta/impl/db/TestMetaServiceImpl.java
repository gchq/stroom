package stroom.meta.impl.db;

import com.google.inject.Guice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import stroom.cache.impl.CacheModule;
import stroom.cluster.lock.mock.MockClusterLockModule;
import stroom.collection.mock.MockCollectionModule;
import stroom.dictionary.mock.MockWordListProviderModule;
import stroom.docrefinfo.mock.MockDocRefInfoModule;
import stroom.meta.impl.MetaModule;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.api.MetaProperties;
import stroom.meta.api.MetaService;
import stroom.meta.shared.Status;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Builder;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.security.mock.MockSecurityContextModule;
import stroom.test.common.util.db.DbTestModule;
import stroom.util.shared.ResultPage;

import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

class TestMetaServiceImpl {
    @Inject
    private Cleanup cleanup;
    @Inject
    private MetaService metaService;

    @BeforeEach
    void setup() {
        Guice.createInjector(
                new MetaModule(),
                new MetaDbModule(),
                new MockClusterLockModule(),
                new MockSecurityContextModule(),
                new MockCollectionModule(),
                new MockDocRefInfoModule(),
                new MockWordListProviderModule(),
                new CacheModule(),
                new DbTestModule(),
                new MetaTestModule())
                .injectMembers(this);
        // Delete everything
        cleanup.clear();
    }

    @Test
    void test() {
        final Meta meta1 = metaService.create(createProperties("FEED1"));
        final Meta meta2 = metaService.create(createProperties("FEED2"));

        final ExpressionOperator expression = new Builder(Op.AND)
                .addTerm(MetaFields.ID, Condition.EQUALS, meta2.getId())
                .build();
        final FindMetaCriteria criteria = new FindMetaCriteria(expression);

        final ResultPage<Meta> list = metaService.find(criteria);

        assertThat(list.size()).isEqualTo(1);

        int deleted = metaService.updateStatus(new FindMetaCriteria(), null, Status.DELETED);
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
