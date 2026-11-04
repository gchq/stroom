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

import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.NotificationConfig;
import stroom.analytics.shared.NotificationDestinationType;
import stroom.analytics.shared.NotificationEmailDestination;
import stroom.analytics.shared.NotificationStreamDestination;
import stroom.docref.DocRef;
import stroom.docstore.api.DependencyRemapper;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestAnalyticFeedDependencies {

    private static final DocRef ERROR_FEED = new DocRef("Feed", "error-feed-uuid", "ERROR_FEED");
    private static final DocRef DESTINATION_FEED = new DocRef("Feed", "destination-feed-uuid", "DESTINATION_FEED");
    private static final DocRef NEW_DESTINATION_FEED = new DocRef("Feed", "new-destination-uuid", "NEW_DESTINATION");

    @Test
    void testRemap_recordsErrorAndDestinationFeeds() {
        final AnalyticRuleDoc doc = createDoc(ERROR_FEED, List.of(streamNotification(), emailNotification()));
        final DependencyRemapper dependencyRemapper = new DependencyRemapper();

        AnalyticFeedDependencies.remap(doc, doc.copy(), dependencyRemapper);

        assertThat(dependencyRemapper.getDependencies())
                .containsExactlyInAnyOrder(ERROR_FEED, DESTINATION_FEED);
    }

    @Test
    void testRemap_remapsDestinationFeed() {
        final AnalyticRuleDoc doc = createDoc(ERROR_FEED, List.of(streamNotification(), emailNotification()));
        final DependencyRemapper dependencyRemapper = new DependencyRemapper(
                Map.of(DESTINATION_FEED, NEW_DESTINATION_FEED));
        final AnalyticRuleDoc.Builder builder = doc.copy();

        AnalyticFeedDependencies.remap(doc, builder, dependencyRemapper);
        final AnalyticRuleDoc remapped = builder.build();

        assertThat(remapped.getErrorFeed())
                .isEqualTo(ERROR_FEED);
        assertThat(remapped.getNotifications())
                .hasSize(2);
        assertThat(remapped.getNotifications().getFirst().getDestination())
                .isInstanceOfSatisfying(NotificationStreamDestination.class, destination ->
                        assertThat(destination.getDestinationFeed()).isEqualTo(NEW_DESTINATION_FEED));
        assertThat(remapped.getNotifications().get(1))
                .isEqualTo(emailNotification());
        assertThat(dependencyRemapper.isChanged())
                .isTrue();
    }

    @Test
    void testRemap_noFeeds() {
        final AnalyticRuleDoc doc = createDoc(null, List.of(emailNotification()));
        final DependencyRemapper dependencyRemapper = new DependencyRemapper();
        final AnalyticRuleDoc.Builder builder = doc.copy();

        AnalyticFeedDependencies.remap(doc, builder, dependencyRemapper);
        final AnalyticRuleDoc remapped = builder.build();

        assertThat(remapped.getErrorFeed())
                .isNull();
        assertThat(remapped.getNotifications())
                .containsExactly(emailNotification());
        assertThat(dependencyRemapper.getDependencies())
                .isEmpty();
    }

    private static AnalyticRuleDoc createDoc(final DocRef errorFeed, final List<NotificationConfig> notifications) {
        return AnalyticRuleDoc.builder()
                .uuid("rule-uuid")
                .name("Rule")
                .errorFeed(errorFeed)
                .notifications(notifications)
                .build();
    }

    private static NotificationConfig streamNotification() {
        return NotificationConfig.builder()
                .uuid("stream-notification-uuid")
                .destinationType(NotificationDestinationType.STREAM)
                .destination(NotificationStreamDestination.builder()
                        .destinationFeed(DESTINATION_FEED)
                        .build())
                .build();
    }

    private static NotificationConfig emailNotification() {
        return NotificationConfig.builder()
                .uuid("email-notification-uuid")
                .destinationType(NotificationDestinationType.EMAIL)
                .destination(NotificationEmailDestination.builder()
                        .to("someone@example.com")
                        .build())
                .build();
    }
}
