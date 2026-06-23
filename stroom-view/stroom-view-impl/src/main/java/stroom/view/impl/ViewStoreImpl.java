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

package stroom.view.impl;

import stroom.docref.DocRef;
import stroom.docstore.api.AbstractDocumentStore;
import stroom.docstore.api.DependencyRemapFunction;
import stroom.docstore.api.StoreFactory;
import stroom.security.api.SecurityContext;
import stroom.view.api.ViewStore;
import stroom.view.shared.ViewDoc;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
class ViewStoreImpl
        extends AbstractDocumentStore<ViewDoc>
        implements ViewStore {

    private final SecurityContext securityContext;

    @Inject
    ViewStoreImpl(final StoreFactory storeFactory,
                  final ViewSerialiser serialiser,
                  final SecurityContext securityContext) {
        super(storeFactory,
                serialiser,
                ViewDoc.TYPE,
                ViewDoc::builder,
                ViewDoc::copy);
        this.securityContext = securityContext;
    }

    @Override
    public DocRef createDocument(final String name) {
        final DocRef docRef = getStore().createDocument(name);

        // Read and write as a processing user to ensure we are allowed as documents do not have permissions added to
        // them until after they are created in the store.
        securityContext.asProcessingUser(() -> {
            final ViewDoc dashboardDoc = getStore().readDocument(docRef);
            getStore().writeDocument(dashboardDoc);
        });
        return docRef;
    }

    @Override
    protected DependencyRemapFunction<ViewDoc> getDependencyRemapFunction() {
        return (doc, dependencyRemapper) -> {
            final ViewDoc.Builder builder = doc.copy();
            if (doc.getDataSource() != null) {
                builder.dataSource(dependencyRemapper.remap(doc.getDataSource()));
            }
            if (doc.getPipeline() != null) {
                builder.pipeline(dependencyRemapper.remap(doc.getPipeline()));
            }
            return builder.build();
        };
    }
}
