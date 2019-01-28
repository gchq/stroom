package stroom.meta.impl.db;

import com.google.inject.Guice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaProperties;
import stroom.meta.shared.Status;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.MetaFieldNames;
import stroom.entity.shared.BaseResultList;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Builder;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.security.impl.mock.MockSecurityContextModule;

import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

class TestMetaServiceImpl {
    @Inject
    private MetaServiceImpl dataMetaService;

    @BeforeEach
    void setup() {
        Guice.createInjector(new MetaDbModule(), new MockSecurityContextModule()).injectMembers(this);
    }

    @Test
    void test() {
        // Delete everything
        dataMetaService.deleteAll();

        final Meta meta1 = dataMetaService.create(createProperties("FEED1"));
        final Meta meta2 = dataMetaService.create(createProperties("FEED2"));

        final ExpressionOperator expression = new Builder(Op.AND)
                .addTerm(MetaFieldNames.ID, Condition.EQUALS, String.valueOf(meta2.getId()))
                .build();
        final FindMetaCriteria criteria = new FindMetaCriteria(expression);

        final BaseResultList<Meta> list = dataMetaService.find(criteria);

        assertThat(list.size()).isEqualTo(1);

        int deleted = dataMetaService.updateStatus(new FindMetaCriteria(), Status.DELETED);
        assertThat(deleted).isEqualTo(2);
    }

    private MetaProperties createProperties(final String feedName) {
        return new MetaProperties.Builder()
                .createMs(System.currentTimeMillis())
                .feedName(feedName)
                .pipelineUuid("PIPELINE_UUID")
                .processorId(1)
                .typeName("TEST_STREAM_TYPE")
                .build();
    }
}
