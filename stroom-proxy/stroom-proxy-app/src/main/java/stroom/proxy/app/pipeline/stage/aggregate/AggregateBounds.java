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

package stroom.proxy.app.pipeline.stage.aggregate;

import java.time.Duration;
import java.util.Objects;

/**
 * When an aggregate closes. The bounds are targets, not guarantees
 * ({@code designs/contracts.md §7}): an aggregate closes when the next item would take it past
 * {@code maxItems} or {@code maxBytes}, or when it has been open longer than {@code maxAge}, and a
 * single item larger than {@code maxBytes} ships alone.
 *
 * @param maxItems the most items an aggregate holds; an item is one base name's entries.
 * @param maxBytes the most declared uncompressed bytes an aggregate holds.
 * @param maxAge   how long an aggregate stays open waiting for company.
 */
public record AggregateBounds(int maxItems, long maxBytes, Duration maxAge) {

    public AggregateBounds {
        if (maxItems < 1) {
            throw new IllegalArgumentException("maxItems must be at least 1, got " + maxItems);
        }
        if (maxBytes < 1) {
            throw new IllegalArgumentException("maxBytes must be at least 1, got " + maxBytes);
        }
        Objects.requireNonNull(maxAge, "maxAge");
        if (maxAge.isNegative() || maxAge.isZero()) {
            throw new IllegalArgumentException("maxAge must be positive, got " + maxAge);
        }
    }
}
