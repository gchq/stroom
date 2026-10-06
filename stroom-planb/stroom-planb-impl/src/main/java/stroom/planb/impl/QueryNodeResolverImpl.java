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

package stroom.planb.impl;

import stroom.docref.DocRef;
import stroom.planb.impl.fs.SharedFileStore;
import stroom.planb.shared.AbstractHttpStoreSettings;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.PlanBDocument;
import stroom.planb.shared.SnapshotSettings;
import stroom.query.api.QueryNodeResolver;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.List;
import java.util.Set;

public class QueryNodeResolverImpl implements QueryNodeResolver {

    private final PlanBDocCache planBDocCache;
    private final Provider<PlanBConfig> configProvider;
    private final Set<String> planBDocumentTypes;

    @Inject
    public QueryNodeResolverImpl(final PlanBDocCache planBDocCache,
                                 final Provider<PlanBConfig> configProvider,
                                 @PlanBDocumentTypes final Set<String> planBDocumentTypes) {
        this.planBDocCache = planBDocCache;
        this.configProvider = configProvider;
        this.planBDocumentTypes = planBDocumentTypes;
    }

    // STROOMWORKS-LOCAL: KEEP LOCAL ON MERGE FROM master. This method is upstream's; the change here
    // is that it applies to every registered Plan B document type rather than to PlanBDoc alone,
    // because this fork adds one (FloorMapEventStoreDoc). origin/master's version tests
    // `PlanBDoc.TYPE.equals(...)` and returns null for anything else, so an incoming version will
    // silently stop pinning our store's queries — which is not an error but a quiet change to
    // reading stale snapshots instead of live data. Keep this side, or re-apply the generalisation.
    //
    // Behaviour for PlanBDoc is deliberately untouched: the shared-file-store test below is guarded
    // so it cannot apply to it.

    /// Pins the query to the node that holds the store, unless snapshots of it are pushed to every node.
    ///
    /// Applies to any document type registered as a Plan B store through [PlanBDocumentTypes]
    /// whose data is node-local. A store kept on a **shared file store** is excluded, because every
    /// node can already reach it and there is therefore no node to pin to — that is what excludes a
    /// traces store, and it is the reason rather than the type that matters.
    ///
    /// Returning null does not mean the query fails on another node: it falls back to that node's
    /// snapshot of the store, which is fetched by UUID and works for any type. It means the query is
    /// answered from a periodically refreshed copy rather than from the live store, which is the right
    /// default only where the caller has asked for it.
    @Override
    public String getNode(final DocRef docRef) {
        if (docRef == null) {
            return null;
        }
        final String type = docRef.getType();
        // PlanBDoc is named explicitly as well as looked up, so upstream's own type keeps working
        // even if the multibinding is ever missing.
        if (!PlanBDoc.TYPE.equals(type) && !NullSafe.set(planBDocumentTypes).contains(type)) {
            return null;
        }

        final PlanBDocument doc = planBDocCache.get(docRef.getName());

        // Guarded on type so that PlanBDoc reaches the same outcome it always did.
        if (!PlanBDoc.TYPE.equals(type) && (doc == null || SharedFileStore.isConfigured(doc))) {
            return null;
        }

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
