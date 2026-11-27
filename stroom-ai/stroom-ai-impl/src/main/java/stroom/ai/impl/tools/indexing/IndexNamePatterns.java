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

package stroom.ai.impl.tools.indexing;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// An Elasticsearch indexing pipeline's index name as a pattern: each `{variable}` (filled per document by Stroom's
/// ElasticIndexingFilter from the document's root field of that name) a wildcard, the rest exact. Used for the
/// cluster lookup before a first run, the comparison of index names across pipelines, and the index_patterns of the
/// index templates the tools build: seen in testing, `acme-events-v1*` also covered `acme-events-v10`.
public final class IndexNamePatterns {

    /// A variable in an index name, as Stroom's ElasticIndexingFilter finds them (`{_year}`).
    public static final Pattern VARIABLE = Pattern.compile("\\{([^}]+?)}");

    // A pattern whose last character before its closing wildcard is a digit: v1* covers v10.
    private static final Pattern DIGIT_THEN_STAR = Pattern.compile(".*\\d\\*$");

    private IndexNamePatterns() {
        // Static only.
    }

    /// @param indexName An index name, perhaps with variables, e.g. `ecs-acme-{year}`.
    /// @return It with each variable as a wildcard, e.g. `ecs-acme-*`; unchanged without variables.
    public static String wildcarded(final String indexName) {
        return VARIABLE.matcher(indexName).replaceAll("*");
    }

    /// @param indexName An index name, perhaps with variables.
    /// @return The index_patterns of an index template for it: the exact name, or, with variables, the name with
    /// each variable as a wildcard (`index-name-v1{_suffix}` to `index-name-v1*`).
    public static List<String> templatePatterns(final String indexName) {
        return List.of(wildcarded(indexName));
    }

    /// @param indexName An index name, perhaps with variables.
    /// @return The names of its variables, in order, each once (`_year` for `acme-{_year}`).
    public static Set<String> variables(final String indexName) {
        final Set<String> names = new LinkedHashSet<>();
        final Matcher matcher = VARIABLE.matcher(indexName);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    /// @param pattern An index pattern, e.g. `acme-events-v1*`.
    /// @return True if it ends in a digit then a wildcard, so also covers longer numbers (`acme-events-v10`).
    public static boolean endsInDigitThenWildcard(final String pattern) {
        return pattern != null && DIGIT_THEN_STAR.matcher(pattern).matches();
    }
}
