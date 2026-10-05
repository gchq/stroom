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

    @Test
    void testClosest() {
        final Play play = new Play();
        final Query row = play.getByText("node3").closest("[role=\"row\"]");
        assertThat(row.describe())
                .isEqualTo("within(<div#workbench-root>).getByText(\"node3\").closest(\"[role=\\\"row\\\"]\")");
        // Queries within the closest element
        assertThat(play.within(row).getByRole("checkbox").describe())
                .startsWith("within(within(<div#workbench-root>).getByText(\"node3\").closest(");
    }

    @Test
    void testBodyAndFirst() {
        final Play play = new Play();
        assertThat(play.body().describe()).isEqualTo("document.body");
        assertThat(play.screen().getAllByText("x").first().describe())
                .isEqualTo("within(document.body).getAllByText(\"x\")[0]");
    }

    @Test
    void testDescribeAs() {
        final Play play = new Play();
        final Query query = play.within(play.getByRole("menu")).getByText("Open");
        // Only the query's own method is replaced, not its scope's
        assertThat(query.describeAs("findByText"))
                .isEqualTo("within(within(<div#workbench-root>).getByRole(\"menu\")).findByText(\"Open\")");
    }

    @Test
    void testPick() {
        assertThat(Query.pick(1, -1, false, "none")).isZero();
        assertThat(Query.pick(3, -1, true, "none")).isZero();
        assertThat(Query.pick(3, 2, false, "none")).isEqualTo(2);
        assertThatThrownBy(() -> Query.pick(0, -1, false, "Unable to find an element with the text: x"))
                .isInstanceOf(PlayException.class)
                .hasMessage("Unable to find an element with the text: x");
        assertThatThrownBy(() -> Query.pick(2, -1, false, "Unable to find an element with the text: x"))
                .isInstanceOf(PlayException.class)
                .hasMessage("Found multiple elements (2): elements with the text: x (use nth() to pick one)");
        assertThatThrownBy(() -> Query.pick(2, 2, true, "Unable to find x"))
                .isInstanceOf(PlayException.class)
                .hasMessage("Unable to find x at index 2 (found 2)");
    }

    @Test
    void testIsAll() {
        final Play play = new Play();
        assertThat(play.getAllByText("x").isAll()).isTrue();
        assertThat(play.queryAllByText("x").isAll()).isTrue();
        assertThat(play.getByText("x").isAll()).isFalse();
        assertThat(play.getAllByText("x").nth(1).isAll()).isFalse();
    }

    @Test
    void testValueLabels() {
        final Play play = new Play();
        final Query button = play.getByRole("button");
        assertThat(button.textContent().getLabel())
                .isEqualTo("within(<div#workbench-root>).getByRole(\"button\").textContent");
        assertThat(play.getAllByRole("row").count().getLabel())
                .isEqualTo("within(<div#workbench-root>).getAllByRole(\"row\").length");
        assertThat(button.attribute("aria-pressed").getLabel()).endsWith(".getAttribute(\"aria-pressed\")");
        assertThat(button.width().getLabel()).endsWith(".getBoundingClientRect().width");
        assertThat(button.property("scrollTop").getLabel()).endsWith(".scrollTop");
        assertThat(button.className().getLabel()).endsWith(".className");
        assertThat(button.value().getLabel()).endsWith(".value");
        assertThat(button.textContents().getLabel()).endsWith(".map(el => el.textContent)");
    }

    @Test
    void testValuesNeedARunningStep() {
        // Reading a value while the play function adds its steps is a mistake
        final Play play = new Play();
        final Value<String> text = play.getByRole("button").textContent();
        assertThatThrownBy(text::get)
                .isInstanceOf(PlayException.class)
                .hasMessageContaining("only be found while a step runs");
    }
}
