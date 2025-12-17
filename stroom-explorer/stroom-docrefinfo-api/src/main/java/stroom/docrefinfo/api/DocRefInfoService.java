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

package stroom.docrefinfo.api;

import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;

import java.util.List;
import java.util.Optional;

public interface DocRefInfoService extends DocRefDecorator {

    /**
     * @return A list of all known and readable docRefs for the given type.
     */
    List<DocRef> findByType(String type);

    Optional<DocRefInfo> info(DocRef docRef);

    Optional<DocRefInfo> info(String uuid);

    Optional<String> name(DocRef docRef);

    /**
     * Find by case-sensitive match on the name.
     * If allowWildCards is true '*' can be used to denote a 0-many char wild card.
     * Names may not be unique for a given type, so a non-wild carded nameFilter may return
     * more than one {@link DocRef}.
     *
     * @param type       Can be null. If null all handlers will be searched
     * @param nameFilter The name of the {@link DocRef}s to filter by. If allowWildCards is true
     *                   find all matching else find those with an exact case-sensitive name match.
     */
    List<DocRef> findByName(String type,
                            String nameFilter,
                            boolean allowWildCards);

    /**
     * Find by case-sensitive match on the name.
     * If allowWildCards is true '*' can be used to denote a 0-many char wild card.
     * Names may not be unique for a given type, so a non-wild carded nameFilter may return
     * more than one {@link DocRef}. Applies all nameFilters using an OR, i.e. returns all docRefs
     * associated with any of the passed nameFilters.
     *
     * @param type        The {@link DocRef} type. Mandatory.
     * @param nameFilters The names of the {@link DocRef}s to filter by. If allowWildCards is true
     *                    find all matching else find those with an exact case-sensitive name match.
     */
    List<DocRef> findByNames(String type,
                             List<String> nameFilters,
                             boolean allowWildCards);
}
