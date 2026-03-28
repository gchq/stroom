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

package stroom.importexport.api;

import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;

public interface NonExplorerDocRefProvider {

    DocRef getOwnerDocument(DocRef docRef,
                            ImportExportDocument importExportDocument);

    /**
     * Find a docref in the explorer tree that is nearest to the provided non-explorer based docref,
     * so that a suitable location can be found, e.g. for keeping serialised content together
     *
     * @param docref the non-explorer based docref known to this instance
     * @return an explorer docref that is located in a suitable location for association with the supplied docref
     * (or null if no suitable explorer docref is found)
     */
    DocRef findNearestExplorerDocRef(final DocRef docref);

    /**
     * Allows an alternative name to be provided for a docref
     *
     * @param docRef the non-explorer based docref known to this instance
     * @return A string that represents a suitable name for this docref.
     */
    String findNameOfDocRef(final DocRef docRef);

    /**
     * Retrieve the audit information for a particular doc ref
     *
     * @param docRef The docRef to return the information for
     * @return The Audit information about the given DocRef.
     */
    DocRefInfo info(DocRef docRef);
}
