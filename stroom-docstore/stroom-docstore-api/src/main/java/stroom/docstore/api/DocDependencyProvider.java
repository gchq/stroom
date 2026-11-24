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

package stroom.docstore.api;

import stroom.docref.DocRef;

import java.util.Set;

/// Supplies the dependencies of the documents of one type, for rebuilding the stored dependency
/// edges. Every [AbstractDocumentStore] is one.
///
/// Register an implementation with [DocDependencyProviderBinder]. [DocumentStoreBinder] does this
/// for every document store it binds.
public interface DocDependencyProvider {

    /// @return The document type this provider supplies dependencies for.
    String getType();

    /// Read a document and extract its dependencies.
    ///
    /// @param docRef The document to read.
    /// @return The document's dependencies, or an empty set if this document type tracks none.
    Set<DocRef> getDependencies(DocRef docRef);
}
