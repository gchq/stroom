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

package stroom.ai.impl.tools.catalogue;

import stroom.ai.impl.tools.sample.RecordSplitter;
import stroom.ai.impl.tools.sample.SampleProfiler;
import stroom.ai.impl.tools.sample.Shapes;
import stroom.ai.impl.tools.sample.ToolJson;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// The uncatalogued records the catch-all caught in processing (its `Caught by` lines, read by review_coverage),
/// grouped by shape ([Shapes]' signatures over the values each line logged: the fields the rules test), as the
/// catalogue's check groups the sample's uncatalogued records, so each shape can be proposed as an event type.
public final class CaughtShapes {

    private CaughtShapes() {
        // Static only.
    }

    /// @param logged The values each caught record's line logged, by field.
    /// @return The shapes, most lines first (in the order first met when as many): each its signature and the
    /// indexes of its lines.
    public static List<Shape> of(final List<Map<String, String>> logged) {
        final List<String> texts = new ArrayList<>();
        final List<Map<String, Object>> parsed = new ArrayList<>();
        for (final Map<String, String> values : logged) {
            final Map<String, Object> record = new LinkedHashMap<>(values);
            texts.add(ToolJson.dumps(record));
            parsed.add(record);
        }
        final RecordSplitter.Chunk chunk = new RecordSplitter.Chunk(SampleProfiler.JSON_LINES, texts, parsed, null,
                null, List.of(), null);
        final Shapes signer = new Shapes();
        final Map<String, List<Integer>> bySignature = new LinkedHashMap<>();
        for (int n = 0; n < logged.size(); n++) {
            bySignature.computeIfAbsent(signer.signature(chunk, n), signature -> new ArrayList<>()).add(n);
        }
        final List<Shape> shapes = new ArrayList<>();
        bySignature.forEach((signature, members) -> shapes.add(new Shape(signature, List.copyOf(members))));
        shapes.sort(Comparator.comparingInt((Shape shape) -> shape.members().size()).reversed());
        return shapes;
    }


    // --------------------------------------------------------------------------------


    /// One shape of caught records.
    ///
    /// @param signature Its signature ([Shapes#signature]).
    /// @param members   The indexes of its lines, in order.
    public record Shape(String signature, List<Integer> members) {

    }
}
