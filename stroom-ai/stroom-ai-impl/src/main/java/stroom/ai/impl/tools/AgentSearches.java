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

import stroom.docref.DocRef;
import stroom.query.api.DestroyReason;
import stroom.query.api.FindResultStoreCriteria;
import stroom.query.api.QueryKey;
import stroom.query.api.SearchRequestSource;
import stroom.query.common.v2.ResultStoreManager;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

/// Stops the searches the agent's tools start, so none outlives the tool call that started it. Stroom keeps a
/// search running for up to a day by default, and a tool that gives up waiting (e.g. at its time limit) would
/// otherwise leave it running, e.g. through every entry of an index whose streams have all been deleted, finding
/// nothing. Never used on the user's own searches.
class AgentSearches {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AgentSearches.class);

    private final Provider<ResultStoreManager> resultStoreManagerProvider;

    @Inject
    AgentSearches(final Provider<ResultStoreManager> resultStoreManagerProvider) {
        this.resultStoreManagerProvider = resultStoreManagerProvider;
    }

    /// Stops a search the agent started and removes its results, once they have been read (or can't be).
    ///
    /// @param queryKey The search, or null if it didn't start.
    void destroy(final QueryKey queryKey) {
        if (queryKey == null) {
            return;
        }
        try {
            resultStoreManagerProvider.get().destroy(queryKey, DestroyReason.NO_LONGER_NEEDED);
        } catch (final RuntimeException e) {
            LOGGER.warn(() -> "Unable to stop the agent's search " + queryKey + ": " + e.getMessage(), e);
        }
    }

    /// Stops and removes the searches made for an owner the agent's tool made up for them (a dashboard UUID no
    /// dashboard has): for when the search's key was never read, e.g. the first request failed after Stroom
    /// started the search. Only the user's own searches are found.
    ///
    /// @param ownerUuid The owner's UUID, as the search's request source gave it.
    void destroyOwnedBy(final String ownerUuid) {
        try {
            final ResultStoreManager manager = resultStoreManagerProvider.get();
            NullSafe.list(manager.find(new FindResultStoreCriteria()).getValues()).stream()
                    .filter(info -> ownerUuid.equals(NullSafe.get(info.getSearchRequestSource(),
                            SearchRequestSource::getOwnerDocRef, DocRef::getUuid)))
                    .forEach(info -> manager.destroy(info.getQueryKey(), DestroyReason.NO_LONGER_NEEDED));
        } catch (final RuntimeException e) {
            LOGGER.warn(() -> "Unable to stop the agent's searches for " + ownerUuid + ": " + e.getMessage(), e);
        }
    }

    /// Stops a search the agent started, keeping the results it has found so far to be read again.
    ///
    /// @param queryKey The search, or null if it didn't start.
    void terminate(final QueryKey queryKey) {
        if (queryKey == null) {
            return;
        }
        try {
            resultStoreManagerProvider.get().terminate(queryKey);
        } catch (final RuntimeException e) {
            LOGGER.warn(() -> "Unable to stop the agent's search " + queryKey + ": " + e.getMessage(), e);
        }
    }
}
