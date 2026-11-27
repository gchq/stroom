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

package stroom.ai.impl.tools.query;

import stroom.docref.DocRef;

import java.time.Instant;
import java.util.Map;
import java.util.function.BooleanSupplier;

/// Index data checks for tests that don't call a cluster or list shards.
public final class IndexDataChecks {

    private IndexDataChecks() {
    }

    /// @return A check saying every index holds data.
    public static IndexDataCheck allHoldData() {
        return holding(Map.of());
    }

    /// @param empty The indexes holding no data, each with why; every other holds data.
    /// @return A check saying so.
    public static IndexDataCheck holding(final Map<DocRef, String> empty) {
        return new IndexDataCheck(() -> null, () -> null, null) {
            @Override
            public Holding check(final DocRef doc,
                                 final Instant from,
                                 final Instant to,
                                 final BooleanSupplier stopped) {
                return empty.containsKey(doc)
                        ? new Holding(doc, State.EMPTY, empty.get(doc))
                        : new Holding(doc, State.DATA, null);
            }
        };
    }
}
