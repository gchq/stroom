/*
 * Copyright 2024 Crown Copyright
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

package stroom.query.common.v2;

import stroom.query.api.v2.Filter;
import stroom.util.shared.string.CIKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class CompiledIncludeExcludeFilter {

    public static Optional<Predicate<String>> create(final Filter filter,
                                                     final Map<CIKey, String> paramMap) {
        if (filter == null) {
            return Optional.empty();
        }

        Optional<Predicate<String>> optional = Optional.empty();
        final List<Pattern> includes = createPatternList(filter.getIncludes(), paramMap);
        if (includes != null) {
            final Predicate<String> includePredicate = value -> {
                for (final Pattern pattern : includes) {
                    if (pattern.matcher(value).find()) {
                        return true;
                    }
                }
                return false;
            };
            optional = Optional.of(includePredicate);
        }

        final List<Pattern> excludes = createPatternList(filter.getExcludes(), paramMap);
        if (excludes != null) {
            final Predicate<String> excludePredicate = value -> {
                for (final Pattern pattern : excludes) {
                    if (pattern.matcher(value).find()) {
                        return false;
                    }
                }
                return true;
            };
            optional = optional
                    .map(includePredicate -> includePredicate.and(excludePredicate))
                    .or(() -> Optional.of(excludePredicate));
        }

        return optional;
    }

    private static List<Pattern> createPatternList(final String patterns, final Map<CIKey, String> paramMap) {
        List<Pattern> patternList = null;
        if (patterns != null && !patterns.trim().isEmpty()) {
            final String replaced = KVMapUtil.replaceParameters(patterns, paramMap);
            final String[] patternArray = replaced.split("\n");
            patternList = new ArrayList<>(patternArray.length);
            for (final String pattern : patternArray) {
                final String trimmed = pattern.trim();
                if (!trimmed.isEmpty()) {
                    patternList.add(Pattern.compile(trimmed));
                }
            }
        }

        return patternList;
    }
}
