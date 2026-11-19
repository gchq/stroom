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

package stroom.gwt.workbench.framework.client.play;

import java.util.Collection;
import java.util.Map;
import java.util.function.Predicate;

/// A value that matches other values loosely, the equivalent of Jest's asymmetric matchers, e.g.
/// `expect.objectContaining({ id: 1 })`. It can be used wherever an expected value is given, e.g.
/// `play.expect(onPick).toHaveBeenCalledWith(ValueMatcher.objectContaining(Map.of("id", 1)))` or
/// `play.expect(() -> last).toEqual(ValueMatcher.anything())`. [TextMatch] is also one, the
/// equivalent of `expect.stringMatching(...)`.
public interface ValueMatcher {

    /// @param value The actual value, may be null.
    /// @return True if the value matches.
    boolean matchesValue(Object value);

    /// @return The matcher as shown in the Interactions addon, e.g. `ObjectContaining({ id: 1 })`.
    String describe();

    /// @param description How the matcher is shown, e.g. `isEven`.
    /// @param predicate   Tests the actual value, which may be null.
    /// @return A matcher for values the predicate accepts.
    static ValueMatcher satisfying(final String description, final Predicate<Object> predicate) {
        return new ValueMatcher() {
            @Override
            public boolean matchesValue(final Object value) {
                return predicate.test(value);
            }

            @Override
            public String describe() {
                return description;
            }

            @Override
            public String toString() {
                return description;
            }
        };
    }

    /// @return A matcher for any value except null, the equivalent of `expect.anything()`.
    static ValueMatcher anything() {
        return satisfying("Anything", value -> value != null);
    }

    /// @param expected The entries the actual map must have; values may be matchers or nested
    ///                 maps, which are matched in the same way.
    /// @return A matcher for maps with (at least) the entries, the equivalent of
    /// `expect.objectContaining({...})`.
    static ValueMatcher objectContaining(final Map<String, ?> expected) {
        return satisfying("ObjectContaining(" + Values.format(expected) + ")",
                value -> Values.matchesObject(value, expected));
    }

    /// @param text The text.
    /// @return A matcher for strings containing the text, the equivalent of
    /// `expect.stringContaining(...)`.
    static ValueMatcher stringContaining(final String text) {
        return satisfying("StringContaining(" + Expectation.quote(text) + ")",
                value -> value instanceof String && ((String) value).contains(text));
    }

    /// @param expected The items the actual collection must contain.
    /// @return A matcher for collections (or arrays) containing all the items, in any order, the
    /// equivalent of `expect.arrayContaining([...])`.
    static ValueMatcher arrayContaining(final Collection<?> expected) {
        return satisfying("ArrayContaining(" + Values.format(expected) + ")", value -> {
            for (final Object item : expected) {
                if (!Values.containsItem(value, item, true)) {
                    return false;
                }
            }
            return Values.isCollectionLike(value);
        });
    }
}
