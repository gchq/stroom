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

import stroom.gwt.workbench.framework.client.play.PlayStep.Kind;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestPlay {

    @Test
    void testParseKeys() {
        assertThat(Play.parseKeys("{Enter}")).containsExactly("Enter");
        assertThat(Play.parseKeys("ab{ArrowDown}c")).containsExactly("a", "b", "ArrowDown", "c");
        assertThat(Play.parseKeys("{Space}")).containsExactly(" ");
        // An unclosed or empty brace is just a character
        assertThat(Play.parseKeys("{")).containsExactly("{");
        assertThat(Play.parseKeys("{}")).containsExactly("{", "}");
        assertThat(Play.parseKeys("")).isEmpty();
    }

    @Test
    void testStepsAreAddedInOrder() {
        // Describing steps doesn't need a browser, as long as nothing resolves a query
        final Play play = new Play();
        final Query button = play.getByRole("button", "Save");
        play.click(button);
        play.keyboard("{Enter}");
        play.tab();

        final List<PlayStep> steps = play.getSteps();
        assertThat(steps).hasSize(3);
        assertThat(steps).extracting(PlayStep::getKind).containsOnly(Kind.ACTION);
        assertThat(steps.get(0).describe(null))
                .isEqualTo("userEvent.click(within(<div#workbench-root>).getByRole(\"button\", { name: \"Save\" }))");
        assertThat(steps.get(1).describe(null)).isEqualTo("userEvent.keyboard(\"{Enter}\")");
        assertThat(steps.get(2).describe(null)).isEqualTo("userEvent.tab()");
    }

    @Test
    void testGroups() {
        final Play play = new Play();
        play.step("Open the menu", () -> {
            play.click(play.getByText("Menu"));
            play.waitFor(() -> play.expect(play.getByRole("menu")).toBeVisible());
        });
        play.type(play.getByLabelText("Name"), "abc");

        final List<PlayStep> steps = play.getSteps();
        assertThat(steps).hasSize(2);
        assertThat(steps.get(0).getKind()).isEqualTo(Kind.STEP);
        assertThat(steps.get(0).describe(null)).isEqualTo("step(\"Open the menu\")");
        assertThat(steps.get(0).getChildren()).hasSize(2);
        final PlayStep waitFor = steps.get(0).getChildren().get(1);
        assertThat(waitFor.getKind()).isEqualTo(Kind.WAIT_FOR);
        assertThat(waitFor.getChildren().get(0).describe(null))
                .isEqualTo("expect(within(<div#workbench-root>).getByRole(\"menu\")).toBeVisible()");
        assertThat(steps.get(1).describe(null))
                .isEqualTo("userEvent.type(within(<div#workbench-root>).getByLabelText(\"Name\"), \"abc\")");
    }

    @Test
    void testWithinAndNth() {
        final Play play = new Play();
        final Play inMenu = play.within(play.getByRole("menu"));
        inMenu.click(inMenu.getByText("Open").nth(1));
        play.expect(play.getByTestId("result")).not().toHaveTextContent("Closed");

        assertThat(play.getSteps()).hasSize(2);
        assertThat(play.getSteps().get(0).describe(null))
                .isEqualTo("userEvent.click(within(within(<div#workbench-root>).getByRole(\"menu\"))"
                           + ".getAllByText(\"Open\")[1])");
        assertThat(play.getSteps().get(1).describe(null))
                .isEqualTo("expect(within(<div#workbench-root>).getByTestId(\"result\"))"
                           + ".not.toHaveTextContent(\"Closed\")");
    }

    @Test
    void testCssString() {
        assertThat(Play.cssString("result")).isEqualTo("\"result\"");
        // Regression: quotes and backslashes weren't escaped, giving an invalid selector
        assertThat(Play.cssString("a\"b")).isEqualTo("\"a\\\"b\"");
        assertThat(Play.cssString("a\\b")).isEqualTo("\"a\\\\b\"");
        assertThat(Play.cssString("a\nb")).isEqualTo("\"a\\a b\"");
    }

    @Test
    void testFindByAddsAWait() {
        final Play play = new Play();
        play.findByRole("dialog", null);

        assertThat(play.getSteps()).hasSize(1);
        assertThat(play.getSteps().get(0).getKind()).isEqualTo(Kind.WAIT_FOR);
        assertThat(play.getSteps().get(0).describe(null))
                .isEqualTo("within(<div#workbench-root>).findByRole(\"dialog\")");
    }
}
