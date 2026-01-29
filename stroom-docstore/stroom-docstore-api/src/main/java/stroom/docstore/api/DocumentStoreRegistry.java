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

package stroom.docstore.api;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Singleton
public class DocumentStoreRegistry {

    private final Map<String, DocumentStore> documentStores;

    @Inject
    public DocumentStoreRegistry(final Set<DocumentStore> documentStores) {
        this.documentStores = documentStores.stream()
                .collect(Collectors.toMap(DocumentStore::getType, Function.identity()));
    }

    public DocumentStore getDocumentStore(final String type) {
        final DocumentStore documentStore = documentStores.get(type);
        if (documentStore == null) {
            throw new RuntimeException("No document store can be found for '" + type + "'");
        }

        return documentStore;
    }
}
