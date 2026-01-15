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

package stroom.util.shared;

import stroom.docref.DocRef;

import java.util.Map;
import java.util.Set;

public interface HasDependencies {

    /**
     * Get a map of dependencies for all documents.
     *
     * @return A map of dependencies.
     */
    Map<DocRef, Set<DocRef>> getDependencies();

    /**
     * Get a map of dependencies for a document.
     *
     * @param docRef The document to get dependencies for.
     * @return The set of document dependencies for the document.
     */
    Set<DocRef> getDependencies(DocRef docRef);

    /**
     * Remap dependencies for a document.
     *
     * @param docRef     The document to apply dependency remappings to.
     * @param remappings The remappings to apply where relevant.
     */
    void remapDependencies(DocRef docRef, Map<DocRef, DocRef> remappings);
}
