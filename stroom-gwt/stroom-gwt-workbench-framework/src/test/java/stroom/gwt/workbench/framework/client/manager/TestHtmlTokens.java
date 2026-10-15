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

package stroom.gwt.workbench.framework.client.manager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestHtmlTokens {

    @ParameterizedTest
    @ValueSource(strings = {"new", "play-fn", "wbm-icon-info", "wbm-tag-menu__icon", "a1"})
    void testIsSafeToken(final String value) {
        assertThat(HtmlTokens.isSafeToken(value)).isTrue();
        assertThat(HtmlTokens.requireSafeToken(value)).isEqualTo(value);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "x\"><img src=x onerror=alert(1)>",
            "a b",
            "Upper",
            "tag'",
            "a<b",
            "a&b",
            "a/b",
    })
    void testIsSafeToken_unsafe(final String value) {
        assertThat(HtmlTokens.isSafeToken(value)).isFalse();
        assertThatThrownBy(() -> HtmlTokens.requireSafeToken(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testIsSafeClassList() {
        assertThat(HtmlTokens.isSafeClassList("wbm-icon wbm-tag-menu__state-icon")).isTrue();
        assertThat(HtmlTokens.requireSafeClassList("wbm-icon")).isEqualTo("wbm-icon");
        assertThat(HtmlTokens.isSafeClassList("wbm-icon\" onclick=\"x")).isFalse();
        assertThat(HtmlTokens.isSafeClassList(" ")).isFalse();
        assertThat(HtmlTokens.isSafeClassList(null)).isFalse();
        assertThatThrownBy(() -> HtmlTokens.requireSafeClassList("a\"b"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "WAITING,waiting",
            "ACTIVE,active",
            "DONE,done",
            "ERROR,error",
            // Anything unexpected from the preview is shown as waiting
            "done,waiting",
            "'\"><script>',waiting",
            "'',waiting",
    })
    void testInteractionStepStatus(final String status, final String expected) {
        assertThat(HtmlTokens.interactionStepStatus(status)).isEqualTo(expected);
    }

    @Test
    void testInteractionStepStatus_null() {
        // Regression test: a step without a status used to throw a NullPointerException
        assertThat(HtmlTokens.interactionStepStatus(null)).isEqualTo(HtmlTokens.STEP_WAITING);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "minor,minor",
            "moderate,moderate",
            "serious,serious",
            "critical,critical",
            "Critical,critical",
    })
    void testAccessibilityImpact(final String impact, final String expected) {
        assertThat(HtmlTokens.accessibilityImpact(impact)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"unknown", "minor\"><img src=x onerror=alert(1)>", "minor critical"})
    void testAccessibilityImpact_invalid(final String impact) {
        assertThat(HtmlTokens.accessibilityImpact(impact)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "#fff",
            "#ffff",
            "#1EA7FD",
            "#1ea7fd80",
            "red",
            "RebeccaPurple",
            "transparent",
            "rgb(255, 0, 0)",
            "rgba(255, 0, 0, 0.5)",
            "rgb(255 0 0 / 50%)",
            "hsl(120deg 100% 50%)",
            "hsla(120, 100%, 50%, .3)",
            " #000000 ",
    })
    void testIsSafeColour(final String colour) {
        assertThat(HtmlTokens.isSafeColour(colour)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            // Regression test: these used to be put into the swatch's style
            "url(https://example.com/x.png)",
            "red; background-image: url(x)",
            "red\" onmouseover=\"alert(1)",
            "rgb(0, 0, url(x))",
            "rgb(0,0,0); position: fixed",
            "var(--x)",
            "expression(alert(1))",
            "#ggg",
            "#12345",
            "rgb(0, 0, 0",
            "red blue",
            "\\72 ed",
            "rgb(0 0 0 / calc(1))",
    })
    void testIsSafeColour_unsafe(final String colour) {
        assertThat(HtmlTokens.isSafeColour(colour)).isFalse();
    }
}
