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

package stroom.query.api.token;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;

public class NRegex {

    private static final Match[] NO_CHILDREN = new Match[0];

    public static class Branch implements Matcher {

        private final Matcher[] matchers;

        public Branch(final Matcher[] matchers) {
            this.matchers = matchers;
        }

        @Override
        public boolean match(final char[] chars, final int start, final int end) {
            for (final Matcher matcher : matchers) {
                if (matcher.match(chars, start, end)) {
                    return true;
                }
            }
            return false;
        }
    }

//    public static MatcherBuilder builder() {
//        return new MatcherBuilder(false);
//    }

//    public static class PatternBuilderList {
//        private final boolean anchorStart;
//        private final List<Matcher> list = new ArrayList<>();
//
//        public PatternBuilderList(final boolean anchorStart) {
//            this.anchorStart = anchorStart;
//        }
//
//        public PatternBuilderList add(final Consumer<PatternBuilder> consumer) {
//            final PatternBuilder builder = new PatternBuilder();
//            consumer.accept(builder);
//            if (builder.anchorStart || anchorStart) {
//                list.add(new AnchoredMatcher(builder.list.toArray(new CharMatcher[0]), builder.anchorEnd));
//            } else {
//                list.add(new FloatingMatcher(builder.list.toArray(new CharMatcher[0]), builder.anchorEnd));
//            }
//            return this;
//        }
//    }
//
//    public static class MatcherBuilder {
//        private final boolean anchorStart;
//        private final List<Matcher> matchers = new ArrayList<>();
//
//        public MatcherBuilder(final boolean anchorStart) {
//            this.anchorStart = anchorStart;
//        }
//
//        public MatcherBuilder choice(final Consumer<PatternBuilderList> consumer) {
//            final boolean anchorStart = !matchers.isEmpty();
//            final PatternBuilderList builders = new PatternBuilderList(anchorStart);
//            consumer.accept(builders);
//            matchers.add(new ChoiceMatcher(builders.list.toArray(new Matcher[0])));
//            return this;
//        }
//
//        public MatcherBuilder add(final Consumer<PatternBuilder> consumer) {
//            final PatternBuilder builder = new PatternBuilder();
//            consumer.accept(builder);
//            if (builder.anchorStart || !matchers.isEmpty()) {
//                matchers.add(new AnchoredMatcher(builder.list.toArray(new CharMatcher[0]), builder.anchorEnd));
//            } else {
//                matchers.add(new FloatingMatcher(builder.list.toArray(new CharMatcher[0]), builder.anchorEnd));
//            }
//            return this;
//        }
//
//        public MatcherBuilder group(final Consumer<MatcherBuilder> consumer) {
//            final boolean anchorStart = !matchers.isEmpty();
//            final MatcherBuilder builder = new MatcherBuilder(this.anchorStart || anchorStart);
//            consumer.accept(builder);
//            matchers.add(builder.build());
//            return this;
//        }
//
//        public Matcher build() {
//            return new SequenceMatcher(matchers.toArray(new Matcher[0]));
//        }
//    }

//    private static class SequenceMatcher implements Matcher {
//
//        private final Matcher[] matchers;
//
//        public SequenceMatcher(final Matcher[] matchers) {
//            this.matchers = matchers;
//        }
//
//        @Override
//        public Optional<Match> match(final char[] chars, final int start, final int end) {
//            int off = -1;
//            int len = 0;
//            int pos = start;
//            final Match[] children = new Match[matchers.length];
//            for (int i = 0; i < matchers.length; i++) {
//                final Matcher matcher = matchers[i];
//                Optional<Match> optional = matcher.match(chars, pos, end);
//                if (optional.isEmpty()) {
//                    return optional;
//                }
//                final Match match = optional.get();
//                children[i] = match;
//                pos = match.off + match.len;
//                if (off == -1) {
//                    off = match.off;
//                }
//                len += match.len;
//            }
//            return Optional.of(new Match(off, len, children));
//        }
//    }
//
//    public static class ChoiceMatcher implements Matcher {
//
//        private final Matcher[] matchers;
//
//        public ChoiceMatcher(final Matcher[] matchers) {
//            this.matchers = matchers;
//        }
//
//        @Override
//        public Optional<Match> match(final char[] chars, final int start, final int end) {
//            for (final Matcher matcher : matchers) {
//                Optional<Match> optional = matcher.match(chars, start, end);
//                if (optional.isPresent()) {
//                    return optional;
//                }
//            }
//            return Optional.empty();
//        }
//    }
//
//    private static class FloatingMatcher implements Matcher {
//
//        private final CharMatcher[] matchers;
//        private final boolean anchorEnd;
//
//        public FloatingMatcher(final CharMatcher[] matchers,
//                               final boolean anchorEnd) {
//            this.matchers = matchers;
//            this.anchorEnd = anchorEnd;
//        }
//
//        @Override
//        public Optional<Match> match(final char[] chars, final int start, final int end) {
//            for (int off = start; off <= end; off++) {
//                int len = 0;
//                for (int i = 0; i < matchers.length; i++) {
//                    final int count = matchers[i].match(chars, off + len, end);
//                    if (count < 0) {
//                        break;
//                    }
//
//                    len += count;
//                    if (i == matchers.length - 1) {
//                        if (anchorEnd && off + len < end) {
//                            return Optional.empty();
//                        }
//                        return Optional.of(new Match(off, len, NO_CHILDREN));
//                    }
//                }
//            }
//            return Optional.empty();
//        }
//    }
//
//    private static class AnchoredMatcher implements Matcher {
//
//        private final CharMatcher[] matchers;
//        private final boolean anchorEnd;
//
//        public AnchoredMatcher(final CharMatcher[] matchers,
//                               final boolean anchorEnd) {
//            this.matchers = matchers;
//            this.anchorEnd = anchorEnd;
//        }
//
//        @Override
//        public boolean match(final char[] chars, final int start, final int end, final Match[] matches) {
//            int len = 0;
//            for (final CharMatcher matcher : matchers) {
//                final int count = matcher.match(chars, start + len, end);
//                if (count < 0) {
//                    return false;
//                }
//
//                len += count;
//            }
//            if (anchorEnd && start + len < end) {
//                return Optional.empty();
//            }
//            return Optional.of(new Match(start, len, NO_CHILDREN));
//        }
//    }

//    public static class OrCharMatcher implements CharMatcher {
//
//        private final CharMatcher[] matchers;
//
//        public OrCharMatcher(final CharMatcher[] matchers) {
//            this.matchers = matchers;
//        }
//
//        @Override
//        public int match(final char[] chars, final int start, final int end) {
//            for (final CharMatcher matcher : matchers) {
//                final int count = matcher.match(chars, start, end);
//                if (count >= 0) {
//                    return count;
//                }
//            }
//            return -1;
//        }
//    }

