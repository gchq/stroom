/*
 * Copyright 2020 Crown Copyright
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
package stroom.importexport.api;

import stroom.docref.DocRef;


public interface NonExplorerDocRefProvider {
    /**
     * Find a docref in the explorer tree that is nearest to the provided non-explorer based docref,
     * so that a suitable location can be found, e.g. for keeping serialised content together
     * @param docref the non-explorer based docref known to this instance
     * @return an explorer docref that is located in a suitable location for association with the supplied docref
     * (or null if no suitable explorer docref is found)
     */
    DocRef findNearestExplorerDocRef(final DocRef docref);

    /**
     * Allows an alternative name to be provided for a docref
     * @param docRef the non-explorer based docref known to this instance
     * @return A string that represents a suitable name for this docref.
     */
    String findNameOfDocRef(final DocRef docRef);

    /**
     * Check whether this DocRef is already known to this instance
     * @param docRef non-explorer based docref that might be known to this instance
     * @return true if the non-explorer based docref is known to this instance
     */
    boolean docExists (DocRef docRef);
}
