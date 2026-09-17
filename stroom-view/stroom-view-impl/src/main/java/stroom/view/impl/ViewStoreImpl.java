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

package stroom.view.impl;

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

    @Inject
    ViewStoreImpl(final StoreFactory storeFactory,
                  final SecurityContext securityContext,
                  final ViewSerialiser serialiser) {
        super(storeFactory,
                securityContext,
                serialiser,
                ViewDoc.TYPE,
                ViewDoc::builder,
                ViewDoc::copy);
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
