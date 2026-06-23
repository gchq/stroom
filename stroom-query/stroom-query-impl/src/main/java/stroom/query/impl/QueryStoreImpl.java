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

package stroom.query.impl;

import stroom.docref.DocRef;
import stroom.docstore.api.AbstractDocumentStore;
import stroom.docstore.api.DependencyRemapFunction;
import stroom.docstore.api.StoreFactory;
import stroom.query.common.v2.DataSourceProviderRegistry;
import stroom.query.language.SearchRequestFactory;
import stroom.query.shared.QueryDoc;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Objects;
import java.util.Optional;

@Singleton
class QueryStoreImpl
        extends AbstractDocumentStore<QueryDoc>
        implements QueryStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(QueryStoreImpl.class);

    private final SecurityContext securityContext;
    private final Provider<DataSourceProviderRegistry> dataSourceProviderRegistryProvider;
    private final SearchRequestFactory searchRequestFactory;

    @Inject
    QueryStoreImpl(final StoreFactory storeFactory,
                   final QuerySerialiser serialiser,
                   final SecurityContext securityContext,
                   final Provider<DataSourceProviderRegistry> dataSourceProviderRegistryProvider,
                   final SearchRequestFactory searchRequestFactory) {
        super(storeFactory,
                serialiser,
                QueryDoc.TYPE,
                QueryDoc::builder,
                QueryDoc::copy);
        this.securityContext = securityContext;
        this.dataSourceProviderRegistryProvider = dataSourceProviderRegistryProvider;
        this.searchRequestFactory = searchRequestFactory;
    }

    @Override
    public DocRef createDocument(final String name) {
        final DocRef docRef = getStore().createDocument(name);

        // Read and write as a processing user to ensure we are allowed as documents do not have permissions added to
        // them until after they are created in the store.
        securityContext.asProcessingUser(() -> {
            final QueryDoc dashboardDoc = getStore().readDocument(docRef);
            getStore().writeDocument(dashboardDoc);
        });
        return docRef;
    }

    @Override
    protected DependencyRemapFunction<QueryDoc> getDependencyRemapFunction() {
        return (doc, dependencyRemapper) -> {
            final QueryDoc.Builder builder = doc.copy();
            try {
                if (doc.getQuery() != null) {
                    searchRequestFactory.extractDataSourceOnly(doc.getQuery(), docRef -> {
                        try {
                            if (docRef != null) {
                                final DataSourceProviderRegistry dataSourceProviderRegistry =
                                        dataSourceProviderRegistryProvider.get();
                                final Optional<DocRef> optional = dataSourceProviderRegistry
                                        .getDataSourceDocRefs()
                                        .stream()
                                        .filter(dr -> dr.equals(docRef))
                                        .findAny();
                                optional.ifPresent(dataSourceRef -> {
                                    final DocRef remapped = dependencyRemapper.remap(dataSourceRef);
                                    if (remapped != null) {
                                        String query = doc.getQuery();
                                        if (remapped.getName() != null &&
                                            !remapped.getName().isBlank() &&
                                            !Objects.equals(remapped.getName(), docRef.getName())) {
                                            query = query.replaceFirst(docRef.getName(), remapped.getName());
                                        }
                                        if (remapped.getUuid() != null &&
                                            !remapped.getUuid().isBlank() &&
                                            !Objects.equals(remapped.getUuid(), docRef.getUuid())) {
                                            query = query.replaceFirst(docRef.getUuid(), remapped.getUuid());
                                        }
                                        builder.query(query);
                                    }
                                });
                            }
                        } catch (final RuntimeException e) {
                            LOGGER.debug(e::getMessage, e);
                        }
                    });
                }
            } catch (final RuntimeException e) {
                LOGGER.debug(e::getMessage, e);
            }
            return builder.build();
        };
    }
}
