package stroom.planb.impl;

import stroom.docref.DocRef;
import stroom.planb.shared.AbstractPlanBSettings;
import stroom.planb.shared.PlanBDoc;
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

    @Override
    public String getNode(final DocRef docRef) {
        if (docRef == null || !PlanBDoc.TYPE.equals(docRef.getType())) {
            return null;
        }

        final PlanBDoc doc = planBDocCache.get(docRef.getName());
        final SnapshotSettings snapshotSettings = NullSafe.getOrElseGet(
                doc,
                PlanBDoc::getSettings,
                AbstractPlanBSettings::getSnapshotSettings,
                SnapshotSettings::new);
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
