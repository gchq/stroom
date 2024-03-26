/*
 * Copyright 2016 Crown Copyright
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

package stroom.analytics;

import stroom.analytics.rule.impl.AnalyticRuleStore;
import stroom.analytics.shared.AnalyticNotificationConfig;
import stroom.analytics.shared.AnalyticNotificationDestinationType;
import stroom.analytics.shared.AnalyticNotificationStreamDestination;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.app.guice.CoreModule;
import stroom.app.guice.JerseyModule;
import stroom.app.uri.UriFactoryModule;
import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.Source;
import stroom.data.store.api.SourceUtil;
import stroom.data.store.api.Store;
import stroom.docref.DocRef;
import stroom.index.VolumeTestConfigModule;
import stroom.index.mock.MockIndexShardWriterExecutorModule;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.statistics.impl.MockMetaStatisticsModule;
import stroom.resource.impl.ResourceModule;
import stroom.security.mock.MockSecurityContextModule;
import stroom.test.BootstrapTestModule;
import stroom.test.StroomIntegrationTest;
import stroom.util.shared.ResultPage;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;

import jakarta.inject.Inject;
import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(GuiceExtension.class)
@IncludeModule(UriFactoryModule.class)
@IncludeModule(CoreModule.class)
@IncludeModule(BootstrapTestModule.class)
@IncludeModule(ResourceModule.class)
@IncludeModule(stroom.cluster.impl.MockClusterModule.class)
@IncludeModule(VolumeTestConfigModule.class)
@IncludeModule(MockSecurityContextModule.class)
@IncludeModule(MockMetaStatisticsModule.class)
@IncludeModule(stroom.test.DatabaseTestControlModule.class)
@IncludeModule(JerseyModule.class)
@IncludeModule(MockIndexShardWriterExecutorModule.class)
class AbstractAnalyticsTest extends StroomIntegrationTest {

    static final SimpleDuration INSTANT = SimpleDuration
            .builder()
            .time(0)
            .timeUnit(TimeUnit.MILLISECONDS)
            .build();

    @Inject
    private MetaService metaService;
    @Inject
    private Store streamStore;
    @Inject
    private AnalyticRuleStore analyticRuleStore;
    @Inject
    private AnalyticsDataSetup analyticsDataSetup;

    @BeforeEach
    final void setup() {
        // Delete existing rules.
        analyticRuleStore.list().forEach(docRef -> analyticRuleStore.deleteDocument(docRef.getUuid()));

        // Delete existing detections.
        final ResultPage<Meta> metaList = metaService.find(FindMetaCriteria.createWithType(StreamTypeNames.DETECTIONS));
        for (final Meta meta : metaList.getValues()) {
            metaService.delete(meta.getId());
        }

        // Find out how many streams are left.
        final int streamCount = analyticsDataSetup.getStreamCount();
        if (streamCount == 0) {
            // Do setup if we don't have any streams.
            analyticsDataSetup.setup();
        }
        // Make sure we now have 8 streams.
        analyticsDataSetup.checkStreamCount(8);

        // Make sure there is a detections feed.
        if (analyticsDataSetup.getDetections() == null) {
            throw new NullPointerException("Detections missing");
        }
    }

    @Override
    protected boolean cleanupBetweenTests() {
        return false;
    }

    protected DocRef writeRule(final AnalyticRuleDoc sample) {
        final DocRef alertRuleDocRef = analyticRuleStore.createDocument("Analytic Rule");
        AnalyticRuleDoc analyticRuleDoc = analyticRuleStore.readDocument(alertRuleDocRef);
        analyticRuleDoc = analyticRuleDoc.copy()
                .languageVersion(sample.getLanguageVersion())
                .query(sample.getQuery())
                .analyticProcessType(sample.getAnalyticProcessType())
                .analyticProcessConfig(sample.getAnalyticProcessConfig())
                .analyticNotificationConfig(sample.getAnalyticNotificationConfig())
                .build();
        analyticRuleStore.writeDocument(analyticRuleDoc);
        return alertRuleDocRef;
    }

    protected void testDetectionsStream(final int expectedStreams,
                                        final int expectedRecords) {
        analyticsDataSetup.checkStreamCount(expectedStreams);

        // As we have created alerts ensure we now have more streams.
        final Meta newestMeta = analyticsDataSetup.getNewestMeta();
        try (final Source source = streamStore.openSource(newestMeta.getId())) {
            final String result = SourceUtil.readString(source);
            assertThat(result.split("<detection>").length).isEqualTo(expectedRecords);
            assertThat(result).contains("user5");
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected AnalyticNotificationConfig createNotificationConfig() {
        return AnalyticNotificationConfig
                .builder()
                .destinationType(AnalyticNotificationDestinationType.STREAM)
                .destination(AnalyticNotificationStreamDestination.builder()
                        .destinationFeed(analyticsDataSetup.getDetections())
                        .useSourceFeedIfPossible(false)
                        .build())
                .errorFeed(analyticsDataSetup.getDetections())
                .build();
    }
}
