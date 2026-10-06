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
        // As user-event 14, names are looked up ignoring case, but user-event 13's other names
        // aren't on its keyboard, so are pressed as unknown keys
        assertThat(describe(Keys.parse("{enter}{esc}{del}{space}"))).containsExactly("Enter", "esc", "del", "space");
        assertThat(Keys.parse("{esc}").get(0).getCode()).isEqualTo(Keys.UNKNOWN);
        assertThat(describe(Keys.parse("{ }"))).containsExactly(" ");
    }

    @Test
    void testParse_codes() {
        assertThat(describe(Keys.parse("[KeyA][Digit1][ShiftLeft][Enter][Period]")))
                .containsExactly("a", "1", "Shift", "Enter", Keys.UNKNOWN);
        // The code is kept, e.g. for the right Shift key, or a code not on the keyboard
        assertThat(Keys.parse("[ShiftRight]").get(0).getCode()).isEqualTo("ShiftRight");
        assertThat(Keys.parse("[Period]").get(0).getCode()).isEqualTo("Period");
        assertThat(Keys.parse("{Shift}").get(0).getCode()).isEqualTo("ShiftLeft");
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
        // {{ and [[ are literal
        assertThat(describe(Keys.parse("{{a[["))).containsExactly("{", "a", "[");
        assertThat(describe(Keys.parse("a}b]"))).containsExactly("a", "}", "b", "]");
    }

    @Test
    void testParse_unclosedOrEmptyThrows() {
        // Regression: an unclosed or empty descriptor was typed literally, where user-event throws
        assertThatThrownBy(() -> Keys.parse("{Enter"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected \"}\"");
        assertThatThrownBy(() -> Keys.parse("abc[KeyA")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Keys.parse("{")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Keys.parse("{}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Keys.parse("{/}")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testParse_namesIgnoreCase() {
        // As user-event 14 looks key names up ignoring case
        assertThat(describe(Keys.parse("{enter}{ARROWDOWN}{backspace}{tab}")))
                .containsExactly("Enter", "ArrowDown", "Backspace", "Tab");
        assertThat(describe(Keys.parse("[keya][shiftleft>]"))).containsExactly("a", "Shift>1");
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
        assertThat(Keys.code("ArrowLeft")).isEqualTo("ArrowLeft");
        assertThat(Keys.code("AltGraph")).isEqualTo("AltRight");
        // As user-event 14's keyboard map, where shifted digits share the digit's code, braces
        // the brackets', and other punctuation isn't on it
        assertThat(Keys.code("!")).isEqualTo("Digit1");
        assertThat(Keys.code("(")).isEqualTo("Digit9");
        assertThat(Keys.code("{")).isEqualTo("BracketLeft");
        assertThat(Keys.code("}")).isEqualTo("BracketRight");
        assertThat(Keys.code(".")).isEqualTo(Keys.UNKNOWN);
        assertThat(Keys.code("-")).isEqualTo(Keys.UNKNOWN);
    }

    @Test
    void testCode_unknownKeys() {
        // Regression: an unknown key's code was its name (or empty), where user-event gives
        // 'Unknown'
        assertThat(Keys.code("é")).isEqualTo("Unknown");
        assertThat(Keys.code("Foo")).isEqualTo("Unknown");
        assertThat(Keys.code("F1")).isEqualTo("Unknown");
        assertThat(describe(Keys.parse("{Foo}"))).containsExactly("Foo");
    }

    @Test
    void testCharCodeAndKeyPress() {
        assertThat(Keys.charCode("a")).isEqualTo(97);
        assertThat(Keys.charCode("Enter")).isEqualTo(13);
        assertThat(Keys.charCode("Escape")).isZero();
        assertThat(Keys.firesKeyPress("a", 0)).isTrue();
        assertThat(Keys.firesKeyPress("a", Keys.SHIFT)).isTrue();
        assertThat(Keys.firesKeyPress("a", Keys.CONTROL)).isFalse();
        // As user-event 14: Control or Alt stop a keypress, even for Enter; Meta doesn't
        assertThat(Keys.firesKeyPress("Enter", Keys.CONTROL)).isFalse();
        assertThat(Keys.firesKeyPress("Enter", Keys.ALT)).isFalse();
        assertThat(Keys.firesKeyPress("Enter", Keys.META)).isTrue();
        assertThat(Keys.firesKeyPress("a", Keys.META)).isTrue();
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
        assertThat(Keys.defaultAction("A", Keys.CONTROL | Keys.SHIFT)).isEqualTo(DefaultAction.SELECT_ALL);
        // As user-event 14: Meta+A doesn't select all, and Meta doesn't stop typing
        assertThat(Keys.defaultAction("a", Keys.META)).isEqualTo(DefaultAction.TYPE);
        assertThat(Keys.defaultAction("w", Keys.ALT)).isEqualTo(DefaultAction.NONE);
        assertThat(Keys.defaultAction("w", Keys.CONTROL)).isEqualTo(DefaultAction.NONE);
        assertThat(Keys.defaultAction("Tab", 0)).isEqualTo(DefaultAction.FOCUS_NEXT);
        assertThat(Keys.defaultAction("Tab", Keys.SHIFT)).isEqualTo(DefaultAction.FOCUS_PREVIOUS);
        assertThat(Keys.defaultAction("Tab", Keys.CONTROL)).isEqualTo(DefaultAction.FOCUS_NEXT);
        assertThat(Keys.defaultAction("Enter", 0)).isEqualTo(DefaultAction.ENTER);
        assertThat(Keys.defaultAction("Enter", Keys.CONTROL)).isEqualTo(DefaultAction.NONE);
        assertThat(Keys.defaultAction("Backspace", 0)).isEqualTo(DefaultAction.DELETE_BACKWARD);
        // Keydown defaults don't depend on the modifiers
        assertThat(Keys.defaultAction("Backspace", Keys.CONTROL)).isEqualTo(DefaultAction.DELETE_BACKWARD);
        assertThat(Keys.defaultAction("Delete", 0)).isEqualTo(DefaultAction.DELETE_FORWARD);
        assertThat(Keys.defaultAction("Home", 0)).isEqualTo(DefaultAction.MOVE_TO_START);
        assertThat(Keys.defaultAction("End", 0)).isEqualTo(DefaultAction.MOVE_TO_END);
        assertThat(Keys.defaultAction("PageUp", 0)).isEqualTo(DefaultAction.PAGE_UP);
        assertThat(Keys.defaultAction("PageDown", 0)).isEqualTo(DefaultAction.PAGE_DOWN);
        assertThat(Keys.defaultAction("ArrowLeft", 0)).isEqualTo(DefaultAction.MOVE_LEFT);
        assertThat(Keys.defaultAction("ArrowRight", Keys.SHIFT)).isEqualTo(DefaultAction.MOVE_RIGHT);
        assertThat(Keys.defaultAction("ArrowUp", 0)).isEqualTo(DefaultAction.ARROW_UP);
        assertThat(Keys.defaultAction("ArrowDown", 0)).isEqualTo(DefaultAction.ARROW_DOWN);
        assertThat(Keys.defaultAction("Escape", 0)).isEqualTo(DefaultAction.NONE);
        assertThat(Keys.defaultAction("Shift", Keys.SHIFT)).isEqualTo(DefaultAction.NONE);
    }

    @Test
    void testKeyPressAction() {
        assertThat(Keys.keyPressAction("Enter")).isEqualTo(DefaultAction.ENTER);
        assertThat(Keys.keyPressAction("x")).isEqualTo(DefaultAction.TYPE);
        assertThat(Keys.keyDownAction("Enter", "Enter", 0)).isEqualTo(DefaultAction.NONE);
        assertThat(Keys.keyDownAction("x", "KeyX", 0)).isEqualTo(DefaultAction.NONE);
        // Control with the A key selects all, whatever the key value
        assertThat(Keys.keyDownAction(Keys.UNKNOWN, "KeyA", Keys.CONTROL)).isEqualTo(DefaultAction.SELECT_ALL);
    }

    private static List<String> describe(final List<KeyAction> actions) {
        return actions.stream().map(KeyAction::toString).toList();
    }
}
