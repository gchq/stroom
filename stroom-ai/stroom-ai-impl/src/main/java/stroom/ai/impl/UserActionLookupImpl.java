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

package stroom.ai.impl;

import stroom.docref.DocRef;
import stroom.docstore.api.DocumentActionHandler;
import stroom.docstore.api.DocumentTypeName;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.shared.ExplorerNode;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.shared.ProcessorFilter;
import stroom.query.impl.QueryService;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/// Looks up Stroom's state for [UserActions], as the current user.
public class UserActionLookupImpl implements UserActionLookup {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(UserActionLookupImpl.class);

    // How far up a pipeline's parents to look for the indexes it writes to.
    private static final int MAX_PIPELINE_DEPTH = 10;
    // The element that writes to Elasticsearch, and its property naming the index it writes to.
    private static final String ELASTIC_INDEXING_FILTER = "ElasticIndexingFilter";
    private static final String INDEX_NAME_PROPERTY = "indexName";

    private final ProcessorFilterService processorFilterService;
    private final ExplorerNodeService explorerNodeService;
    private final SecurityContext securityContext;
    @SuppressWarnings("rawtypes")
    private final Map<DocumentTypeName, DocumentActionHandler> documentActionHandlers;
    private final Provider<QueryService> queryServiceProvider;

    @SuppressWarnings("rawtypes")
    UserActionLookupImpl(final ProcessorFilterService processorFilterService,
                         final ExplorerNodeService explorerNodeService,
                         final SecurityContext securityContext,
                         final Map<DocumentTypeName, DocumentActionHandler> documentActionHandlers) {
        this(processorFilterService, explorerNodeService, securityContext, documentActionHandlers, () -> null);
    }

    @Inject
    @SuppressWarnings("rawtypes")
    UserActionLookupImpl(final ProcessorFilterService processorFilterService,
                         final ExplorerNodeService explorerNodeService,
                         final SecurityContext securityContext,
                         final Map<DocumentTypeName, DocumentActionHandler> documentActionHandlers,
                         final Provider<QueryService> queryServiceProvider) {
        this.queryServiceProvider = queryServiceProvider;
        this.processorFilterService = processorFilterService;
        this.explorerNodeService = explorerNodeService;
        this.securityContext = securityContext;
        this.documentActionHandlers = documentActionHandlers;
    }

    @Override
    public Optional<ProcessorFilter> filter(final int id) {
        try {
            return processorFilterService.fetch(id)
                    .filter(filter -> !filter.isDeleted())
                    .filter(filter -> filter.getPipeline() != null
                                      && securityContext.hasDocumentPermission(filter.getPipeline(),
                            DocumentPermission.VIEW));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to fetch processor filter " + id, e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<DocRef> find(final DocRef docRef) {
        if (!securityContext.hasDocumentPermission(docRef, DocumentPermission.VIEW)) {
            return Optional.empty();
        }
        return explorerNodeService.getNode(docRef)
                .map(ExplorerNode::getDocRef)
                .filter(found -> docRef.getType().equals(found.getType()));
    }

    @Override
    public boolean canEdit(final DocRef docRef) {
        if (docRef == null || docRef.getUuid() == null) {
            return false;
        }
        try {
            return securityContext.hasDocumentPermission(docRef, DocumentPermission.EDIT);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to check Edit on " + docRef, e);
            return false;
        }
    }

    @Override
    public Optional<DocRef> usableDataSource(final String query) {
        final QueryService queryService = queryServiceProvider.get();
        if (queryService == null || query == null || query.isBlank()) {
            return Optional.empty();
        }
        try {
            return queryService.getReferencedDataSource(query)
                    .filter(dataSource -> securityContext.hasDocumentPermission(dataSource, DocumentPermission.USE));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to find the data source of a query", e);
            return Optional.empty();
        }
    }

    @Override
    public Set<String> tags(final DocRef docRef) {
        return explorerNodeService.getNode(docRef)
                .map(ExplorerNode::getTags)
                .map(NullSafe::set)
                .orElse(Set.of());
    }

    @Override
    public Set<String> elasticIndexNames(final DocRef pipeline) {
        final Set<String> names = new HashSet<>();
        final Set<String> seen = new HashSet<>();
        // Each element's type, and the index name each element is given, nearest the pipeline first, as a pipeline
        // sets what it inherits.
        final Map<String, String> elementTypes = new HashMap<>();
        final Map<String, String> indexNames = new HashMap<>();
        DocRef current = pipeline;
        for (int depth = 0; current != null && depth < MAX_PIPELINE_DEPTH && seen.add(current.getUuid()); depth++) {
            final PipelineDoc doc = read(current, PipelineDoc.TYPE, PipelineDoc.class);
            if (doc == null) {
                break;
            }
            for (final DocRef property : doc.getPropertyDocRefs()) {
                if (ElasticIndexDoc.TYPE.equals(property.getType())) {
                    NullSafe.consume(read(property, ElasticIndexDoc.TYPE, ElasticIndexDoc.class),
                            index -> NullSafe.consume(index.getIndexName(), names::add));
                }
            }
            final PipelineData data = doc.getPipelineData();
            if (data != null) {
                for (final PipelineElement element : data.getAddedElements()) {
                    elementTypes.putIfAbsent(element.getId(), element.getType());
                }
                for (final PipelineProperty property : data.getAddedProperties()) {
                    if (INDEX_NAME_PROPERTY.equals(property.getName()) && property.getValue() != null
                        && NullSafe.isNonBlankString(property.getValue().getString())) {
                        indexNames.putIfAbsent(property.getElement(), property.getValue().getString().strip());
                    }
                }
            }
            current = doc.getParentPipeline();
        }
        indexNames.forEach((element, name) -> {
            if (ELASTIC_INDEXING_FILTER.equals(elementTypes.get(element))) {
                names.add(name);
            }
        });
        return names;
    }

    private <T> T read(final DocRef docRef, final String type, final Class<T> clazz) {
        if (!securityContext.hasDocumentPermission(docRef, DocumentPermission.VIEW)) {
            return null;
        }
        final DocumentActionHandler<?> handler = documentActionHandlers.get(new DocumentTypeName(type));
        if (handler == null) {
            return null;
        }
        try {
            final Object doc = handler.readDocument(new DocRef(type, docRef.getUuid()));
            return clazz.isInstance(doc)
                    ? clazz.cast(doc)
                    : null;
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to read " + docRef, e);
            return null;
        }
    }
}