    public static class RecordStartMatcher implements Matcher {

        private final Matcher next;
        private final Match match;

        public RecordStartMatcher(final Matcher next, final Match match) {
            this.next = next;
            this.match = match;
        }

        @Override
        public boolean match(final char[] chars, final int start, final int end) {
            if (next.match(chars, start, end)) {
                match.start = start;
                return true;
            }
            return false;
        }
    }

    public static class RecordEndMatcher implements Matcher {

        private final Matcher next;
        private final Match match;

        public RecordEndMatcher(final Matcher next, final Match match) {
            this.next = next;
            this.match = match;
        }

        @Override
        public boolean match(final char[] chars, final int start, final int end) {
            if (next.match(chars, start, end)) {
                match.end = start;
                return true;
            }
            return false;
        }
    }

    public static class StringMatcher implements Matcher {

        private final char[] buffer;
        private final Matcher next;

        public StringMatcher(String string, Matcher next) {
            this.buffer = string.toCharArray();
            this.next = next;
        }

        @Override
        public boolean match(final char[] chars, final int start, final int end) {
            if (end - start + 1 < buffer.length) {
                return false;
            }

            for (int i = 0; i < buffer.length; i++) {
                if (buffer[i] != chars[start + i]) {
                    return false;
                }
            }

            return next.match(chars, start + buffer.length, end);
        }
    }


    public static class GreedyMatcher implements Matcher {

        private final CharPredicate predicate;
        private final int min;
        private final int max;
        private final Matcher next;

        public GreedyMatcher(final CharPredicate predicate,
                             final int min,
                             final int max,
                             final Matcher next) {
            this.predicate = predicate;
            this.min = min;
            this.max = max;
            this.next = next;
        }

        public static GreedyMatcher star(final CharPredicate predicate,
                                         final Matcher next) {
            return new GreedyMatcher(predicate, 0, Integer.MAX_VALUE, next);
        }

