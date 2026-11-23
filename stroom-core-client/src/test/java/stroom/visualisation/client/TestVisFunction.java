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

package stroom.visualisation.client;

import stroom.visualisation.shared.VisualisationDoc;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestVisFunction {

    @Test
    void testOneFetchForAllWaiting() {
        // Regression test (#6059): the keys and the drawing share one fetch of the visualisation
        final VisFunction function = new VisFunction(1);
        final List<String> calls = new ArrayList<>();

        assertThat(function.awaitFetch(() -> calls.add("keys"))).isTrue();
        assertThat(function.awaitFetch(() -> calls.add("draw"))).isFalse();
        assertThat(calls).isEmpty();

        final VisualisationDoc doc = VisualisationDoc.builder().uuid("vis-1").name("Bar").build();
        function.fetched(doc, null);

        assertThat(calls).containsExactly("keys", "draw");
        assertThat(function.getVisualisationDoc()).isSameAs(doc);
        assertThat(function.getFetchFailure()).isNull();
    }

    @Test
    void testFetchAgainAfterFailure() {
        final VisFunction function = new VisFunction(1);
        final List<String> calls = new ArrayList<>();

        function.awaitFetch(() -> calls.add("first"));
        function.fetched(null, "Not found");

        assertThat(calls).containsExactly("first");
        assertThat(function.getVisualisationDoc()).isNull();
        assertThat(function.getFetchFailure()).isEqualTo("Not found");
        // Nothing waits any more, so the next to ask fetches it again
        assertThat(function.awaitFetch(() -> calls.add("second"))).isTrue();
    }
}
