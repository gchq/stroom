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

package stroom.docstore.impl;

import stroom.docref.DocRef;
import stroom.docstore.api.RWLockFactory;
import stroom.util.PredicateUtil;
import stroom.util.shared.NullSafe;
import stroom.util.string.PatternUtil;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public interface Persistence {

    boolean exists(DocRef docRef);

    void delete(DocRef docRef);

    Map<String, byte[]> read(DocRef docRef) throws IOException;

    void write(DocRef docRef, boolean update, Map<String, byte[]> data) throws IOException;

    List<DocRef> list(String type);

    RWLockFactory getLockFactory();

    /**
     * Find docRefs by name and type. Name can be optionally wild carded using '*' to match 0-many chars.
     */
    default List<DocRef> find(final String type,
                              final String nameFilter,
                              final boolean allowWildCards) {
        // Default impl that does all filtering in java. Not efficient for DB impls.
        return nameFilter == null
                ? Collections.emptyList()
                : find(type, List.of(nameFilter), allowWildCards);
    }

    /**
     * Find docRefs by type and one or more nameFilters.
     * nameFilters can be optionally wild carded using '*' to match 0-many chars.
     */
    default List<DocRef> find(final String type,
                              final List<String> nameFilters,
                              final boolean allowWildCards) {
        // Default impl that does all filtering in java. Not efficient for DB impls.
        if (NullSafe.isEmptyCollection(nameFilters)) {
            return Collections.emptyList();
        } else {
            // Merge the filters into one predicate
            final Predicate<DocRef> combinedPredicate = nameFilters.stream()
                    .map(nameFilter -> {
                        final Predicate<DocRef> predicate;
                        if (allowWildCards && PatternUtil.containsWildCards(nameFilter)) {
                            final Pattern pattern = PatternUtil.createPatternFromWildCardFilter(
                                    nameFilter, true);
                            predicate = docRef ->
                                    pattern.matcher(docRef.getName()).matches();
                        } else {
                            predicate = docRef ->
                                    nameFilter.equals(docRef.getName());
                        }
                        return predicate;
                    })
                    .reduce(PredicateUtil::orPredicates)
                    .orElse(val -> false); // no filters, no matches

            return list(type)
                    .stream()
                    .filter(combinedPredicate)
                    .collect(Collectors.toList());
        }
    }
}
