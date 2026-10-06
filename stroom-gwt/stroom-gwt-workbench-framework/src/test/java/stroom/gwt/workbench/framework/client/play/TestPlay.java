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

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestPlay {

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
    void testGetByTextWithSelector() {
        final Play play = new Play();
        final String prefix = "within(<div#workbench-root>).";
        assertThat(play.getByText("Name", "label").describe())
                .isEqualTo(prefix + "getByText(\"Name\", { selector: \"label\" })");
        assertThat(play.getAllByText("Name", "label").describe())
                .isEqualTo(prefix + "getAllByText(\"Name\", { selector: \"label\" })");
        assertThat(play.queryByText("Name", "label").describe())
                .isEqualTo(prefix + "queryByText(\"Name\", { selector: \"label\" })");
        assertThat(play.queryAllByText("Name", "label").describe())
                .isEqualTo(prefix + "queryAllByText(\"Name\", { selector: \"label\" })");
        assertThat(play.findByText("Name", "label").describe())
                .isEqualTo(prefix + "getByText(\"Name\", { selector: \"label\" })");
        assertThat(play.findAllByText("Name", "label").describe())
                .isEqualTo(prefix + "getAllByText(\"Name\", { selector: \"label\" })");
        assertThat(play.getSteps()).hasSize(2);
    }

    @Test
    void testFindByAddsAWait() {
        final Play play = new Play();
        play.findByRole("dialog");

        assertThat(play.getSteps()).hasSize(1);
        assertThat(play.getSteps().get(0).getKind()).isEqualTo(Kind.WAIT_FOR);
        assertThat(play.getSteps().get(0).describe(null))
                .isEqualTo("within(<div#workbench-root>).findByRole(\"dialog\")");
    }

    @Test
    void testScreenScope() {
        final Play play = new Play();
        final Play screen = play.screen();
        screen.click(screen.getByRole("button", TextMatch.exactIgnoreCase("ok")));
        play.within(screen.getByRole("dialog")).click(play.getByText("Cancel"));

        assertThat(describeAll(play)).containsExactly(
                "userEvent.click(within(document.body).getByRole(\"button\", { name: /^ok$/i }))",
                // The query was made by the story's builder, so isn't within the dialog
                "userEvent.click(within(<div#workbench-root>).getByText(\"Cancel\"))");
    }

    @Test
    void testQueryVariants() {
        final Play play = new Play();
        assertThat(play.getAllByText("A").describe()).isEqualTo("within(<div#workbench-root>).getAllByText(\"A\")");
        assertThat(play.queryByTitle("T").describe()).isEqualTo("within(<div#workbench-root>).queryByTitle(\"T\")");
        assertThat(play.queryAllByRole("row").describe())
                .isEqualTo("within(<div#workbench-root>).queryAllByRole(\"row\")");
        assertThat(play.getByText(TextMatch.exact("Name"), "label").describe())
                .isEqualTo("within(<div#workbench-root>).getByText(\"Name\", { selector: \"label\" })");
        assertThat(play.getByPlaceholderText(TextMatch.containingIgnoreCase("search")).describe())
                .isEqualTo("within(<div#workbench-root>).getByPlaceholderText(/search/i)");
        assertThat(play.getByDisplayValue("x").describe())
                .isEqualTo("within(<div#workbench-root>).getByDisplayValue(\"x\")");
        assertThat(play.getByLabelText("L").describe())
                .isEqualTo("within(<div#workbench-root>).getByLabelText(\"L\")");
        assertThat(play.getByTestId("id").describe())
                .isEqualTo("within(<div#workbench-root>).getByTestId(\"id\")");
        assertThat(play.querySelectorAll(".row").describe())
                .isEqualTo("within(<div#workbench-root>).querySelectorAll(\".row\")");
        assertThat(play.queryByText("A").nth(2).describe())
                .isEqualTo("within(<div#workbench-root>).queryAllByText(\"A\")[2]");
        // Only the find methods add steps
        assertThat(play.getSteps()).isEmpty();
    }

    @Test
    void testFindVariants() {
        final Play play = new Play();
        final Play screen = play.screen();
        final Query dialog = screen.findByRole("dialog", "Settings");
        final Query rows = play.within(dialog).findAllByText(TextMatch.startingWith("Row"));

        assertThat(play.getSteps()).hasSize(2);
        assertThat(play.getSteps()).extracting(PlayStep::getKind).containsOnly(Kind.WAIT_FOR);
        assertThat(describeAll(play)).containsExactly(
                "within(document.body).findByRole(\"dialog\", { name: \"Settings\" })",
                "within(within(document.body).getByRole(\"dialog\", { name: \"Settings\" }))"
                + ".findAllByText(/^Row/)");
        // The queries returned are then known to be there
        assertThat(dialog.describe()).isEqualTo("within(document.body).getByRole(\"dialog\", { name: \"Settings\" })");
        assertThat(rows.describe()).endsWith(".getAllByText(/^Row/)");
    }

    @Test
    void testUserEventDescriptions() {
        final Play play = new Play();
        final Query field = play.getByLabelText("Name");
        play.dblClick(field);
        play.rightClick(field);
        play.hover(field);
        play.unhover(field);
        play.clear(field);
        play.type(field, "abc{Enter}");
        play.selectOptions(field, "a");
        play.selectOptions(field, "a", "b");
        play.tab(true);
        play.upload(field, "a.txt", "data", "text/plain");

        final String q = "within(<div#workbench-root>).getByLabelText(\"Name\")";
        assertThat(describeAll(play)).containsExactly(
                "userEvent.dblClick(" + q + ")",
                "userEvent.pointer({ keys: \"[MouseRight]\", target: " + q + " })",
                "userEvent.hover(" + q + ")",
                "userEvent.unhover(" + q + ")",
                "userEvent.clear(" + q + ")",
                "userEvent.type(" + q + ", \"abc{Enter}\")",
                "userEvent.selectOptions(" + q + ", \"a\")",
                "userEvent.selectOptions(" + q + ", [\"a\", \"b\"])",
                "userEvent.tab({ shift: true })",
                "userEvent.upload(" + q + ", new File([\"data\"], \"a.txt\", { type: \"text/plain\" }))");
    }

    @Test
    void testInvalidKeysFailWhenAdded() {
        final Play play = new Play();
        assertThatThrownBy(() -> play.keyboard("{a>x}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testWaitForTimeout() {
        final Play play = new Play();
        play.waitFor(() -> play.expect(() -> 1).toBe(1));
        play.waitFor(8000, () -> play.expect(() -> 1).toBe(1));

        assertThat(play.getSteps()).extracting(PlayStep::getTimeoutMillis).containsExactly(1000, 8000);
        assertThat(describeAll(play)).containsExactly(
                "waitFor(anonymous)",
                "waitFor(anonymous, { timeout: 8000 })");
        assertThatThrownBy(() -> play.waitFor(-1, () -> { }))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testSleepAndRun() {
        final List<String> ran = new ArrayList<>();
        final Play play = new Play();
        play.sleep(50);
        play.run("Record", () -> ran.add("ran"));

        assertThat(play.getSteps().get(0).getKind()).isEqualTo(Kind.SLEEP);
        assertThat(play.getSteps().get(0).getTimeoutMillis()).isEqualTo(50);
        assertThat(describeAll(play)).containsExactly("sleep(50)", "Record");
        play.getSteps().get(1).run(null);
        assertThat(ran).containsExactly("ran");
    }

    @Test
    void testSpy() {
        final Play play = new Play();
        Spies.startRendering();
        Spies.register("onTestPlaySpy").call("a");
        // The play's spy reads the calls of the rendering's spy with the same name
        assertThat(play.spy("onTestPlaySpy").getName()).isEqualTo("onTestPlaySpy");
        assertThat(play.spy("onTestPlaySpy").getCalls()).containsExactly(List.of("a"));
        assertThat(play.getSteps()).isEmpty();
        // Changing it while the play adds its steps adds a step
        play.spy("onTestPlaySpy").mockClear();
        assertThat(describeAll(play)).containsExactly("onTestPlaySpy.mockClear()");
        assertThat(play.spy("onTestPlaySpy").getCallCount()).isOne();
        play.getSteps().get(0).run(null);
        assertThat(play.spy("onTestPlaySpy").getCallCount()).isZero();
    }

    private static List<String> describeAll(final Play play) {
        final List<String> descriptions = new ArrayList<>();
        for (final PlayStep step : play.getSteps()) {
            descriptions.add(step.describe(null));
        }
        return descriptions;
    }
}
