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

package stroom.ai.impl.tools;

import stroom.docstore.api.DocFinder;
import stroom.feed.shared.FeedDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

/// Checks View on a feed known only by its name, as processing tasks and their summaries name their feeds.
///
/// A feed the user can't view is treated exactly as one that doesn't exist, and every check fails closed.
class FeedAccess {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FeedAccess.class);

    private final Provider<DocFinder> docFinderProvider;
    private final DocumentAccess documentAccess;

    /// @param docFinderProvider Finds feeds by name.
    /// @param documentAccess    Checks View on each feed found.
    @Inject
    FeedAccess(final Provider<DocFinder> docFinderProvider, final DocumentAccess documentAccess) {
        this.docFinderProvider = docFinderProvider;
        this.documentAccess = documentAccess;
    }

    /// @param name A feed's name.
    /// @return True if the user can view a feed of this name. False if not, if the name is blank, or if it can't
    /// be found (failing closed).
    boolean canView(final String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        try {
            return docFinderProvider.get().findByName(FeedDoc.TYPE, name).stream()
                    .anyMatch(documentAccess::canView);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to find the feed " + name, e);
            return false;
        }
    }
}
