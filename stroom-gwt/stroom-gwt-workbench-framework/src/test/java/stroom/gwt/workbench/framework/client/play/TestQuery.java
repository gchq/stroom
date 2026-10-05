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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestQuery {

    @Test
    void testNth_querySelector() {
        // Regression: the description has no 'By', which threw StringIndexOutOfBoundsException
        final Play play = new Play();
        final Query query = play.querySelector(".item").nth(2);

        assertThat(query.describe())
                .isEqualTo("within(<div#workbench-root>).querySelectorAll(\".item\")[2]");
    }

    @Test
    void testNth_getBy() {
        final Play play = new Play();
        assertThat(play.getByRole("button").nth(0).describe())
                .isEqualTo("within(<div#workbench-root>).getAllByRole(\"button\")[0]");
        assertThat(play.getByTestId("x").nth(1).describe())
                .isEqualTo("within(<div#workbench-root>).getAllByTestId(\"x\")[1]");
    }

    @Test
    void testNth_ofNth() {
        // The later index replaces the earlier one rather than being appended to the description
        final Play play = new Play();
        assertThat(play.getByText("Open").nth(1).nth(3).describe())
                .isEqualTo("within(<div#workbench-root>).getAllByText(\"Open\")[3]");
    }

    @Test
    void testNth_negative() {
        // Regression: -1 was silently treated as 'exactly one'
        final Play play = new Play();
        final Query query = play.getByText("Open");
        assertThatThrownBy(() -> query.nth(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testCountMatches() {
        assertThat(Query.countMatches(0, -1)).isZero();
        assertThat(Query.countMatches(3, -1)).isEqualTo(3);
        assertThat(Query.countMatches(3, 2)).isOne();
        assertThat(Query.countMatches(3, 3)).isZero();
    }
}
