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

package stroom.streamstore.meta.db;

import com.google.inject.Guice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import stroom.properties.impl.mock.MockPropertyModule;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.security.impl.mock.MockSecurityContextModule;
import stroom.streamstore.meta.api.FindStreamCriteria;
import stroom.streamstore.meta.api.Stream;
import stroom.streamstore.meta.api.StreamProperties;
import stroom.streamstore.shared.ExpressionUtil;
import stroom.streamstore.shared.StreamDataSource;
import stroom.util.date.DateUtil;

import javax.inject.Inject;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestMetaValueServiceImpl {
    @Inject
    private StreamMetaServiceImpl streamMetaService;
    @Inject
    private MetaValueServiceImpl metaValueService;
    @Inject
    private MetaValueConfig metaValueConfig;

    @BeforeEach
    void setup() {
        Guice.createInjector(new StreamStoreMetaDbModule(), new MockSecurityContextModule(), new MockPropertyModule()).injectMembers(this);
        metaValueConfig.setAddAsync(false);
    }

    @Test
    void testFind() {
        // Delete everything
        streamMetaService.deleteAll();
        metaValueService.deleteAll();

        final Stream stream = streamMetaService.createStream(createProperties("FEED1"));

        streamMetaService.addAttributes(stream, createAttributes());

        FindStreamCriteria criteria = new FindStreamCriteria();
        criteria.obtainSelectedIdSet().add(stream.getId());
        criteria.setExpression(ExpressionUtil.createSimpleExpression(StreamDataSource.CREATE_TIME, Condition.EQUALS, DateUtil.createNormalDateTimeString(stream.getCreateMs())));

        assertThat(streamMetaService.find(criteria).size()).isEqualTo(1);

        criteria = new FindStreamCriteria();
        criteria.obtainSelectedIdSet().add(stream.getId());
        criteria.setExpression(ExpressionUtil.createSimpleExpression(StreamDataSource.CREATE_TIME, Condition.EQUALS, DateUtil.createNormalDateTimeString(0L)));

        assertThat(streamMetaService.find(criteria).size()).isEqualTo(0);

        criteria = new FindStreamCriteria();
        criteria.obtainSelectedIdSet().add(stream.getId());
        criteria.setExpression(ExpressionUtil.createSimpleExpression(StreamDataSource.FILE_SIZE, Condition.GREATER_THAN, "0"));

        assertThat(streamMetaService.find(criteria).size()).isEqualTo(1);

        criteria = new FindStreamCriteria();
        criteria.obtainSelectedIdSet().add(stream.getId());
        criteria.setExpression(ExpressionUtil.createSimpleExpression(StreamDataSource.FILE_SIZE, Condition.BETWEEN, "0,1000000"));

        assertThat(streamMetaService.find(criteria).size()).isEqualTo(1);
    }

    @Test
    void testDeleteOld() {
        // Delete everything
        streamMetaService.deleteAll();
        metaValueService.deleteAll();

        final Stream stream = streamMetaService.createStream(createProperties("FEED1"));

        streamMetaService.addAttributes(stream, createAttributes());

        FindStreamCriteria criteria = new FindStreamCriteria();
        criteria.obtainSelectedIdSet().add(stream.getId());
        criteria.setExpression(ExpressionUtil.createSimpleExpression(StreamDataSource.CREATE_TIME, Condition.EQUALS, DateUtil.createNormalDateTimeString(stream.getCreateMs())));

        assertThat(streamMetaService.find(criteria).size()).isEqualTo(1);

        criteria = new FindStreamCriteria();
        criteria.obtainSelectedIdSet().add(stream.getId());
        criteria.setExpression(ExpressionUtil.createSimpleExpression(StreamDataSource.CREATE_TIME, Condition.EQUALS, DateUtil.createNormalDateTimeString(0L)));

        assertThat(streamMetaService.find(criteria).size()).isEqualTo(0);

        criteria = new FindStreamCriteria();
        criteria.obtainSelectedIdSet().add(stream.getId());
        criteria.setExpression(ExpressionUtil.createSimpleExpression(StreamDataSource.FILE_SIZE, Condition.GREATER_THAN, "0"));

        assertThat(streamMetaService.find(criteria).size()).isEqualTo(1);

        criteria = new FindStreamCriteria();
        criteria.obtainSelectedIdSet().add(stream.getId());
        criteria.setExpression(ExpressionUtil.createSimpleExpression(StreamDataSource.FILE_SIZE, Condition.BETWEEN, "0,1000000"));

        assertThat(streamMetaService.find(criteria).size()).isEqualTo(1);

        metaValueService.deleteOldValues();

        criteria = new FindStreamCriteria();
        criteria.obtainSelectedIdSet().add(stream.getId());
        criteria.setExpression(ExpressionUtil.createSimpleExpression(StreamDataSource.FILE_SIZE, Condition.BETWEEN, "0,1000000"));

        assertThat(streamMetaService.find(criteria).size()).isEqualTo(0);
    }

    private StreamProperties createProperties(final String feedName) {
        return new StreamProperties.Builder()
                .createMs(1000L)
                .feedName(feedName)
                .pipelineUuid("PIPELINE_UUID")
                .streamProcessorId(1)
                .streamTypeName("TEST_STREAM_TYPE")
                .build();
    }

    private Map<String, String> createAttributes() {
        return Collections.singletonMap(StreamDataSource.FILE_SIZE, "100");
    }
}
