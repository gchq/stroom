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

package stroom.gwt.workbench.framework.client.args;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestArgType {

    @Test
    void testBuilders() {
        final ArgType radio = ArgType.radio("variant", "a", "b")
                .description("Visual variant.")
                .defaultSummary("'a'");
        assertThat(radio.getName()).isEqualTo("variant");
        assertThat(radio.getControl()).isEqualTo(ControlType.RADIO);
        assertThat(radio.getOptions()).containsExactly("a", "b");
        assertThat(radio.getDescription()).isEqualTo("Visual variant.");
        assertThat(radio.getDefaultSummary()).isEqualTo("'a'");
        assertThat(radio.getTypeName()).isEqualTo("union");

        final ArgType range = ArgType.range("size", 1, 10, 0.5);
        assertThat(range.getMin()).isEqualTo(1);
        assertThat(range.getMax()).isEqualTo(10);
        assertThat(range.getStep()).isEqualTo(0.5);

        assertThat(ArgType.text("text").typeName("SvgImage").getTypeName()).isEqualTo("SvgImage");
        assertThat(ArgType.text("text").getTypeName()).isEqualTo("string");
    }

    @Test
    void testInvalid() {
        assertThatThrownBy(() -> ArgType.radio("variant")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ArgType.text("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ArgType.text(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testConvert() {
        assertThat(ArgType.bool("b").convert("true")).isEqualTo(true);
        assertThat(ArgType.bool("b").convert(false)).isEqualTo(false);
        assertThat(ArgType.number("n").convert("2.5")).isEqualTo(2.5);
        assertThat(ArgType.number("n").convert("abc")).isNull();
        assertThat(ArgType.range("r", 0, 10, 1).convert("3")).isEqualTo(3.0);
        assertThat(ArgType.check("c", "x", "y").convert("x")).isEqualTo(List.of("x"));
        assertThat(ArgType.check("c", "x", "y").convert(List.of("x", "y"))).isEqualTo(List.of("x", "y"));
        assertThat(ArgType.text("t").convert(5.0)).isEqualTo("5.0");
        assertThat(ArgType.action("onClick").convert("anything")).isNull();
        assertThat(ArgType.text("t").convert(null)).isNull();
    }

    @Test
    void testControlTypes() {
        assertThat(ControlType.CHECK.isMulti()).isTrue();
        assertThat(ControlType.RADIO.isMulti()).isFalse();
        assertThat(ControlType.SELECT.hasOptions()).isTrue();
        assertThat(ControlType.TEXT.hasOptions()).isFalse();
    }
}
