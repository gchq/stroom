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

class TestTextMatch {

    @Test
    void testExact() {
        final TextMatch match = TextMatch.exact("Save");
        assertThat(match.matches("Save")).isTrue();
        assertThat(match.matches("save")).isFalse();
        assertThat(match.matches("Save all")).isFalse();
        assertThat(match.matches(null)).isFalse();
        assertThat(match.describe()).isEqualTo("\"Save\"");
        assertThat(match.isExact()).isTrue();
        assertThatThrownBy(() -> TextMatch.exact(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testIgnoreCase() {
        assertThat(TextMatch.exactIgnoreCase("ok").matches("OK")).isTrue();
        assertThat(TextMatch.exactIgnoreCase("ok").matches("OK!")).isFalse();
        assertThat(TextMatch.exactIgnoreCase("ok").describe()).isEqualTo("/^ok$/i");
        assertThat(TextMatch.containingIgnoreCase("feed").matches("My Feed")).isTrue();
        assertThat(TextMatch.containingIgnoreCase("feed").describe()).isEqualTo("/feed/i");
    }

    @Test
    void testContainingStartingEnding() {
        assertThat(TextMatch.containing("CASE-1").matches("Open CASE-1 now")).isTrue();
        assertThat(TextMatch.containing("CASE-1").matches("case-1")).isFalse();
        assertThat(TextMatch.startingWith("Locked").matches("Locked until 3pm")).isTrue();
        assertThat(TextMatch.startingWith("Locked").matches("Not Locked")).isFalse();
        assertThat(TextMatch.endingWith("- Click for help").matches("Base URL - Click for help")).isTrue();
        // Characters with a meaning in a regex are escaped when shown
        assertThat(TextMatch.containing("How can I help?").describe()).isEqualTo("/How can I help\\?/");
        assertThat(TextMatch.startingWith("a.b").describe()).isEqualTo("/^a\\.b/");
        assertThat(TextMatch.endingWith("x").describe()).isEqualTo("/x$/");
    }

    @Test
    void testRegex() {
        final TextMatch either = TextMatch.regex("close|ok", "i");
        assertThat(either.matches("OK")).isTrue();
        assertThat(either.matches("Close")).isTrue();
        assertThat(either.matches("Cancel")).isFalse();
        assertThat(either.describe()).isEqualTo("/close|ok/i");

        final TextMatch anchored = TextMatch.regex("^(geo|reference)$");
        assertThat(anchored.matches("geo")).isTrue();
        assertThat(anchored.matches("geography")).isFalse();

        assertThat(TextMatch.regex("Feed\\s+contains\\s+TEST", "i").matches("feed   CONTAINS test")).isTrue();
        assertThat(TextMatch.regex("^Locked until .*, after 3 failed sign-ins$")
                .matches("Locked until 10:00, after 3 failed sign-ins")).isTrue();
        // The global flag makes test() stateful in JavaScript, so it's dropped
        final TextMatch global = TextMatch.regex("a", "g");
        assertThat(global.matches("a")).isTrue();
        assertThat(global.matches("a")).isTrue();
        assertThat(global.describe()).isEqualTo("/a/");
    }

    @Test
    void testMatchesValue() {
        assertThat(TextMatch.containing("x").matchesValue("axb")).isTrue();
        assertThat(TextMatch.containing("1").matchesValue(1)).isFalse();
        assertThat(TextMatch.containing("x").matchesValue(null)).isFalse();
    }

    @Test
    void testEscape() {
        assertThat(TextMatch.escape("a(b)[c]{d}|e^f$g.h*i+j?k/l\\m"))
                .isEqualTo("a\\(b\\)\\[c\\]\\{d\\}\\|e\\^f\\$g\\.h\\*i\\+j\\?k\\/l\\\\m");
    }
}
