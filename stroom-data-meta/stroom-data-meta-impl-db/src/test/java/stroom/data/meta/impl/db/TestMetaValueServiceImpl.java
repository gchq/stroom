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

package stroom.data.meta.impl.db;

import com.google.inject.Guice;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import stroom.data.meta.shared.AttributeMap;
import stroom.data.meta.shared.Meta;
import stroom.data.meta.shared.MetaProperties;
import stroom.data.meta.shared.ExpressionUtil;
import stroom.data.meta.shared.FindMetaCriteria;
import stroom.data.meta.shared.MetaDataSource;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.security.impl.mock.MockSecurityContextModule;
import stroom.util.date.DateUtil;

import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

class TestMetaValueServiceImpl {
    @Inject
    private DataMetaServiceImpl dataMetaService;
    @Inject
    private MetaValueServiceImpl metaValueService;
    @Inject
    private MetaValueConfig metaValueConfig;

    @BeforeEach
    void setup() {
        Guice.createInjector(new DataMetaDbModule(), new MockSecurityContextModule()).injectMembers(this);
        metaValueConfig.setAddAsync(false);
    }

    @AfterEach
    void unsetProperties() {
        metaValueConfig.setAddAsync(true);
    }

    @Test
    void testFind() {
        // Delete everything
        dataMetaService.deleteAll();
        metaValueService.deleteAll();

        final Meta data = dataMetaService.create(createProperties("FEED1"));

        dataMetaService.addAttributes(data, createAttributes());

        FindMetaCriteria criteria = new FindMetaCriteria();
        criteria.obtainSelectedIdSet().add(data.getId());
        criteria.setExpression(ExpressionUtil.createSimpleExpression(MetaDataSource.CREATE_TIME, Condition.EQUALS, DateUtil.createNormalDateTimeString(data.getCreateMs())));

        assertThat(dataMetaService.find(criteria).size()).isEqualTo(1);

        criteria = new FindMetaCriteria();
        criteria.obtainSelectedIdSet().add(data.getId());
        criteria.setExpression(ExpressionUtil.createSimpleExpression(MetaDataSource.CREATE_TIME, Condition.EQUALS, DateUtil.createNormalDateTimeString(0L)));

        assertThat(dataMetaService.find(criteria).size()).isEqualTo(0);

        criteria = new FindMetaCriteria();
        criteria.obtainSelectedIdSet().add(data.getId());
        criteria.setExpression(ExpressionUtil.createSimpleExpression(MetaDataSource.FILE_SIZE, Condition.GREATER_THAN, "0"));

        assertThat(dataMetaService.find(criteria).size()).isEqualTo(1);

        criteria = new FindMetaCriteria();
        criteria.obtainSelectedIdSet().add(data.getId());
        criteria.setExpression(ExpressionUtil.createSimpleExpression(MetaDataSource.FILE_SIZE, Condition.BETWEEN, "0,1000000"));

        assertThat(dataMetaService.find(criteria).size()).isEqualTo(1);
    }

    @Test
    void testDeleteOld() {
        // Delete everything
        dataMetaService.deleteAll();
        metaValueService.deleteAll();

        final Meta data = dataMetaService.create(createProperties("FEED1"));

        dataMetaService.addAttributes(data, createAttributes());

        FindMetaCriteria criteria = new FindMetaCriteria();
        criteria.obtainSelectedIdSet().add(data.getId());
        criteria.setExpression(ExpressionUtil.createSimpleExpression(MetaDataSource.CREATE_TIME, Condition.EQUALS, DateUtil.createNormalDateTimeString(data.getCreateMs())));

        assertThat(dataMetaService.find(criteria).size()).isEqualTo(1);

        criteria = new FindMetaCriteria();
        criteria.obtainSelectedIdSet().add(data.getId());
        criteria.setExpression(ExpressionUtil.createSimpleExpression(MetaDataSource.CREATE_TIME, Condition.EQUALS, DateUtil.createNormalDateTimeString(0L)));

        assertThat(dataMetaService.find(criteria).size()).isEqualTo(0);

        criteria = new FindMetaCriteria();
        criteria.obtainSelectedIdSet().add(data.getId());
        criteria.setExpression(ExpressionUtil.createSimpleExpression(MetaDataSource.FILE_SIZE, Condition.GREATER_THAN, "0"));

        assertThat(dataMetaService.find(criteria).size()).isEqualTo(1);

        criteria = new FindMetaCriteria();
        criteria.obtainSelectedIdSet().add(data.getId());
        criteria.setExpression(ExpressionUtil.createSimpleExpression(MetaDataSource.FILE_SIZE, Condition.BETWEEN, "0,1000000"));

        assertThat(dataMetaService.find(criteria).size()).isEqualTo(1);

        metaValueService.deleteOldValues();

        criteria = new FindMetaCriteria();
        criteria.obtainSelectedIdSet().add(data.getId());
        criteria.setExpression(ExpressionUtil.createSimpleExpression(MetaDataSource.FILE_SIZE, Condition.BETWEEN, "0,1000000"));

        assertThat(dataMetaService.find(criteria).size()).isEqualTo(0);
    }

    private MetaProperties createProperties(final String feedName) {
        return new MetaProperties.Builder()
                .createMs(1000L)
                .feedName(feedName)
                .pipelineUuid("PIPELINE_UUID")
                .processorId(1)
                .typeName("TEST_STREAM_TYPE")
                .build();
    }

    private AttributeMap createAttributes() {
        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put(MetaDataSource.FILE_SIZE, "100");
        return attributeMap;
    }
}
