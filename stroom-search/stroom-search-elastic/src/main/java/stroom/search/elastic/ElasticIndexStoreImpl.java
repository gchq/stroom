/*
 * Copyright 2017 Crown Copyright
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

package stroom.search.elastic;

import stroom.docref.DocRef;
import stroom.docstore.api.AbstractDocumentStore;
import stroom.docstore.api.DependencyRemapFunction;
import stroom.docstore.api.StoreFactory;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.security.api.SecurityContext;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ElasticIndexStoreImpl
        extends AbstractDocumentStore<ElasticIndexDoc>
        implements ElasticIndexStore {

    private final ElasticIndexService elasticIndexService;

    @Inject
    public ElasticIndexStoreImpl(
            final StoreFactory storeFactory,
            final SecurityContext securityContext,
            final ElasticIndexService elasticIndexService,
            final ElasticIndexSerialiser serialiser) {
        super(storeFactory,
                securityContext,
                serialiser,
                ElasticIndexDoc.TYPE,
                ElasticIndexDoc::builder,
                ElasticIndexDoc::copy);
        this.elasticIndexService = elasticIndexService;
    }

    // super, not getStore(), in both: the base applies this type's VIEW and EDIT checks, and
    // getStore() is the deliberately unchecked handle.

    @Override
    public ElasticIndexDoc readDocument(final DocRef docRef) {
        final ElasticIndexDoc doc = super.readDocument(docRef);
        return doc.copy().fields(elasticIndexService.getFields(doc)).build();
    }

    @Override
    public ElasticIndexDoc writeDocument(final ElasticIndexDoc document) {
        return super.writeDocument(document.copy().fields(elasticIndexService.getFields(document)).build());
    }

    @Override
    protected DependencyRemapFunction<ElasticIndexDoc> getDependencyRemapFunction() {
        return (doc, dependencyRemapper) -> {
            dependencyRemapper.remap(doc.getClusterRef());
            dependencyRemapper.remap(doc.getVectorGenerationModelRef());
            dependencyRemapper.remap(doc.getRerankModelRef());
            return doc;
        };
    }
}
