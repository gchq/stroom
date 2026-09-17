/*
 * Copyright 2025 Crown Copyright
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

package stroom.planb.impl;

import stroom.docref.DocRef;
import stroom.planb.shared.AbstractHttpStoreSettings;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.PlanBDocument;
import stroom.planb.shared.SnapshotSettings;
import stroom.query.api.QueryNodeResolver;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.List;

public class QueryNodeResolverImpl implements QueryNodeResolver {

    private final PlanBDocCache planBDocCache;
    private final Provider<PlanBConfig> configProvider;

    @Inject
    public QueryNodeResolverImpl(final PlanBDocCache planBDocCache,
                                 final Provider<PlanBConfig> configProvider) {
        this.planBDocCache = planBDocCache;
        this.configProvider = configProvider;
    }

    /**
     * Pins the query to the node that holds the store, unless snapshots of it are pushed to every node.
     *
     * <p>Only applies to {@link PlanBDoc}. A traces store is read through the shared file store, which
     * every node can reach, and its non-shared-file-store fallbacks locate the data themselves, so
     * there is no node to pin a trace query to.
     */
    @Override
    public String getNode(final DocRef docRef) {
        if (docRef == null || !PlanBDoc.TYPE.equals(docRef.getType())) {
            return null;
        }

        final PlanBDocument doc = planBDocCache.get(docRef.getName());
        final SnapshotSettings snapshotSettings = AbstractHttpStoreSettings.snapshotSettings(
                NullSafe.get(doc, PlanBDocument::getSettings));
        if (snapshotSettings.isUseSnapshotsForQuery()) {
            return null;
        }

        final List<String> nodes = configProvider.get().getNodeList();
        if (NullSafe.isEmptyCollection(nodes)) {
            return null;
        }

        return nodes.getFirst();
    }
}
