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

import stroom.cache.impl.CacheModule;
import stroom.cluster.lock.mock.MockClusterLockModule;
import stroom.collection.mock.MockCollectionModule;
import stroom.dictionary.mock.MockWordListProviderModule;
import stroom.docrefinfo.mock.MockDocRefInfoModule;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.MetaProperties;
import stroom.meta.impl.MetaValueDao;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaExpressionUtil;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.SelectionSummary;
import stroom.meta.shared.Status;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.security.mock.MockSecurityContextModule;
import stroom.test.common.util.db.DbTestModule;
import stroom.util.shared.ResultPage;

import com.google.inject.Guice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

class TestMetaDaoImpl {
    private static final String RAW_STREAM_TYPE_NAME = "RAW_TEST_STREAM_TYPE";
    private static final String PROCESSED_STREAM_TYPE_NAME = "TEST_STREAM_TYPE";
    private static final String TEST1_FEED_NAME = "TEST1";
    private static final String TEST2_FEED_NAME = "TEST2";

    @Inject
    private Cleanup cleanup;
    @Inject
    private MetaDaoImpl metaDao;
    @Inject
    private MetaValueDao metaValueDao;
    @Inject
    private MetaDbConnProvider metaDbConnProvider;

    @BeforeEach
    void setup() {
        Guice.createInjector(
                new MetaTestModule(),
                new MetaDbModule(),
                new MockClusterLockModule(),
                new MockSecurityContextModule(),
                new MockCollectionModule(),
                new MockDocRefInfoModule(),
                new MockWordListProviderModule(),
                new CacheModule(),
                new DbTestModule())
                .injectMembers(this);
        // Delete everything`
        cleanup.cleanup();

        // Add some test data.
        for (int i = 0; i < 10; i++) {
            final Meta parent = metaDao.create(createRawProperties(TEST1_FEED_NAME));
            Meta myMeta = metaDao.create(createProcessedProperties(parent, TEST1_FEED_NAME));

            AttributeMap attributeMap = new AttributeMap();
            attributeMap.put(MetaFields.REC_READ.getName(), "" + 100 * i);
            attributeMap.put(MetaFields.REC_WRITE.getName(), "" + 10 * i);
            metaValueDao.addAttributes(myMeta, attributeMap);
        }
        for (int i = 0; i < 10; i++) {
            final Meta parent = metaDao.create(createRawProperties(TEST2_FEED_NAME));
            Meta myMeta = metaDao.create(createProcessedProperties(parent, TEST2_FEED_NAME));
            AttributeMap attributeMap = new AttributeMap();
            attributeMap.put(MetaFields.REC_READ.getName(), "" + 1000 * i);
            attributeMap.put(MetaFields.REC_WRITE.getName(), "" + 100 * i);
            metaValueDao.addAttributes(myMeta, attributeMap);
        }

        metaValueDao.flush();
        // Unlock all streams.
        metaDao.updateStatus(new FindMetaCriteria(new ExpressionOperator.Builder().build()), Status.LOCKED, Status.UNLOCKED, System.currentTimeMillis());
    }

    @Test
    void testFind() {
        setup();

        ResultPage<Meta> resultPage = metaDao.find(new FindMetaCriteria(MetaExpressionUtil.createFeedExpression(TEST1_FEED_NAME)));
        assertThat(resultPage.size()).isEqualTo(20);

        resultPage = metaDao.find(new FindMetaCriteria(MetaExpressionUtil.createFeedExpression(TEST2_FEED_NAME)));
        assertThat(resultPage.size()).isEqualTo(20);

        resultPage = metaDao.find(new FindMetaCriteria(MetaExpressionUtil.createFeedsExpression(TEST1_FEED_NAME, TEST2_FEED_NAME)));
        assertThat(resultPage.size()).isEqualTo(40);

        resultPage = metaDao.find(new FindMetaCriteria(MetaExpressionUtil.createFeedsExpression()));
        assertThat(resultPage.size()).isEqualTo(0);

        final ExpressionOperator expression = new ExpressionOperator.Builder(Op.AND)
                .addTerm(MetaFields.FEED_NAME, Condition.EQUALS, TEST1_FEED_NAME)
                .addTerm(MetaFields.TYPE_NAME, Condition.EQUALS, RAW_STREAM_TYPE_NAME)
                .build();
        resultPage = metaDao.find(new FindMetaCriteria(expression));
        assertThat(resultPage.size()).isEqualTo(10);
    }

