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

package stroom.query.language.functions;

class Statistics {

    static double standardDeviation(final Double[] values) {
        return Math.sqrt(variance(values));
    }

    static double variance(final Double[] values) {
        if (values.length == 0) {
            return 0;
        }
        // Calculate mean
        double total = 0;
        for (final double d : values) {
            total += d;
        }
        final double mean = total / values.length;
        // calculate squared differences.
        double sqTotal = 0;
        for (final double d : values) {
            final double diff = d - mean;
            final double sq = diff * diff;
            sqTotal += sq;
        }
        // calculate variance
        return sqTotal / values.length;
    }
}
