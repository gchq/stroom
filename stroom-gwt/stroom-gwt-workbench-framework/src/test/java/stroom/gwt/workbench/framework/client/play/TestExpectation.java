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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestExpectation {

    @Test
    void testIsPresent() {
        assertThat(Expectation.isPresent(0)).isFalse();
        assertThat(Expectation.isPresent(1)).isTrue();
        // Regression: several matches made resolve() throw, which was treated as 'not there', so
        // not().toBeInTheDocument() passed
        assertThat(Expectation.isPresent(2)).isTrue();
    }

    @Test
    void testQuote() {
        assertThat(Expectation.quote("a\"b")).isEqualTo("\"a\\\"b\"");
        assertThat(Expectation.quote(null)).isEqualTo("\"\"");
    }

    @Test
    void testDescribeNegated() {
        final Play play = new Play();
        play.expect(play.getByText("Gone")).not().toBeInTheDocument();

        assertThat(play.getSteps().get(0).describe(null))
                .isEqualTo("expect(within(<div#workbench-root>).getByText(\"Gone\")).not.toBeInTheDocument()");
    }
}