    @Test
    void testExtendedFind() {
        setup();

        ResultPage<Meta> resultPage = metaDao.find(new FindMetaCriteria(MetaExpressionUtil.createFeedExpression(TEST1_FEED_NAME)));
        assertThat(resultPage.size()).isEqualTo(20);

        resultPage = metaDao.find(new FindMetaCriteria(MetaExpressionUtil.createFeedExpression(TEST2_FEED_NAME)));
        assertThat(resultPage.size()).isEqualTo(20);

        resultPage = metaDao.find(new FindMetaCriteria(MetaExpressionUtil.createFeedsExpression(TEST1_FEED_NAME, TEST2_FEED_NAME)));
        assertThat(resultPage.size()).isEqualTo(40);

        resultPage = metaDao.find(new FindMetaCriteria(MetaExpressionUtil.createFeedsExpression()));
        assertThat(resultPage.size()).isEqualTo(0);

        final ExpressionOperator expression = new ExpressionOperator.Builder(Op.AND)
                .addOperator(new ExpressionOperator.Builder(Op.OR)
                        .addTerm(MetaFields.FEED_NAME, Condition.EQUALS, TEST1_FEED_NAME)
                        .addTerm(MetaFields.FEED_NAME, Condition.EQUALS, TEST2_FEED_NAME)
                        .build())
                .addTerm(MetaFields.TYPE_NAME, Condition.EQUALS, PROCESSED_STREAM_TYPE_NAME)
                .addTerm(MetaFields.REC_WRITE.getName(), Condition.EQUALS, "0")
                .addTerm(MetaFields.REC_READ.getName(), Condition.GREATER_THAN_OR_EQUAL_TO, "0")
                .build();

        System.err.println("About to find...");
        resultPage = metaDao.find(new FindMetaCriteria(expression));
        assertThat(resultPage.size()).isEqualTo(2);
    }

    @Test
    void testFindReprocess() {
        setup();

        ResultPage<Meta> resultPage = metaDao.findReprocess(new FindMetaCriteria(MetaExpressionUtil.createFeedExpression(TEST1_FEED_NAME)));
        assertThat(resultPage.size()).isEqualTo(10);

        resultPage = metaDao.findReprocess(new FindMetaCriteria(MetaExpressionUtil.createFeedExpression(TEST2_FEED_NAME)));
        assertThat(resultPage.size()).isEqualTo(10);

        resultPage = metaDao.findReprocess(new FindMetaCriteria(MetaExpressionUtil.createFeedsExpression(TEST1_FEED_NAME, TEST2_FEED_NAME)));
        assertThat(resultPage.size()).isEqualTo(20);

        resultPage = metaDao.findReprocess(new FindMetaCriteria(MetaExpressionUtil.createFeedsExpression()));
        assertThat(resultPage.size()).isEqualTo(0);

        final ExpressionOperator expression = new ExpressionOperator.Builder(Op.AND)
                .addOperator(new ExpressionOperator.Builder(Op.OR)
                        .addTerm(MetaFields.FEED_NAME, Condition.EQUALS, TEST1_FEED_NAME)
                        .addTerm(MetaFields.FEED_NAME, Condition.EQUALS, TEST2_FEED_NAME)
                        .build())
                .addTerm(MetaFields.TYPE_NAME, Condition.EQUALS, RAW_STREAM_TYPE_NAME)
                .build();
        resultPage = metaDao.findReprocess(new FindMetaCriteria(expression));
        assertThat(resultPage.size()).isEqualTo(0);
    }

