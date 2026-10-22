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

package stroom.ai.impl.tools.validation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/// The "did you mean" suggestions, as Python's difflib gives them.
class TestCloseMatches {

    @Test
    void testTheRatioIsDifflibs() {
        // As Python's difflib.SequenceMatcher(None, a, b).ratio() gives them.
        assertThat(CloseMatches.ratio("lookp", "lookup")).isCloseTo(0.9090909, within(1e-6));
        assertThat(CloseMatches.ratio("abc", "xyz")).isZero();
        assertThat(CloseMatches.ratio("", "")).isEqualTo(1.0);
    }

    @Test
    void testTheClosestComeFirstAndThoseBelowTheCutoffNot() {
        assertThat(CloseMatches.closeMatches("appel", List.of("ape", "apple", "peach", "puppy"), 3, 0.6))
                .containsExactly("apple", "ape");
        assertThat(CloseMatches.closeMatches("Acess", List.of("Access", "Outcome", "Authenticate"), 3, 0.6))
                .containsExactly("Access");
        assertThat(CloseMatches.closeMatches("zzz", List.of("Access"), 3, 0.6)).isEmpty();
    }
}