        public static GreedyMatcher plus(final CharPredicate predicate,
                                         final Matcher next) {
            return new GreedyMatcher(predicate, 1, Integer.MAX_VALUE, next);
        }

        public boolean match(final char[] chars, final int start, final int end) {
            int count = 0;
            for (int j = start; count < max && j <= end; j++) {
                final boolean match = predicate.test(chars[j]);
                if (match) {
                    count++;
                } else {
                    break;
                }
            }

            // Is length of match within bounds.
            if (count < min) {
                return false;
            }

            return next.match(chars, start + count, end);
        }
    }

    public static class ReluctantMatcher implements Matcher {

        private final CharPredicate predicate;
        private final int min;
        private final int max;
        private final Matcher next;

        public ReluctantMatcher(final CharPredicate predicate,
                                final int min,
                                final int max,
                                final Matcher next) {
            this.predicate = predicate;
            this.min = min;
            this.max = max;
            this.next = next;
        }

        public static ReluctantMatcher star(final CharPredicate predicate,
                                         final Matcher next) {
            return new ReluctantMatcher(predicate, 0, Integer.MAX_VALUE, next);
        }

        public static ReluctantMatcher plus(final CharPredicate predicate,
                                         final Matcher next) {
            return new ReluctantMatcher(predicate, 1, Integer.MAX_VALUE, next);
        }

        public boolean match(final char[] chars, final int start, final int end) {
            int count = 0;
            for (int j = start; count < max && j <= end; j++) {
                if (count >= min && next.match(chars, start + count, end)) {
                    return true;
                }

                final boolean match = predicate.test(chars[j]);
                if (match) {
                    count++;
                } else {
                    break;
                }
            }

            // Is length of match within bounds.
            if (count < min) {
                return false;
            }

            return next.match(chars, start + count, end);
        }
    }

    public static class SingleMatcher implements Matcher {

        private final CharPredicate predicate;
        private final Matcher next;

        public SingleMatcher(final CharPredicate predicate, final Matcher next) {
            this.predicate = predicate;
            this.next = next;
        }

        public boolean match(final char[] chars, final int start, final int end) {
            return chars.length > start && predicate.test(chars[start]) && next.match(chars, start + 1, end);
        }
    }

    public static class EndMatcher implements Matcher {

        public boolean match(final char[] chars, final int start, final int end) {
            return start >= end;
        }
    }

    public static class TerminalMatcher implements Matcher {

        public boolean match(final char[] chars, final int start, final int end) {
            return true;
        }
    }


//    public static class CharMatcherImpl implements CharMatcher {
//
//        private final CharPredicate predicate;
//        private final int min;
//        private final int max;
//
//        public CharMatcherImpl(final CharPredicate predicate,
//                               final int min,
//                               final int max) {
//            this.predicate = predicate;
//            this.min = min;
//            this.max = max;
//        }
//
//        @Override
//        public boolean match(final char c) {
//            return predicate.test(c);
//        }
//
//        @Override
//        public int min() {
//            return min;
//        }
//
//        @Override
//        public int max() {
//            return max;
//        }
//    }
//
//    public static class PatternBuilder {
//
//        private final List<CharMatcher> list = new ArrayList<>();
//        private boolean anchorStart;
//        private boolean anchorEnd;
//
//        public PatternBuilder plus(final CharPredicate predicate) {
//            list.add(new GreedyMatcher(predicate, 1, Integer.MAX_VALUE));
//            return this;
//        }
//
//        public PatternBuilder star(final CharPredicate predicate) {
//            list.add(new GreedyMatcher(predicate, 0, Integer.MAX_VALUE));
//            return this;
//        }
//
//        public PatternBuilder one(final CharPredicate predicate) {
//            list.add(new SingleMatcher(predicate));
//            return this;
//        }
//
//        public PatternBuilder string(final String string) {
//            final char[] chars = string.toCharArray();
//            for (final char c : chars) {
//                list.add(new SingleMatcher(CharPredicates.eq(c)));
//            }
//            return this;
//        }
//
//        public PatternBuilder character(final char c) {
//            list.add(new SingleMatcher(CharPredicates.eq(c)));
//            return this;
//        }
//
//        public PatternBuilder anchorStart(final boolean anchorStart) {
//            this.anchorStart = anchorStart;
//            return this;
//        }
//
//        public PatternBuilder anchorEnd(final boolean anchorEnd) {
//            this.anchorEnd = anchorEnd;
//            return this;
//        }
//    }
//
//    private interface CharMatcher {
//
//        int match(char[] chars, int start, int end);
//    }

