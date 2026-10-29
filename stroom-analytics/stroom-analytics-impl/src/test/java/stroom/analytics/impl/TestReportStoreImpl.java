/*
 * Copyright 2026 Crown Copyright
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

package stroom.analytics.impl;

import stroom.analytics.shared.NotificationConfig;
import stroom.analytics.shared.NotificationDestinationType;
import stroom.analytics.shared.NotificationStreamDestination;
import stroom.analytics.shared.ReportDoc;
import stroom.docref.DocRef;
import stroom.docstore.api.DependencyRemapper;
import stroom.docstore.api.StoreFactory;
import stroom.query.common.v2.DataSourceProviderRegistry;
import stroom.query.language.SearchRequestFactory;
import stroom.security.api.SecurityContext;

import jakarta.inject.Provider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestReportStoreImpl {

    private static final DocRef ERROR_FEED = new DocRef("Feed", "error-feed-uuid", "ERROR_FEED");
    private static final DocRef DESTINATION_FEED = new DocRef("Feed", "destination-feed-uuid", "DESTINATION_FEED");

    @Mock
    private StoreFactory storeFactory;
    @Mock
    private SecurityContext securityContext;
    @Mock
    private ReportSerialiser serialiser;
    @Mock
    private Provider<ExecutionScheduleDao> executionScheduleDaoProvider;
    @Mock
    private Provider<DataSourceProviderRegistry> dataSourceProviderRegistryProvider;
    @Mock
    private SearchRequestFactory searchRequestFactory;

    @Test
    void testDependencyRemapFunction_recordsErrorAndDestinationFeeds() {
        final ReportStoreImpl reportStore = new ReportStoreImpl(
                storeFactory,
                securityContext,
                serialiser,
                executionScheduleDaoProvider,
                dataSourceProviderRegistryProvider,
                searchRequestFactory);
        final ReportDoc doc = ReportDoc.builder()
                .uuid("report-uuid")
                .name("Report")
                .errorFeed(ERROR_FEED)
                .notifications(List.of(NotificationConfig.builder()
                        .destinationType(NotificationDestinationType.STREAM)
                        .destination(NotificationStreamDestination.builder()
                                .destinationFeed(DESTINATION_FEED)
                                .build())
                        .build()))
                .build();
        final DependencyRemapper dependencyRemapper = new DependencyRemapper();

        reportStore.getDependencyRemapFunction().remap(doc, dependencyRemapper);

        assertThat(dependencyRemapper.getDependencies())
                .containsExactlyInAnyOrder(ERROR_FEED, DESTINATION_FEED);
    }
}
