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

import stroom.analytics.shared.AbstractAnalyticRuleDoc;
import stroom.analytics.shared.AbstractAnalyticRuleDoc.AbstractAnalyticRuleDocBuilder;
import stroom.analytics.shared.NotificationConfig;
import stroom.analytics.shared.NotificationStreamDestination;
import stroom.docstore.api.DependencyRemapper;
import stroom.util.shared.NullSafe;

import java.util.List;

/// Remaps the feed references held by analytic rules and reports, i.e. the error feed and the
/// destination feed of each stream notification.
final class AnalyticFeedDependencies {

    private AnalyticFeedDependencies() {
        // Utility class
    }

    /// Remap the error feed and notification destination feeds of `doc` onto `builder`, recording
    /// each one as a dependency.
    ///
    /// @param doc                The document to read the feed references from.
    /// @param builder            A builder for the same document, which receives the remapped references.
    /// @param dependencyRemapper The remapper that records dependencies and supplies any remappings.
    static void remap(final AbstractAnalyticRuleDoc doc,
                      final AbstractAnalyticRuleDocBuilder<?, ?> builder,
                      final DependencyRemapper dependencyRemapper) {
        builder.errorFeed(dependencyRemapper.remap(doc.getErrorFeed()));
        final List<NotificationConfig> notifications = doc.getNotifications();
        if (NullSafe.hasItems(notifications)) {
            builder.notifications(notifications
                    .stream()
                    .map(notification -> remapNotification(notification, dependencyRemapper))
                    .toList());
        }
    }

    private static NotificationConfig remapNotification(final NotificationConfig notification,
                                                        final DependencyRemapper dependencyRemapper) {
        if (notification != null
            && notification.getDestination() instanceof final NotificationStreamDestination streamDestination) {
            final NotificationStreamDestination remappedDestination = streamDestination
                    .copy()
                    .destinationFeed(dependencyRemapper.remap(streamDestination.getDestinationFeed()))
                    .build();
            return notification.copy().destination(remappedDestination).build();
        }
        return notification;
    }
}
