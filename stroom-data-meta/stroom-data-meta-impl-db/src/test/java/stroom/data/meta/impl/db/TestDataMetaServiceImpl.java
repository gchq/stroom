package stroom.data.meta.impl.db;

import com.google.inject.Guice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import stroom.data.meta.api.Data;
import stroom.data.meta.api.DataProperties;
import stroom.data.meta.api.DataStatus;
import stroom.data.meta.api.FindDataCriteria;
import stroom.data.meta.api.MetaDataSource;
import stroom.entity.shared.BaseResultList;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Builder;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.security.impl.mock.MockSecurityContextModule;

import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

class TestDataMetaServiceImpl {
    @Inject
    private DataMetaServiceImpl dataMetaService;

    @BeforeEach
    void setup() {
        Guice.createInjector(new DataMetaDbModule(), new MockSecurityContextModule()).injectMembers(this);
    }

    @Test
    void test() {
        // Delete everything
        dataMetaService.deleteAll();

        final Data data1 = dataMetaService.create(createProperties("FEED1"));
        final Data data2 = dataMetaService.create(createProperties("FEED2"));

        final ExpressionOperator expression = new Builder(Op.AND)
                .addTerm(MetaDataSource.STREAM_ID, Condition.EQUALS, String.valueOf(data2.getId()))
                .build();
        final FindDataCriteria criteria = new FindDataCriteria(expression);

        final BaseResultList<Data> list = dataMetaService.find(criteria);

        assertThat(list.size()).isEqualTo(1);

        int deleted = dataMetaService.updateStatus(new FindDataCriteria(), DataStatus.DELETED);
        assertThat(deleted).isEqualTo(2);
    }

    private DataProperties createProperties(final String feedName) {
        return new DataProperties.Builder()
                .createMs(System.currentTimeMillis())
                .feedName(feedName)
                .pipelineUuid("PIPELINE_UUID")
                .processorId(1)
                .typeName("TEST_STREAM_TYPE")
                .build();
    }
}
