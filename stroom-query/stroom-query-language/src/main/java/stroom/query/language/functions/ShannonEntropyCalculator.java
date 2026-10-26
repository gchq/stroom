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

package stroom.query.language.functions;

import java.util.HashMap;
import java.util.Map;

/// Calculates the Shannon entropy of a string, i.e. how unpredictable its characters are.
///
/// Shared by the `shannonEntropy()` expression function and the `shannon-entropy()`
/// XSLT function so that both always give the same result.
public final class ShannonEntropyCalculator {

    private static final double LN_2 = Math.log(2);

    private ShannonEntropyCalculator() {
        // Utility class.
    }

    /// Calculates the Shannon entropy of `value` in bits per character.
    ///
    /// Characters are counted as Unicode code points and are case-sensitive.
    /// The result ranges from `0.0`, when the value is one repeated character, to
    /// `log2(n)`, when all `n` characters are different.
    ///
    /// @param value The string to measure. May be `null`.
    /// @return The entropy in bits per character, or `0.0` if `value` is `null` or empty.
    /// Never returns `-0.0`.
    public static double calculate(final String value) {
        if (value == null || value.isEmpty()) {
            return 0.0;
        }

        final Map<Integer, Integer> counts = new HashMap<>();
        value.codePoints().forEach(codePoint -> counts.merge(codePoint, 1, Integer::sum));

        // One distinct character always scores zero. Returning it here guarantees 0.0 rather than
        // the -0.0 that some arrangements of the formula give, which displays as -0.
        if (counts.size() == 1) {
            return 0.0;
        }

        final double length = value.codePointCount(0, value.length());
        double sum = 0.0;
        for (final int count : counts.values()) {
            final double probability = count / length;
            sum -= probability * Math.log(probability);
        }
        return sum / LN_2;
    }
}
