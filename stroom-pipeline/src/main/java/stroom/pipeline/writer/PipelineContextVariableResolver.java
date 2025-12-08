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

package stroom.pipeline.writer;


import stroom.docref.DocRef;
import stroom.meta.shared.Meta;
import stroom.node.api.NodeInfo;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.MetaHolder;
import stroom.pipeline.state.PipelineHolder;
import stroom.pipeline.state.SearchIdHolder;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Provides a means to lookup pipeline scoped values by their field name.
 */
@Singleton
public class PipelineContextVariableResolver {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PipelineContextVariableResolver.class);

    private final Map<String, Supplier<String>> varToValueSuppliersMap = new HashMap<>();

    @Inject
    PipelineContextVariableResolver(final Provider<FeedHolder> feedHolder,
                                    final Provider<PipelineHolder> pipelineHolder,
                                    final Provider<MetaHolder> metaHolder,
                                    final Provider<SearchIdHolder> searchIdHolder,
                                    final Provider<NodeInfo> nodeInfo) {

        addValueSupplier("feed", feedHolder, FeedHolder::getFeedName);
        addValueSupplier("pipeline", pipelineHolder, holder ->
                NullSafe.get(holder.getPipeline(), DocRef::getName));

        addValueSupplier("sourceId", metaHolder, holder ->
                NullSafe.get(holder.getMeta(), Meta::getId, String::valueOf));
        // TODO : DEPRECATED ALIAS FOR SOURCE ID.
        addValueSupplier("streamId", metaHolder, holder ->
                NullSafe.get(holder.getMeta(), Meta::getId, String::valueOf));

        addValueSupplier("partNo", metaHolder, holder -> String.valueOf(holder.getPartNo()));
        // TODO : DEPRECATED ALIAS FOR PART NO.
        addValueSupplier("streamNo", metaHolder, holder -> String.valueOf(holder.getPartNo()));

        addValueSupplier("searchId", searchIdHolder, SearchIdHolder::getSearchId);

        addValueSupplier("node", nodeInfo, NodeInfo::getThisNodeName);
    }

    private <T> void addValueSupplier(final String var,
                                      final Provider<T> provider,
                                      final Function<T, String> getter) {
        if (provider != null) {
            this.varToValueSuppliersMap.put(var, () ->
                    NullSafe.get(provider, Provider::get, getter));
        } else {
            LOGGER.warn("No provider for var: '{}'", var);
        }
    }

    /**
     * Gets the value for the named field or, an empty if the field is not known or if the value is null
     */
    public Optional<String> getVariableValue(final String var) {
        if (NullSafe.isBlankString(var)) {
            return Optional.empty();
        } else {
            final Supplier<String> valueSupplier = varToValueSuppliersMap.get(var);
            return Optional.ofNullable(valueSupplier)
                    .map(Supplier::get);
        }
    }
}
