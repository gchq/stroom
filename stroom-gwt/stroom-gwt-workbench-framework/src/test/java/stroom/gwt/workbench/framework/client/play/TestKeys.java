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

import stroom.gwt.workbench.framework.client.play.Keys.DefaultAction;
import stroom.gwt.workbench.framework.client.play.Keys.KeyAction;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestKeys {

    @Test
    void testParse_characters() {
        assertThat(describe(Keys.parse("ab"))).containsExactly("a", "b");
        assertThat(Keys.parse("")).isEmpty();
    }

    @Test
    void testParse_namedKeys() {
        assertThat(describe(Keys.parse("ab{ArrowDown}c"))).containsExactly("a", "b", "ArrowDown", "c");
        assertThat(describe(Keys.parse("{Enter}{Escape}{Tab}"))).containsExactly("Enter", "Escape", "Tab");
        // user-event 13's lower case names
        assertThat(describe(Keys.parse("{enter}{esc}{del}{space}"))).containsExactly("Enter", "Escape", "Delete", " ");
        assertThat(describe(Keys.parse("{Space}"))).containsExactly(" ");
        assertThat(describe(Keys.parse("{selectall}"))).containsExactly(Keys.SELECT_ALL);
    }

    @Test
    void testParse_codes() {
        assertThat(describe(Keys.parse("[KeyA][Digit1][ShiftLeft][Enter][Period]")))
                .containsExactly("a", "1", "Shift", "Enter", ".");
    }

    @Test
    void testParse_holdAndRelease() {
        final List<KeyAction> actions = Keys.parse("{Control>}a{/Control}");
        assertThat(describe(actions)).containsExactly("Control>1", "a", "/Control");
        assertThat(actions.get(0).isPress()).isTrue();
        assertThat(actions.get(0).isRelease()).isFalse();
        assertThat(actions.get(2).isPress()).isFalse();
        assertThat(actions.get(2).isRelease()).isTrue();

        assertThat(describe(Keys.parse("{a>3}"))).containsExactly("a>3");
        final KeyAction repeatedAndReleased = Keys.parse("{a>3/}").get(0);
        assertThat(repeatedAndReleased.getRepeat()).isEqualTo(3);
        assertThat(repeatedAndReleased.isRelease()).isTrue();
        assertThatThrownBy(() -> Keys.parse("{a>x}")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testParse_literals() {
        // {{ and [[ are literal, as is an unclosed or empty brace
        assertThat(describe(Keys.parse("{{a[["))).containsExactly("{", "a", "[");
        assertThat(describe(Keys.parse("{"))).containsExactly("{");
        assertThat(describe(Keys.parse("{}"))).containsExactly("{", "}");
    }

    @Test
    void testKeyCode() {
        assertThat(Keys.keyCode("a")).isEqualTo(65);
        assertThat(Keys.keyCode("A")).isEqualTo(65);
        assertThat(Keys.keyCode("7")).isEqualTo(55);
        assertThat(Keys.keyCode("Enter")).isEqualTo(13);
        assertThat(Keys.keyCode("Escape")).isEqualTo(27);
        assertThat(Keys.keyCode("ArrowDown")).isEqualTo(40);
        assertThat(Keys.keyCode(" ")).isEqualTo(32);
        assertThat(Keys.keyCode("Shift")).isEqualTo(16);
        assertThat(Keys.keyCode("F2")).isEqualTo(113);
        assertThat(Keys.keyCode(".")).isEqualTo(190);
        assertThat(Keys.keyCode("Unknown")).isZero();
    }

    @Test
    void testCode() {
        assertThat(Keys.code("a")).isEqualTo("KeyA");
        assertThat(Keys.code("W")).isEqualTo("KeyW");
        assertThat(Keys.code("1")).isEqualTo("Digit1");
        assertThat(Keys.code(" ")).isEqualTo("Space");
        assertThat(Keys.code("Shift")).isEqualTo("ShiftLeft");
        assertThat(Keys.code("Enter")).isEqualTo("Enter");
        assertThat(Keys.code("é")).isEmpty();
    }

    @Test
    void testCharCodeAndKeyPress() {
        assertThat(Keys.charCode("a")).isEqualTo(97);
        assertThat(Keys.charCode("Enter")).isEqualTo(13);
        assertThat(Keys.charCode("Escape")).isZero();
        assertThat(Keys.firesKeyPress("a", 0)).isTrue();
        assertThat(Keys.firesKeyPress("a", Keys.SHIFT)).isTrue();
        assertThat(Keys.firesKeyPress("a", Keys.CONTROL)).isFalse();
        assertThat(Keys.firesKeyPress("Enter", Keys.CONTROL)).isTrue();
        assertThat(Keys.firesKeyPress("Escape", 0)).isFalse();
    }

    @Test
    void testModifierBit() {
        assertThat(Keys.modifierBit("Shift")).isEqualTo(Keys.SHIFT);
        assertThat(Keys.modifierBit("Control")).isEqualTo(Keys.CONTROL);
        assertThat(Keys.modifierBit("Alt")).isEqualTo(Keys.ALT);
        assertThat(Keys.modifierBit("Meta")).isEqualTo(Keys.META);
        assertThat(Keys.modifierBit("a")).isZero();
    }

    @Test
    void testDefaultAction() {
        assertThat(Keys.defaultAction("a", 0)).isEqualTo(DefaultAction.TYPE);
        assertThat(Keys.defaultAction("a", Keys.SHIFT)).isEqualTo(DefaultAction.TYPE);
        assertThat(Keys.defaultAction("a", Keys.CONTROL)).isEqualTo(DefaultAction.SELECT_ALL);
        assertThat(Keys.defaultAction("A", Keys.META)).isEqualTo(DefaultAction.SELECT_ALL);
        assertThat(Keys.defaultAction("w", Keys.ALT)).isEqualTo(DefaultAction.NONE);
        assertThat(Keys.defaultAction("Tab", 0)).isEqualTo(DefaultAction.FOCUS_NEXT);
        assertThat(Keys.defaultAction("Tab", Keys.SHIFT)).isEqualTo(DefaultAction.FOCUS_PREVIOUS);
        assertThat(Keys.defaultAction("Enter", 0)).isEqualTo(DefaultAction.ENTER);
        assertThat(Keys.defaultAction("Backspace", 0)).isEqualTo(DefaultAction.DELETE_BACKWARD);
        assertThat(Keys.defaultAction("Delete", 0)).isEqualTo(DefaultAction.DELETE_FORWARD);
        assertThat(Keys.defaultAction("Home", 0)).isEqualTo(DefaultAction.MOVE_TO_START);
        assertThat(Keys.defaultAction("End", 0)).isEqualTo(DefaultAction.MOVE_TO_END);
        assertThat(Keys.defaultAction("Escape", 0)).isEqualTo(DefaultAction.NONE);
        assertThat(Keys.defaultAction("Shift", Keys.SHIFT)).isEqualTo(DefaultAction.NONE);
        assertThat(Keys.defaultAction(Keys.SELECT_ALL, 0)).isEqualTo(DefaultAction.SELECT_ALL);
    }

    private static List<String> describe(final List<KeyAction> actions) {
        return actions.stream().map(KeyAction::toString).toList();
    }
}
