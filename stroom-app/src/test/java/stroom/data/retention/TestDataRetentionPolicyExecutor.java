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

package stroom.data.retention;


import stroom.data.retention.impl.DataRetentionPolicyExecutor;
import stroom.data.retention.impl.DataRetentionRulesService;
import stroom.data.retention.shared.DataRetentionRule;
import stroom.data.retention.shared.DataRetentionRules;
import stroom.data.shared.StreamTypeNames;
import stroom.docref.DocRef;
import stroom.meta.api.MetaProperties;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.Status;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.common.util.test.FileSystemTestUtil;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.time.TimeUnit;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestDataRetentionPolicyExecutor extends AbstractCoreIntegrationTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestDataRetentionPolicyExecutor.class);

    private static final int RETENTION_PERIOD_DAYS = 1;

    @Inject
    private MetaService metaService;
    @Inject
    private DataRetentionPolicyExecutor dataRetentionPolicyExecutor;
    @Inject
    private DataRetentionRulesService dataRetentionRulesService;

    @SuppressWarnings("checkstyle:VariableDeclarationUsageDistance")
    @Test
    void testMultipleRuns() {
        final String feedName1 = FileSystemTestUtil.getUniqueTestString();
        final String feedName2 = FileSystemTestUtil.getUniqueTestString();

        final long now = System.currentTimeMillis();
        final long timeOutsideRetentionPeriod = now - java.util.concurrent.TimeUnit.DAYS.toMillis(RETENTION_PERIOD_DAYS)
                - java.util.concurrent.TimeUnit.MINUTES.toMillis(1);

        LOGGER.info(() -> "now: " + DateUtil.createNormalDateTimeString(now));
        LOGGER.info(() -> "timeOutsideRetentionPeriod: " + DateUtil.createNormalDateTimeString(
                timeOutsideRetentionPeriod));

        // save two streams, one inside retention period, one outside
        final DataRetentionRule rule1 = createRule(1,
                ExpressionOperator.builder()
                        .addTextTerm(MetaFields.FEED, Condition.EQUALS, feedName1)
                        .build(), RETENTION_PERIOD_DAYS, TimeUnit.DAYS);
        final DataRetentionRule rule2 = createForeverRule(2,
                ExpressionOperator.builder()
                        .addTextTerm(MetaFields.FEED, Condition.EQUALS, feedName2)
                        .build());

        final Set<DocRef> docs = dataRetentionRulesService.listDocuments();
        DataRetentionRules dataRetentionRules = null;
        if (docs.size() > 0) {
            dataRetentionRules = dataRetentionRulesService.readDocument(docs.iterator().next());
        }

        if (dataRetentionRules == null) {
            final DocRef docRef = dataRetentionRulesService.createDocument("test");
            dataRetentionRules = dataRetentionRulesService.readDocument(docRef);
        }

        dataRetentionRules.setRules(List.of(rule1, rule2));
        dataRetentionRulesService.writeDocument(dataRetentionRules);

        Meta metaInsideRetention = createMeta(feedName1, now);
        Meta metaOutsideRetention = createMeta(feedName1, timeOutsideRetentionPeriod);
        Meta foreverMeta = createMeta(feedName2, timeOutsideRetentionPeriod);

        dumpStreams();

        Long lastStatusMsInside = metaInsideRetention.getStatusMs();
        Long lastStatusMsOutside = metaOutsideRetention.getStatusMs();
        final Long lastStatusMsForever = foreverMeta.getStatusMs();

        // run the stream retention task which should 'delete' one stream
        dataRetentionPolicyExecutor.exec();

        metaInsideRetention = metaService.getMeta(metaInsideRetention.getId(), true);
        metaOutsideRetention = metaService.getMeta(metaOutsideRetention.getId(), true);
        foreverMeta = metaService.getMeta(foreverMeta.getId(), true);

        dumpStreams();

        assertThat(metaInsideRetention.getStatus()).isEqualTo(Status.UNLOCKED);
        assertThat(metaOutsideRetention.getStatus()).isEqualTo(Status.DELETED);
        assertThat(foreverMeta.getStatus()).isEqualTo(Status.UNLOCKED);

        // no change to the record
        assertThat(metaInsideRetention.getStatusMs()).isEqualTo(lastStatusMsInside);
        assertThat(foreverMeta.getStatusMs()).isEqualTo(lastStatusMsForever);

        // record changed
        assertThat(metaOutsideRetention.getStatusMs() > lastStatusMsOutside).isTrue();

        lastStatusMsInside = metaInsideRetention.getStatusMs();
        lastStatusMsOutside = metaOutsideRetention.getStatusMs();

        // run the task again, but this time no changes should be made as the
        // one outside the retention period is already 'deleted'
        dataRetentionPolicyExecutor.exec();

        metaInsideRetention = metaService.getMeta(metaInsideRetention.getId(), true);
        metaOutsideRetention = metaService.getMeta(metaOutsideRetention.getId(), true);

        dumpStreams();

        assertThat(metaInsideRetention.getStatus()).isEqualTo(Status.UNLOCKED);
        assertThat(metaOutsideRetention.getStatus()).isEqualTo(Status.DELETED);
        // no change to the records
        assertThat(metaInsideRetention.getStatusMs()).isEqualTo(lastStatusMsInside);
        assertThat(metaOutsideRetention.getStatusMs()).isEqualTo(lastStatusMsOutside);
    }

    private Meta createMeta(final String feedName, final long createTime) {
        Meta meta = metaService.create(
                MetaProperties.builder()
                        .feedName(feedName)
                        .typeName(StreamTypeNames.RAW_EVENTS)
                        .createMs(createTime)
                        .statusMs(createTime)
                        .build());

        // Streams are locked initially so unlock.
        meta = metaService.updateStatus(meta, Status.LOCKED, Status.UNLOCKED);

        return meta;
    }

    private DataRetentionRule createRule(final int num,
                                         final ExpressionOperator expression,
                                         final int age,
                                         final TimeUnit timeUnit) {
        return DataRetentionRule.ageRule(num,
                System.currentTimeMillis(),
                "rule " + num,
                true,
                expression,
                age,
                timeUnit);
    }

    private DataRetentionRule createForeverRule(final int num, final ExpressionOperator expression) {
        return DataRetentionRule.foreverRule(num, System.currentTimeMillis(), "rule " + num, true, expression);
    }

    private void dumpStreams() {
        final List<Meta> list = metaService.find(new FindMetaCriteria()).getValues();

        assertThat(list.size()).isEqualTo(3);

        for (final Meta meta : list) {
            LOGGER.info(() -> "meta: " +
                    meta +
                    ", createMs:" +
                    DateUtil.createNormalDateTimeString(meta.getCreateMs()) +
                    ", statusMs: " +
                    DateUtil.createNormalDateTimeString(meta.getStatusMs()) +
                    ", status: " +
                    meta.getStatus());
        }
    }
}
