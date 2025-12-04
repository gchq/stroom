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

package stroom.query.shared;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum QueryHelpType {
    /**
     * A datasource, e.g a Lucene Index or a Searchable
     */
    DATA_SOURCE,
    /**
     * A field within a datasource that can be queried. Only known once a datasource has been defined.
     */
    QUERYABLE_FIELD,
    /**
     * A field within a datasource. Only known once a datasource has been defined.
     */
    FIELD,
    /**
     * An expression function, e.g. concat()
     */
    FUNCTION,
    /**
     * Not really a category of help items. A title item for a category/group of help items.
     */
    TITLE,
    /**
     * Structural element of a StroomQL query, e.g. 'from', 'select', etc.
     */
    STRUCTURE,
    /**
     * A Visualisation Doc.
     */
    VISUALISATION,
    /**
     * A dictionary Doc.
     */
    DICTIONARY;

    public static final Set<QueryHelpType> ALL_TYPES = Collections.unmodifiableSet(EnumSet.allOf(QueryHelpType.class));

    public static final Set<QueryHelpType> NO_TYPES = Collections.unmodifiableSet(EnumSet.noneOf(QueryHelpType.class));
}
