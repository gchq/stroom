/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.meta.impl.db;

import stroom.cache.impl.CacheModule;
import stroom.cluster.lock.mock.MockClusterLockModule;
import stroom.collection.mock.MockCollectionModule;
import stroom.dictionary.mock.MockWordListProviderModule;
import stroom.docrefinfo.mock.MockDocRefInfoModule;
import stroom.event.logging.mock.MockStroomEventLoggingModule;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.MetaProperties;
import stroom.meta.impl.MetaModule;
import stroom.meta.impl.MetaServiceConfig;
import stroom.meta.impl.MetaServiceImpl;
import stroom.meta.impl.MetaValueConfig;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.security.mock.MockSecurityContextModule;
import stroom.task.mock.MockTaskModule;
import stroom.test.common.MockMetricsModule;
import stroom.test.common.util.db.DbTestModule;
import stroom.util.date.DateUtil;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestMetaValueDaoImpl {

    @Inject
    private Cleanup cleanup;
    @Inject
    private MetaServiceImpl metaService;
    @Inject
    private MetaValueDaoImpl metaValueDao;

    private MetaServiceConfig metaServiceConfig = new MetaServiceConfig();

    @BeforeEach
    void setup() {
        final AbstractModule module = new AbstractModule() {
            @Override
            protected void configure() {
                bind(MetaServiceConfig.class).toProvider(() ->
                        getMetaServiceConfig());
                bind(MetaValueConfig.class).toProvider(() ->
                        getMetaValueConfig());
            }
        };

        Guice.createInjector(
                        new MetaModule(),
                        new MetaDbModule(),
                        new MetaDaoModule(),
                        new MockClusterLockModule(),
                        new MockSecurityContextModule(),
                        new MockCollectionModule(),
                        new MockDocRefInfoModule(),
                        new MockWordListProviderModule(),
                        new MockMetricsModule(),
                        new CacheModule(),
                        new DbTestModule(),
                        new MetaTestModule(),
                        new MockTaskModule(),
                        new MockStroomEventLoggingModule(),
                        module)
                .injectMembers(this);
        setAddAsync(false);
        // Delete everything
        cleanup.cleanup();
    }

    @AfterEach
    void unsetProperties() {
        setAddAsync(true);
    }

    @Test
    void testFind() {
        final Meta meta = metaService.create(createProperties("FEED1"));

        metaService.addAttributes(meta, createAttributes());

        ExpressionOperator expression = ExpressionOperator.builder()
                .addIdTerm(MetaFields.ID, Condition.EQUALS, meta.getId())
                .addDateTerm(MetaFields.CREATE_TIME,
                        Condition.EQUALS,
                        DateUtil.createNormalDateTimeString(meta.getCreateMs()))
                .build();
        FindMetaCriteria criteria = new FindMetaCriteria(expression);
        assertThat(metaService.find(criteria).size()).isEqualTo(1);

        expression = ExpressionOperator.builder()
                .addIdTerm(MetaFields.ID, Condition.EQUALS, meta.getId())
                .addDateTerm(MetaFields.CREATE_TIME, Condition.EQUALS, DateUtil.createNormalDateTimeString(0L))
                .build();
        criteria = new FindMetaCriteria(expression);
        assertThat(metaService.find(criteria).size()).isEqualTo(0);

        expression = ExpressionOperator.builder()
                .addIdTerm(MetaFields.ID, Condition.EQUALS, meta.getId())
                .addIdTerm(MetaFields.FILE_SIZE, Condition.GREATER_THAN, 0)
                .build();
        criteria = new FindMetaCriteria(expression);
        assertThat(metaService.find(criteria).size()).isEqualTo(1);

        expression = ExpressionOperator.builder()
                .addIdTerm(MetaFields.ID, Condition.EQUALS, meta.getId())
                .addTerm(MetaFields.FILE_SIZE.getFldName(), Condition.BETWEEN, "0,1000000")
                .build();
        criteria = new FindMetaCriteria(expression);
        assertThat(metaService.find(criteria).size()).isEqualTo(1);
    }

    @Test
    void testDeleteOld() {
        final Meta meta = metaService.create(createProperties("FEED1"));

        metaService.addAttributes(meta, createAttributes());

        ExpressionOperator expression = ExpressionOperator.builder()
                .addIdTerm(MetaFields.ID, Condition.EQUALS, meta.getId())
                .addDateTerm(MetaFields.CREATE_TIME,
                        Condition.EQUALS,
                        DateUtil.createNormalDateTimeString(meta.getCreateMs()))
                .build();
        FindMetaCriteria criteria = new FindMetaCriteria(expression);
        assertThat(metaService.find(criteria).size()).isEqualTo(1);

        expression = ExpressionOperator.builder()
                .addIdTerm(MetaFields.ID, Condition.EQUALS, meta.getId())
                .addDateTerm(MetaFields.CREATE_TIME, Condition.EQUALS, DateUtil.createNormalDateTimeString(0L))
                .build();
        criteria = new FindMetaCriteria(expression);
        assertThat(metaService.find(criteria).size()).isEqualTo(0);

        expression = ExpressionOperator.builder()
                .addIdTerm(MetaFields.ID, Condition.EQUALS, meta.getId())
                .addIdTerm(MetaFields.FILE_SIZE, Condition.GREATER_THAN, 0)
                .build();
        criteria = new FindMetaCriteria(expression);
        assertThat(metaService.find(criteria).size()).isEqualTo(1);

        expression = ExpressionOperator.builder()
                .addIdTerm(MetaFields.ID, Condition.EQUALS, meta.getId())
                .addTerm(MetaFields.FILE_SIZE.getFldName(), Condition.BETWEEN, "0,1000000")
                .build();
        criteria = new FindMetaCriteria(expression);
        assertThat(metaService.find(criteria).size()).isEqualTo(1);

        metaValueDao.deleteOldValues();

        expression = ExpressionOperator.builder()
                .addIdTerm(MetaFields.ID, Condition.EQUALS, meta.getId())
                .addTerm(MetaFields.FILE_SIZE.getFldName(), Condition.BETWEEN, "0,1000000")
                .build();
        criteria = new FindMetaCriteria(expression);
        assertThat(metaService.find(criteria).size()).isEqualTo(0);
    }

    private MetaProperties createProperties(final String feedName) {
        return MetaProperties.builder()
                .createMs(1000L)
                .feedName(feedName)
                .processorUuid("12345")
                .pipelineUuid("PIPELINE_UUID")
                .typeName("TEST_STREAM_TYPE")
                .build();
    }

    private AttributeMap createAttributes() {
        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put(MetaFields.FILE_SIZE.getFldName(), "100");
        return attributeMap;
    }

    public MetaServiceConfig getMetaServiceConfig() {
        return metaServiceConfig;
    }

    public MetaValueConfig getMetaValueConfig() {
        return metaServiceConfig.getMetaValueConfig();
    }

    private void setAddAsync(final boolean addAsync) {
        metaServiceConfig = metaServiceConfig.withMetaValueConfig(
                metaServiceConfig.getMetaValueConfig()
                        .withAddAsync(addAsync));
    }
}
