package stroom.planb.impl.pipeline;

import stroom.pipeline.refdata.LookupIdentifier;
import stroom.pipeline.refdata.ReferenceDataResult;
import stroom.pipeline.refdata.store.MapDefinition;
import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.pipeline.xsltfunctions.PlanBLookup;
import stroom.planb.impl.PlanBDocCache;
import stroom.planb.impl.data.GetRequest;
import stroom.planb.impl.data.PlanBQueryService;
import stroom.planb.impl.data.TemporalState;
import stroom.planb.shared.PlanBDoc;
import stroom.security.api.SecurityContext;
import stroom.util.pipeline.scope.PipelineScoped;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@PipelineScoped
public class PlanBLookupImpl implements PlanBLookup {

    private final PlanBDocCache stateDocCache;
    private final Cache<GetRequest, Optional<TemporalState>> cache;
    private final Map<String, Optional<PlanBDoc>> stateDocMap = new HashMap<>();
    private final SecurityContext securityContext;
    private final PlanBQueryService planBQueryService;

    @Inject
    public PlanBLookupImpl(final PlanBDocCache stateDocCache,
                           final SecurityContext securityContext,
                           final PlanBQueryService planBQueryService) {
        this.stateDocCache = stateDocCache;
        this.securityContext = securityContext;
        this.planBQueryService = planBQueryService;
        cache = Caffeine.newBuilder().maximumSize(1000).build();
    }

    @Override
    public void lookup(final LookupIdentifier lookupIdentifier,
                       final ReferenceDataResult result) {
        getValue(
                lookupIdentifier.getPrimaryMapName(),
                lookupIdentifier.getKey(),
                lookupIdentifier.getEventTime(),
                result);
    }

    private void getValue(final String mapName,
                          final String keyName,
                          final long eventTimeMs,
                          final ReferenceDataResult result) {
        final String docName = mapName.toLowerCase(Locale.ROOT);
        final Optional<PlanBDoc> stateOptional = stateDocMap.computeIfAbsent(docName, k ->
                securityContext.useAsReadResult(() ->
                        Optional.ofNullable(stateDocCache.get(docName))));
        stateOptional.ifPresent(stateDoc -> {
            final GetRequest request = new GetRequest(docName, keyName, eventTimeMs);
            final Optional<TemporalState> optional = cache.get(request,
                    k -> Optional.ofNullable(planBQueryService.lookup(k)));

            // If we found a result then add the value.
            if (optional.isPresent()) {
                final TemporalState state = optional.get();
                final RefStreamDefinition refStreamDefinition =
                        new RefStreamDefinition(stateDoc.asDocRef(), "0", -1);
                final MapDefinition mapDefinition = new MapDefinition(refStreamDefinition, docName);
                result.addRefDataValueProxy(new StateValueProxy(state, mapDefinition));
            }
        });
    }
}
