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

package stroom.sqlstore.impl.pipeline;

import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.pipeline.refdata.LookupIdentifier;
import stroom.pipeline.refdata.ReferenceDataResult;
import stroom.pipeline.refdata.store.MapDefinition;
import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.pipeline.xsltfunctions.SqlStoreLookup;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.sqlstore.api.UpdatableTemporalStore;
import stroom.sqlstore.impl.UpdatableTemporalStoreProvider;
import stroom.util.pipeline.scope.PipelineScoped;
import stroom.util.shared.ResultPage;
import stroom.util.shared.TemporalEntry;

import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.Map;

@PipelineScoped
public class SqlStoreLookupImpl implements SqlStoreLookup {

    private final UpdatableTemporalStoreProvider storeProvider;

    /**
     * Store documents resolved by name, for the life of this pipeline instance.
     *
     * <p>Resolving a name lists the document store - a database query with no cache behind it -
     * and permission-checks every row. This runs once per XSLT {@code lookup()}, so on a stream
     * that looks up per event it ran once per event. The name to document mapping only changes if
     * a store is created, renamed or deleted, so it is resolved once here and reused.</p>
     *
     * <p>Safe to hold for the run because it caches <em>identity</em>, not authorisation or data:
     * {@code find(DocRef, ...)} still permission-checks every call, and no entry values are cached
     * - those depend on the lookup's effective time and would need a temporal cache key to be
     * correct.</p>
     *
     * <p>The class is {@link PipelineScoped}, so this dies with the pipeline instance and cannot
     * go stale across runs.</p>
     */
    private final Map<String, DocRef> resolvedStores = new HashMap<>();

    @Inject
    public SqlStoreLookupImpl(final UpdatableTemporalStoreProvider storeProvider) {
        this.storeProvider = storeProvider;
    }

    @Override
    public void lookup(final LookupIdentifier lookupIdentifier, final ReferenceDataResult result) {
        final String mapName = lookupIdentifier.getPrimaryMapName();
        final String key = lookupIdentifier.getKey();
        final long eventTimeMs = lookupIdentifier.getEventTime();

        try {
            final UpdatableTemporalStore store = storeProvider.get(mapName);
            //noinspection unused k
            final DocRef storeDocRef = resolvedStores.computeIfAbsent(
                    mapName, k -> store.resolveStoreByName(mapName));

            // Build query criteria
            final ExpressionOperator expression = ExpressionOperator.builder()
                    .addTerm(UpdatableTemporalStore.MAP_FIELD.getFldName(), ExpressionTerm.Condition.EQUALS, mapName)
                    .addTerm(UpdatableTemporalStore.KEY_FIELD.getFldName(), ExpressionTerm.Condition.EQUALS, key)
                    .addTerm(UpdatableTemporalStore.TIME_FIELD.getFldName(), ExpressionTerm.Condition.EQUALS,
                            String.valueOf(eventTimeMs))
                    .build();

            final ExpressionCriteria criteria = new ExpressionCriteria(expression);
            final ResultPage<TemporalEntry> resultPage = store.find(storeDocRef, criteria);

            if (resultPage != null && !resultPage.getValues().isEmpty()) {
                final TemporalEntry entry = resultPage.getValues().getFirst();
                if (entry.getValue() != null) {
                    final RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                            new stroom.docref.DocRef(stroom.sqlstore.shared.SqlTemporalStoreDoc.TYPE, "", mapName),
                            "0",
                            -1
                    );
                    final MapDefinition mapDefinition = new MapDefinition(refStreamDefinition, mapName);
                    result.addRefDataValueProxy(new SqlStoreValueProxy(
                            entry.getKey(), entry.getValue(), mapDefinition));
                }
            }
        } catch (final Exception e) {
            // Ignore lookup exceptions during pipelining (similar to other lookup providers)
        }
    }
}
