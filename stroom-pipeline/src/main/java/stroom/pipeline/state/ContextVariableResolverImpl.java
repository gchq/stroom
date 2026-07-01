/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.pipeline.state;


import stroom.docref.DocRef;
import stroom.meta.shared.Meta;
import stroom.node.api.NodeInfo;
import stroom.pipeline.writer.ExtendedPathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.string.CIKey;
import stroom.util.string.TemplateUtil.ContextVariableResolver;
import stroom.util.string.TemplateUtil.ExecutorBuilder;

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
 * A replacement of {@link ExtendedPathCreator}
 */
@Singleton
public class ContextVariableResolverImpl implements ContextVariableResolver {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ContextVariableResolverImpl.class);
    private static final CIKey FEED_VAR = CIKey.internStaticKey("feed");
    private static final CIKey PIPELINE_VAR = CIKey.internStaticKey("pipeline");
    private static final CIKey SOURCE_ID_VAR = CIKey.internStaticKey("sourceId");
    // TODO : DEPRECATED ALIAS FOR SOURCE ID.
    private static final CIKey STREAM_ID_VAR = CIKey.internStaticKey("streamId");
    private static final CIKey PART_NO_VAR = CIKey.internStaticKey("partNo");
    // TODO : DEPRECATED ALIAS FOR SOURCE ID.
    private static final CIKey STREAM_NO_VAR = CIKey.internStaticKey("streamNo");
    private static final CIKey SEARCH_ID_VAR = CIKey.internStaticKey("searchId");
    private static final CIKey NODE_VAR = CIKey.internStaticKey("node");

    private final Map<CIKey, Supplier<String>> varToValueSuppliersMap = new HashMap<>();

    @Inject
    ContextVariableResolverImpl(final Provider<FeedHolder> feedHolder,
                                final Provider<PipelineHolder> pipelineHolder,
                                final Provider<MetaHolder> metaHolder,
                                final Provider<SearchIdHolder> searchIdHolder,
                                final Provider<NodeInfo> nodeInfo) {

        addValueSupplier(FEED_VAR, feedHolder, FeedHolder::getFeedName);
        addValueSupplier(PIPELINE_VAR, pipelineHolder, holder ->
                NullSafe.get(holder.getPipeline(), DocRef::getName));

        addValueSupplier(SOURCE_ID_VAR, metaHolder, holder ->
                NullSafe.get(holder.getMeta(), Meta::getId, String::valueOf));
        // TODO : DEPRECATED ALIAS FOR SOURCE ID.
        addValueSupplier(STREAM_ID_VAR, metaHolder, holder ->
                NullSafe.get(holder.getMeta(), Meta::getId, String::valueOf));

        addValueSupplier(PART_NO_VAR, metaHolder, holder -> String.valueOf(holder.getPartNo()));
        // TODO : DEPRECATED ALIAS FOR PART NO.
        addValueSupplier(STREAM_NO_VAR, metaHolder, holder -> String.valueOf(holder.getPartNo()));

        addValueSupplier(SEARCH_ID_VAR, searchIdHolder, SearchIdHolder::getSearchId);
        addValueSupplier(NODE_VAR, nodeInfo, NodeInfo::getThisNodeName);
    }

    private <T> void addValueSupplier(final CIKey var,
                                      final Provider<T> provider,
                                      final Function<T, String> getter) {
        if (provider != null) {
            this.varToValueSuppliersMap.put(var, () ->
                    NullSafe.get(provider.get(), getter));
        } else {
            LOGGER.debug("No provider for var: '{}'", var);
        }
    }

    /**
     * Gets the value for the named field or, an empty if the field is not known or if the value is null
     */
    @Override
    public Optional<String> getVariableValue(final CIKey var) {
        if (CIKey.isBlank(var)) {
            return Optional.empty();
        } else {
            final Supplier<String> valueSupplier = varToValueSuppliersMap.get(var);
            return Optional.ofNullable(valueSupplier)
                    .map(Supplier::get);
        }
    }

    /**
     * Add all the contextual replaces as formerly handled by {@link ExtendedPathCreator#replaceContextVars(String)}
     */
    @Override
    public ExecutorBuilder addContextReplacements(final ExecutorBuilder executorBuilder) {
        if (executorBuilder != null) {
            return executorBuilder.addDynamicReplacementProvider(this::getVariableValue);
        } else {
            return null;
        }
    }
}
