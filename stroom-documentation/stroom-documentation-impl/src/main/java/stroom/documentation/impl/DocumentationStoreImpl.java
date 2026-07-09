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

package stroom.documentation.impl;

import stroom.docref.DocRef;
import stroom.docstore.api.AbstractDocumentStore;
import stroom.docstore.api.StoreFactory;
import stroom.documentation.shared.DocumentationDoc;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;

@Singleton
public class DocumentationStoreImpl
        extends AbstractDocumentStore<DocumentationDoc>
        implements DocumentationStore {

    @Inject
    public DocumentationStoreImpl(final StoreFactory storeFactory,
                                  final DocumentationSerialiser documentationSerialiser) {
        super(storeFactory,
                documentationSerialiser,
                DocumentationDoc.TYPE,
                DocumentationDoc::builder,
                DocumentationDoc::copy);
    }

    @Override
    public void remapDependencies(final DocRef docRef,
                                  final Map<DocRef, DocRef> remappings) {
        // No-op: documentation docs have no dependencies.
    }
}
