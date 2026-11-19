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

package stroom.data.client.component;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestClassificationLabelController {

    @Test
    void parseLabelColours() {
        final Map<String, String> colours = ClassificationLabelController.parseLabelColours(
                "official=green,SECRET=#ff9800,TOP SECRET=#f44336");

        // Upper case names, in the order given, which is the order they are matched in
        assertThat(colours).containsExactly(
                Map.entry("OFFICIAL", "green"),
                Map.entry("SECRET", "#ff9800"),
                Map.entry("TOP SECRET", "#f44336"));
    }

    @Test
    void parseLabelColours_firstOfARepeatedNameWins() {
        assertThat(ClassificationLabelController.parseLabelColours("SECRET=red,secret=blue"))
                .containsExactly(Map.entry("SECRET", "red"));
    }

    @Test
    void parseLabelColours_badPairsAreLeftOut() {
        assertThat(ClassificationLabelController.parseLabelColours("OFFICIAL,SECRET=red=blue,,=,TOP=#000"))
                .containsExactly(Map.entry("TOP", "#000"));
    }

    @Test
    void parseLabelColours_none() {
        assertThat(ClassificationLabelController.parseLabelColours(null)).isEmpty();
        assertThat(ClassificationLabelController.parseLabelColours("")).isEmpty();
    }
}
