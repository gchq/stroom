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

class TestFireEvent {

    @Test
    void testDescriptions() {
        final Play play = new Play();
        final Query tree = play.getByText("Countries");
        final FireEvent fireEvent = play.fireEvent();
        fireEvent.contextMenu(tree);
        fireEvent.mouseDown(tree, EventInit.create().ctrlKey().button(0));
        fireEvent.mouseUp(play.body());
        fireEvent.keyDown(play.body(), EventInit.create().key("u"));
        fireEvent.pointerMove(play.body(), EventInit.create().atCentreOf(tree));
        fireEvent.pointerUp(play.body(), EventInit.create().relativeTo(tree, 3, 10.5));
        fireEvent.change(tree, "x");
        fireEvent.input(tree, "y");
        fireEvent.event("scroll", tree, null);
        fireEvent.focus(tree);
        fireEvent.dblClick(tree);
        fireEvent.mouseMove(play.body(), EventInit.create().at(360, 150));

        final String q = "within(<div#workbench-root>).getByText(\"Countries\")";
        assertThat(play.getSteps()).extracting(step -> step.describe(null)).containsExactly(
                "fireEvent.contextMenu(" + q + ")",
                "fireEvent.mouseDown(" + q + ", { button: 0, ctrlKey: true })",
                "fireEvent.mouseUp(document.body)",
                "fireEvent.keyDown(document.body, { key: \"u\" })",
                "fireEvent.pointerMove(document.body, { at: centreOf(" + q + ") })",
                "fireEvent.pointerUp(document.body, { clientX: left + 3, clientY: top + 10.5 of " + q + " })",
                "fireEvent.change(" + q + ", { target: { value: \"x\" } })",
                "fireEvent.input(" + q + ", { target: { value: \"y\" } })",
                "fireEvent(" + q + ", new Event(\"scroll\"))",
                "fireEvent.focus(" + q + ")",
                "fireEvent.dblClick(" + q + ")",
                "fireEvent.mouseMove(document.body, { clientX: 360, clientY: 150 })");
    }

    @Test
    void testEventClass() {
        assertThat(FireEvent.eventClass("mousedown")).isEqualTo("MouseEvent");
        assertThat(FireEvent.eventClass("click")).isEqualTo("MouseEvent");
        assertThat(FireEvent.eventClass("dblclick")).isEqualTo("MouseEvent");
        assertThat(FireEvent.eventClass("contextmenu")).isEqualTo("MouseEvent");
        assertThat(FireEvent.eventClass("pointerup")).isEqualTo("PointerEvent");
        assertThat(FireEvent.eventClass("keydown")).isEqualTo("KeyboardEvent");
        assertThat(FireEvent.eventClass("focusin")).isEqualTo("FocusEvent");
        assertThat(FireEvent.eventClass("blur")).isEqualTo("FocusEvent");
        assertThat(FireEvent.eventClass("input")).isEqualTo("InputEvent");
        assertThat(FireEvent.eventClass("wheel")).isEqualTo("WheelEvent");
        assertThat(FireEvent.eventClass("change")).isEqualTo("Event");
    }

    @Test
    void testDefaults() {
        assertThat(FireEvent.defaultBubbles("click")).isTrue();
        assertThat(FireEvent.defaultBubbles("focus")).isFalse();
        assertThat(FireEvent.defaultBubbles("focusin")).isTrue();
        assertThat(FireEvent.defaultBubbles("mouseenter")).isFalse();
        assertThat(FireEvent.defaultCancelable("mousedown")).isTrue();
        assertThat(FireEvent.defaultCancelable("change")).isFalse();
        assertThat(FireEvent.defaultButton("contextmenu")).isEqualTo(2);
        assertThat(FireEvent.defaultButton("mousedown")).isZero();
        assertThat(FireEvent.defaultButtons("mousedown", 0)).isEqualTo(1);
        assertThat(FireEvent.defaultButtons("pointerdown", 2)).isEqualTo(2);
        assertThat(FireEvent.defaultButtons("mousedown", 1)).isEqualTo(4);
        assertThat(FireEvent.defaultButtons("mouseup", 0)).isZero();
        assertThat(FireEvent.defaultKeyCode("keydown", "u")).isEqualTo(85);
        assertThat(FireEvent.defaultKeyCode("keypress", "u")).isEqualTo(117);
        assertThat(FireEvent.defaultKeyCode("keyup", "Enter")).isEqualTo(13);
    }
}