    @Test
    void testGetSelectionSummary() {
        setup();

        SelectionSummary selectionSummary = metaDao.getSelectionSummary(new FindMetaCriteria(MetaExpressionUtil.createFeedExpression(TEST1_FEED_NAME)));
        assertThat(selectionSummary.getItemCount()).isEqualTo(20);

        selectionSummary = metaDao.getSelectionSummary(new FindMetaCriteria(MetaExpressionUtil.createFeedExpression(TEST2_FEED_NAME)));
        assertThat(selectionSummary.getItemCount()).isEqualTo(20);

        selectionSummary = metaDao.getSelectionSummary(new FindMetaCriteria(MetaExpressionUtil.createFeedsExpression(TEST1_FEED_NAME, TEST2_FEED_NAME)));
        assertThat(selectionSummary.getItemCount()).isEqualTo(40);

        selectionSummary = metaDao.getSelectionSummary(new FindMetaCriteria(MetaExpressionUtil.createFeedsExpression()));
        assertThat(selectionSummary.getItemCount()).isEqualTo(0);

        final ExpressionOperator expression = new ExpressionOperator.Builder(Op.AND)
                .addTerm(MetaFields.FEED_NAME, Condition.EQUALS, TEST1_FEED_NAME)
                .addTerm(MetaFields.TYPE_NAME, Condition.EQUALS, RAW_STREAM_TYPE_NAME)
                .build();
        selectionSummary = metaDao.getSelectionSummary(new FindMetaCriteria(expression));
        assertThat(selectionSummary.getItemCount()).isEqualTo(10);
    }

    @Test
    void testGetReprocessSelectionSummary() {
        setup();

        SelectionSummary selectionSummary = metaDao.getReprocessSelectionSummary(new FindMetaCriteria(MetaExpressionUtil.createFeedExpression(TEST1_FEED_NAME)));
        assertThat(selectionSummary.getItemCount()).isEqualTo(10);

        selectionSummary = metaDao.getReprocessSelectionSummary(new FindMetaCriteria(MetaExpressionUtil.createFeedExpression(TEST2_FEED_NAME)));
        assertThat(selectionSummary.getItemCount()).isEqualTo(10);

        selectionSummary = metaDao.getReprocessSelectionSummary(new FindMetaCriteria(MetaExpressionUtil.createFeedsExpression(TEST1_FEED_NAME, TEST2_FEED_NAME)));
        assertThat(selectionSummary.getItemCount()).isEqualTo(20);

        selectionSummary = metaDao.getReprocessSelectionSummary(new FindMetaCriteria(MetaExpressionUtil.createFeedsExpression()));
        assertThat(selectionSummary.getItemCount()).isEqualTo(0);

        final ExpressionOperator expression = new ExpressionOperator.Builder(Op.AND)
                .addTerm(MetaFields.FEED_NAME, Condition.EQUALS, TEST1_FEED_NAME)
                .addTerm(MetaFields.TYPE_NAME, Condition.EQUALS, RAW_STREAM_TYPE_NAME)
                .build();
        selectionSummary = metaDao.getReprocessSelectionSummary(new FindMetaCriteria(expression));
        assertThat(selectionSummary.getItemCount()).isEqualTo(0);
    }

    private MetaProperties createRawProperties(final String feedName) {
        return new MetaProperties.Builder()
                .createMs(System.currentTimeMillis())
                .feedName(feedName)
                .typeName(RAW_STREAM_TYPE_NAME)
                .build();
    }

    private MetaProperties createProcessedProperties(final Meta parent, final String feedName) {
        return new MetaProperties.Builder()
                .parent(parent)
                .createMs(System.currentTimeMillis())
                .feedName(feedName)
                .processorUuid("12345")
                .pipelineUuid("PIPELINE_UUID")
                .typeName(PROCESSED_STREAM_TYPE_NAME)
                .build();
    }
}
