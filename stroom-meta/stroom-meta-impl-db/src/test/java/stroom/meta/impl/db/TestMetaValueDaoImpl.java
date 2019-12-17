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
 *
 */

package stroom.meta.impl.db;

import com.google.inject.Guice;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import stroom.cache.impl.CacheModule;
import stroom.cluster.lock.mock.MockClusterLockModule;
import stroom.collection.mock.MockCollectionModule;
import stroom.dictionary.mock.MockWordListProviderModule;
import stroom.meta.impl.MetaModule;
import stroom.meta.impl.MetaServiceImpl;
import stroom.meta.shared.AttributeMap;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaExpressionUtil;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.MetaProperties;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.security.mock.MockSecurityContextModule;
import stroom.test.common.util.db.TestDbModule;
import stroom.util.date.DateUtil;

import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

class TestMetaValueDaoImpl {
    @Inject
    private Cleanup cleanup;
    @Inject
    private MetaServiceImpl metaService;
    @Inject
    private MetaValueDaoImpl metaValueDao;
    @Inject
    private MetaValueConfig metaValueConfig;

    @BeforeEach
    void setup() {
        Guice.createInjector(
                new MetaModule(),
                new MetaDbModule(),
                new MockClusterLockModule(),
                new MockSecurityContextModule(),
                new MockCollectionModule(),
                new MockWordListProviderModule(),
                new CacheModule(),
                new TestDbModule(),
                new MetaTestModule())
                .injectMembers(this);
        metaValueConfig.setAddAsync(false);
        // Delete everything
        cleanup.clear();
    }

    @AfterEach
    void unsetProperties() {
        metaValueConfig.setAddAsync(true);
    }

    @Test
    void testFind() {
        final Meta meta = metaService.create(createProperties("FEED1"));

        metaService.addAttributes(meta, createAttributes());

        FindMetaCriteria criteria = new FindMetaCriteria();
        criteria.obtainSelectedIdSet().add(meta.getId());
        criteria.setExpression(MetaExpressionUtil.createSimpleExpression(MetaFields.CREATE_TIME, Condition.EQUALS, DateUtil.createNormalDateTimeString(meta.getCreateMs())));

        assertThat(metaService.find(criteria).size()).isEqualTo(1);

        criteria = new FindMetaCriteria();
        criteria.obtainSelectedIdSet().add(meta.getId());
        criteria.setExpression(MetaExpressionUtil.createSimpleExpression(MetaFields.CREATE_TIME, Condition.EQUALS, DateUtil.createNormalDateTimeString(0L)));

        assertThat(metaService.find(criteria).size()).isEqualTo(0);

        criteria = new FindMetaCriteria();
        criteria.obtainSelectedIdSet().add(meta.getId());
        criteria.setExpression(MetaExpressionUtil.createSimpleExpression(MetaFields.FILE_SIZE, Condition.GREATER_THAN, "0"));

        assertThat(metaService.find(criteria).size()).isEqualTo(1);

        criteria = new FindMetaCriteria();
        criteria.obtainSelectedIdSet().add(meta.getId());
        criteria.setExpression(MetaExpressionUtil.createSimpleExpression(MetaFields.FILE_SIZE, Condition.BETWEEN, "0,1000000"));

        assertThat(metaService.find(criteria).size()).isEqualTo(1);
    }

    @Test
    void testDeleteOld() {
        final Meta meta = metaService.create(createProperties("FEED1"));

        metaService.addAttributes(meta, createAttributes());

        FindMetaCriteria criteria = new FindMetaCriteria();
        criteria.obtainSelectedIdSet().add(meta.getId());
        criteria.setExpression(MetaExpressionUtil.createSimpleExpression(MetaFields.CREATE_TIME, Condition.EQUALS, DateUtil.createNormalDateTimeString(meta.getCreateMs())));

        assertThat(metaService.find(criteria).size()).isEqualTo(1);

        criteria = new FindMetaCriteria();
        criteria.obtainSelectedIdSet().add(meta.getId());
        criteria.setExpression(MetaExpressionUtil.createSimpleExpression(MetaFields.CREATE_TIME, Condition.EQUALS, DateUtil.createNormalDateTimeString(0L)));

        assertThat(metaService.find(criteria).size()).isEqualTo(0);

        criteria = new FindMetaCriteria();
        criteria.obtainSelectedIdSet().add(meta.getId());
        criteria.setExpression(MetaExpressionUtil.createSimpleExpression(MetaFields.FILE_SIZE, Condition.GREATER_THAN, "0"));

        assertThat(metaService.find(criteria).size()).isEqualTo(1);

        criteria = new FindMetaCriteria();
        criteria.obtainSelectedIdSet().add(meta.getId());
        criteria.setExpression(MetaExpressionUtil.createSimpleExpression(MetaFields.FILE_SIZE, Condition.BETWEEN, "0,1000000"));

        assertThat(metaService.find(criteria).size()).isEqualTo(1);

        metaValueDao.deleteOldValues();

        criteria = new FindMetaCriteria();
        criteria.obtainSelectedIdSet().add(meta.getId());
        criteria.setExpression(MetaExpressionUtil.createSimpleExpression(MetaFields.FILE_SIZE, Condition.BETWEEN, "0,1000000"));

        assertThat(metaService.find(criteria).size()).isEqualTo(0);
    }

    private MetaProperties createProperties(final String feedName) {
        return new MetaProperties.Builder()
                .createMs(1000L)
                .feedName(feedName)
                .processorUuid("12345")
                .pipelineUuid("PIPELINE_UUID")
                .typeName("TEST_STREAM_TYPE")
                .build();
    }

    private AttributeMap createAttributes() {
        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put(MetaFields.FILE_SIZE.getName(), "100");
        return attributeMap;
    }
}
