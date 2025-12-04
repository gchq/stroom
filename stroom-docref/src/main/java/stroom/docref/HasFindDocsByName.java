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

package stroom.docref;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public interface HasFindDocsByName {

    /**
     * @return A list of all known and readable docRefs.
     */
    Set<DocRef> listDocuments();

    /**
     * Find by exact case-sensitive match on the name
     */
    default List<DocRef> findByName(final String name) {
        // GWT so no List.of()
        return name != null
                ? findByNames(Collections.singletonList(name), false)
                : Collections.emptyList();
    }

    /**
     * Find by case-sensitive match on the name.
     * If allowWildCards is true '*' can be used to denote a 0-many char wild card.
     */
    default List<DocRef> findByName(final String name, final boolean allowWildCards) {
        // GWT so no List.of()
        return name != null
                ? findByNames(Collections.singletonList(name), allowWildCards)
                : Collections.emptyList();
    }

    /**
     * Find by case-sensitive match on the names.
     * If allowWildCards is true '*' can be used to denote a 0-many char wild card.
     * Finds all docRefs associated with any of the names, i.e. an OR.
     */
    List<DocRef> findByNames(List<String> names, boolean allowWildCards);
}
