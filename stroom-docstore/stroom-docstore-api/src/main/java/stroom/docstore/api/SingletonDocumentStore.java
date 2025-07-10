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
 *
 */

package stroom.docstore.api;

import stroom.docref.DocRef;
import stroom.docstore.shared.AbstractSingletonDoc;
import stroom.importexport.api.ImportExportActionHandler;

/**
 * A document store for a document type that can only ever have zero or one document.
 * The document will always have the same UUID and name.
 *
 * @param <D>
 */
public interface SingletonDocumentStore<D extends AbstractSingletonDoc>
        extends ImportExportActionHandler, DocumentActionHandler<D>, ContentIndexable {

    /**
     * Get the singleton document or create it if it does not exist.
     */
    D getOrCreate();

    /**
     * @return The fixed UUID for the single instance of this document type
     */
    String getSingletonUuid();

    String getSingletonName();

    DocRef getSingletonDocRef();

    @Override // from ImportExportActionHandler
    default boolean isSingleton() {
        return true;
    }
}
