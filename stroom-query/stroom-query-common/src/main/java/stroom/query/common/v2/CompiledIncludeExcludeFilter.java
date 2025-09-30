/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.query.common.v2;

import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.query.api.IncludeExcludeFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static stroom.util.PredicateUtil.createWildCardedInPredicate;

public class CompiledIncludeExcludeFilter {

    public static Optional<Predicate<String>> create(final IncludeExcludeFilter filter,
                                                     final Map<String, String> paramMap,
                                                     final WordListProvider wordListProvider) {
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

        if (!filter.getIncludeDictionaries().isEmpty()) {
            for (final DocRef dictionary : filter.getIncludeDictionaries()) {
                final Predicate<String> includeDictionary = createWildCardedInPredicate(
                        loadWords(dictionary, wordListProvider), true);
                optional = optional
                        .map(predicate -> predicate.or(includeDictionary))
                        .or(() -> Optional.of(includeDictionary));
            }
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
                    .map(predicate -> predicate.and(excludePredicate))
                    .or(() -> Optional.of(excludePredicate));
        }

        if (!filter.getExcludeDictionaries().isEmpty()) {
            for (final DocRef dictionary : filter.getExcludeDictionaries()) {
                final Predicate<String> excludeDictionary = Predicate.not(createWildCardedInPredicate(
                        loadWords(dictionary, wordListProvider), true));
                optional = optional
                        .map(predicate -> predicate.and(excludeDictionary))
                        .or(() -> Optional.of(excludeDictionary));
            }
        }

        return optional;
    }

    private static String[] loadWords(final DocRef docRef, final WordListProvider wordListProvider) {
        final String[] words = wordListProvider.getWords(docRef);
        if (words == null) {
            throw new IllegalArgumentException("Dictionary \"" + docRef + "\" not found");
        }

        return words;
    }

    private static List<Pattern> createPatternList(final String patterns, final Map<String, String> paramMap) {
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