    public static class CharPredicates {

        public static CharPredicate any() {
            return c -> true;
        }

        public static CharPredicate eq(char ch) {
            return c -> ch == c;
        }

        public static CharPredicate ne(char ch) {
            return c -> ch != c;
        }

        public static CharPredicate letter() {
            return Character::isLetter;
        }

        public static CharPredicate digit() {
            return Character::isDigit;
        }

        public static CharPredicate letterOrDigit() {
            return Character::isLetterOrDigit;
        }

        public static CharPredicate alphabetic() {
            return Character::isAlphabetic;
        }

        public static CharPredicate space() {
            return Character::isSpaceChar;
        }

        public static CharPredicate whitespace() {
            return Character::isWhitespace;
        }

        public static CharPredicate upperCase() {
            return Character::isUpperCase;
        }

        public static CharPredicate lowerCase() {
            return Character::isLowerCase;
        }

        private static char[] sortAndDedupe(final char[] chars) {
            // Sort
            Arrays.sort(chars);

            // Dedupe
            char lastChar = 0;
            char[] src = new char[chars.length];
            int pos = 0;
            for (int i = 0; i < chars.length; i++) {
                final char c = chars[i];
                if (i == 0 || lastChar != c) {
                    src[pos++] = c;
                }
                lastChar = c;
            }
            final char[] dest = new char[pos];
            System.arraycopy(src, 0, dest, 0, dest.length);
            return dest;
        }

        public static CharPredicate characterClass(final String string) {
            final char[] chars = sortAndDedupe(string.toCharArray());

            boolean useArr = false;
            boolean useIteration = false;
            final boolean[] arr = new boolean[128];
            for (char c : chars) {
                if (c < arr.length) {
                    arr[c] = true;
                    useArr = true;
                } else {
                    useIteration = true;
                }
            }

            if (useArr) {
                if (useIteration) {
                    return c -> {
                        if (c < arr.length) {
                            return arr[c];
                        }
                        for (final char ch : chars) {
                            if (ch == c) {
                                return true;
                            } else if (ch > c) {
                                return false;
                            }
                        }
                        return false;
                    };
                } else {
                    return c -> {
                        if (c < arr.length) {
                            return arr[c];
                        }
                        return false;
                    };
                }

            } else if (useIteration) {
                return c -> {
                    for (final char ch : chars) {
                        if (ch == c) {
                            return true;
                        } else if (ch > c) {
                            return false;
                        }
                    }
                    return false;
                };
            } else {
                return c -> false;
            }
        }

        public static CharPredicate not(final CharPredicate predicate) {
            return c -> !predicate.test(c);
        }
    }

    public interface CharPredicate {

        boolean test(char c);
    }

//    private static class CharsMatcher implements Matcher {
//
//        private final char[] matchChars;
//
//        public CharsMatcher(final char[] matchChars) {
//            this.matchChars = matchChars;
//        }
//
//        @Override
//        public Optional<Match> match(final char[] chars, final int start, final int end) {
//            for (int pos = start; pos <= end - matchChars.length; pos++) {
//                for (int i = 0; i < matchChars.length; i++) {
//                    if (matchChars[i] != chars[pos + i]) {
//                        break;
//                    } else if (i == matchChars.length - 1) {
//                        return Optional.of(new Match(pos, pos + matchChars.length));
//                    }
//                }
//            }
//            return Optional.empty();
//        }
//    }

    public interface Matcher {

        default boolean match(char[] chars) {
            return match(chars, 0, chars.length - 1);
        }

        boolean match(char[] chars, int start, int end);
    }

    public static class Match {
        public int start = -1;
        public int end = -1;

        public Match() {
        }

        public Match(final int start, final int end) {
            this.start = start;
            this.end = end;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final Match match = (Match) o;
            return start == match.start && end == match.end;
        }

        @Override
        public int hashCode() {
            return Objects.hash(start, end);
        }

        @Override
        public String toString() {
            return "Match{" +
                   "start=" + start +
                   ", end=" + end +
                   '}';
        }
    }
}
