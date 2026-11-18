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

package stroom.query.language;

import stroom.query.api.JoinKey;
import stroom.query.api.JoinType;
import stroom.query.api.JoinWindow;
import stroom.query.api.Query;
import stroom.query.api.token.AbstractToken;

import java.util.List;

/// A join line while [SearchRequestFactory] parses it. Its alias is set once its source is known, and
/// its keys and window once its on clause is parsed.
final class PendingJoin {

    // The join keyword group, for pointing errors at.
    final AbstractToken token;
    final JoinType type;
    // The joined source's search, to which where conditions may be added.
    final Query.Builder queryBuilder;
    // The bracketed query of the join line, or null for a data source.
    final ParsedQuery bracketedQuery;
    String alias;
    // The join line's sql clause, for pointing errors at, or null if it has none.
    AbstractToken sqlToken;
    List<JoinKey> keys;
    JoinWindow window;

    PendingJoin(final AbstractToken token,
                final JoinType type,
                final Query.Builder queryBuilder,
                final ParsedQuery bracketedQuery) {
        this.token = token;
        this.type = type;
        this.queryBuilder = queryBuilder;
        this.bracketedQuery = bracketedQuery;
    }
}
