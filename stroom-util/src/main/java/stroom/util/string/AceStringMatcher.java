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

/*

This code is a re-write of a method in
https://github.com/ajaxorg/ace/blob/master/src/autocomplete.js
which has the following licence.

Copyright (c) 2010, Ajax.org B.V.
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name of Ajax.org B.V. nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL AJAX.ORG B.V. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package stroom.util.string;

import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

// https://github.com/ajaxorg/ace/blob/master/src/autocomplete.js
public class AceStringMatcher {

    public static final Comparator<AceMatchResult<?>> SCORE_DESC_THEN_NAME_COMPARATOR;

    static {
        Comparator<AceMatchResult<?>> c = Comparator.comparingInt(AceMatchResult::score);
        c = c.reversed();
        c = c.thenComparing(AceMatchResult::name, String.CASE_INSENSITIVE_ORDER);
        SCORE_DESC_THEN_NAME_COMPARATOR = c;
    }

    private AceStringMatcher() {
    }

    public static <T> Optional<AceMatchResult<T>> isMatch(final T item,
                                                          final String pattern,
                                                          final int initialScore,
                                                          final Function<T, String> stringExtractor) {
        return filterCompletions(List.of(item), pattern, initialScore, stringExtractor)
                .stream()
                .findFirst();
    }

    public static List<AceMatchResult<String>> filterCompletions(final List<String> items,
                                                                 final String pattern) {
        return filterCompletions(items, pattern, 0, Function.identity());
    }

    public static List<AceMatchResult<String>> filterCompletions(final List<String> items,
                                                                 final String pattern,
                                                                 final int initialScore) {
        return filterCompletions(items, pattern, initialScore, Function.identity());
    }

    public static <T> List<AceMatchResult<T>> filterCompletions(final List<T> items,
                                                                final String pattern,
                                                                final int initialScore,
                                                                final Function<T, String> stringExtractor) {
        if (NullSafe.isEmptyCollection(items)) {
            return Collections.emptyList();
        } else if (NullSafe.isEmptyString(pattern)) {
            // No pattern so just return the unfiltered items
            return items.stream()
                    .map(item -> new AceMatchResult<>(
                            item,
                            stringExtractor.apply(item),
                            initialScore,
                            false))
                    .toList();
        } else {
            final List<AceMatchResult<T>> results = new ArrayList<>();
            final String safePattern = NullSafe.string(pattern);
            final String upperPattern = safePattern.toUpperCase();
            final String lowerPattern = safePattern.toLowerCase();
            final int len = safePattern.length();

            outerLoop:
            for (final T item : items) {
                final String name = stringExtractor.apply(item);
                if (NullSafe.isBlankString(name)) {
                    continue;
                }
                int lastIndex = -1;
//                BitSet matchMask = new BitSet(len);
//                int matchMask = 0;
                int penalty = 0;
                int index;
                int distance;

                /*
                 * It is for situation then, for example, we find some like 'tab' in item.value="Check the table"
                 * and want to see "Check the TABle" but see "Check The tABle".
                 */
                final int fullMatchIndex = name.toLowerCase()
                        .indexOf(lowerPattern);
                if (fullMatchIndex > -1) {
                    penalty = fullMatchIndex;
                } else {
                    // caption char iteration is faster in Chrome but slower in Firefox, so lets use indexOf
                    for (int j = 0; j < len; j++) {
                        // TODO add penalty on case mismatch
                        final int lowerIdx = name.indexOf(lowerPattern.charAt(j), lastIndex + 1);
                        final int upperIdx = name.indexOf(upperPattern.charAt(j), lastIndex + 1);

                        if (lowerIdx >= 0) {
                            if (upperIdx < 0 || lowerIdx < upperIdx) {
                                index = lowerIdx;
                            } else {
                                index = upperIdx;
                            }
                        } else {
                            index = upperIdx;
                        }

                        if (index < 0) {
                            // No match for this char so go to next item
                            continue outerLoop;
                        }

                        distance = index - lastIndex - 1;
                        if (distance > 0) {
                            // first char mismatch should be more sensitive
                            if (lastIndex == -1) {
                                penalty += 10;
                            }
                            penalty += distance;
//                            matchMask = matchMask | (1 << j);
//                            matchMask.set(j);
                        }
                        lastIndex = index;
                    }
                }
                final boolean exactMatch = penalty <= 0;
                final int score = initialScore - penalty;
                results.add(new AceMatchResult<>(item, name, score, exactMatch));
            }
            return results;
        }
    }


    // --------------------------------------------------------------------------------


    public record AceMatchResult<T>(T item,
                                    String name,
                                    int score,
                                    boolean exactMatch) {

        @Override
        public String toString() {
            return name
                   + " score: " + score
                   + " exactMatch: " + exactMatch;
        }
    }
}


// --------------------------------------------------------------------------------


// THis is the original JS code

//    filterCompletions(items, needle) {
//        var results = [];
//        var upper = needle.toUpperCase();
//        var lower = needle.toLowerCase();
//        loop: for (var i = 0, item; item = items[i]; i++) {
//            var caption = (!this.ignoreCaption && item.caption) || item.value || item.snippet;
//            if (!caption) continue;
//            var lastIndex = -1;
//            var matchMask = 0;
//            var penalty = 0;
//            var index, distance;
//
//            if (this.exactMatch) {
//                if (needle !== caption.substr(0, needle.length))
//                    continue loop;
//            } else {
//                /**
//                 * It is for situation then, for example, we find some like 'tab' in item.value="Check the table"
//                 * and want to see "Check the TABle" but see "Check The tABle".
//                 */
//                var fullMatchIndex = caption.toLowerCase().indexOf(lower);
//                if (fullMatchIndex > -1) {
//                    penalty = fullMatchIndex;
//                } else {
//                    // caption char iteration is faster in Chrome but slower in Firefox, so lets use indexOf
//                    for (var j = 0; j < needle.length; j++) {
//                        // TODO add penalty on case mismatch
//                        var i1 = caption.indexOf(lower[j], lastIndex + 1);
//                        var i2 = caption.indexOf(upper[j], lastIndex + 1);
//                        index = (i1 >= 0) ? ((i2 < 0 || i1 < i2) ? i1 : i2) : i2;
//                        if (index < 0)
//                            continue loop;
//                        distance = index - lastIndex - 1;
//                        if (distance > 0) {
//                            // first char mismatch should be more sensitive
//                            if (lastIndex === -1)
//                                penalty += 10;
//                            penalty += distance;
//                            matchMask = matchMask | (1 << j);
//                        }
//                        lastIndex = index;
//                    }
//                }
//            }
//            item.matchMask = matchMask;
//            item.exactMatch = penalty ? 0 : 1;
//            item.$score = (item.score || 0) - penalty;
//            results.push(item);
//        }
//        return results;
//    }
