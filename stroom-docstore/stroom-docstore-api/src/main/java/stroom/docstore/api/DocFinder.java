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

import stroom.docref.DocRef;

import java.util.List;
import java.util.Optional;

public interface DocFinder {

    /**
     * Find by case-sensitive match on the name.
     * Names may not be unique for a given type, so a non-wild carded nameFilter may return
     * more than one {@link DocRef}.
     * <p>Delegates to {@link #findByName(String, String, boolean)} with {@code allowWildCards = false}.</p>
     *
     * @param type       Can be null. If null all handlers will be searched.
     * @param nameFilter The name of the {@link DocRef}s to filter by, using an exact
     *                   case-sensitive match.
     * @return A list of {@link DocRef}s whose name matches the given filter.
     */
    default List<DocRef> findByName(final String type,
                                    final String nameFilter) {
        return findByName(type, nameFilter, false);
    }

    /**
     * Find by case-sensitive match on the name.
     * If {@code allowWildCards} is true, '*' can be used to denote a 0-many character wild card.
     * Names may not be unique for a given type, so a non-wild carded nameFilter may return
     * more than one {@link DocRef}.
     *
     * @param type           Can be null. If null all handlers will be searched.
     * @param nameFilter     The name of the {@link DocRef}s to filter by. If {@code allowWildCards}
     *                       is true find all matching, else find those with an exact
     *                       case-sensitive name match.
     * @param allowWildCards If true, treat '*' in {@code nameFilter} as a wild card.
     * @return A list of {@link DocRef}s whose name matches the given filter.
     */
    List<DocRef> findByName(String type,
                            String nameFilter,
                            boolean allowWildCards);

    /**
     * Find by case-sensitive match on the name.
     * Names may not be unique for a given type, so a non-wild carded nameFilter may return
     * more than one {@link DocRef}. Applies all nameFilters using an OR, i.e. returns all docRefs
     * associated with any of the passed nameFilters.
     * <p>Delegates to {@link #findByNames(String, List, boolean)} with {@code allowWildCards = false}.</p>
     *
     * @param type        The {@link DocRef} type. Mandatory.
     * @param nameFilters The names of the {@link DocRef}s to filter by, using exact
     *                    case-sensitive matching.
     * @return A list of {@link DocRef}s whose name matches any of the given filters.
     */
    default List<DocRef> findByNames(final String type,
                                     final List<String> nameFilters) {
        return findByNames(type, nameFilters, false);
    }

    /**
     * Find by case-sensitive match on the name.
     * If {@code allowWildCards} is true, '*' can be used to denote a 0-many character wild card.
     * Names may not be unique for a given type, so a non-wild carded nameFilter may return
     * more than one {@link DocRef}. Applies all nameFilters using an OR, i.e. returns all docRefs
     * associated with any of the passed nameFilters.
     *
     * @param type           The {@link DocRef} type. Mandatory.
     * @param nameFilters    The names of the {@link DocRef}s to filter by. If {@code allowWildCards}
     *                       is true find all matching, else find those with an exact
     *                       case-sensitive name match.
     * @param allowWildCards If true, treat '*' in the name filters as a wild card.
     * @return A list of {@link DocRef}s whose name matches any of the given filters.
     */
    List<DocRef> findByNames(String type,
                             List<String> nameFilters,
                             boolean allowWildCards);

    /**
     * Get the name associated with the given {@link DocRef}.
     *
     * @param docRef The {@link DocRef} to look up.
     * @return An {@link Optional} containing the name if the document exists, or empty otherwise.
     */
    Optional<String> getName(DocRef docRef);

    /**
     * Return a copy of the given {@link DocRef} with its name populated from the store.
     * If the document cannot be found, the original {@code docRef} is returned unchanged.
     *
     * @param docRef The {@link DocRef} to decorate.
     * @return A {@link DocRef} with the name set, or the original if the document is not found.
     */
    default DocRef decorate(final DocRef docRef) {
        return getName(docRef).map(name -> docRef.copy().name(name).build()).orElse(docRef);
    }

    /**
     * Return a copy of the given {@link DocRef} with its name populated from the store,
     * only if the document exists.
     *
     * @param docRef The {@link DocRef} to decorate.
     * @return An {@link Optional} containing the decorated {@link DocRef} if the document exists,
     * or empty otherwise.
     */
    default Optional<DocRef> decorateIfExists(final DocRef docRef) {
        return getName(docRef).map(name -> docRef.copy().name(name).build());
    }
}
