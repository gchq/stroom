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

import stroom.docref.DocAuditEntry;
import stroom.docref.DocRef;
import stroom.util.shared.ResultPage;

import java.util.List;
import java.util.Optional;

public interface DocFinder {

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
    default List<DocRef> findByName(final String type,
                                    final String nameFilter) {
        return findByName(type, nameFilter, false);
    }

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
    default List<DocRef> findByNames(final String type,
                                     final List<String> nameFilters) {
        return findByNames(type, nameFilters, false);
    }

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

    Optional<String> getName(DocRef docRef);

    default DocRef decorate(final DocRef docRef) {
        return getName(docRef).map(name -> docRef.copy().name(name).build()).orElse(docRef);
    }

    default Optional<DocRef> decorateIfExists(final DocRef docRef) {
        return getName(docRef).map(name -> docRef.copy().name(name).build());
    }

    ResultPage<DocAuditEntry> getAuditInfo(DocRef docRef);
}
